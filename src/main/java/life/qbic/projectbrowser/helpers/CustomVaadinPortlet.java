/*******************************************************************************
 * QBiC Project qNavigator enables users to manage their projects.
 * Copyright (C) "2016”  Christopher Mohr, David Wojnar, Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package life.qbic.projectbrowser.helpers;


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.portlet.PortletException;
import javax.portlet.PortletSession;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;
import javax.servlet.http.HttpServletResponse;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.DataSetFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import life.qbic.projectbrowser.model.DatasetBean;
import life.qbic.projectbrowser.model.ExperimentBean;
import life.qbic.projectbrowser.model.ProjectBean;
import life.qbic.projectbrowser.model.SampleBean;

import life.qbic.openbis.openbisclient.OpenBisClient;

import com.vaadin.server.DeploymentConfiguration;
import com.vaadin.server.ServiceException;
import com.vaadin.server.VaadinPortlet;
import com.vaadin.server.VaadinPortletService;
import com.vaadin.server.VaadinRequest;

/**
 * 
 * copied from:
 * https://github.com/jamesfalkner/vaadin-liferay-beacon-demo/blob/master/src/main/java/
 * com/liferay/mavenizedbeacons/CustomVaadinPortlet.java This custom Vaadin portlet allows for
 * serving Vaadin resources like theme or widgetset from its web context (instead of from ROOT).
 * Usually it doesn't need any changes.
 * 
 */
public class CustomVaadinPortlet extends VaadinPortlet {
  private static final long serialVersionUID = -13615405654173335L;

  private class CustomVaadinPortletService extends VaadinPortletService {

    private static final long serialVersionUID = -6282242585931296999L;

    public CustomVaadinPortletService(final VaadinPortlet portlet,
                                      final DeploymentConfiguration config) throws ServiceException {
      super(portlet, config);
    }


    /**
     * This method is used to determine the uri for Vaadin resources like theme or widgetset.
     * It's overriden to point to this web application context instead of ROOT context.
     */
    @Override
    public String getStaticFileLocation(final VaadinRequest request) {
      // return super.getStaticFileLocation(request);
      // self contained approach:
      return request.getContextPath();
    }
  }

  private static final Logger LOG = LogManager.getLogger(CustomVaadinPortletService.class);
  public static final String RESOURCE_ID = "mainPortletResourceId";
  public static final String RESOURCE_ATTRIBUTE = "resURL";

  @Override
  protected void doDispatch(javax.portlet.RenderRequest request, javax.portlet.RenderResponse response)
          throws PortletException, IOException {
    if (request.getPortletSession().getAttribute(RESOURCE_ATTRIBUTE,
        PortletSession.APPLICATION_SCOPE) == null) {
      ResourceURL resURL = response.createResourceURL();
      // get Resource ID ?
      resURL.setResourceID(RESOURCE_ID);
      request.getPortletSession().setAttribute(RESOURCE_ATTRIBUTE, resURL.toString(),
          PortletSession.APPLICATION_SCOPE);
    }
    super.doDispatch(request, response);
  }

  @Override
  public void serveResource(javax.portlet.ResourceRequest request, ResourceResponse response)
          throws PortletException, IOException {
    // System.out.println(request.getResourceID());
    // System.out.println(RESOURCE_ID);
    if (request.getResourceID().equals("openbisUnreachable")) {
      response.setContentType("text/plain");
      response.setProperty(ResourceResponse.HTTP_STATUS_CODE, String.valueOf(HttpServletResponse.SC_GATEWAY_TIMEOUT));
      response.getWriter().append(
          "Internal Error.\nRetry later or contact your project manager.\n" + "Time: "
              + (new Date()).toString());
    } else if (request.getResourceID().equals(RESOURCE_ID)) {
      serveDownloadResource(request, response);
    } else {
      super.serveResource(request, response);
    }
  }

