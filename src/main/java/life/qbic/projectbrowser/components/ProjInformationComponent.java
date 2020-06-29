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
import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.search.ExperimentSearchCriteria;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.search.SampleSearchCriteria;
import com.vaadin.data.Item;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.data.util.HierarchicalContainer;
import com.vaadin.event.FieldEvents.BlurEvent;
import com.vaadin.event.FieldEvents.BlurListener;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Resource;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Window.CloseEvent;
import com.vaadin.ui.Window.CloseListener;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.datamodel.experiments.ExperimentType;
import life.qbic.datamodel.sorters.SampleExtIDComparator;
import life.qbic.portal.portlet.ProjectBrowserPortlet;
import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.controllers.DataHandler;
import life.qbic.projectbrowser.controllers.State;
import life.qbic.projectbrowser.helpers.DatasetViewFilterDecorator;
import life.qbic.projectbrowser.helpers.DatasetViewFilterGenerator;
import life.qbic.projectbrowser.helpers.QcMlOpenbisSource;
import life.qbic.projectbrowser.model.DatasetBean;
import life.qbic.projectbrowser.model.ProjectBean;
import life.qbic.projectbrowser.model.TestSampleBean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tepi.filtertable.FilterTreeTable;

import javax.portlet.PortletSession;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;

public class ProjInformationComponent extends CustomComponent {

  /**
   * 
   */
  private static final long serialVersionUID = 8672873911284888801L;

  private static final Logger LOG = LogManager.getLogger(ProjInformationComponent.class);
  private FilterTreeTable datasetTable;
  private HierarchicalContainer datasets;
  private BeanItemContainer<TestSampleBean> samples;
  VerticalLayout vert;
  private final String DOWNLOAD_BUTTON_CAPTION = "Download";
  private final String VISUALIZE_BUTTON_CAPTION = "Visualize";
  public final static String navigateToLabel = "datasetview";
  // Label descriptionLabel = new Label("");
  private DataHandler datahandler;
  private State state;
  private String resourceUrl;
  private final ButtonLink download =
      new ButtonLink(DOWNLOAD_BUTTON_CAPTION, new ExternalResource(""));

  private final String[] FILTER_TABLE_COLUMNS =
      new String[] {"Select", "Description", "File Name", "Registration Date"};

  private int numberOfDatasets;

  private Label investigator;
  private Label contactPerson;
  private Label descContent;

  private EditableLabel longDescription; // for PCT

  private Label contact;

  private Label patientInformation;

  private ProjectBean projectBean;
  private String projectType;

  private Label experimentLabel;

  private Label hlaTypeLabel;

  private VerticalLayout statusContent;

  private TSVDownloadComponent tsvDownloadContent;

  // private HorizontalLayout horz;
  private HorizontalLayout descHorz;
  private Button descEdit;

  private Accordion peopleInCharge;

  private ChangeProjectMetadataComponent changeMetadata;

  private Label projectManager;

  private Panel statusPanel;
  private Panel descriptionPanel;

  public ProjInformationComponent(DataHandler dh, State state, String resourceurl) {
    this.datahandler = dh;
    this.resourceUrl = resourceurl;
    this.state = state;

    changeMetadata = new ChangeProjectMetadataComponent(dh, state, resourceurl);

    this.setCaption("");

    this.initUI();
  }

