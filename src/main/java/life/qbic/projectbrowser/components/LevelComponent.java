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
package life.qbic.projectbrowser.components;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;

import com.vaadin.data.Item;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.*;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.server.*;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Window.CloseEvent;
import com.vaadin.ui.Window.CloseListener;
import com.vaadin.ui.renderers.ButtonRenderer;
import com.vaadin.ui.renderers.ClickableRenderer.RendererClickEvent;
import com.vaadin.ui.renderers.ClickableRenderer.RendererClickListener;

import life.qbic.portal.portlet.ProjectBrowserPortlet;
import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.controllers.DataHandler;
import life.qbic.projectbrowser.controllers.State;
import life.qbic.projectbrowser.helpers.*;
import life.qbic.projectbrowser.model.DatasetBean;
import life.qbic.projectbrowser.model.TestSampleBean;
import life.qbic.xml.manager.StudyXMLParser;
import life.qbic.xml.properties.Property;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tepi.filtertable.FilterTreeTable;

import javax.portlet.PortletSession;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Displays raw data / results.
 */
public class LevelComponent extends CustomComponent {
  private static final long serialVersionUID = 8672873911284888801L;
  private static final Logger LOG = LogManager.getLogger(LevelComponent.class);
  private static final String[] FILTER_TABLE_COLUMNS = new String[] {"Select", "Sample",
      "File Name", "Description", "Dataset Type", "Registration Date", "File Size"};

  private final VerticalLayout mainLayout;
  private final FilterTreeTable datasetTable;
  private final VerticalLayout vert;
  private final String DOWNLOAD_BUTTON_CAPTION = "Download";
  private final Label descriptionLabel;
  private final DataHandler datahandler;
  private final ChangeSampleMetadataComponent changeMetadata;
  private final ButtonLink download;
  private final UglyToPrettyNameMapper prettyNameMapper = new UglyToPrettyNameMapper();
  private final Button exportData;
  private final Button exportSamples;
  private final Grid sampleGrid;

  private HierarchicalContainer datasets;
  private BeanItemContainer<TestSampleBean> samples;
  private int numberOfDatasets;
  private int numberOfSamples;
  private volatile FileDownloader fileDownloaderData;
  private volatile FileDownloader fileDownloaderSamples;


  public LevelComponent(DataHandler dh, State state, String resourceurl, String caption) {
    this.datahandler = dh;

    changeMetadata = new ChangeSampleMetadataComponent(dh, state, resourceurl);

    this.setCaption(caption);

    vert = new VerticalLayout();
    mainLayout = new VerticalLayout(vert);
    datasetTable = buildFilterTable();
    sampleGrid = new Grid();
    download = new ButtonLink(DOWNLOAD_BUTTON_CAPTION, new ExternalResource(""));
    descriptionLabel = new Label("", ContentMode.HTML);
    exportData = new Button("Export as TSV");
    exportSamples = new Button("Export as TSV");

    this.initUI();
  }

  private void initUI() {
    mainLayout.setResponsive(true);
    vert.setResponsive(true);
    vert.setMargin(new MarginInfo(false, true, false, false));

    setResponsive(true);

    exportData.setIcon(FontAwesome.DOWNLOAD);
    exportSamples.setIcon(FontAwesome.DOWNLOAD);

    this.setCompositionRoot(mainLayout);
  }

