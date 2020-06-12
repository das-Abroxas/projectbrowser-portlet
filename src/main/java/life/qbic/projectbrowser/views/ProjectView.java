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
package life.qbic.projectbrowser.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.project.Project;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import life.qbic.portal.utils.PortalUtils;
import org.tepi.filtertable.FilterTable;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Page;
import com.vaadin.server.Resource;
import com.vaadin.server.StreamResource;
import com.vaadin.server.WebBrowser;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomTable.RowHeaderMode;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.openbis.openbisclient.OpenBisClient;

import life.qbic.portal.utils.ConfigurationManager;

import life.qbic.projectbrowser.model.ProjectBean;
import life.qbic.projectbrowser.model.ExperimentBean;
import life.qbic.projectbrowser.samplegraph.*;
import life.qbic.xml.properties.Property;
import life.qbic.projectbrowser.components.*;
import life.qbic.projectbrowser.controllers.*;
import life.qbic.projectbrowser.helpers.GraphGenerator;
import life.qbic.projectbrowser.controllers.WorkflowViewController;
import life.qbic.projectbrowser.helpers.Utils;
import life.qbic.projectbrowser.helpers.DatasetViewFilterDecorator;
import life.qbic.projectbrowser.helpers.DatasetViewFilterGenerator;
import life.qbic.projectbrowser.helpers.ViewTablesClickListener;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("serial")
public class ProjectView extends VerticalLayout implements View {

  private static final Logger logger = LogManager.getLogger(ProjectView.class);
  public final static String navigateToLabel = "project";

  FilterTable table;
  VerticalLayout projectview_content;
  VerticalLayout buttonLayoutSection;


  private ProjectBean currentBean;

  private Button export;

  private DataHandler datahandler;

  private State state;

  private FileDownloader fileDownloader;

  private String resourceUrl;

  private Label contact;

  private Label descContent;

  private VerticalLayout status;

  private HorizontalLayout statContent;

  private VerticalLayout graphSectionContent;
  private GraphPage newGraphContent;

  private VerticalLayout membersSection;
  private StringBuilder memberString;

  private TabSheet projectview_tab;

  private HorizontalLayout membersLayout;

  private DatasetComponent datasetComponent;

  Map<String, String> members = new TreeMap<String, String>();
  Map<String, String> memberLetters = new HashMap<String, String>();

  private LevelComponent measuredSamplesComponent;

  private BiologicalSamplesComponent biologicalSamplesComponent;

  private LevelComponent resultsComponent;

  private static final Logger LOG = LogManager.getLogger(ProjectView.class);

  private String headerLabel;

  private WorkflowViewController wfController;

  private WorkflowComponent workflowComponent;

  private ConfigurationManager manager;

  private AttachmentUploadComponent uploadComponent;

  private ProjInformationComponent projectInformation;

  private VerticalLayout projDescriptionContent;

  private ExperimentComponent experimentComponent;


  public String getHeaderLabel() {
    return headerLabel;
  }

  public void setHeaderLabel(String headerLabel) {
    this.headerLabel = headerLabel;
  }

  public ProjectView(DataHandler datahandler, State state, String resourceurl,
      WorkflowViewController wfController, ConfigurationManager manager) {
    this(datahandler, state, wfController);
    this.resourceUrl = resourceurl;
    this.manager = manager;
  }

  public ProjectView(DataHandler datahandler, State state, WorkflowViewController wfController) {
    this.wfController = wfController;
    this.datahandler = datahandler;
    this.state = state;
    resourceUrl = "javascript;";
    initView();
  }

  /**
   * updates view, if height, width or the browser changes.
   * 
   * @param browserHeight
   * @param browserWidth
   * @param browser
   */
  public void updateView(int browserHeight, int browserWidth, WebBrowser browser) {
    setWidth((browserWidth * 0.85f), Unit.PIXELS);
  }