  //used!
  public void serveDownloadResource(javax.portlet.ResourceRequest request, ResourceResponse response)
          throws PortletException, IOException {
    OpenBisClient openBisClient =
        (OpenBisClient) request.getPortletSession().getAttribute("openbisClient",
            PortletSession.APPLICATION_SCOPE);
    String liferayUserId = request.getRemoteUser();
    LOG.info(String.format("Liferay User %s is downloading...", liferayUserId));

    Object bean = request.getPortletSession().getAttribute("qbic_download",
            PortletSession.APPLICATION_SCOPE);

    if (bean instanceof ProjectBean) {
      serveProject2((ProjectBean) bean, new TarWriter(), response, openBisClient);
    } else if (bean instanceof ExperimentBean) {
      serveExperiment2((ExperimentBean) bean, new TarWriter(), response, openBisClient);
    } else if (bean instanceof SampleBean) {
      serveSample2((SampleBean) bean, new TarWriter(), response, openBisClient);
    } else if (bean instanceof Map<?, ?>) {
      HashMap<String, SimpleEntry<String, Long>> entry = null;

      try {
        entry = (HashMap<String, SimpleEntry<String, Long>>) bean;
      } catch (Exception e) {
        LOG.error("portlet session attribute 'qbic_download' contains wrong entry set",
            e.getStackTrace());
        response.setContentType("text/javascript");
        response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                String.valueOf(HttpServletResponse.SC_BAD_REQUEST));
        response.getWriter().append("Please select at least one dataset for download");
        return;
      }
      serveEntries(entry, new TarWriter(), response, openBisClient);

    } else {
      response.setContentType("text/javascript");
      response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
              String.valueOf(HttpServletResponse.SC_BAD_REQUEST));
      response.getWriter().append("Please select at least one dataset for download");
    }
  }

  /**
   * Note: the provided stream will be closed.
   * @param entries
   * @param writer writes
   * @param response writer writes to its outputstream
   * @param openbisClient
   */
  private void serveEntries(HashMap<String, SimpleEntry<String, Long>> entries, TarWriter writer,
                            ResourceResponse response, OpenBisClient openbisClient) {

    if (entries.keySet().size() > 1) {

      String timestamp = new SimpleDateFormat("yyyyMMddhhmm").format(new Date());

      String filename = "qbicdatasets" + timestamp + ".tar";

      // response.setContentType(writer.getContentType());
      StringBuilder sb = new StringBuilder("attachement; filename=\"");
      sb.append(filename);
      sb.append("\"");
      response.setProperty("Content-Disposition", sb.toString());

      long tarFileLength = writer.computeTarLength2(entries);

      LOG.debug("tar file length: "+String.valueOf(tarFileLength));

      // Integer fileSize = (int) (long) tarFileLength;

      // For some reason setContentLength does not work
      response.setProperty("Content-Length", String.valueOf(tarFileLength));

      // Seems to work with Liferay 6.2, when using setProperty some tarred files are corrupt,
      // unexpected end of file
      // Didn't work with Liferay 6.2 either
      // However deactivating GzipFilter by setting
      // com.liferay.portal.servlet.filters.gzip.GZipFilter=false seems to fix it
      // Probably the Content Length can't be set in the header of this Filter
      // response.setContentLength(fileSize);


      try {
        writer.setOutputStream(response.getPortletOutputStream());

      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      Set<Entry<String, SimpleEntry<String, Long>>> entrySet = entries.entrySet();
      Iterator<Entry<String, SimpleEntry<String, Long>>> it = entrySet.iterator();
      while (it.hasNext()) {
        Entry<String, SimpleEntry<String, Long>> entry = it.next();
        String entryKey = entry.getKey().replaceFirst(entry.getValue().getKey() + "/", "");
        String[] splittedFilePath = entryKey.split("/");

        if ((splittedFilePath.length == 0) || (splittedFilePath == null)) {
          // writer.writeEntry(entry.getValue().getKey() + "/" + entry.getKey(),
          writer.writeEntry(entry.getKey(), openbisClient.getDatasetStream(entry.getValue()
              .getKey()), entry.getValue().getValue());
        } else {
          // writer.writeEntry(entry.getValue().getKey() + "/" + entry.getKey(), openbisClient
          writer.writeEntry(splittedFilePath[splittedFilePath.length - 1], openbisClient
              .getDatasetStream(entry.getValue().getKey(), entryKey), entry.getValue().getValue());
        }
      }
      writer.closeStream();

    } else {
      Set<Entry<String, SimpleEntry<String, Long>>> entrySet = entries.entrySet();
      Iterator<Entry<String, SimpleEntry<String, Long>>> it = entrySet.iterator();

      // response.setContentType(writer.getContentType());

      while (it.hasNext()) {
        Entry<String, SimpleEntry<String, Long>> entry = it.next();
        String entryKey = entry.getKey().replaceFirst(entry.getValue().getKey() + "/", "");
        String[] splittedFilePath = entryKey.split("/");

        InputStream datasetStream =
            openbisClient.getDatasetStream(entry.getValue().getKey(), entryKey);

        StringBuilder sb = new StringBuilder("attachement; filename=\"");
        sb.append(splittedFilePath[splittedFilePath.length - 1]);
        sb.append("\"");
        response.setProperty("Content-Disposition", sb.toString());
        response.setProperty("Content-Type",
            getPortletContext().getMimeType((splittedFilePath[splittedFilePath.length - 1])));
        // Integer fileSize = (int) (long) entry.getValue().getValue();

        response.setProperty("Content-Length", String.valueOf(entry.getValue().getValue()));

        // response.setContentLength(fileSize);

        byte[] buffer = new byte[32768];
        int bytesRead;
        try {
          while ((bytesRead = datasetStream.read(buffer)) != -1) {
            response.getPortletOutputStream().write(buffer, 0, bytesRead);
          }

        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

      }
    }
  }

  void serveProject2(ProjectBean bean, TarWriter writer, ResourceResponse response,
                     OpenBisClient openbisClient) {

    long startTime = System.nanoTime();
    List<DataSet> datasets = openbisClient.getDataSetsOfProject(bean.getId());
    long endTime = System.nanoTime();
    LOG.debug(String.format("getDataSetsOfProject took %f s", ((endTime - startTime) / 1000000000.0)));

    startTime = System.nanoTime();
    List<String> datasetCodes = datasets.stream().map(DataSet::getCode).collect(Collectors.toList());
    List<DataSetFile> res = openbisClient
            .listDataSetFiles(datasetCodes).stream()
            .filter(dsf -> !dsf.getPath().isEmpty() || !dsf.getPath().equals("original"))
            .collect(Collectors.toList());
    LOG.debug(String.format("listDataSetFiles took %f s", ((endTime - startTime) / 1000000000.0)));

    download(res, writer, response, openbisClient, bean.getCode());
  }

  void serveExperiment2(ExperimentBean bean, TarWriter writer, ResourceResponse response,
                        OpenBisClient openbisClient) {

    long startTime = System.nanoTime();
    List<DataSet> datasets = openbisClient.getDataSetsOfExperimentByIdentifier(bean.getId());
    long endTime = System.nanoTime();
    LOG.debug(String.format("getDataSetsOfExperimentByIdentifier took %f s", ((endTime - startTime) / 1000000000.0)));

    startTime = System.nanoTime();
    List<String> datasetCodes = datasets.stream().map(DataSet::getCode).collect(Collectors.toList());
    List<DataSetFile> res = openbisClient
            .listDataSetFiles(datasetCodes).stream()
            .filter(dsf -> !dsf.getPath().isEmpty() || !dsf.getPath().equals("original"))
            .collect(Collectors.toList());
    endTime = System.nanoTime();
    LOG.debug(String.format("listDataSetFiles took %f s", ((endTime - startTime) / 1000000000.0)));

    download(res, writer, response, openbisClient, bean.getCode());
  }

  void serveSample2(SampleBean bean, TarWriter writer, ResourceResponse response,
                    OpenBisClient openbisClient) {

    long startTime = System.nanoTime();
    List<DataSet> datasets = openbisClient.getDataSetsOfSampleByIdentifier(bean.getId());
    long endTime = System.nanoTime();
    LOG.debug(String.format("getDataSetsOfProjectByIdentifier took %f s", ((endTime - startTime) / 1000000000.0)));

    startTime = System.nanoTime();
    List<String> datasetCodes = datasets.stream().map(DataSet::getCode).collect(Collectors.toList());
    List<DataSetFile> res = openbisClient.listDataSetFiles(datasetCodes)
            .stream()
            .filter(dsf -> !dsf.getPath().isEmpty() || !dsf.getPath().equals("original"))
            .collect(Collectors.toList());
    endTime = System.nanoTime();
    LOG.debug(String.format("listDataSetFiles took %f s", ((endTime - startTime) / 1000000000.0)));

    download(res, writer, response, openbisClient, bean.getCode());
  }

  void download(List<DataSetFile> res, TarWriter writer, ResourceResponse response,
                OpenBisClient openbisClient, String openbisCode) {

    Map<String, SimpleEntry<String, Long>> entries = convertQueryTabelModelToEntries(res);
    String filename = openbisCode + ".tar";

    writeToClient(response, writer, filename, entries, openbisClient, openbisCode);
  }


  void writeToClient(ResourceResponse response, TarWriter writer, String filename,
      Map<String, SimpleEntry<String, Long>> entries, OpenBisClient openbisClient,
      String openbisCode) {
    response.setContentType(writer.getContentType());
    StringBuilder sb = new StringBuilder("attachement; filename=\"");
    sb.append(filename);
    sb.append("\"");
    response.setProperty("Content-Disposition", sb.toString());

    long tarFileLength = writer.computeTarLength2(entries);
    LOG.debug(String.valueOf(tarFileLength));
    // response.setContentLength((int) tarFileLength);
    // For some reason setContentLength did not work as expected (liferay 6.1.2)
    response.setProperty("Content-Length", String.valueOf(tarFileLength));
    try {
      writer.setOutputStream(response.getPortletOutputStream());
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    Set<Entry<String, SimpleEntry<String, Long>>> entrySet = entries.entrySet();
    Iterator<Entry<String, SimpleEntry<String, Long>>> it = entrySet.iterator();
    while (it.hasNext()) {
      Entry<String, SimpleEntry<String, Long>> entry = it.next();
      String entryKey = entry.getKey().replaceFirst(entry.getValue().getKey() + "/", "");
      String[] splittedFilePath = entryKey.split("/");

      if ((splittedFilePath.length == 0) || (splittedFilePath == null)) {
        writer.writeEntry(openbisCode + "/" + entry.getKey(),
            openbisClient.getDatasetStream(entry.getValue().getKey()), entry.getValue().getValue());
      } else {
        writer.writeEntry(openbisCode + "/" + entry.getKey(), openbisClient.getDatasetStream(entry
            .getValue().getKey(), entryKey), entry.getValue().getValue());
      }
    }
    writer.closeStream();
  }

  private Map<String, SimpleEntry<String, Long>> convertQueryTabelModelToEntries(List<DataSetFile> datasetFiles) {
    Map<String, SimpleEntry<String, Long>> entries = new HashMap<>();

    for (DataSetFile dsf : datasetFiles) {
      String filePath =
              dsf.getPath().startsWith("original") ? dsf.getPath().substring(9) : dsf.getPath();

      entries.put(filePath, new SimpleEntry<>(dsf.getDataSetPermId().toString(), dsf.getFileLength()));
    }

    return entries;
  }

  /**
   * Given datasetbean (and its children) is included into the entry, which can be used for download
   * 
   * @param db
   * @param entries
   * @return
   */
  Map<String, SimpleEntry<String, Long>> addEntry(DatasetBean db,
      Map<String, SimpleEntry<String, Long>> entries) {
    StringBuilder sb = new StringBuilder(db.getCode());
    sb.append("/");
    sb.append(db.getName());
    if (db.getIsDirectory()) {
      for (DatasetBean child : db.getChildren()) {
        addChildrensEntry(child, entries, sb.toString());
      }
    } else {
      entries.put(sb.toString(),
          new SimpleEntry<String, Long>(db.getCode(), db.getFileSize()));
    }
    return entries;
  }

  /**
   * Helper function of addEntry. Adds name of parent db to children.
   * 
   * @param db
   * @param entries
   * @param name
   * @return
   */
  private Map<String, SimpleEntry<String, Long>> addChildrensEntry(DatasetBean db,
      Map<String, SimpleEntry<String, Long>> entries, String name) {
    StringBuilder sb = new StringBuilder(name);
    sb.append("/");
    sb.append(db.getName());
    if (db.getIsDirectory()) {
      for (DatasetBean child : db.getChildren()) {
        addChildrensEntry(child, entries, sb.toString());
      }
    } else {
      entries.put(sb.toString(),
          new SimpleEntry<String, Long>(db.getCode(), db.getFileSize()));
    }
    return entries;

  }

  @Override
  protected VaadinPortletService createPortletService(
      final DeploymentConfiguration deploymentConfiguration) throws ServiceException {
    final CustomVaadinPortletService customVaadinPortletService =
        new CustomVaadinPortletService(this, deploymentConfiguration);
    customVaadinPortletService.init();
    return customVaadinPortletService;
  }
}