  public void updateUI(final String type, final String id, final String filterFor) {

    if (id == null) { return; }

    try {
      HierarchicalContainer datasetContainer = new HierarchicalContainer();
      datasetContainer.addContainerProperty("Select", CheckBox.class, null);
      datasetContainer.addContainerProperty("Project", String.class, null);
      datasetContainer.addContainerProperty("Sample", String.class, null);
      datasetContainer.addContainerProperty("Description", String.class, null);
      datasetContainer.addContainerProperty("File Name", String.class, null);
      datasetContainer.addContainerProperty("File Type", String.class, null);
      datasetContainer.addContainerProperty("Dataset Type", String.class, null);
      datasetContainer.addContainerProperty("Registration Date", String.class, null);
      datasetContainer.addContainerProperty("Validated", Boolean.class, null);
      datasetContainer.addContainerProperty("File Size", String.class, null);
      datasetContainer.addContainerProperty("file_size_bytes", Long.class, null);
      datasetContainer.addContainerProperty("dl_link", String.class, null);
      datasetContainer.addContainerProperty("isDirectory", Boolean.class, null);
      datasetContainer.addContainerProperty("CODE", String.class, null);

      List<DataSet> retrievedDatasetsAll = null;
      List<DataSet> retrievedDatasets = new ArrayList<>();
      Map<String, ArrayList<DataSet>> datasetFilter = new HashMap<>();

      // Clear download queue for new view
      PortletSession portletSession = ((ProjectBrowserPortlet) UI.getCurrent()).getPortletSession();
      portletSession.setAttribute("qbic_download", new HashMap<String, SimpleEntry<String, Long>>(),
          PortletSession.APPLICATION_SCOPE);
      Map<String, Sample> checkedTestSamples = new HashMap<>();

      // data for complex xml properties
      StudyXMLParser xmlParser = new StudyXMLParser();

      switch (type) {
        case "project":
          String projectIdentifier = id;
          retrievedDatasetsAll = datahandler.getOpenBisClient().getDataSetsOfProject(projectIdentifier);

          for (DataSet ds : retrievedDatasetsAll) {
            ArrayList<DataSet> values = datasetFilter.get(ds.getSample().getIdentifier().toString());

            if (values == null) {
              values = new ArrayList<>();
              datasetFilter.put(ds.getSample().getIdentifier().toString(), values);
            }
            values.add(ds);
          }

          if (filterFor.equals("measured")) {
            BeanItemContainer<TestSampleBean> samplesContainer = new BeanItemContainer<>(TestSampleBean.class);

            List<Sample> allSamples = datahandler.getOpenBisClient().getSamplesOfProject(id);

            for (Sample sample : allSamples) {
              checkedTestSamples.put(sample.getCode(), sample);

              if (sample.getType().getCode().equals("Q_TEST_SAMPLE")) {
                Map<String, String> sampleProperties = sample.getProperties();

                TestSampleBean newBean = new TestSampleBean();
                newBean.setCode(sample.getCode());
                newBean.setId(sample.getIdentifier().toString());
                newBean.setType(sample.getType().getCode());
                newBean.setSampleType(sampleProperties.get("Q_SAMPLE_TYPE"));
                newBean.setAdditionalInfo(sampleProperties.get("Q_ADDITIONAL_INFO"));
                newBean.setExternalDB(sampleProperties.get("Q_EXTERNALDB_ID"));
                newBean.setSecondaryName(sampleProperties.get("Q_SECONDARY_NAME"));

                List<Property> complexProps = xmlParser.getFactorsAndPropertiesForSampleCode(
                    datahandler.getExperimentalSetup(), sample.getCode());
                newBean.setProperties(sampleProperties, complexProps);

                samplesContainer.addBean(newBean);

                List<DataSet> foundDataset =
                    datasetFilter.get(sample.getIdentifier().toString());

                if (foundDataset != null) {
                  retrievedDatasets.addAll(foundDataset);
                }

                for (Sample child : sample.getChildren()) {
                  foundDataset = datasetFilter.get(child.getIdentifier().toString());

                  if (foundDataset != null) {
                    retrievedDatasets.addAll(foundDataset);
                  }
                }
              } else if (sample.getType().getCode().equals("Q_MHC_LIGAND_EXTRACT")) {

                Map<String, String> sampleProperties = sample.getProperties();
                TestSampleBean newBean = new TestSampleBean();
                newBean.setCode(sample.getCode());
                newBean.setId(sample.getIdentifier().toString());
                newBean.setType(sample.getType().getCode());
                newBean.setSampleType(sampleProperties.get("Q_MHC_CLASS"));
                newBean.setAdditionalInfo(sampleProperties.get("Q_ANTIBODY"));
                newBean.setExternalDB(sampleProperties.get("Q_EXTERNALDB_ID"));
                newBean.setSecondaryName(sampleProperties.get("Q_SECONDARY_NAME"));

                List<Property> complexProps = xmlParser.getFactorsAndPropertiesForSampleCode(
                    datahandler.getExperimentalSetup(), sample.getCode());
                newBean.setProperties(sampleProperties, complexProps);

                samplesContainer.addBean(newBean);

                ArrayList<DataSet> foundDataset = datasetFilter.get(sample.getIdentifier().toString());

                if (foundDataset != null) {
                  retrievedDatasets.addAll(foundDataset);
                }

                for (Sample child : sample.getChildren()) {
                  foundDataset = datasetFilter.get(child.getIdentifier().toString());
                  if (foundDataset != null) {
                    retrievedDatasets.addAll(foundDataset);
                  }
                }
              }
            }

            numberOfSamples = samplesContainer.size();
            samples = samplesContainer;
            final GeneratedPropertyContainer gpc = new GeneratedPropertyContainer(samples);
            gpc.removeContainerProperty("id");
            gpc.removeContainerProperty("type");
            sampleGrid.removeAllColumns();
            sampleGrid.setContainerDataSource(gpc);
            sampleGrid.setColumnReorderingAllowed(true);

            gpc.addGeneratedProperty("edit", new PropertyValueGenerator<String>() {
              @Override
              public String getValue(Item item, Object itemId, Object propertyId) {
                return "Edit";
              }

              @Override
              public Class<String> getType() {
                return String.class;
              }
            });

            sampleGrid.addItemClickListener(new ItemClickListener() {

              @Override
              public void itemClick(ItemClickEvent event) {

                BeanItem selected = (BeanItem) samples.getItem(event.getItemId());
                TestSampleBean selectedExp = (TestSampleBean) selected.getBean();

                State state = (State) UI.getCurrent().getSession().getAttribute("state");
                ArrayList<String> message = new ArrayList<String>();
                message.add("clicked");
                message.add(selectedExp.getId());
                message.add("sample");
                state.notifyObservers(message);
              }
            });

            sampleGrid.getColumn("edit")
                .setRenderer(new ButtonRenderer(new RendererClickListener() {

                  @Override
                  public void click(RendererClickEvent event) {
                    BeanItem selected = (BeanItem) samples.getItem(event.getItemId());
                    TestSampleBean selectedSample = (TestSampleBean) selected.getBean();

                    Window subWindow = new Window("Edit Metadata");

                    changeMetadata.updateUI(selectedSample.getId(), selectedSample.getType());
                    VerticalLayout subContent = new VerticalLayout();
                    subContent.setMargin(true);
                    subContent.addComponent(changeMetadata);
                    subWindow.setContent(subContent);
                    // Center it in the browser window
                    subWindow.center();
                    subWindow.setModal(true);
                    subWindow.setIcon(FontAwesome.PENCIL);
                    subWindow.setHeight("75%");
                    subWindow.setResizable(false);

                    subWindow.addCloseListener(new CloseListener() {
                      private static final long serialVersionUID = -1329152609834711109L;

                      @Override
                      public void windowClose(CloseEvent e) {
                        updateUI(type, id, filterFor);
                      }
                    });
                    ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
                    ui.addWindow(subWindow);
                  }
                }));
            sampleGrid.getColumn("edit").setHeaderCaption("");
            sampleGrid.getColumn("edit").setWidth(70);
            sampleGrid.setColumnOrder("edit", "secondaryName", "sampleType", "code", "properties",
                "additionalInfo", "externalDB");

            GridFunctions.addColumnFilters(sampleGrid, gpc);
            numberOfSamples = samplesContainer.size();

            sampleGrid.setCaption("Measured Samples");
            this.datasetTable.setCaption("Raw Data");

            numberOfDatasets = retrievedDatasets.size();
            this.datasetTable.setPageLength(Math.max(3, Math.min(numberOfDatasets, 10)));
          } else if (filterFor.equals("results")) {
            BeanItemContainer<TestSampleBean> samplesContainer =
                new BeanItemContainer<>(TestSampleBean.class);

            List<Sample> allSamples = datahandler.getOpenBisClient().getSamplesOfProject(projectIdentifier);

            for (Sample sample : allSamples) {
              checkedTestSamples.put(sample.getCode(), sample);

              if (!sample.getType().getCode().equals("Q_TEST_SAMPLE")
                  && !sample.getType().getCode().equals("Q_MICROARRAY_RUN")
                  && !sample.getType().getCode().equals("Q_MS_RUN")
                  && !sample.getType().getCode().equals("Q_BIOLOGICAL_SAMPLE")
                  && !sample.getType().getCode().equals("Q_BIOLOGICAL_ENTITY")
                  && !sample.getType().getCode().equals("Q_NGS_SINGLE_SAMPLE_RUN")) {

                Map<String, String> sampleProperties = sample.getProperties();
                TestSampleBean newBean = new TestSampleBean();
                newBean.setCode(sample.getCode());
                newBean.setId(sample.getIdentifier().toString());
                newBean.setType(prettyNameMapper.getPrettyName(sample.getType().getCode()));
                newBean.setAdditionalInfo(sampleProperties.get("Q_ADDITIONAL_INFO"));
                newBean.setSecondaryName(sampleProperties.get("Q_SECONDARY_NAME"));

                List<Property> complexProps = xmlParser.getFactorsAndPropertiesForSampleCode(
                    datahandler.getExperimentalSetup(), sample.getCode());
                newBean.setProperties(sampleProperties, complexProps);

                samplesContainer.addBean(newBean);

                ArrayList<DataSet> foundDataset = datasetFilter.get(sample.getIdentifier().toString());

                if (foundDataset != null) {
                  for (DataSet ds : foundDataset) {
                    // we don't want to show project data or log files in the results tab
                    if (ds.getType().getCode().equals("Q_PROJECT_DATA")) {
                      if (ds.getProperties().get("Q_ATTACHMENT_TYPE").equals("INFORMATION")) {
                        continue;
                      } else {
                        retrievedDatasets.add(ds);
                      }
                    } else if (ds.getType().getCode().contains("LOGS")) {
                      continue;
                    } else {
                      retrievedDatasets.add(ds);
                    }
                  }
                }
              }
            }
            samples = samplesContainer;
            final GeneratedPropertyContainer gpc = new GeneratedPropertyContainer(samples);
            gpc.removeContainerProperty("id");
            gpc.removeContainerProperty("sampleType");
            sampleGrid.setContainerDataSource(gpc);
            sampleGrid.setColumnReorderingAllowed(true);
            sampleGrid.setColumnOrder("secondaryName", "type", "code", "properties");
            numberOfSamples = samplesContainer.size();

            sampleGrid.setCaption("Workflow Runs");
            GridFunctions.addColumnFilters(sampleGrid, gpc);
            this.datasetTable.setCaption("Result Files");
            datasetTable.setColumnHeader("Sample", "Workflow Run");

            sampleGrid.addItemClickListener(new ItemClickListener() {

              @Override
              public void itemClick(ItemClickEvent event) {

                BeanItem selected = (BeanItem) samples.getItem(event.getItemId());
                TestSampleBean selectedExp = (TestSampleBean) selected.getBean();

                State state = (State) UI.getCurrent().getSession().getAttribute("state");
                ArrayList<String> message = new ArrayList<String>();
                message.add("clicked");
                message.add(selectedExp.getId());
                message.add("sample");
                state.notifyObservers(message);
              }
            });

            numberOfDatasets = retrievedDatasets.size();
            this.datasetTable.setPageLength(Math.max(3, Math.min(numberOfDatasets, 10)));

          }
          break;

        case "experiment":
          String experimentIdentifier = id;
          retrievedDatasets =
                  datahandler.getOpenBisClient().getDataSetsOfExperimentByIdentifier(experimentIdentifier);
          break;

        case "sample":
          String sampleIdentifier = id;
          retrievedDatasets = datahandler.getOpenBisClient().getDataSetsOfSample(sampleIdentifier);
          break;

        default:
          retrievedDatasets = new ArrayList<>();
          break;
      }

      BeanItemContainer<DatasetBean> forExport = new BeanItemContainer(DatasetBean.class);
      numberOfDatasets = retrievedDatasets.size();

      if (numberOfDatasets == 0 & filterFor.equals("measured")) {

        descriptionLabel.setValue(String.format(
            "This project contains %s measured samples for which %s raw data dataset(s) have been registered.",
            numberOfSamples, 0));

        Utils.Notification("No raw data available.",
            "No raw data is available for this project. Please contact the project manager if this is not expected.",
            "warning");
      } else if (numberOfDatasets == 0 & filterFor.equals("results")) {
        descriptionLabel.setValue(String.format("This project contains %s result datasets.", 0));

        Utils.Notification("No results available.",
            "No result data is available for this project. Please contact the project manager if this is not expected.",
            "warning");
      } else {

        // project same for all datasets
        String projectCode =
                retrievedDatasets.get(0).getSample().getIdentifier().toString().split("/")[2];

        Map<String, String> samples =
                retrievedDatasets.stream().collect(Collectors.toMap(DataSet::getCode, ds -> ds.getSample().getCode()));

        List<DatasetBean> dsBeans = datahandler.queryDatasetsForFolderStructure(retrievedDatasets);

        for (DatasetBean d : dsBeans) {
          Date date = d.getRegistrationDate();
          SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
          String dateString = sd.format(date);
          String sampleID = samples.get(d.getCode());
          forExport.addBean(d);

          Sample dsSample  = checkedTestSamples.get(sampleID);
          String secNameDS = d.getProperties().get("Q_SECONDARY_NAME");
          String secName   = datahandler.getSecondaryName(dsSample, secNameDS);

          registerDatasetInTable(d, datasetContainer, projectCode, sampleID, dateString, null, secName);
        }

        if (filterFor.equals("measured"))

        {
          descriptionLabel.setValue(String.format(
              "This project contains %s measured samples for which %s raw data dataset(s) have been registered.",
              numberOfSamples, dsBeans.size()));
        } else if (filterFor.equals("results")) {
          descriptionLabel
              .setValue(String.format("This project contains %s result datasets.", dsBeans.size()));
        }

      }

      this.setContainerDataSource(datasetContainer);
      prepareDownloads(forExport, id);

    } catch (Exception e) {
      e.printStackTrace();
      LOG.error(String.format("getting dataset failed for dataset %s %s", type, id),
          e.getStackTrace());
    }
  }