  /**
   * init this view. builds the layout skeleton Menubar Description and others Statisitcs Experiment
   * Table Graph
   */
  void initView() {
    projectview_content = new VerticalLayout();
    projectview_content.setMargin(new MarginInfo(true, false, false, false));

    // labelContent = new VerticalLayout();
    // labelContent.setMargin(new MarginInfo(true, false, true, false));

    headerLabel = "";

    // labelContent.addComponent(headerLabel);
    // projectview_content.addComponent(labelContent);

    projectview_tab = new TabSheet();
    projectview_tab.setWidth("100%");
    projectview_tab.setHeight("100%");

    datasetComponent = new DatasetComponent(datahandler, state, resourceUrl);
    biologicalSamplesComponent =
        new BiologicalSamplesComponent(datahandler, state, resourceUrl, "Biological Samples");
    measuredSamplesComponent = new LevelComponent(datahandler, state, resourceUrl, "Raw Data");
    resultsComponent = new LevelComponent(datahandler, state, resourceUrl, "Results");
    workflowComponent = new WorkflowComponent(wfController);
    uploadComponent = new AttachmentUploadComponent();
    projectInformation = new ProjInformationComponent(datahandler, state, resourceUrl);
    experimentComponent = new ExperimentComponent(datahandler, state, resourceUrl);

    // add styles to tab sheet
    projectview_tab.addStyleName(ValoTheme.TABSHEET_EQUAL_WIDTH_TABS);
    projectview_tab.addStyleName(ValoTheme.TABSHEET_FRAMED);
    // projectview_tab.addStyleName(ValoTheme.TABSHEET_PADDED_TABBAR);

    // add tabs to tabsheet
    projectview_tab.addTab(projectInformation).setIcon(FontAwesome.INFO_CIRCLE);
    projectview_tab.addTab(initGraphs()).setIcon(FontAwesome.SITEMAP);
    // projectview_tab.addTab(initMemberSection()).setIcon(FontAwesome.USERS);

    projectview_tab.addTab(experimentComponent).setIcon(FontAwesome.FLASK);
    projectview_tab.addTab(datasetComponent).setIcon(FontAwesome.DATABASE);
    projectview_tab.addTab(biologicalSamplesComponent).setIcon(FontAwesome.TINT);
    projectview_tab.addTab(measuredSamplesComponent).setIcon(FontAwesome.SIGNAL);
    projectview_tab.addTab(resultsComponent).setIcon(FontAwesome.TH_LARGE);
    projectview_tab.addTab(workflowComponent).setIcon(FontAwesome.COGS);
    projectview_tab.addTab(uploadComponent).setIcon(FontAwesome.UPLOAD);

    projectview_tab.setImmediate(true);


    projectview_tab.addSelectedTabChangeListener(new SelectedTabChangeListener() {

      @Override
      public void selectedTabChange(SelectedTabChangeEvent event) {
        if (event.getTabSheet().getSelectedTab().getCaption().equals("Project Graph")) {
          loadGraph();
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Exp. Steps")) {
          experimentComponent.updateUI(getCurrentBean());
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Datasets")) {
          datasetComponent.updateUI(navigateToLabel, getCurrentBean().getId());
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Raw Data")) {
          measuredSamplesComponent.updateUI(navigateToLabel, getCurrentBean().getId(), "measured");
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Biological Samples")) {
          biologicalSamplesComponent.updateUI(getCurrentBean().getId());
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Results")) {
          resultsComponent.updateUI(navigateToLabel, getCurrentBean().getId(), "results");
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Workflows")) {
          Map<String, String> args = new HashMap<String, String>();
          args.put("id", getCurrentBean().getId());
          args.put("type", navigateToLabel);
          workflowComponent.update(args);
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("Upload Files")) {
          // (get space from currentBean)
          uploadComponent.updateUI(manager, getCurrentBean().getCode(),
              currentBean.getId().split("/")[1], datahandler.getOpenBisClient());
        } else if (event.getTabSheet().getSelectedTab().getCaption().equals("")) {
          projectInformation.updateUI(getCurrentBean(), "project");
        }
      }
    });

    // projectview_content.addComponent(initMenuBar());
    projectview_content.addComponent(projectview_tab);
    // projectview_content.addComponent(initDescription());
    // projectview_content.addComponent(initStatistics());
    // projectview_content.addComponent(initTable());
    // projectview_content.addComponent(initButtonLayout());

    // projectview_content.addComponent(initGraph());

    // use the component that is returned by initTable
    // projectview_content.setComponentAlignment(this.table, Alignment.TOP_CENTER);
    // projectview_content.setWidth("100%");
    this.addComponent(projectview_content);
  }

