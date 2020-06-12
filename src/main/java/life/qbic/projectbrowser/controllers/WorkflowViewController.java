/*******************************************************************************
 * QBiC Project qNavigator enables users to manage their projects. Copyright (C) "2016” Christopher
 * Mohr, David Wojnar, Andreas Friedrich
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package life.qbic.projectbrowser.controllers;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.DataSetFile;
import com.vaadin.data.Container;
import com.vaadin.data.util.BeanItemContainer;

import de.uni_tuebingen.qbic.beans.DatasetBean;

import life.qbic.openbis.openbisclient.OpenBisClient;
import life.qbic.projectbrowser.helpers.Utils;
import life.qbic.projectbrowser.views.*;
import life.qbic.xml.manager.XMLParser;
import life.qbic.xml.properties.Property;
import life.qbic.xml.properties.PropertyType;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import submitter.SubmitFailedException;
import submitter.Submitter;
import submitter.Workflow;
import submitter.parameters.Parameter;
import submitter.parameters.ParameterSet;

import javax.xml.bind.JAXBException;
import java.io.Serializable;
import java.net.ConnectException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class WorkflowViewController {
  private DataHandler datahandler;
  private Submitter submitter;

  private static final Logger LOG = LogManager.getLogger(WorkflowViewController.class);
  private String user;
  private final String wf_id = "Q_WF_ID";
  private final String wf_version = "Q_WF_VERSION";
  private final String wf_executer = "Q_WF_EXECUTED_BY";
  private final String wf_started = "Q_WF_STARTED_AT";
  private final String wf_status = "Q_WF_STATUS";
  private final String wf_name = "Q_WF_NAME";
  private final String openbis_dss = "DSS1"; // ToDo: This shouldn't be hardcoded but yet it is

  // used by Microarray QC Workflow. See function mapExperimentalProperties
  private Map<String, String> expProps;
  private Set<String> expFactors;
  private String projectID;
  private List<String> fileNames;
  private List<String> expDesignWfs = new ArrayList<String>(Arrays.asList("Microarray QC"));

  private enum workflow_statuses {
    RUNNING
  };

  public WorkflowViewController(Submitter submitter, DataHandler datahandler, String user) {
    this.datahandler = datahandler;
    this.submitter = submitter;
    this.user = user;
  }

  /**
   * Returns a Container with the informations of de.uni_tuebingen.qbic.beans.DatasetBean.
   * 
   * @param datasets
   * @return
   */
  public Container fillTable(List<DataSet> datasets, String projectID) {
    HashMap<String, DataSet> dataMap = new HashMap<>();
    BeanItemContainer<DatasetBean> container = new BeanItemContainer<>(DatasetBean.class);

    for (DataSet ds : datasets)
      dataMap.put(ds.getCode(), ds);

    List<life.qbic.projectbrowser.model.DatasetBean> datasetBeans = datahandler.queryDatasetsForFiles(datasets);
    List<String> fileNames = new ArrayList<>();

    for (life.qbic.projectbrowser.model.DatasetBean bean : datasetBeans) {
      fileNames.add(bean.getFileName());

      DatasetBean newBean = new DatasetBean(bean.getFileName(),
          dataMap.get(bean.getCode()).getType().getCode(), bean.getCode(), bean.getDssPath(),
          dataMap.get(bean.getCode()).getSample().getIdentifier().toString());

      if (dataMap.get(bean.getCode()).getProperties() != null) {
        newBean.setProperties(dataMap.get(bean.getCode()).getProperties());
      }

      container.addBean(newBean);
    }

    this.projectID = projectID;
    this.fileNames = fileNames;

    return container;
  }

  /**
   * Register a new Workflow experiment in openBIS. Should be done when starting the workflow.
   * Experiment name is automatically created from the project name and the number of existing
   * experiments in that project. Standard workflow experiment fields are also initialized.
   * 
   * @param space space code
   * @param project project code
   * @param typecode openbis type code of the workflow
   * @param wfName name of the workflow
   * @param wfVersion version of the workflow
   * @param userID the user that starts the workflow
   * @param qProperties
   * @return Code of the newly registered experiment
   */
  public String registerWFExperiment(String space, String project, String typecode, String wfName,
      String wfVersion, String userID, String qProperties) {
    int last = 0;
    for (Experiment e : datahandler.getOpenBisClient().getExperimentsOfProjectByIdentifier(
        (new StringBuilder("/")).append(space).append("/").append(project).toString())) {
      String[] codeSplit = e.getCode().split("E");
      String number = codeSplit[codeSplit.length - 1];
      int num = 0;
      try {
        num = Integer.parseInt(number);
      } catch (NumberFormatException ex) {
      }
      last = Math.max(num, last);
    }

    LOG.debug("Space: " + space);
    LOG.debug("Project: " + project);
    LOG.debug("StringBuilder: " + new StringBuilder("/").append(space).append("/").append(project).toString());

    String code = String.format("%sE%d", project, (last+1));

    LOG.debug("Code: " + code);

    Map<String, Object> params = new HashMap<>();
    params.put("code", code);
    params.put("type", typecode);
    params.put("project", project);
    params.put("space", space);

    Map<String, Object> properties = new HashMap<>();
    properties.put(wf_name, wfName);
    properties.put(wf_version, wfVersion);
    properties.put(wf_executer, userID);
    properties.put(wf_started, Utils.getTime());
    properties.put(wf_status, workflow_statuses.RUNNING.toString());
    properties.put("Q_PROPERTIES", qProperties);

    params.put("properties", properties);

    datahandler.getOpenBisClient().triggerIngestionService("register-exp", params);
    return code;
  }


  public List<String> getConnectedSamples(List<DatasetBean> datasetBeans) {
    List<String> sampleIDs = new ArrayList<String>();
    for (DatasetBean bean : datasetBeans) {
      sampleIDs.add(bean.getSampleIdentifier());
    }
    return sampleIDs;
  }

  public String registerWFSample(String space, String project, String experiment, String typecode,
      List<String> parents, List<DatasetBean> datasets) {
    int last = 0;
    for (Sample s : datahandler.getOpenBisClient().getSamplesOfExperiment(
            (new StringBuilder("/"))
                    .append(space).append("/")
                    .append(project).append("/")
                    .append(experiment).toString())) {
      String[] codeSplit = s.getCode().split("R");
      String number = codeSplit[codeSplit.length - 1];
      int num = 0;

      try {
        num = Integer.parseInt(number);
      } catch (NumberFormatException ex) { }

      last = Math.max(num, last);
    }

    String code = String.format("%sR%d", experiment, (last+1));

    Map<String, Object> params = new HashMap<>();
    params.put("code", code);
    params.put("type", typecode);

    params.put("sample_class", "");
    params.put("parents", parents);

    params.put("project", project);
    params.put("space", space);
    params.put("experiment", experiment);

    StringBuilder result = new StringBuilder();
    result.append("Input Files: ");

    for (DatasetBean b : datasets) {
      result.append(b.getFileName());
      result.append(",");
    }

    String secName = result.length() > 0 ? result.substring(0, result.length() - 1) : "";

    Map<String, Object> properties = new HashMap<>();
    properties.put("Q_ADDITIONAL_INFO", secName);
    params.put("properties", properties);

    datahandler.getOpenBisClient().triggerIngestionService("register-samp", params);
    return code;
  }


  /**
   * Set the workflow ID for a workflow experiment. This must be the experiment whose code has been
   * given to the submitter to ensure correct registration of the results and log files.
   * 
   * @param space space code
   * @param project project code
   * @param experiment experiment code
   * @param wfID workflow ID created by the submitter for this workflow experiment
   */
  public void setWorkflowID(String space, String project, String experiment, String wfID) {
    Map<String, Object> params = new HashMap<>();
    params.put("identifier", "/" + space + "/" + project + "/" + experiment);
    Map<String, Object> properties = new HashMap<>();
    properties.put(wf_id, wfID);
    params.put("properties", properties);
    datahandler.getOpenBisClient().triggerIngestionService( "update-experiment-metadata", params);
  }

  /**
   * returns all known workflows, that can be executed with the given filetype
   * 
   * @param fileType
   * @return
   */
  public BeanItemContainer<Workflow> suitableWorkflows(String fileType) {
    try {
      return submitter.getAvailableSuitableWorkflows(fileType);
    } catch (Exception e) {
      e.printStackTrace();
      return new BeanItemContainer<Workflow>(Workflow.class);
    }
  }

  /**
   * returns all known workflows, that can be executed with one of the given filetypes
   * 
   * @param fileType
   * @return
   */
  public BeanItemContainer<Workflow> suitableWorkflows(List<String> fileType) {
    try {
      BeanItemContainer<Workflow> wfs = submitter.getAvailableSuitableWorkflows(fileType);
      for (Workflow wf : wfs.getItemIds()) {
        if (expDesignWfs.contains(wf.getName()))
          // ToDo: Add other workflows to the list that are needed
          mapExperimentalProperties(projectID, fileNames);
      }
      return wfs;
    } catch (Exception e) {
      e.printStackTrace();
      // LOG.debug("No suitable workflows founds.");
      return new BeanItemContainer<Workflow>(Workflow.class);
    }
  }

  /**
   * returns all known workflows, that can be executed with one of the given filetypes
   * 
   * @param experimentType
   * @return
   */
  public BeanItemContainer<Workflow> suitableWorkflowsByExperimentType(String experimentType) {
    try {
      return submitter.getWorkflowsByExperimentType(experimentType);
    } catch (Exception e) {
      e.printStackTrace();
      return new BeanItemContainer<Workflow>(Workflow.class);
    }
  }


  public BeanItemContainer<DatasetBean> getcontainer(String type, String id) {
    List<DataSet> datasets = new ArrayList<>();

    switch (type) {
      case "project":
        // datasets = openbis.getClientDatasetsOfProjectByIdentifierWithSearchCriteria(id);
        datasets = datahandler.getOpenBisClient().getDataSetsOfProject(id);
        break;

      case "experiment":
        // TODO
        break;

      case "sample":
        // TODO
        break;

      default:
        break;
    }
    return (BeanItemContainer<DatasetBean>) fillTable(datasets, id);
  }

  private void mapExperimentalProperties(String id, List<String> fileNames) {
    Map<String, String> fileProps = new HashMap<>();
    Set<String> factorNames       = new HashSet<>();
    Set<String> secondaryNames    = new HashSet<>();
    Set<String> factorLabels      = datahandler.getFactorLabels();

    for (Sample s : datahandler.getOpenBisClient().getSamplesOfProject(id)) {
      List<String> matches = getMatchingStrings(fileNames, s.getCode());
      List<Property> properties = new ArrayList<>();

      for (String label : factorLabels) {
        Property f = datahandler.getFactorsForLabelsAndSamples().get(new ImmutablePair<>(label, s.getCode()));

        if (f != null) {
          properties.add(f);
        }
      }

      if (!matches.isEmpty()) {
        for (String match : matches) {
          StringBuilder row = new StringBuilder();
          String externalID    = s.getProperties().get("Q_EXTERNALDB_ID");
          String secondaryName = s.getProperties().get("Q_SECONDARY_NAME");

          while (secondaryNames.contains(secondaryName))
            secondaryName += "1";
          secondaryNames.add(secondaryName);
          row.append(secondaryName);

          for (Property f : properties) {
            factorNames.add(f.getLabel());
            String val = f.getValue();

            if (f.hasUnit())
              val += f.getUnit();
            row.append("\t" + val);
          }
          fileProps.put(match, row.toString());
        }
      }
    }
    this.expProps = fileProps;
    this.expFactors = factorNames;

/*
    Map<String, Object> params = new HashMap<>();
    List<String> codes = new ArrayList<>();

    for (Sample s : datahandler.getOpenBisClient().getSamplesOfProject(id))
      codes.add(s.getCode());

    params.put("codes", codes);
    QueryTableModel res =
        datahandler.getOpenBisClient().getAggregationService("get-property-tsv", params);



    for (Serializable[] ss : res.getRows()) {
      String sampleCode = (String) ss[0];
      List<Property> properties = new ArrayList<>();

      for (String label : factorLabels) {
        Property f =
            datahandler.getFactorsForLabelsAndSamples().get(new ImmutablePair<>(label, sampleCode));
        if (f != null) {
          properties.add(f);
        }
      }

      List<String> matches = getMatchingStrings(fileNames, sampleCode);
      if (!matches.isEmpty()) {
        for (String match : matches) {
          StringBuilder row = new StringBuilder();
          String extID = (String) ss[1];// how to use this if it is preferred over secondary name?
          String secondaryName = (String) ss[2];
          while (secondaryNames.contains(secondaryName))
            secondaryName += "1";
          secondaryNames.add(secondaryName);
          row.append(secondaryName);
          for (Property f : properties) {
            factorNames.add(f.getLabel());
            String val = f.getValue();
            if (f.hasUnit())
              val += f.getUnit();
            row.append("\t" + val);
          }
          fileProps.put(match, row.toString());
        }
      }
    }
    this.expProps = fileProps;
    this.expFactors = factorNames;
*/
  }

  /**
   * Finds the the matching strings in the list, e.g. sample codes in file names
   *
   * @param list The list of strings to check
   * @param substring The regular expression to use
   * @return List of matching Strings
   */
  static List<String> getMatchingStrings(List<String> list, String substring) {
    List<String> res = new ArrayList<String>();
    for (String s : list) {
      if (s.contains(substring)) {
        res.add(s);
      }
    }
    return res;
  }

  public Submitter getSubmitter() {
    return submitter;
  }

  public String submitAndRegisterWf(String type, String id, Workflow workflow,
      List<DatasetBean> selectedDatasets)
      throws ConnectException, IllegalArgumentException, SubmitFailedException {

    SpaceAndProjectCodes spaceandproject = getSpaceAndProjects(type, id);

    String spaceCode = spaceandproject.space;
    String projectCode = spaceandproject.project;

    ParameterSet params = workflow.getParameters();
    List<Property> factors = new ArrayList<Property>();

    XMLParser xmlParser = new XMLParser();

    for (Map.Entry<String, Parameter> entry : workflow.getData().getData().entrySet()) {
      String key = entry.getKey();
      Parameter value = entry.getValue();

      if (key.contains("input")) {
        List<String> files = (List<String>) value.getValue();
        List<String> inputFiles = new ArrayList<String>();

        for (String f : files) {
          String[] splitted = f.split("/");
          String fileName = splitted[splitted.length - 1];
          inputFiles.add(fileName);
        }

        System.out.println(inputFiles.toString());
        String concat = String.join("; ", inputFiles);
        System.out.println(concat);
        Property newProperty = new Property("input_files", concat, PropertyType.Property);
        factors.add(newProperty);
      }

      else {
        Property newProperty = new Property("database",
            value.getValue().toString().replace("/lustre_cfc/qbic/reference_genomes/", ""),
            PropertyType.Property);
        factors.add(newProperty);
      }
    }

    for (String p : params.getParamNames()) {
      Parameter par = params.getParam(p);
      String[] splitted = par.getTitle().split("\\.");
      String parName = splitted[splitted.length - 1].replace(" ", "_").toLowerCase();

      Property newProperty =
          new Property(parName, par.getValue().toString(), PropertyType.Property);
      factors.add(newProperty);
    }

    String qProperties = "";

    try {
      qProperties = xmlParser.toString(xmlParser.createXMLFromProperties(factors));
      System.out.println(qProperties);
    } catch (JAXBException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    String experimentCode = registerWFExperiment(spaceCode, projectCode,
        workflow.getExperimentType(), workflow.getID(), workflow.getVersion(), user, qProperties);

    List<String> parents = getConnectedSamples(selectedDatasets);
    String sampleType = workflow.getSampleType();

    String sampleCode = registerWFSample(spaceCode, projectCode, experimentCode, sampleType,
        parents, selectedDatasets);

    String openbisId =
        String.format("%s-%s-%s-%s", spaceCode, projectCode, experimentCode, sampleCode);

    LOG.info("User: " + user + " is submitting workflow " + workflow.getID() + " openbis id is:"
        + openbisId);

    String submit_id = submitter.submit(workflow, openbisId, user);
    LOG.info("Workflow has guse id: " + submit_id);

    setWorkflowID(spaceCode, projectCode, experimentCode, submit_id);
    return openbisId;
  }

  private SpaceAndProjectCodes getSpaceAndProjects(String type, String id) {
    String[] split = id.split("/");

    if (split.length == 0)
      return null;
    switch (type) {
      case PatientView.navigateToLabel:
      case ProjectView.navigateToLabel:
      case ExperimentView.navigateToLabel:
      case "workflowExperimentType":
        return new SpaceAndProjectCodes(split[1], split[2]);
      case SampleView.navigateToLabel:
        String expId = datahandler.getOpenBisClient()
            .getSampleByIdentifier(String.format("%s/%s", split[1], split[2]))
            .getExperiment().getIdentifier().toString();
        if (expId == null)
          return null;
        return getSpaceAndProjects(ExperimentView.navigateToLabel, expId);
      case DatasetView.navigateToLabel:
        throw new NotImplementedException("Dataset view is not ready for workflows!");
      default:
        LOG.debug(String.format("Problem with id %s, type %s", id, type));
        return null;
    }
  }

  class SpaceAndProjectCodes {
    public String space;
    public String project;

    public SpaceAndProjectCodes(String spaceCode, String projectCode) {
      this.space = spaceCode;
      this.project = projectCode;
    }
  }

  /**
   * get for a {@link de.uni_tuebingen.qbic.beans.DatasetBean} file or directory the path it has on
   * the data store server.
   *
   * @param bean
   * @return full path of dataset
   * @throws IllegalArgumentException
   */
  public String getDatasetsNfsPath(DatasetBean bean) throws IllegalArgumentException {

    try {
      DataSet dataset = datahandler.getOpenBisClient().getDataSet(bean.getOpenbisCode());
      // String path = dataset.getDataSetDss().tryGetInternalPathInDataStore();
      String path = dataset.getPhysicalData().getLocation();

      if (bean.getFullPath().startsWith("original")) {
        path = Paths.get(path, bean.getFullPath()).toString();

      } else {
        List<DataSetFile> datasetFiles = datahandler.getOpenBisClient()
                .listDataSetFiles(dataset.getCode())
                .stream()
                .filter(dsf -> !dsf.getPath().isEmpty() || !dsf.getPath().equals("original"))
                .collect(Collectors.toList());

        path = path + "/original/" + datasetFiles.get(0).getPath();
      }

      // TODO get rid of hardocded paths
      path = path.replaceFirst("/mnt/" + openbis_dss, "/mnt/nfs/qbic");
      path = path.replaceFirst("/mnt/DSS_icgc", "/mnt/glusterfs/DSS_icgc");
      path = path.replaceFirst("/beegfs/bigbio", "/mnt/nfs/bigbio_temp");
      return path;
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Could not retrieve nfs path for dataset " + bean);
    }
  }

  public OpenBisClient getOpenbis() {
    return this.datahandler.getOpenBisClient();
  }


  /**
   * Returns experimental factor names parsed from the properties of samples in this project
   *
   * @return Unique set of all experimental factors that are saved in Q_Properties of this project
   */
  public Set<String> getExperimentalFactors() {
    return expFactors;
  }

  public Map<String, String> getExperimentalPropsForFiles() {
    return expProps;
  }

}