  private void prepareDownloads(final BeanItemContainer<DatasetBean> forExport, final String id) {
    UI.getCurrent().setPollInterval(150);

    if (fileDownloaderData != null) {
      this.exportData.removeExtension(fileDownloaderData);
    }
    final CompletableFuture<String> rawDataFuture =
        CompletableFuture.supplyAsync(() -> Utils.containerToString(forExport));
    final CompletableFuture<StreamResource> rawResourceFuture =
        rawDataFuture.thenApplyAsync(rawData -> Utils.getTSVStream(rawData, String.format("%s_%s_",
            id.substring(1).replace("/", "_"), datasetTable.getCaption().replace(" ", "_"))));
    final CompletableFuture<Void> rawUIControlsFuture =
        rawResourceFuture.thenAcceptAsync((stream) -> {
          UI.getCurrent().access(() -> {
            fileDownloaderData = new FileDownloader(stream);
            fileDownloaderData.extend(exportData);
          });
        });

    if (fileDownloaderSamples != null) {
      this.exportSamples.removeExtension(fileDownloaderSamples);
    }
    final CompletableFuture<String> samplesDataFuture =
        CompletableFuture.supplyAsync(() -> Utils.containerToString(samples));
    final CompletableFuture<StreamResource> samplesResourceFuture = samplesDataFuture
        .thenApplyAsync(samplesData -> Utils.getTSVStream(samplesData, String.format("%s_%s_",
            id.substring(1).replace("/", "_"), sampleGrid.getCaption().replaceAll(" ", "_"))));
    final CompletableFuture<Void> samplesUIControlsFuture =
        samplesResourceFuture.thenAcceptAsync((stream) -> {
          UI.getCurrent().access(() -> {
            fileDownloaderSamples = new FileDownloader(stream);
            fileDownloaderSamples.extend(exportSamples);
          });
        });

    CompletableFuture.allOf(samplesUIControlsFuture, rawUIControlsFuture)
        .thenRun(() -> UI.getCurrent().access(() -> UI.getCurrent().setPollInterval(-1)));
  }