  /**
   * This function should be called each time currentBean is changed
   */
  public void updateContent() {
    // updateContentToolBar();
    headerLabel = String.format("%s", getCurrentBean().getCode());

    // updateContentDescription();
    // updateContentMemberSection();
    // updateContentStatistics();
    updateContentTable();

    // updateContentButtonLayout();

    projectInformation.updateUI(getCurrentBean(), "project");
  }


  /**
   * 
   * @return
   */
  Component initButtonLayout() {
    this.export = new Button("Export as TSV");
    buttonLayoutSection = new VerticalLayout();
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setHeight(null);
    buttonLayout.setWidth("100%");
    buttonLayout.addComponent(this.export);
    buttonLayout.setMargin(new MarginInfo(false, false, false, true));
    buttonLayoutSection.addComponent(buttonLayout);
    buttonLayoutSection.setSpacing(true);
    buttonLayoutSection.setMargin(new MarginInfo(false, false, false, true));

    return buttonLayoutSection;
  }

  void updateContentButtonLayout() {
    if (fileDownloader != null)
      this.export.removeExtension(fileDownloader);
    StreamResource sr = Utils.getTSVStream(Utils.containerToString(currentBean.getExperiments()),
        currentBean.getId());
    fileDownloader = new FileDownloader(sr);
    fileDownloader.extend(this.export);
  }


  /**
   * initializes the description layout
   * 
   * @return
   */
  VerticalLayout initDescription() {
    VerticalLayout projDescription = new VerticalLayout();
    projDescriptionContent = new VerticalLayout();

    projDescription.setCaption("");

    descContent = new Label("");
    contact = new Label("", ContentMode.HTML);
    projDescriptionContent.addComponent(descContent);
    projDescriptionContent.addComponent(contact);
    projDescriptionContent.setMargin(new MarginInfo(true, false, true, true));

    projDescription.addComponent(projDescriptionContent);
    projDescriptionContent.setSpacing(true);
    projDescription.setMargin(new MarginInfo(true, false, true, true));
    projDescription.setWidth("100%");
    projDescription.setSpacing(true);
    projDescription.addComponent(projectInformation);

    return projDescription;
  }

  VerticalLayout initMemberSection() {
    VerticalLayout projMembers = new VerticalLayout();
    projMembers.setCaption("Members");

    membersSection = new VerticalLayout();
    Component membersContent = new VerticalLayout();

    // membersContent.setIcon(FontAwesome.USERS);
    // membersContent.setCaption("Members");
    membersSection.addComponent(membersContent);
    // membersSection.setMargin(new MarginInfo(false, false, false, true));
    membersSection.setWidth("100%");
    membersSection.setSpacing(true);

    membersSection.setMargin(new MarginInfo(true, false, true, true));
    projMembers.addComponent(membersSection);

    projMembers.setMargin(new MarginInfo(true, false, true, true));
    projMembers.setWidth("100%");
    projMembers.setSpacing(true);

    return projMembers;
  }

  void updateContentDescription() {
    projDescriptionContent.removeAllComponents();
    contact.setValue(
        "<a href=\"mailto:support@qbic.zendesk.com?subject=Question%20concerning%20project%20"
            + currentBean.getId()
            + "\" style=\"color: #0068AA; text-decoration: none\">Send question regarding project "
            + currentBean.getId() + "</a>");
    String desc = currentBean.getDescription();
    if (!desc.isEmpty()) {
      descContent.setValue(desc);
    }

    projDescriptionContent.addComponent(
        new Label(String.format("%s experiment(s),", currentBean.getExperiments().size())));

    // VerticalLayout statusContent =
    // this.createProjectStatusComponent(datahandler.computeProjectStatuses(currentBean));

    // statusContent.setSpacing(true);
    // statusContent.setMargin(new MarginInfo(true, false, true, true));

    // projDescriptionContent.addComponent(statusContent);
  }

