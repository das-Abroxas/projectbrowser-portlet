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

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Layout;
import com.vaadin.ui.HorizontalLayout;

import life.qbic.portal.utils.PortalUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;

import javax.portlet.PortletSession;
import javax.servlet.http.HttpServletResponse;

import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.theme.ThemeDisplay;
import com.vaadin.navigator.Navigator;
import com.vaadin.navigator.View;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinService;
import com.vaadin.server.WrappedPortletSession;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ValoTheme;

import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Project;
import life.qbic.projectbrowser.controllers.*;
import life.qbic.projectbrowser.views.*;
import life.qbic.projectbrowser.model.DBConfig;
import life.qbic.projectbrowser.model.DBManager;
import life.qbic.openbis.openbisclient.OpenBisClient;
import life.qbic.portal.utils.ConfigurationManager;
import life.qbic.portal.utils.ConfigurationManagerFactory;
import submitter.Submitter;
import submitter.WorkflowSubmitterFactory;
import submitter.WorkflowSubmitterFactory.Type;

/**
 * Entry point for portlet projectbrowser-portlet. This class derives from {@link QBiCPortletUI},
 * which is found in the {@code portal-utils-lib} library.
 * 
 * @see https://github.com/qbicsoftware/portal-utils-lib
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
  private String version = "1.6.3";
  private String revision = "da6891a";
  private String resUrl;
  protected View currentView;

  @Override
  protected Layout getPortletContent(final VaadinRequest request) {
    LOG.info("Generating content for {}", ProjectBrowserPortlet.class);

    Layout mainLayout;

    if (PortalUtils.getUser() == null) {
      mainLayout = buildNotLoggedinLayout();
    } else {
      manager = ConfigurationManagerFactory.getInstance();
      // log who is connecting, when.
      LOG.info(String.format("ProjectBrowser used by: %s", PortalUtils.getUser().getScreenName()));
      // try to init connection to openbis and write some session attributes, that can be accessed
      // globally
      try {
        initConnection();
        initSessionAttributes();
      } catch (Exception e) {
        // probably the connection to openbis failed
        buildOpenbisConnectionErrorLayout(request);
        // write an error message if failed to load openbis and is in production
        errorMessageIfIsProduction();
      }
      this.resUrl =
          (String) getPortletSession().getAttribute("resURL", PortletSession.APPLICATION_SCOPE);
      mainLayout = initProgressBarAndThreading(request);
    }

    setContent(mainLayout);
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
   * builds page if user is not logged in
   */
  private Layout buildNotLoggedinLayout() {
    // Mail to qbic
    ExternalResource resource = new ExternalResource("mailto:info@qbic.uni-tuebingen.de");
    Link mailToQbicLink = new Link("", resource);
    mailToQbicLink.setIcon(new ThemeResource("mail9.png"));


    ThemeDisplay themedisplay =
        (ThemeDisplay) VaadinService.getCurrentRequest().getAttribute(WebKeys.THEME_DISPLAY);

    // redirect to liferay login page
    Link loginPortalLink = new Link("", new ExternalResource(themedisplay.getURLSignIn()));
    loginPortalLink.setIcon(new ThemeResource("lock12.png"));

    // left part of the page
    VerticalLayout signIn = new VerticalLayout();
    signIn.addComponent(new Label("<h3>Sign in to manage your projects and access your data:</h3>",
        ContentMode.HTML));
    signIn.addComponent(loginPortalLink);
    signIn.setStyleName("no-user-login");
    // right part of the page
    VerticalLayout contact = new VerticalLayout();
    contact.addComponent(new Label(
        "<h3>If you are interested in doing projects get in contact:</h3>", ContentMode.HTML));
    contact.addComponent(mailToQbicLink);
    contact.setStyleName("no-user-login");

    // build final layout, with some gaps between
    HorizontalLayout notSignedInLayout = new HorizontalLayout();
    Label expandingGap1 = new Label();
    expandingGap1.setWidth("100%");
    notSignedInLayout.addComponent(expandingGap1);
    notSignedInLayout.addComponent(signIn);

    notSignedInLayout.addComponent(contact);
    notSignedInLayout.setExpandRatio(expandingGap1, 0.16f);
    notSignedInLayout.setExpandRatio(signIn, 0.36f);

    notSignedInLayout.setExpandRatio(contact, 0.36f);

    notSignedInLayout.setWidth("100%");
    notSignedInLayout.setSpacing(true);

    return notSignedInLayout;
  }

  /**
   * starts the querying of openbis and initializing the view
   *
   * @param request
   */
  protected Layout initProgressBarAndThreading(VaadinRequest request) {
    GridLayout layout = new GridLayout();
    // setContent(layout);

    // TODO so this function uses the same error as above, but doesn't call
    // OpenbisConnectionErrorLayout...we might want to change that
    // final Label status = new Label("Connecting to database.");
    // status.addStyleName(ValoTheme.LABEL_HUGE);
    // status.addStyleName(ValoTheme.LABEL_LIGHT);
    // layout.addComponent(status);
    // layout.setComponentAlignment(status, Alignment.MIDDLE_RIGHT);

    try {
      layout = buildMainLayout(datahandler, request, PortalUtils.getUser().getScreenName());
    } catch (Exception e) {
      if (datahandler.getOpenBisClient().loggedin()) {
        LOG.error("User not known?", e);
        buildUserUnknownError(request);
      } else {
        LOG.error("exception thrown during initialization.", e);
        // status.setValue(
        // "An error occured, while trying to connect to the database. Please try again later, or
        // contact your project manager.");
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

    List<Project> projects = datahandler.getOpenBisClient().getOpenbisInfoService()
        .listProjectsOnBehalfOfUser(datahandler.getOpenBisClient().getSessionToken(), user);
    int numberOfProjects = 0;
    for (Project project : projects) {
      if (project.getSpaceCode().contains("IVAC")) {
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

    /*
     * VerticalLayout versionLayout = new VerticalLayout(); versionLayout.setWidth(100,
     * Unit.PERCENTAGE); Label versionLabel = new Label(String.format("version: %s", version));
     * Label revisionLabel = new Label(String.format("rev: %s", revision));
     * revisionLabel.setWidth(null); versionLabel.setWidth(null);
     * 
     * versionLayout.addComponent(versionLabel); if (!isInProductionMode()) {
     * versionLayout.addComponent(revisionLabel); versionLayout.setComponentAlignment(revisionLabel,
     * Alignment.BOTTOM_RIGHT); }
     * 
     * mainLayout.addComponent(versionLayout, 0, 2, 2, 2); mainLayout.setRowExpandRatio(2, 1.0f);
     * 
     * versionLayout.setComponentAlignment(versionLabel, Alignment.MIDDLE_RIGHT);
     */
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
    DBConfig mysqlConfig = new DBConfig(manager.getMsqlHost(), manager.getMysqlPort(),
        manager.getMysqlDB(), manager.getMysqlUser(), manager.getMysqlPass());
    DBManager databaseManager = new DBManager(mysqlConfig);

    this.datahandler = new DataHandler(openBisConnection, databaseManager);
  }
}