  private void initUI() {
    vert = new VerticalLayout();
    descHorz = new HorizontalLayout();
    // horz = new HorizontalLayout();
    statusPanel = new Panel();
    descriptionPanel = new Panel();
    datasetTable = buildFilterTable();
    peopleInCharge = new Accordion();

    setResponsive(true);
    vert.setResponsive(true);
    descHorz.setResponsive(true);
    statusPanel.setResponsive(true);
    descriptionPanel.setResponsive(true);

    vert.setMargin(new MarginInfo(true, true, false, false));

    setSizeFull();
    vert.setSizeFull();
    descHorz.setSizeFull();
    statusPanel.setSizeFull();
    descriptionPanel.setSizeFull();

    investigator = new Label("", ContentMode.HTML);
    contactPerson = new Label("", ContentMode.HTML);
    projectManager = new Label("", ContentMode.HTML);

    final VerticalLayout layoutI = new VerticalLayout(investigator);
    final VerticalLayout layoutC = new VerticalLayout(contactPerson);
    final VerticalLayout layoutP = new VerticalLayout(projectManager);

    layoutI.setMargin(true);
    layoutC.setMargin(true);
    layoutP.setMargin(true);

    peopleInCharge.addTab(layoutI, "Investigator");
    peopleInCharge.addTab(layoutC, "Contact Person");
    peopleInCharge.addTab(layoutP, "Project Manager");

    descEdit = new Button("Edit");
    descEdit.setIcon(FontAwesome.PENCIL);
    descEdit.setStyleName(ValoTheme.BUTTON_BORDERLESS_COLORED);
    descEdit.setResponsive(true);

    descEdit.addClickListener(new ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        changeMetadata.updateUI(projectBean);
        VerticalLayout subContent = new VerticalLayout();
        subContent.setMargin(true);
        subContent.addComponent(changeMetadata);

        Window subWindow = new Window("Edit Metadata");
        subWindow.setContent(subContent);
        // Center it in the browser window
        subWindow.center();
        subWindow.setModal(true);
        subWindow.setIcon(FontAwesome.PENCIL);
        subWindow.setHeight("75%");
        subWindow.setResizable(false);
        // subWindow.setSizeFull();

        subWindow.addCloseListener(new CloseListener() {
          /**
           * 
           */
          private static final long serialVersionUID = -1329152609834711109L;

          @Override
          public void windowClose(CloseEvent e) {
            ProjectBean updatedBean = datahandler.getProjectFromDB(projectBean.getId());
            updateUI(updatedBean, projectType);
          }
        });

        ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
        ui.addWindow(subWindow);
      }
    });

    // horz.addComponent(descEdit);
    // horz.setComponentAlignment(descEdit, Alignment.TOP_RIGHT);
    // horz.setExpandRatio(investigator, 0.4f);
    // horz.setExpandRatio(contactPerson, 0.4f);
    // horz.setExpandRatio(descEdit, 0.2f);

    contact = new Label("", ContentMode.HTML);
    patientInformation = new Label("No patient information provided.", ContentMode.HTML);

    experimentLabel = new Label("");
    statusContent = new VerticalLayout();
    hlaTypeLabel = new Label("Not available.", ContentMode.HTML);
    hlaTypeLabel.setStyleName("patientview");

    this.setCompositionRoot(vert);
    // this.setCompositionRoot(mainLayout);
  }

  private void initTSVDownloads(String space, String project) {
    tsvDownloadContent = new TSVDownloadComponent();
    tsvDownloadContent.setVisible(false);
    List<String> types = new ArrayList<String>(
        Arrays.asList("Q_BIOLOGICAL_ENTITY", "Q_BIOLOGICAL_SAMPLE", "Q_TEST_SAMPLE"));
    boolean tsvable = false;
    for (Sample s : datahandler.getOpenBisClient().getSamplesOfProject("/" + space + "/" + project))
      if (types.contains(s.getType().getCode())) {
        tsvable = true;
        break;
      }
    if (tsvable) {
      // need to be disabled first so old project tsvs are not downloadable
      tsvDownloadContent.disableSpreadSheets();
      tsvDownloadContent.prepareSpreadsheets(types, space, project, datahandler.getOpenBisClient(),
          datahandler.getFactorLabels(), datahandler.getFactorsForLabelsAndSamples());
      tsvDownloadContent.setVisible(true);
    } else {
      // nothing to create a tsv from
      tsvDownloadContent.setVisible(false);
    }
  }

  public void updateUI(final ProjectBean currentBean, String projectType) {

    if (currentBean.getId() == null)
      return;
    try {
      this.projectType = projectType;
      projectBean = currentBean;

      String identifier = currentBean.getId();
      String space = identifier.split("/")[1];

      String pi = projectBean.getPrincipalInvestigator();
      investigator.setValue(pi);

      String ctct = projectBean.getContactPerson();
      contactPerson.setValue(ctct);

      String pm = projectBean.getProjectManager();
      projectManager.setValue(pm);

      // ToDo: Set correct contact. Maybe email of Project Manager if available
      // datahandler.getDatabaseManager().getPersonDetailsForProject(identifier, "manager");

      contact.setValue(
          "<a href=\"mailto:support@qbic.zendesk.com?subject=Question%20concerning%20project%20"
              + identifier
              + "\" style=\"color: #0068AA; text-decoration: none\">Send question regarding project "
              + currentBean.getCode() + " (" + space + ")" + "</a>");

      descHorz.removeAllComponents();
      descContent = new Label("", ContentMode.HTML);

      String desc = currentBean.getDescription();
      if (!desc.isEmpty()) {
        String secondaryName = projectBean.getSecondaryName();
        String[] codes = projectBean.getId().split("/");

        if (secondaryName.equals("n/a") || secondaryName.equals(desc)) {
          descContent.setCaption(String.format("%s-%s", codes[1], codes[2]));
        } else {
          descContent.setCaption(String.format("%s-%s: ", codes[1], codes[2]) + secondaryName);
        }
        descContent.setValue(desc);
      }

      longDescription = new EditableLabel(projectBean.getLongDescription());
      longDescription.addBlurListener(new BlurListener() {

        @Override
        public void blur(BlurEvent event) {
          String value = longDescription.getValue();
          datahandler.getDatabaseManager().changeLongProjectDescription(currentBean.getId(), value);
          currentBean.setLongDescription(value);
        }
      });
      descriptionPanel.setCaption("Detailed Description");
      descriptionPanel.setContent(longDescription);

      // experimentLabel.setValue((String.format("This project includes %s experimental step(s)",
      // currentBean.getExperiments().size())));

      statusPanel.setCaption((String.format("Project includes %s experimental step(s)",
          currentBean.getExperiments().size())));

      statusContent = datahandler
          .createProjectStatusComponentNew(datahandler.computeProjectStatuses(currentBean));
      statusPanel.setContent(statusContent);
      statusPanel.setResponsive(true);
      statusPanel.setResponsive(true);
      statusContent.setWidth(25, Unit.PERCENTAGE);


      initTSVDownloads(space, currentBean.getCode());

      HierarchicalContainer datasetContainer = new HierarchicalContainer();
      datasetContainer.addContainerProperty("Select", CheckBox.class, null);
      datasetContainer.addContainerProperty("Project", String.class, null);
      datasetContainer.addContainerProperty("Description", String.class, null);
      datasetContainer.addContainerProperty("Sample", String.class, null);
      // datasetContainer.addContainerProperty("Sample Type", String.class, null);
      datasetContainer.addContainerProperty("File Name", String.class, null);
      datasetContainer.addContainerProperty("File Type", String.class, null);
      datasetContainer.addContainerProperty("Dataset Type", String.class, null);
      datasetContainer.addContainerProperty("Registration Date", String.class, null);
      datasetContainer.addContainerProperty("Validated", Boolean.class, null);
      datasetContainer.addContainerProperty("File Size", String.class, null);
      datasetContainer.addContainerProperty("file_size_bytes", Long.class, null);
      datasetContainer.addContainerProperty("dl_link", String.class, null);
      datasetContainer.addContainerProperty("CODE", String.class, null);


      final String projectIdentifier = currentBean.getId();
      List<DataSet> retrievedDatasetsAll = datahandler.getOpenBisClient().getDataSetsOfProject(projectIdentifier);
      List<DataSet> retrievedDatasets = new ArrayList<>();
      Map<String, ArrayList<DataSet>> datasetFilter = new HashMap<>();


      for (DataSet ds : retrievedDatasetsAll) {
        ArrayList<DataSet> values = datasetFilter.get(ds.getSample().getIdentifier().toString());

        if (values == null) {
          values = new ArrayList<>();
          datasetFilter.put(ds.getSample().getIdentifier().toString(), values);
        }
        values.add(ds);
      }

      List<Sample> allSamples = datahandler.getOpenBisClient().getSamplesOfProject(projectIdentifier);

      for (Sample sample : allSamples) {
        if (sample.getType().getCode().equals("Q_ATTACHMENT_SAMPLE")) {

          ArrayList<DataSet> foundDataset =
              datasetFilter.get(sample.getIdentifier());

          if (foundDataset != null) {
            for (DataSet ds : foundDataset) {
              if (ds.getProperties().get("Q_ATTACHMENT_TYPE").equals("INFORMATION")) {
                retrievedDatasets.add(ds);
              }
            }
          }
        }
      }

      this.datasetTable.setCaption("Project Data");

      numberOfDatasets = retrievedDatasets.size();
      this.datasetTable.setPageLength(Math.max(3, Math.min(numberOfDatasets, 10)));

      Boolean dataAvailable = true;

      if (numberOfDatasets == 0) {
        dataAvailable = false;
        // new Notification("No datasets available.",
        // "<br/>Please contact the project manager.", Type.WARNING_MESSAGE, true).show(Page
        // .getCurrent());
      } else {

        Map<String, String> samples = new HashMap<>();

        // project same for all datasets
        String projectCode =
                retrievedDatasets.get(0).getExperiment().getIdentifier().toString().split("/")[2];

        for (DataSet dataset : retrievedDatasets) {
          samples.put(dataset.getCode(), dataset.getSample().getIdentifier().toString().split("/")[2]);
        }

        List<DatasetBean> dsBeans = datahandler.queryDatasetsForFolderStructure(retrievedDatasets);

        for (DatasetBean d : dsBeans) {
          Date date = d.getRegistrationDate();
          SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
          String dateString = sd.format(date);
          // Timestamp ts = Timestamp.valueOf(dateString);
          String sampleID = samples.get(d.getCode());

          registerDatasetInTable(d, datasetContainer, projectCode, sampleID, dateString, null);
        }
      }

      this.setContainerDataSource(datasetContainer, dataAvailable, projectType);

    } catch (Exception e) {
      e.printStackTrace();
      LOG.error(String.format("getting dataset failed for dataset %s %s", currentBean.getId()),
          e.getStackTrace());
    }
  }

  public void setContainerDataSource(HierarchicalContainer newDataSource, Boolean dataAvailable,
      String projectType) {
    datasets = (HierarchicalContainer) newDataSource;
    datasetTable.setContainerDataSource(this.datasets);

    datasetTable.setVisibleColumns((Object[]) FILTER_TABLE_COLUMNS);

    datasetTable.setSizeFull();
    this.buildLayout(dataAvailable, projectType);
  }

  public HierarchicalContainer getContainerDataSource() {
    return this.datasets;
  }

  /**
   * Precondition: {DatasetView#table} has to be initialized. e.g. with
   * {DatasetView#buildFilterTable} If it is not, strange behaviour has to be expected. builds the
   * Layout of this view.
   */
  private void buildLayout(Boolean dataAvailable, String projectType) {
    vert.removeAllComponents();

    // Table (containing datasets) section
    VerticalLayout tableSection = new VerticalLayout();
    HorizontalLayout tableSectionContent = new HorizontalLayout();

    VerticalLayout projDescription = new VerticalLayout();
    VerticalLayout projDescriptionContent = new VerticalLayout();

    tableSectionContent.setMargin(new MarginInfo(false, false, false, false));
    projDescriptionContent.setMargin(new MarginInfo(false, false, false, false));

    descHorz.addComponent(descContent);
    descHorz.addComponent(descEdit);
    descHorz.setComponentAlignment(descEdit, Alignment.TOP_RIGHT);
    descHorz.setExpandRatio(descContent, 0.9f);
    descHorz.setExpandRatio(descEdit, 0.1f);

    projDescriptionContent.addComponent(descHorz);
    projDescriptionContent.addComponent(peopleInCharge);
    projDescriptionContent.addComponent(descriptionPanel);
    projDescriptionContent.addComponent(statusPanel);


    if (projectType.equals("patient")) {
      String patientInfo = "";
      Boolean available = false;

/*
      SearchCriteria sampleSc = new SearchCriteria();
      SearchCriteria projectSc = new SearchCriteria();
      SearchCriteria experimentSc = new SearchCriteria();

      sampleSc.addMatchClause(
          MatchClause.createAttributeMatch(MatchClauseAttribute.TYPE, "Q_BIOLOGICAL_ENTITY"));

      projectSc.addMatchClause(
          MatchClause.createAttributeMatch(MatchClauseAttribute.PROJECT, projectBean.getCode()));
      sampleSc.addSubCriteria(SearchSubCriteria.createExperimentCriteria(projectSc));

      experimentSc.addMatchClause(MatchClause.createAttributeMatch(MatchClauseAttribute.TYPE,
          ExperimentType.Q_EXPERIMENTAL_DESIGN.name()));
      sampleSc.addSubCriteria(SearchSubCriteria.createExperimentCriteria(experimentSc));

      List<Sample> samples = datahandler.getOpenBisClient().getFacade().searchForSamples(sampleSc);
*/

      SampleSearchCriteria ssc = new SampleSearchCriteria();
      ssc.withType().withCode().thatEquals("Q_BIOLOGICAL_ENTITY");
      ssc.withProject().withCode().thatEquals(projectBean.getCode());
      ssc.withExperiment().withType().withCode().thatEquals(ExperimentType.Q_EXPERIMENTAL_DESIGN.name());

      List<Sample> samples = datahandler.getOpenBisClient().getSamples(ssc);

      for (Sample sample : samples) {
        if (sample.getProperties().get("Q_ADDITIONAL_INFO") != null) {
          available = true;
          String[] splitted = sample.getProperties().get("Q_ADDITIONAL_INFO").split(";");
          for (String s : splitted) {
            String[] splitted2 = s.split(":");
            patientInfo += String.format("<p><u>%s</u>: %s </p> ", splitted2[0], splitted2[1]);
          }
        }
      }



      if (available) {
        patientInformation.setValue(patientInfo);
      } else {
        patientInformation.setValue("No patient information provided.");
      }

      updateHLALayout();
      projDescriptionContent.addComponent(patientInformation);
      projDescriptionContent.addComponent(hlaTypeLabel);

      // Vaccine Designer

      /*
       * Button vaccineDesigner = new Button("Vaccine Designer");
       * vaccineDesigner.setStyleName(ValoTheme.BUTTON_PRIMARY);
       * vaccineDesigner.setIcon(FontAwesome.CUBES);
       * 
       * vaccineDesigner.addClickListener(new ClickListener() {
       * 
       * @Override public void buttonClick(ClickEvent event) {
       * 
       * ArrayList<String> message = new ArrayList<String>(); message.add("clicked"); StringBuilder
       * sb = new StringBuilder("type="); sb.append("vaccinedesign"); sb.append("&");
       * sb.append("id="); sb.append(projectBean.getId()); message.add(sb.toString());
       * message.add(VaccineDesignerView.navigateToLabel); state.notifyObservers(message);
       */
      // UI.getCurrent().getNavigator()
      // .navigateTo(String.format(VaccineDesignerView.navigateToLabel));
      // }
      // });

      // projDescriptionContent.addComponent(vaccineDesigner);

    }


    projDescriptionContent.addComponent(tsvDownloadContent);

    projDescription.addComponent(projDescriptionContent);

    projDescriptionContent.setSpacing(true);
    projDescription.setMargin(new MarginInfo(false, false, true, true));
    projDescription.setWidth("100%");
    projDescription.setSpacing(true);

    // descriptionLabel.setWidth("100%");
    // tableSection.addComponent(descriptionLabel);
    tableSectionContent.addComponent(this.datasetTable);

    projDescriptionContent.addComponent(contact);

    tableSection.setMargin(new MarginInfo(true, false, false, true));
    tableSection.setSpacing(true);

    tableSection.addComponent(tableSectionContent);

    this.vert.addComponent(projDescription);

    datasetTable.setWidth("100%");
    tableSection.setWidth("100%");
    tableSectionContent.setWidth("100%");

    // this.table.setSizeFull();

    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setMargin(new MarginInfo(false, false, true, false));
    buttonLayout.setHeight(null);
    // buttonLayout.setWidth("100%");
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

    buttonLayout.addComponent(checkAll);
    buttonLayout.addComponent(uncheckAll);

    buttonLayout.addComponent(checkAll);
    buttonLayout.addComponent(uncheckAll);
    buttonLayout.addComponent(this.download);
    /**
     * prepare download.
     */
    download.setResource(new ExternalResource("javascript:"));
    download.setEnabled(false);


    for (final Object itemId : this.datasetTable.getItemIds()) {
      setCheckedBox(itemId,
          (String) this.datasetTable.getItem(itemId).getItemProperty("CODE").getValue());
    }


    /*
     * Send message that in datasetview the following was selected. WorkflowViews get those messages
     * and save them, if it is valid information for them.
     */
    this.datasetTable.addValueChangeListener(new ValueChangeListener() {
      /**
       * 
       */
      private static final long serialVersionUID = -3554627008191389648L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        // Nothing selected or more than one selected.
        Set<Object> selectedValues = (Set<Object>) event.getProperty().getValue();
        State state = (State) UI.getCurrent().getSession().getAttribute("state");
        ArrayList<String> message = new ArrayList<>();
        message.add("DataSetView");

        if (selectedValues != null && selectedValues.size() == 1) {
          Iterator<Object> iterator = selectedValues.iterator();
          Object next = iterator.next();
          String datasetType =
              (String) datasetTable.getItem(next).getItemProperty("Dataset Type").getValue();
          message.add(datasetType);
          String project =
              (String) datasetTable.getItem(next).getItemProperty("Project").getValue();

          String space = datahandler.getOpenBisClient().getProject(project).getSpace().getCode();// .getIdentifier().split("/")[1];
          message.add(project);
          message.add((String) datasetTable.getItem(next).getItemProperty("Sample").getValue());
          message.add((String) datasetTable.getItem(next).getItemProperty("dl_link").getValue());
          message.add((String) datasetTable.getItem(next).getItemProperty("File Name").getValue());
          message.add(space);

        } else {
          message.add("null");
        } // TODO
          // state.notifyObservers(message);

      }
    });

    this.datasetTable.addItemClickListener((ItemClickListener) event -> {
      Item selectedItem = datasetTable.getItem(event.getItemId());

      if (selectedItem != null ||
              event.isDoubleClick() ||
              ((boolean) selectedItem.getItemProperty("isDirectory").getValue())) { return; }

      String datasetCode     = (String) selectedItem.getItemProperty("CODE").getValue();
      String datasetFileName = (String) selectedItem.getItemProperty("File Name").getValue();

      try {
        Object parent = datasetTable.getParent(event.getItemId());
        String mimeType = "";

        if (parent != null) {
          String parentDatasetFileName = (String) datasetTable.getItem(parent).getItemProperty("File Name").getValue();
          datasetFileName = parentDatasetFileName + "/" + datasetFileName;
        }

        if (datasetFileName.endsWith(".pdf"))
          mimeType = "application/pdf";

        if (!"".equals(mimeType)) {
          QcMlOpenbisSource re = new QcMlOpenbisSource(datasetCode, datasetFileName, datahandler.getOpenBisClient());
          StreamResource streamres = new StreamResource(re, datasetFileName);
          streamres.setMIMEType( mimeType );

          BrowserFrame frame = new BrowserFrame("", streamres);
          frame.setSizeFull();
          frame.setHeight("100%");

          VerticalLayout subContent = new VerticalLayout();
          subContent.setMargin(true);
          subContent.setSizeFull();
          subContent.addComponent(frame);

          Window subWindow = new Window();
          subWindow.setContent(subContent);
          subWindow.center();  // Center it in the browser window
          subWindow.setModal(true);
          subWindow.setSizeUndefined();
          subWindow.setHeight("80%");
          subWindow.setWidth("80%");
          subWindow.setResizable(false);

          // Open it in the UI
          ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
          ui.addWindow(subWindow);
        }

      } catch (Exception e) {
        LOG.error(e.getMessage());
        LOG.error(String.format(
                "Visualization failed because of malformedURL for dataset: %s", datasetCode));
        Notification.show(
                "Given dataset has no file attached to it! " +
                        "Please Contact your project manager. " +
                        "Or check whether it already has some data",
                Notification.Type.ERROR_MESSAGE);
      }
    });


    // this.vert.addComponent(buttonLayout);
    if (dataAvailable) {
      this.vert.addComponent(tableSection);
      tableSection.addComponent(buttonLayout);
      projDescription.setMargin(new MarginInfo(false, false, false, true));
    }
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

    filterTable.setColumnCollapsingAllowed(true);
    filterTable.setColumnReorderingAllowed(true);

    if (this.datasets != null) {
      filterTable.setContainerDataSource(this.datasets);
    }

    return filterTable;
  }

  @SuppressWarnings("unchecked")
  public void registerDatasetInTable(DatasetBean d, HierarchicalContainer dataset_container,
      String project, String sample, String ts, Object parent) {
    if (d.hasChildren()) {

      Object new_ds = dataset_container.addItem();

      List<DatasetBean> subList = d.getChildren();


      dataset_container.setChildrenAllowed(new_ds, true);

      dataset_container.getContainerProperty(new_ds, "Select").setValue(new CheckBox());

      dataset_container.getContainerProperty(new_ds, "Project").setValue(project);
      dataset_container.getContainerProperty(new_ds, "Sample").setValue(sample);
      String secName = d.getProperties().get("Q_SECONDARY_NAME");

      // TODO add User here
      if (secName != null) {
        dataset_container.getContainerProperty(new_ds, "Description")
            .setValue(d.getProperties().get("Q_SECONDARY_NAME"));
      }

      dataset_container.getContainerProperty(new_ds, "File Name").setValue(d.getName());
      dataset_container.getContainerProperty(new_ds, "File Type").setValue("Folder");
      dataset_container.getContainerProperty(new_ds, "Dataset Type").setValue(d.getType());
      dataset_container.getContainerProperty(new_ds, "Registration Date").setValue(ts);
      dataset_container.getContainerProperty(new_ds, "Validated").setValue(true);
      dataset_container.getContainerProperty(new_ds, "dl_link").setValue(d.getDssPath());
      dataset_container.getContainerProperty(new_ds, "CODE").setValue(d.getCode());
      dataset_container.getContainerProperty(new_ds, "file_size_bytes").setValue(d.getFileSize());

      if (parent != null) {
        dataset_container.setParent(new_ds, parent);
      }

      for (DatasetBean file : subList) {
        registerDatasetInTable(file, dataset_container, project, sample, ts, new_ds);
      }

    } else {
      // System.out.println("Now it should be a file: " + filelist[0].getPathInDataSet());

      Object new_file = dataset_container.addItem();
      dataset_container.setChildrenAllowed(new_file, false);

      dataset_container.getContainerProperty(new_file, "Select").setValue(new CheckBox());
      dataset_container.getContainerProperty(new_file, "Project").setValue(project);
      dataset_container.getContainerProperty(new_file, "Sample").setValue(sample);
      String secName = d.getProperties().get("Q_SECONDARY_NAME");

      // TODO add User here too
      if (secName != null) {
        dataset_container.getContainerProperty(new_file, "Description")
            .setValue(d.getProperties().get("Q_SECONDARY_NAME"));
      }

      dataset_container.getContainerProperty(new_file, "File Name").setValue(d.getFileName());
      dataset_container.getContainerProperty(new_file, "File Type").setValue(d.getFileType());
      dataset_container.getContainerProperty(new_file, "Dataset Type").setValue(d.getType());
      dataset_container.getContainerProperty(new_file, "Registration Date").setValue(ts);
      dataset_container.getContainerProperty(new_file, "Validated").setValue(true);
      dataset_container.getContainerProperty(new_file, "File Size")
          .setValue(PortalUtils.humanReadableByteCount(d.getFileSize(), true));
      dataset_container.getContainerProperty(new_file, "dl_link").setValue(d.getDssPath());
      dataset_container.getContainerProperty(new_file, "CODE").setValue(d.getCode());
      dataset_container.getContainerProperty(new_file, "file_size_bytes").setValue(d.getFileSize());
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

  // TODO seems this isn't used. can we delete it?
  /**
   * The input should have the following form: type=openbis_type&id=openbis_id e.g.
   * type=sample&id=/ABI_SYSBIO/QMARI117AV It is specifically designed to be used in the case of
   * datasetView. In other cases there is no guarantee that it will work correctly. returns a map
   * with two entries: "type": "openbistype" "id" : "openbisId"
   * 
   * @param parameters
   * @return
   */
  public static Map<String, String> getMap(String parameters) {
    if (parameters == null || parameters.equals(""))
      return null;
    String[] params = parameters.split("&");
    // TODO check for length == 2 needed ?
    // if (params == null || params.length != 2)
    if (params == null || params.length > 3)
      return null;
    HashMap<String, String> map = new HashMap<String, String>();
    for (String p : params) {
      String[] kv = p.split("=");
      if (kv.length != 2)
        return null;
      map.put(kv[0], kv[1]);
    }
    return map;
  }

  void updateHLALayout() {

    String labelContent = "<head> <title></title> </head> <body> ";
    Boolean available = false;

/*
    SearchCriteria sc = new SearchCriteria();
    SearchCriteria projectSc = new SearchCriteria();
    SearchCriteria experimentSc = new SearchCriteria();

    sc.addMatchClause(
            MatchClause.createAttributeMatch(
                    MatchClauseAttribute.TYPE, ExperimentType.Q_NGS_HLATYPING.name()));

    projectSc.addMatchClause(
        MatchClause.createAttributeMatch(MatchClauseAttribute.PROJECT, projectBean.getCode()));
    sc.addSubCriteria(SearchSubCriteria.createExperimentCriteria(projectSc));

    experimentSc.addMatchClause(MatchClause.createAttributeMatch(MatchClauseAttribute.TYPE,
        ExperimentType.Q_NGS_HLATYPING.name()));
    sc.addSubCriteria(SearchSubCriteria.createExperimentCriteria(experimentSc));

    List<Sample> samples = datahandler.getOpenBisClient().getFacade().searchForSamples(sc);


    SearchCriteria sc2 = new SearchCriteria();
    SearchCriteria projectSc2 = new SearchCriteria();
    SearchCriteria experimentSc2 = new SearchCriteria();

    sc2.addMatchClause(
            MatchClause.createAttributeMatch(
            MatchClauseAttribute.TYPE, ExperimentType.Q_WF_NGS_HLATYPING.name()));

    projectSc2.addMatchClause(
            MatchClause.createAttributeMatch(
                    MatchClauseAttribute.PROJECT, projectBean.getCode()));
    sc2.addSubCriteria(SearchSubCriteria.createExperimentCriteria(projectSc2));

    experimentSc2.addMatchClause(
            MatchClause.createAttributeMatch(
                    MatchClauseAttribute.TYPE, ExperimentType.Q_WF_NGS_HLATYPING.name()));
    sc2.addSubCriteria(SearchSubCriteria.createExperimentCriteria(experimentSc2));

    List<Experiment> wfExperiments =
        datahandler.getOpenBisClient().getFacade().searchForExperiments(sc2);
*/

    SampleSearchCriteria ssc = new SampleSearchCriteria();
    ssc.withType().withCode().thatEquals(ExperimentType.Q_NGS_HLATYPING.name());
    ssc.withProject().withCode().thatEquals(projectBean.getCode());
    ssc.withExperiment().withType().withCode().thatEquals(ExperimentType.Q_NGS_HLATYPING.name());

    ExperimentSearchCriteria esc = new ExperimentSearchCriteria();
    esc.withType().withCode().thatEquals(ExperimentType.Q_WF_NGS_HLATYPING.name());
    esc.withProject().withCode().thatEquals(projectBean.getCode());

    List<Sample> samples = datahandler.getOpenBisClient().getSamples(ssc);
    List<Sample> wfSamples = new ArrayList<>();
    List<Experiment> wfExperiments = datahandler.getOpenBisClient().getExperiments(esc);

    for (Experiment exp : wfExperiments) {
      // Note: Should be exp.getIdentifier().toString() since getCode() of experiment never contains project code
      if (exp.getCode().contains(projectBean.getCode())) {
        wfSamples
            .addAll(datahandler.getOpenBisClient().getSamplesOfExperiment(exp.getIdentifier().toString()));
      }
    }

    for (Sample sample : samples) {
      available = true;
      String classString = sample.getProperties().get("Q_HLA_CLASS");

      String lastOne = "";
      if (classString != null) {
        String[] splitted = classString.split("_");
        lastOne = splitted[splitted.length - 1];
      } else {
        lastOne = "unknown";
      }

      String addInformation = "";

      if (!(sample.getProperties().get("Q_ADDITIONAL_INFO") == null)) {
        addInformation = sample.getProperties().get("Q_ADDITIONAL_INFO");
      }

      labelContent += String.format("MHC Class %s " + "<p><u>Patient</u>: %s </p> " + "<p>%s </p> ",
          lastOne, sample.getProperties().get("Q_HLA_TYPING"), addInformation);
    }

    for (Sample sample : wfSamples) {
      available = true;


      if (!(sample.getProperties().get("Q_HLA_TYPING") == null)) {
        labelContent += String.format("<u>Computational Typing (OptiType)</u>" + "<p> %s </p> ",
            sample.getProperties().get("Q_HLA_TYPING"));
      }
    }

    labelContent += "</body>";
    if (available) {
      hlaTypeLabel.setValue(labelContent);
    }

    else {
      hlaTypeLabel.setValue("HLA typing not available.");
    }
  }

  public Label getHlaTypeLabel() {
    return hlaTypeLabel;
  }

  public void setHlaTypeLabel(Label hlaTypeLabel) {
    this.hlaTypeLabel = hlaTypeLabel;
  }

}