  /*
   * 
   */
  void updateContentMemberSection() {
    membersSection.removeAllComponents();
    membersSection.addComponent(getMembersComponent());
  }


  /**
   * 
   * @return
   * 
   */
  VerticalLayout initStatistics() {
    VerticalLayout statistics = new VerticalLayout();
    statistics.setCaption("Status");

    statContent = new HorizontalLayout();
    statContent.addComponent(new Label(""));

    statContent.setMargin(new MarginInfo(true, false, false, true));
    statContent.setSpacing(true);

    statistics.addComponent(statContent);
    statistics.setMargin(new MarginInfo(true, false, false, true));
    statistics.setSpacing(true);

    status = new VerticalLayout();
    status.setSpacing(true);

    statistics.addComponent(status);
    return statistics;
  }

  /**
   * 
   */
  void updateContentStatistics() {
    statContent.removeAllComponents();
    statContent.addComponent(
        new Label(String.format("%s experiment(s),", currentBean.getExperiments().size())));
    statContent.setMargin(new MarginInfo(true, false, true, true));

    status.removeAllComponents();
  }



  VerticalLayout initTable() {
    this.table = this.buildFilterTable();
    this.tableClickChangeTreeView();
    VerticalLayout tableSection = new VerticalLayout();
    VerticalLayout tableSectionContent = new VerticalLayout();

    tableSection.setCaption("Exp. Steps");
    // tableSectionContent.setCaption("Registered Experiments");
    // tableSectionContent.setIcon(FontAwesome.FLASK);
    tableSectionContent.addComponent(this.table);

    tableSectionContent.setMargin(new MarginInfo(true, false, false, true));
    tableSection.setMargin(new MarginInfo(true, false, false, true));
    this.table.setWidth("100%");
    tableSection.setWidth(Page.getCurrent().getBrowserWindowWidth() * 0.8f, Unit.PIXELS);
    tableSectionContent.setWidth("100%");

    tableSection.addComponent(tableSectionContent);

    this.export = new Button("Export as TSV");
    buttonLayoutSection = new VerticalLayout();
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setHeight(null);
    buttonLayout.setWidth("100%");
    buttonLayout.addComponent(this.export);
    buttonLayout.setMargin(new MarginInfo(false, false, true, false));
    buttonLayoutSection.addComponent(buttonLayout);
    buttonLayoutSection.setSpacing(true);
    buttonLayoutSection.setMargin(new MarginInfo(false, false, true, true));

    tableSection.addComponent(buttonLayoutSection);

    return tableSection;
  }


  /**
   * 
   */
  void updateContentTable() {
    // Nothing to do here at the moment
    // table is already set in setdataresource
  }

  /**
   * 
   */
  void resetGraphs() {
    graphSectionContent.removeAllComponents();
    newGraphContent.removeAllComponents();
  }

  /**
   * 
   * @return
   */
  VerticalLayout initGraph() {
    VerticalLayout graphSection = new VerticalLayout();
    graphSectionContent = new VerticalLayout();
    graphSection.setCaption("Project Graph");

    graphSectionContent.setMargin(new MarginInfo(true, false, true, true));
    graphSection.setMargin(new MarginInfo(true, false, true, true));
    graphSection.setWidth(Page.getCurrent().getBrowserWindowWidth() * 0.8f, Unit.PIXELS);
    graphSectionContent.setWidth("100%");
    graphSection.addComponent(graphSectionContent);
    return graphSection;
  }

