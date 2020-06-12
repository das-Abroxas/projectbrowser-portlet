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
package life.qbic.portal.portlet;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.project.Project;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.navigator.Navigator;
import com.vaadin.navigator.View;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinService;
import com.vaadin.server.WrappedPortletSession;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.openbis.openbisclient.OpenBisClient;
import life.qbic.portal.utils.ConfigurationManager;
import life.qbic.portal.utils.ConfigurationManagerFactory;
import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.controllers.DataHandler;
import life.qbic.projectbrowser.controllers.MultiscaleController;
import life.qbic.projectbrowser.controllers.State;
import life.qbic.projectbrowser.controllers.WorkflowViewController;
import life.qbic.projectbrowser.model.DBConfig;
import life.qbic.projectbrowser.model.DBManager;
import life.qbic.projectbrowser.views.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import submitter.Submitter;
import submitter.WorkflowSubmitterFactory;
import submitter.WorkflowSubmitterFactory.Type;

import javax.portlet.PortletSession;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;

/**
 * Entry point for portlet projectbrowser-portlet. This class derives from {@link QBiCPortletUI},
 * which is found in the {@code portal-utils-lib} library.
 * 
 * {@see https://github.com/qbicsoftware/portal-utils-lib}
 */

@Theme("mytheme")
@SuppressWarnings("serial")
@Widgetset("life.qbic.portal.portlet.AppWidgetSet")
public class ProjectBrowserPortlet extends QBiCPortletUI {

  private OpenBisClient openBisConnection;
  private DataHandler datahandler;
  private GridLayout mainLayout;
  private ConfigurationManager manager;

  private static final Logger LOG = LogManager.getLogger(ProjectBrowserPortlet.class);
  private String resUrl;
  protected View currentView;

  @Override
  protected Layout getPortletContent(final VaadinRequest request) {
    LOG.info("Generating content for {}", ProjectBrowserPortlet.class);

    manager = ConfigurationManagerFactory.getInstance();
    
    // initialize tmp folder
    try {
      File tmpFolder = new File(manager.getTmpFolder());
      if (!tmpFolder.exists()) {
        tmpFolder.mkdirs();
      }
    } catch (Exception e) {
      LOG.warn("unsuccessfully tried to initialize temporary folder:"+manager.getTmpFolder());
      LOG.warn(e.getMessage());
    }

    
    // check if we are allowed to display content to unauthenticated users
    if (PortalUtils.isLiferayPortlet() && PortalUtils.getUser() == null) {
      final ConfigurationManager configurationManager = ConfigurationManagerFactory.getInstance();
      if (!configurationManager.isAllowUnauthenticatedAccess("projectbrowser")) {
        // display a "please log in" message
        return PortalUtils.buildNotLoggedInLayout();
      }
    }

    Layout mainLayout;

    // log who is connecting, when.
    LOG.info(String.format("ProjectBrowser used by: %s", PortalUtils.getNonNullScreenName()));
    // try to init connection to openbis and write some session attributes, that can be accessed globally
    try {
      initConnection();
      initSessionAttributes();
    } catch (Exception e) {
      // probably the connection to openbis failed
      buildOpenbisConnectionErrorLayout(request);
      // write an error message if failed to load openbis and is in production
      errorMessageIfIsProduction();
    }
    this.resUrl = (String) getPortletSession().getAttribute("resURL", PortletSession.APPLICATION_SCOPE);
    mainLayout = initProgressBarAndThreading(request);

    return mainLayout;
  }