  public void setContainerDataSource(HierarchicalContainer newDataSource) {
    datasets = newDataSource;
    datasetTable.setContainerDataSource(this.datasets);

    datasetTable.setVisibleColumns((Object[]) FILTER_TABLE_COLUMNS);

    datasetTable.setSizeFull();

    Object[] sorting = {"Registration Date"};
    boolean[] order = {false};
    datasetTable.sort(sorting, order);

    this.buildLayout();
  }

  /**
   * Precondition: {DatasetView#table} has to be initialized. e.g. with
   * {DatasetView#buildFilterTable} If it is not, strange behaviour has to be expected. builds the
   * Layout of this view.
   */
  private void buildLayout() {
    this.vert.removeAllComponents();
    this.vert.setWidth("100%");

    // Table (containing datasets) section
    VerticalLayout tableSectionDatasets = new VerticalLayout();
    VerticalLayout tableSectionSamples = new VerticalLayout();
    HorizontalLayout tableSectionContent = new HorizontalLayout();
    HorizontalLayout sampletableSectionContent = new HorizontalLayout();

    tableSectionContent.setMargin(new MarginInfo(false, false, false, false));
    sampletableSectionContent.setMargin(new MarginInfo(false, false, false, false));

    // tableSectionContent.setCaption("Datasets");
    // tableSectionContent.setIcon(FontAwesome.FLASK);

    descriptionLabel.setWidth("100%");
    tableSectionDatasets.addComponent(descriptionLabel);

    sampletableSectionContent.addComponent(sampleGrid);
    tableSectionContent.addComponent(this.datasetTable);

    tableSectionDatasets.setMargin(new MarginInfo(true, false, false, true));
    tableSectionDatasets.setSpacing(true);

    tableSectionSamples.setMargin(new MarginInfo(true, false, true, true));
    tableSectionSamples.setSpacing(true);

    tableSectionDatasets.addComponent(tableSectionContent);

    tableSectionSamples.addComponent(sampletableSectionContent);
    tableSectionSamples.addComponent(exportSamples);

    this.vert.addComponent(tableSectionDatasets);

    sampleGrid.setWidth("100%");
    datasetTable.setWidth("100%");
    tableSectionDatasets.setWidth("100%");

    tableSectionSamples.setWidth("100%");
    sampletableSectionContent.setWidth("100%");

    tableSectionContent.setWidth("100%");

    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setMargin(new MarginInfo(false, false, false, true));
    buttonLayout.setHeight(null);
    buttonLayout.setSpacing(true);

    this.download.setEnabled(false);
    buttonLayout.setSpacing(true);

    Button checkAll = new Button("Select all datasets");
    checkAll.addClickListener(new ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        for (Object itemId : datasetTable.getItemIds()) {
          ((CheckBox) datasetTable.getItem(itemId).getItemProperty("Select").getValue())
              .setValue(true);
        }
      }
    });

    Button uncheckAll = new Button("Unselect all datasets");
    uncheckAll.addClickListener(new ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        for (Object itemId : datasetTable.getItemIds()) {
          ((CheckBox) datasetTable.getItem(itemId).getItemProperty("Select").getValue())
              .setValue(false);
        }
      }
    });

    buttonLayout.addComponent(exportData);
    buttonLayout.addComponent(checkAll);
    buttonLayout.addComponent(uncheckAll);
    // buttonLayout.addComponent(visualize);
    buttonLayout.addComponent(this.download);

    String content =
        "<p> In case of multiple file selections, Project Browser will create a tar archive.</p>"
            + "<hr>"
            + "<p> If you need help on extracting a tar archive file, follow the tips below: </p>"
            + "<p>" + FontAwesome.WINDOWS.getHtml() + " Windows </p>"
            + "<p> To open/extract TAR file on Windows, you can use 7-Zip, Easy 7-Zip, PeaZip.</p>"
            + "<hr>" + "<p>" + FontAwesome.APPLE.getHtml() + " MacOS </p>"
            + "<p> To open/extract TAR file on Mac, you can use Mac OS built-in utility Archive Utility,<br> or third-part freeware. </p>"
            + "<hr>" + "<p>" + FontAwesome.LINUX.getHtml() + " Linux </p>"
            + "<p> You need to use command tar. The tar is the GNU version of tar archiving utility. <br> "
            + "To extract/unpack a tar file, type: $ tar -xvf file.tar</p>";

    PopupView tooltip = new PopupView(new ToolTip(content));
    tooltip.setHeight("44px");

    HorizontalLayout help = new HorizontalLayout();
    help.setSizeFull();
    HorizontalLayout helpContent = new HorizontalLayout();
    // helpContent.setSizeFull();

    help.setMargin(new MarginInfo(false, false, false, true));
    Label helpText = new Label("Attention: Click here before Download!");
    helpContent.addComponent(new Label(FontAwesome.QUESTION_CIRCLE.getHtml(), ContentMode.HTML));
    helpContent.addComponent(helpText);
    helpContent.addComponent(tooltip);
    helpContent.setSpacing(true);

    help.addComponent(helpContent);
    help.setComponentAlignment(helpContent, Alignment.TOP_CENTER);

    /**
     * prepare download.
     */
    download.setResource(new ExternalResource("javascript:"));
    download.setEnabled(false);

    for (final Object itemId : this.datasetTable.getItemIds()) {
      setCheckedBox(itemId,
          (String) this.datasetTable.getItem(itemId).getItemProperty("CODE").getValue());
    }

    this.datasetTable.addItemClickListener(new ItemClickListener() {
      @Override
      public void itemClick(ItemClickEvent event) {
        if (!event.isDoubleClick() & !((boolean) datasetTable.getItem(event.getItemId())
            .getItemProperty("isDirectory").getValue())) {
          String datasetCode =
              (String) datasetTable.getItem(event.getItemId()).getItemProperty("CODE").getValue();
          String datasetFileName = (String) datasetTable.getItem(event.getItemId())
              .getItemProperty("File Name").getValue();
          URL url = null;
          try {
            Resource res = null;
            Object parent = datasetTable.getParent(event.getItemId());
            if (parent != null) {

              String parentDatasetFileName =
                  (String) datasetTable.getItem(parent).getItemProperty("File Name").getValue();
              try {
                url = datahandler.getOpenBisClient().getUrlForDataset(datasetCode,
                    parentDatasetFileName + "/" + URLEncoder.encode(datasetFileName, "UTF-8"));
              } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            } else {
              try {
                url = datahandler.getOpenBisClient().getUrlForDataset(datasetCode,
                    URLEncoder.encode(datasetFileName, "UTF-8"));
              } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }

            Window subWindow = new Window();
            VerticalLayout subContent = new VerticalLayout();
            subContent.setMargin(true);
            subContent.setSizeFull();
            subWindow.setContent(subContent);
            ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
            Boolean visualize = false;

            if (datasetFileName.endsWith(".pdf")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("application/pdf");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".png")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("application/png");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".qcML")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("text/xml");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".alleles")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("text/plain");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".tsv")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("text/plain");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".log")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("text/plain");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".html")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("text/html");
              res = streamres;
              visualize = true;
            }

            if (datasetFileName.endsWith(".GSvar")) {
              QcMlOpenbisSource re = new QcMlOpenbisSource(url);
              StreamResource streamres = new StreamResource(re, datasetFileName);
              streamres.setMIMEType("text/plain");
              res = streamres;
              visualize = true;
            }

            if (visualize) {
              BrowserFrame frame = new BrowserFrame("", res);

              frame.setSizeFull();
              subContent.addComponent(frame);

              // Center it in the browser window
              subWindow.center();
              subWindow.setModal(true);
              subWindow.setSizeUndefined();
              subWindow.setHeight("75%");
              subWindow.setWidth("75%");
              subWindow.setResizable(false);

              frame.setSizeFull();
              frame.setHeight("100%");
              // frame.setHeight((int) (ui.getPage().getBrowserWindowHeight() * 0.9), Unit.PIXELS);

              // Open it in the UI
              ui.addWindow(subWindow);
            }

          } catch (MalformedURLException e) {
            LOG.error(String.format("Visualization failed because of malformedURL for dataset: %s",
                datasetCode));
            Utils.Notification("No file attached.",
                "Given dataset has no file attached to it!! Please Contact your project manager. Or check whether it already has some data",
                "error");

            // Notification
            // .show(
            // "Given dataset has no file attached to it!! Please Contact your project manager. Or
            // check whether it already has some data",
            // Notification.Type.ERROR_MESSAGE);
          }
        }
      }
    });
    this.vert.addComponent(help);
    this.vert.addComponent(buttonLayout);
    this.vert.addComponent(tableSectionSamples);

  }


  private void setCheckedBox(Object itemId, String parentFolder) {
    CheckBox itemCheckBox =
        (CheckBox) this.datasetTable.getItem(itemId).getItemProperty("Select").getValue();
    itemCheckBox.addValueChangeListener(new TableCheckBoxValueChangeListener(itemId, parentFolder));

    if (datasetTable.hasChildren(itemId)) {
      for (Object childId : datasetTable.getChildren(itemId)) {
        String newParentFolder = Paths
            .get(parentFolder,
                (String) this.datasetTable.getItem(itemId).getItemProperty("File Name").getValue())
            .toString();
        setCheckedBox(childId, newParentFolder);
      }
    }

  }

  private FilterTreeTable buildFilterTable() {
    FilterTreeTable filterTable = new FilterTreeTable();
    filterTable.setSizeFull();

    filterTable.setFilterDecorator(new DatasetViewFilterDecorator());
    filterTable.setFilterGenerator(new DatasetViewFilterGenerator());

    filterTable.setFilterBarVisible(true);

    filterTable.setSelectable(true);
    filterTable.setImmediate(true);
    filterTable.setMultiSelect(true);

    // filterTable.setRowHeaderMode(RowHeaderMode.INDEX);

    filterTable.setColumnCollapsingAllowed(true);

    filterTable.setColumnReorderingAllowed(true);

    if (this.datasets != null) {
      filterTable.setContainerDataSource(this.datasets);
    }

    return filterTable;
  }

  public void registerDatasetInTable(DatasetBean d, HierarchicalContainer dataset_container,
      String project, String sample, String ts, Object parent, String secName) {
    if (d.hasChildren()) {

      Object new_ds = dataset_container.addItem();

      List<DatasetBean> subList = d.getChildren();

      dataset_container.setChildrenAllowed(new_ds, true);

      // String secName = d.getProperties().get("Q_SECONDARY_NAME");
      // TODO add User here too
      // if (secName != null) {
      dataset_container.getContainerProperty(new_ds, "Description").setValue(secName);
      // }

      dataset_container.getContainerProperty(new_ds, "Select").setValue(new CheckBox());

      dataset_container.getContainerProperty(new_ds, "Project").setValue(project);
      dataset_container.getContainerProperty(new_ds, "Sample").setValue(sample);
      // dataset_container.getContainerProperty(new_ds, "Sample Type").setValue(
      // d.getSample().getType());
      dataset_container.getContainerProperty(new_ds, "File Name").setValue(d.getName());
      dataset_container.getContainerProperty(new_ds, "File Type").setValue("Folder");
      dataset_container.getContainerProperty(new_ds, "Dataset Type")
          .setValue(prettyNameMapper.getPrettyName(d.getType()) + " Folder");
      dataset_container.getContainerProperty(new_ds, "Registration Date").setValue(ts);
      dataset_container.getContainerProperty(new_ds, "Validated").setValue(true);
      dataset_container.getContainerProperty(new_ds, "dl_link").setValue(d.getDssPath());
      dataset_container.getContainerProperty(new_ds, "CODE").setValue(d.getCode());
      dataset_container.getContainerProperty(new_ds, "file_size_bytes").setValue(d.getFileSize());
      dataset_container.getContainerProperty(new_ds, "isDirectory").setValue(true);

      if (parent != null) {
        dataset_container.setParent(new_ds, parent);
      }

      for (DatasetBean file : subList) {
        registerDatasetInTable(file, dataset_container, project, sample, ts, new_ds, secName);
      }

    } else {
      // System.out.println("Now it should be a file: " + filelist[0].getPathInDataSet());

      Object new_file = dataset_container.addItem();
      dataset_container.setChildrenAllowed(new_file, false);

      // TODO no hardcoding
      // String secName = d.getProperties().get("Q_SECONDARY_NAME");
      // TODO add User here too
      // if (secName != null) {
      dataset_container.getContainerProperty(new_file, "Description").setValue(secName);
      // }
      dataset_container.getContainerProperty(new_file, "Select").setValue(new CheckBox());
      dataset_container.getContainerProperty(new_file, "Project").setValue(project);
      dataset_container.getContainerProperty(new_file, "Sample").setValue(sample);
      // dataset_container.getContainerProperty(new_file, "Sample Type").setValue(sampleType);
      dataset_container.getContainerProperty(new_file, "File Name").setValue(d.getFileName());
      dataset_container.getContainerProperty(new_file, "File Type").setValue(d.getFileType());
      dataset_container.getContainerProperty(new_file, "Dataset Type")
          .setValue(prettyNameMapper.getPrettyName(d.getType()));
      dataset_container.getContainerProperty(new_file, "Registration Date").setValue(ts);
      dataset_container.getContainerProperty(new_file, "Validated").setValue(true);
      dataset_container.getContainerProperty(new_file, "File Size")
          .setValue(PortalUtils.humanReadableByteCount(d.getFileSize(), true));
      dataset_container.getContainerProperty(new_file, "dl_link").setValue(d.getDssPath());
      dataset_container.getContainerProperty(new_file, "CODE").setValue(d.getCode());
      dataset_container.getContainerProperty(new_file, "file_size_bytes").setValue(d.getFileSize());
      dataset_container.getContainerProperty(new_file, "isDirectory").setValue(false);

      if (parent != null) {
        dataset_container.setParent(new_file, parent);
      }
    }
  }


  private class TableCheckBoxValueChangeListener implements ValueChangeListener {

    /**
     *
     */
    private static final long serialVersionUID = -7177199525909283879L;
    private Object itemId;
    private String itemFolderName;

    public TableCheckBoxValueChangeListener(final Object itemId, String itemFolderName) {
      this.itemFolderName = itemFolderName;
      this.itemId = itemId;
    }

    @Override
    public void valueChange(ValueChangeEvent event) {

      PortletSession portletSession = ((ProjectBrowserPortlet) UI.getCurrent()).getPortletSession();
      Map<String, SimpleEntry<String, Long>> entries =
          (Map<String, SimpleEntry<String, Long>>) portletSession.getAttribute("qbic_download",
              PortletSession.APPLICATION_SCOPE);

      boolean itemSelected = (Boolean) event.getProperty().getValue();
      /*
       * String fileName = ""; Object parentId = table.getParent(itemId); //In order to prevent
       * infinity loop int folderDepth = 0; while(parentId != null && folderDepth < 100){ fileName =
       * Paths.get((String) table.getItem(parentId).getItemProperty("File Name").getValue(),
       * fileName).toString(); parentId = table.getParent(parentId); folderDepth++; }
       */

      valueChange(itemId, itemSelected, entries, itemFolderName);
      portletSession.setAttribute("qbic_download", entries, PortletSession.APPLICATION_SCOPE);

      if (entries == null || entries.isEmpty()) {
        download.setResource(new ExternalResource("javascript:"));
        download.setEnabled(false);
      } else {
        String resourceUrl =
            (String) portletSession.getAttribute("resURL", PortletSession.APPLICATION_SCOPE);
        download.setResource(new ExternalResource(resourceUrl));
        download.setEnabled(true);
      }

    }

    /**
     * updates entries (puts and removes) for selected table item and all its children. Means
     * Checkbox is updated. And in case download button is clicked all checked items will be
     * downloaded.
     *
     * @param itemId Container id
     * @param itemSelected checkbox value of the item
     * @param entries all checked items
     * @param fileName fileName of current item
     */
    private void valueChange(Object itemId, boolean itemSelected,
        Map<String, SimpleEntry<String, Long>> entries, String fileName) {

      ((CheckBox) datasetTable.getItem(itemId).getItemProperty("Select").getValue())
          .setValue(itemSelected);
      fileName = Paths
          .get(fileName,
              (String) datasetTable.getItem(itemId).getItemProperty("File Name").getValue())
          .toString();

      // System.out.println(fileName);
      if (datasetTable.hasChildren(itemId)) {
        for (Object childId : datasetTable.getChildren(itemId)) {
          valueChange(childId, itemSelected, entries, fileName);
        }
      } else if (itemSelected) {
        String datasetCode =
            (String) datasetTable.getItem(itemId).getItemProperty("CODE").getValue();
        Long datasetFileSize =
            (Long) datasetTable.getItem(itemId).getItemProperty("file_size_bytes").getValue();
        entries.put(fileName, new SimpleEntry<String, Long>(datasetCode, datasetFileSize));
      } else {
        entries.remove(fileName);
      }
    }
  }

  /**
   * The input should have the following form: type=openbis_type&id=openbis_id e.g.
   * type=sample&id=/ABI_SYSBIO/QMARI117AV It is specifically designed to be used in the case of
   * datasetView. In other cases there is no guarantee that it will work correctly. returns a map
   * with two entries: "type": "openbistype" "id" : "openbisId"
   */
  public static Map<String, String> getMap(String parameters) {
    if (parameters == null || parameters.equals("")) {
      return null;
    }
    String[] params = parameters.split("&");
    // TODO check for length == 2 needed ?
    // if (params == null || params.length != 2)
    if (params == null || params.length > 3) {
      return null;
    }
    HashMap<String, String> map = new HashMap<String, String>();
    for (String p : params) {
      String[] kv = p.split("=");
      if (kv.length != 2) {
        return null;
      }
      map.put(kv[0], kv[1]);
    }
    return map;
  }
}