  /**
   * for both graphs
   * 
   * @return the tablayout containing both graphs
   */
  Component initGraphs() {
    TabSheet graphTab = new TabSheet();
    graphTab.setCaption("Project Graph");

    VerticalLayout graphSection = new VerticalLayout();
    graphSectionContent = new VerticalLayout();

    graphSection.setCaption("Project Graph");

    graphSectionContent.setMargin(new MarginInfo(true, false, true, true));
    graphSection.setMargin(new MarginInfo(true, false, true, true));
    graphSection.setWidth("100%");
    graphSectionContent.setWidth("100%");

    OpenBisClient openbis = datahandler.getOpenBisClient();
    Map<String, String> taxMap = openbis.getVocabCodesAndLabelsForVocab("Q_NCBI_TAXONOMY");
    Map<String, String> tissueMap = openbis.getVocabCodesAndLabelsForVocab("Q_PRIMARY_TISSUES");

    newGraphContent = new GraphPage(taxMap, tissueMap);

    graphSection.addComponent(graphSectionContent);
    graphTab.addTab(graphSection, "Sample Graph");
    graphTab.addTab(newGraphContent, "Sample Graph v2 (beta)");
    return graphTab;
  }

  public void processed() {
    UI.getCurrent().setPollInterval(-1);
  }

  class Worker extends Thread {
    private ProjectView projectView;

    public Worker(ProjectView current) {
      projectView = current;
    }

    @Override
    public void run() {
      projectView.updateContentGraph();
      synchronized (UI.getCurrent()) {
        processed();
      }

    }
  }

  /**
   * 
   */
  public void loadGraph() {
    if (graphSectionContent.getComponentCount() == 0
        || !(graphSectionContent.getComponent(0) instanceof Image)) {
      ProgressBar progress = new ProgressBar();
      progress.setIndeterminate(true);
      Label info = new Label(
          "Computing the project graph can take several seconds on big projects. Please be patient.");
      info.setStyleName(ValoTheme.LABEL_SUCCESS);
      graphSectionContent.removeAllComponents();
      graphSectionContent.addComponent(progress);
      graphSectionContent.setComponentAlignment(progress, Alignment.MIDDLE_CENTER);
      Worker worker = new Worker(getCurrent());
      worker.start();
      UI.getCurrent().setPollInterval(500);
    }
  }

  /**
   * 
   * @return
   */
  public ProjectView getCurrent() {
    return this;
  }

  /**
   * 
   */
  void updateContentGraph() {
    String projectID = currentBean.getId();
    List<Sample> pSamples = datahandler.getOpenBisClient().getSamplesOfProject(projectID);
    List<Sample> samples = datahandler.getOpenBisClient().getSamplesWithParentsAndChildren(
            pSamples.stream().map(Sample::getCode).collect(Collectors.toList())
    );
    parseNewGraph(projectID, samples);
    Resource resource = getGraphResource(projectID, samples);

    if (resource != null) {
      graphSectionContent.removeAllComponents();
      Image graphImage = new Image("", resource);

      graphSectionContent.addComponent(graphImage);
      graphSectionContent.setComponentAlignment(graphImage, Alignment.MIDDLE_CENTER);
    } else {
      Label error = new Label("Project Graph can not be computed at that time for this project.");
      error.setStyleName(ValoTheme.LABEL_FAILURE);
      graphSectionContent.removeAllComponents();
      graphSectionContent.addComponent(error);
      graphSectionContent.setComponentAlignment(error, Alignment.MIDDLE_CENTER);

      LOG.error(String.format("%s: %s", error.getValue(), currentBean.getId()));
    }
  }


  private void parseNewGraph(String projectID, List<Sample> samples) {
    List<DataSet> datasets = datahandler.getOpenBisClient().getDataSetsOfProject(projectID);
    Set<String> factorLabels = datahandler.getFactorLabels();
    
    Map<Pair<String, String>, Property> factorsForLabelsAndSamples =
        datahandler.getFactorsForLabelsAndSamples();
    newGraphContent.loadProjectGraph(projectID, samples, datasets, factorLabels,
        factorsForLabelsAndSamples);
  }

  /**
   * 
   * @param resourceurl
   */
  public void setResourceUrl(String resourceurl) {
    this.resourceUrl = resourceurl;
  }

  /**
   * 
   * @return
   */
  public String getResourceUrl() {
    return resourceUrl;
  }