  /**
   *
   */
  void errorMessageIfIsProduction() {
    if (isInProductionMode())
      try {
        VaadinService.getCurrentResponse().sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT,
            "openbis could not be accessed.");
      } catch (IOException | IllegalArgumentException e1) {
        VaadinService.getCurrentResponse().setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
      }
  }

  /**
   *
   * @return
   */
  private boolean isInProductionMode() {
    return VaadinService.getCurrent().getDeploymentConfiguration().isProductionMode();
  }

  /**
   * standard error layout, if connection to database failed.
   *
   * @param request
   */
  private void buildOpenbisConnectionErrorLayout(final VaadinRequest request) {
    VerticalLayout vl = new VerticalLayout();
    this.setContent(vl);
    vl.addComponent(new Label(
        "An error occured while trying to connect to the database. Please try again later or contact your project manager."));
  }

  /**
   * standard error layout, if openbis threw error on initialization despite a successful login
   *
   * @param request
   */
  private Layout buildUserUnknownError(final VaadinRequest request) {
    VerticalLayout vl = new VerticalLayout();
    setContent(vl);
    vl.addComponent(new Label(
        "An error occured while trying to load projects. Please contact your project manager to make sure your account is added to your projects."));
    LOG.error(
        "Couldn't initialize view. User is probably not added to openBIS and has been informed to contact project manager.");

    return vl;
  }

  /**
   * starts the querying of openbis and initializing the view
   *
   * @param request
   */
  protected Layout initProgressBarAndThreading(VaadinRequest request) {
    GridLayout layout = new GridLayout();

    try {
      layout = buildMainLayout(datahandler, request, PortalUtils.getNonNullScreenName());
    } catch (Exception e) {
      if (datahandler.getOpenBisClient().loggedIn()) {
        LOG.error("User not known?", e);
        buildUserUnknownError(request);
      } else {
        LOG.error("exception thrown during initialization.", e);
      }
    }

    return layout;
  }

  /**
   *
   * @return
   */
  public static ProjectBrowserPortlet getCurrent() {
    return (ProjectBrowserPortlet) UI.getCurrent();
  }

  /**
   *
   * @param datahandler
   * @param request
   * @param user
   */
  public GridLayout buildMainLayout(DataHandler datahandler, VaadinRequest request, String user) {
    State state = (State) UI.getCurrent().getSession().getAttribute("state");
    MultiscaleController multiscaleController =
        new MultiscaleController(datahandler.getOpenBisClient(), user);

    final HomeView homeView =
        new HomeView(datahandler, "Your Projects", user, state, resUrl, manager.getTmpFolder());
    DatasetView datasetView = new DatasetView(datahandler, state, resUrl);
    final SampleView sampleView = new SampleView(datahandler, state, resUrl, multiscaleController);
    final ExperimentView experimentView =
        new ExperimentView(datahandler, state, resUrl, multiscaleController);
    final AddPatientView addPatientView = new AddPatientView(datahandler, state, resUrl);
    final SearchResultsView searchResultsView =
        new SearchResultsView(datahandler, "Search results", user, state, resUrl);

    Submitter submitter = null;
    try {
      submitter = WorkflowSubmitterFactory.getSubmitter(Type.guseSubmitter, manager);
    } catch (Exception e1) {
      e1.printStackTrace();
    }

    WorkflowViewController controller = new WorkflowViewController(submitter, datahandler, user);

    final ProjectView projectView =
        new ProjectView(datahandler, state, resUrl, controller, manager);
    final PatientView patientView =
        new PatientView(datahandler, state, resUrl, controller, manager);

    VerticalLayout navigatorContent = new VerticalLayout();

    final Navigator navigator = new Navigator(UI.getCurrent(), navigatorContent);

    navigator.addView(DatasetView.navigateToLabel, datasetView);
    navigator.addView(SampleView.navigateToLabel, sampleView);
    navigator.addView("", homeView);
    navigator.addView(ProjectView.navigateToLabel, projectView);
    // navigator.addView(BarcodeView.navigateToLabel, barcodeView);
    navigator.addView(ExperimentView.navigateToLabel, experimentView);
    navigator.addView(PatientView.navigateToLabel, patientView);
    navigator.addView(AddPatientView.navigateToLabel, addPatientView);
    navigator.addView(SearchResultsView.navigateToLabel, searchResultsView);

    setNavigator(navigator);

    // Production
    // mainLayout = new VerticalLayout();
    for (Window w : getWindows()) {
      w.setSizeFull();
    }

    mainLayout = new GridLayout(3, 3);
    mainLayout.setResponsive(true);
    mainLayout.setWidth(100, Unit.PERCENTAGE);

    mainLayout.addComponent(navigatorContent, 0, 1, 2, 1);
    mainLayout.setColumnExpandRatio(0, 0.2f);
    mainLayout.setColumnExpandRatio(1, 0.3f);
    mainLayout.setColumnExpandRatio(2, 0.5f);

    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setSpacing(true);

    Button homeButton = new Button("Home");
    homeButton.setIcon(FontAwesome.HOME);
    homeButton.setResponsive(true);
    homeButton.setStyleName(ValoTheme.BUTTON_LARGE);
    homeButton.addClickListener(new Button.ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        navigator.navigateTo("");
      }

    });

    // Production
    buttonLayout.addComponent(homeButton);

    Boolean includePatientCreation = false;

    List<Project> projects = datahandler.getOpenBisClient().listProjectsForUser(user);
    int numberOfProjects = 0;
    for (Project project : projects) {
      if (project.getSpace().getCode().contains("IVAC")) {
        includePatientCreation = true;
      }
      numberOfProjects += 1;
    }

    // add patient button
    if (includePatientCreation) {
      Button addPatient = new Button("Add Patient");
      addPatient.setIcon(FontAwesome.PLUS);
      addPatient.setStyleName(ValoTheme.BUTTON_LARGE);
      addPatient.setResponsive(true);

      addPatient.addClickListener(new ClickListener() {
        @Override
        public void buttonClick(ClickEvent event) {
          UI.getCurrent().getNavigator().navigateTo(String.format(AddPatientView.navigateToLabel));
        }
      });

      // Production
      buttonLayout.addComponent(addPatient);
    }

    mainLayout.addComponent(buttonLayout, 0, 0);

    Button header = new Button(String.format("Total number of projects: %s", numberOfProjects));
    header.setIcon(FontAwesome.HAND_O_RIGHT);
    header.setStyleName(ValoTheme.BUTTON_LARGE);
    header.addStyleName(ValoTheme.BUTTON_BORDERLESS);

    SearchEngineView searchBarView = new SearchEngineView(datahandler);

    mainLayout.addComponent(header, 1, 0);
    mainLayout.addComponent(searchBarView, 2, 0);

    mainLayout.setComponentAlignment(searchBarView, Alignment.BOTTOM_RIGHT);

    return mainLayout;
  }

  /**
   *
   * @return
   */
  public PortletSession getPortletSession() {
    UI.getCurrent().getSession().getService();
    VaadinRequest vaadinRequest = VaadinService.getCurrentRequest();
    WrappedPortletSession wrappedPortletSession =
        (WrappedPortletSession) vaadinRequest.getWrappedSession();
    return wrappedPortletSession.getPortletSession();
  }

  /**
   *
   */
  private void initSessionAttributes() {
    if (this.openBisConnection == null) {
      this.initConnection();
    }
    UI.getCurrent().getSession().setAttribute("state", new State());

    PortletSession portletSession = ((ProjectBrowserPortlet) UI.getCurrent()).getPortletSession();
    portletSession.setAttribute("openbisClient", this.openBisConnection,
        PortletSession.APPLICATION_SCOPE);

    portletSession.setAttribute("qbic_download",
        new HashMap<String, AbstractMap.SimpleEntry<String, Long>>(),
        PortletSession.APPLICATION_SCOPE);
  }

  /**
   *
   */
  private void initConnection() {
    this.openBisConnection = new OpenBisClient(manager.getDataSourceUser(),
        manager.getDataSourcePassword(), manager.getDataSourceUrl());
    this.openBisConnection.login();
    DBConfig mysqlConfig = new DBConfig(manager.getMysqlHost(), manager.getMysqlPort(),
        manager.getMysqlDB(), manager.getMysqlUser(), manager.getMysqlPass());
    DBManager databaseManager = new DBManager(mysqlConfig);

    this.datahandler = new DataHandler(openBisConnection, databaseManager);
  }
}