  /**
   * 
   * @return
   */
  public String getNavigatorLabel() {
    return navigateToLabel;
  }

  /**
   * sets the ContainerDataSource for showing it in a table and the id of the current Openbis
   * Project. The id is shown in the caption.
   * 
   * @param projectBean
   */
  public void setContainerDataSource(ProjectBean projectBean) {
    this.currentBean = projectBean;
    this.table.setContainerDataSource(projectBean.getExperiments());

    for (Iterator<?> i = table.getItemIds().iterator(); i.hasNext();) {
      // Get the current item identifier, which is an integer.
      ExperimentBean item = (ExperimentBean) i.next();

    }
    table.setVisibleColumns(
        new Object[] {"code", "prettyType", "registrationDate", "registrator", "status"});

    table.setColumnHeader("prettyType", "Type");
    int rowNumber = this.table.size();

    if (rowNumber == 0) {
      this.table.setVisible(false);
    } else {
      this.table.setVisible(true);
      this.table.setPageLength(Math.min(rowNumber, 10));
    }
  }


  /**
   * returns Resource which represents the project graph of the current Bean. Can be set as the
   * resource of an {@link Image}.
   * 
   * @return
   */
  private Resource getGraphResource(String projectID, List<Sample> samples) {
    Resource resource = null;
    try {
      GraphGenerator graphFrame =
          new GraphGenerator(samples, datahandler.getOpenBisClient().getSampleTypes(),
              datahandler.getOpenBisClient(), projectID);
      resource = graphFrame.getRes();
    } catch (IOException e) {
      LOG.error("graph creation failed", e.getStackTrace());
    }
    return resource;
  }

  private void tableClickChangeTreeView() {
    table.setSelectable(true);
    table.setImmediate(true);
    this.table
        .addValueChangeListener(new ViewTablesClickListener(table, ExperimentView.navigateToLabel));
  }

  /**
   * initializes and builds a filtering table for this view
   * 
   * @return
   */
  private FilterTable buildFilterTable() {
    FilterTable filterTable = new FilterTable();

    filterTable.setFilterDecorator(new DatasetViewFilterDecorator());
    filterTable.setFilterGenerator(new DatasetViewFilterGenerator());

    filterTable.setFilterBarVisible(true);


    filterTable.setSelectable(true);
    filterTable.setImmediate(true);

    filterTable.setRowHeaderMode(RowHeaderMode.INDEX);

    filterTable.setColumnCollapsingAllowed(true);

    filterTable.setColumnReorderingAllowed(true);


    filterTable.setColumnHeader("code", "Name");
    filterTable.setColumnHeader("type", "Type");
    filterTable.setColumnHeader("registrationDate", "Registration Date");
    filterTable.setColumnHeader("registrator", "Registered By");
    filterTable.setColumnHeader("status", "Status");

    return filterTable;
  }


  class MemberWorker extends Thread {

    @Override
    public void run() {
      Company company = null;
      long companyId = 1;
      try {
        String webId = PropsUtil.get(PropsKeys.COMPANY_DEFAULT_WEB_ID);
        company = CompanyLocalServiceUtil.getCompanyByWebId(webId);
        companyId = company.getCompanyId();

      } catch (PortalException | SystemException e) {
        LOG.error("liferay error, could not retrieve companyId. Trying default companyId, which is "
            + companyId, e.getStackTrace());
      }
      Set<String> list = datahandler.removeQBiCStaffFromMemberSet(
          datahandler.getOpenBisClient().getSpaceMembers(currentBean.getId().split("/")[1]));
      members = new TreeMap<String, String>();
      memberLetters = new HashMap<String, String>();

      if (list != null) {
        memberString = new StringBuilder();
        for (String member : list) {
          User user = null;
          try {
            user = UserLocalServiceUtil.getUserByScreenName(companyId, member);
          } catch (PortalException | SystemException e) {
          }

          if (memberString.length() > 0) {
            // memberString.append(" , ");
          }

          if (user == null) {
            LOG.warn(String.format("Openbis user %s appears to not exist in Portal", member));
            // memberString.append(member);
            members.put(member, member);
            // membersLayout.addComponent(new Label(member));
          } else {
            String firstName = user.getFirstName();
            String lastName = user.getLastName();

            String email = user.getEmailAddress();

            String userString =
                "<a href=\"mailto:" + email + "\" style=\"color: #0068AA; text-decoration: none\">"
                    + lastName + ", " + firstName + "</a>";

            if (user.getLastName().length() > 0) {
              members.put(user.getLastName(), userString);
            }

            else {
              members.put(user.getFirstName(), userString);
            }

          }
        }
        synchronized (UI.getCurrent()) {
          processedMember();
        }
      }
    }
  }

  /**
   * 
   * @return
   */
  private Component getMembersComponent() {
    membersLayout = new HorizontalLayout();
    membersLayout.setWidth("100%");

    ProgressBar progress = new ProgressBar();
    progress.setIndeterminate(true);
    Label info = new Label(
        "Searching for members. Can take several seconds on big projects. Please be patient.");
    info.setStyleName(ValoTheme.LABEL_SUCCESS);
    membersLayout.addComponent(progress);
    MemberWorker worker = new MemberWorker();
    worker.start();
    UI.getCurrent().setPollInterval(500);

    return membersLayout;
  }


  /**
   * 
   */
  public void processedMember() {
    String memberString = "";
    Label label;

    if (members.size() < 1) {
      label = new Label("No Members found.");
    } else {
      for (Entry<String, String> entry : members.entrySet()) {
        String firstLetter = String.valueOf(entry.getKey().charAt(0));

        if (!memberLetters.containsKey(firstLetter)) {
          memberString +=
              String.format("<font size='16'><b>%s</b></font><br>", firstLetter.toUpperCase());
          memberLetters.put(firstLetter, "");
        }

        memberString += String.format("%s<br>", entry.getValue());
      }
      label = new Label(memberString, ContentMode.HTML);
    }
    membersLayout.removeAllComponents();
    membersLayout.addComponent(label);
    membersLayout.setSpacing(true);
    membersLayout.setMargin(new MarginInfo(false, false, true, true));

    UI.getCurrent().setPollInterval(-1);
  }


  /**
   * 
   */
  @Override
  public void enter(ViewChangeEvent event) {
    String currentValue = event.getParameters();

    OpenBisClient oc = datahandler.getOpenBisClient();
    List<Project> userProjects = oc.listProjectsForUser(PortalUtils.getNonNullScreenName());

    List<String> projectIDs = new ArrayList<String>();

    for (Project p : userProjects) {
      projectIDs.add(p.getIdentifier().toString());
    }
    if (projectIDs.contains(currentValue)) {
      // TODO updateContent only if currentProject is not equal to newProject
      // this.table.unselect(this.table.getValue());
      ProjectBean pbean = datahandler.getProject2(currentValue);
      // if the new project bean is different then reset the graph.
      if (currentBean != null && !pbean.getId().equals(currentBean.getId())) {
        resetGraphs();
        projectview_tab.setSelectedTab(0);
      }
      this.currentBean = pbean;
      // this.setContainerDataSource(pbean);

      updateContent();
    } else {
      Utils.Notification("Unable to load project",
          String.format(
              "The requested project %s could not be loaded. You probably don't have access to the requested project. Please contact the corresponding project manager or write an email to support@qbic.zendesk.com.",
              currentValue),
          "error");
    }
  }


  /**
   * Enables or disables the component. The user can not interact disabled components, which are
   * shown with a style that indicates the status, usually shaded in light gray color. Components
   * are enabled by default.
   */
  public void setEnabled(boolean enabled) {
    this.export.setEnabled(enabled);
    this.table.setEnabled(enabled);
  }

  /**
   * 
   * @return
   */
  public ProjectBean getCurrentBean() {
    return currentBean;
  }

  /**
   * 
   * @param currentBean
   */
  public void setCurrentBean(ProjectBean currentBean) {
    this.currentBean = currentBean;
  }


}
