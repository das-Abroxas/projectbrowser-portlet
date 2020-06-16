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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.search.ExperimentSearchCriteria;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.project.Project;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.SampleType;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.search.SampleSearchCriteria;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.validator.NullValidator;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.NativeSelect;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.portal.utils.PortalUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import life.qbic.projectbrowser.controllers.*;

/**
 *
 */
public class SearchEngineView extends CustomComponent {

  private static final long serialVersionUID = 5371970241077786446L;
  private static final Logger LOG = LogManager.getLogger(SearchEngineView.class);
  private Panel mainlayout;
  private DataHandler datahandler;
  private final String infotext = "no info text";

  Map<String, Map<String, String>> samplePropertiesMapping;
  Set<String> visibleSpaces;

  public SearchEngineView(DataHandler datahandler) {
    this.datahandler = datahandler;
    this.visibleSpaces = listSpacesOnBehalfOfUser();
    initSearchEngine();
    initUI();
  }

  private void initSearchEngine() {
    // Retrieve all relevant sample type search fields (properties/attributes)
    Map<String, SampleType> sampleTypeMap = datahandler.getOpenBisClient().getSampleTypes();

    for (String key : sampleTypeMap.keySet()) {
      // LOG.info(key);
      List<PropertyType> propertyTypes =
              datahandler.getOpenBisClient().listPropertiesForType(sampleTypeMap.get(key));
      Map<String, String> propertyLabelMap =
              propertyTypes.stream().collect(Collectors.toMap(PropertyType::getCode, PropertyType::getLabel));

      // Map<String, String> props = datahandler.getOpenBisClient().getLabelsOfProperties(sampleTypeMap.get(key));
      // LOG.info(props.toString());
      // samplePropertiesMapping.put(key, props);
    }
  }

  public void initUI() {

    mainlayout = new Panel();
    mainlayout.addStyleName(ValoTheme.PANEL_BORDERLESS);

    // Search bar
    // *----------- search text field .... search button-----------*
    VerticalLayout searchbar = new VerticalLayout();
    searchbar.setWidth(100, Unit.PERCENTAGE);
    setResponsive(true);
    searchbar.setResponsive(true);
    // searchbar.setWidth();

    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setResponsive(true);
    buttonLayout.setWidth(75, Unit.PERCENTAGE);

    // searchbar.setSpacing(true);
    final TextField searchfield = new TextField();
    searchfield.setHeight("44px");
    searchfield.setImmediate(true);
    searchfield.setResponsive(true);
    searchfield.setWidth(75, Unit.PERCENTAGE);

    buttonLayout.setSpacing(true);


    searchfield.setInputPrompt("search DB");
    // searchfield.setCaption("QSearch");
    // searchfield.setWidth(25.0f, Unit.EM);
    // searchfield.setWidth(60, Unit.PERCENTAGE);

    // TODO would be nice to have a autofill or something similar
    // searchFieldLayout.addComponent(searchfield);
    searchbar.addComponent(searchfield);
    searchbar.setComponentAlignment(searchfield, Alignment.MIDDLE_RIGHT);


    final NativeSelect navsel = new NativeSelect();
    navsel.addItem("Whole DB");
    navsel.addItem("Projects Only");
    navsel.addItem("Experiments Only");
    navsel.addItem("Samples Only");
    navsel.setValue("Whole DB");
    navsel.setHeight("30px");
    navsel.setNullSelectionAllowed(false);
    navsel.setResponsive(true);
    navsel.setWidth(100, Unit.PERCENTAGE);

    navsel.addValueChangeListener(new Property.ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -6896454887050432147L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        // TODO Auto-generated method stub
        Notification.show((String) navsel.getValue());

        switch ((String) navsel.getValue()) {
          case "Whole DB":
            datahandler.setShowOptions(Arrays.asList("Projects", "Experiments", "Samples"));
            break;
          case "Projects Only":
            datahandler.setShowOptions(Arrays.asList("Projects"));
            break;
          case "Experiments Only":
            datahandler.setShowOptions(Arrays.asList("Experiments"));
            break;
          case "Samples Only":
            datahandler.setShowOptions(Arrays.asList("Samples"));
            break;
          default:
            datahandler.setShowOptions(Arrays.asList("Projects", "Experiments", "Samples"));
            break;
        }


      }
    });

    searchbar.addComponent(buttonLayout);
    searchbar.setComponentAlignment(buttonLayout, Alignment.MIDDLE_RIGHT);
    Button searchOk = new Button("");
    searchOk.setStyleName(ValoTheme.BUTTON_TINY);
    // searchOk.addStyleName(ValoTheme.BUTTON_BORDERLESS);
    searchOk.setIcon(FontAwesome.SEARCH);
    searchOk.setSizeUndefined();
    // searchOk.setWidth(15.0f, Unit.EM);
    searchOk.setResponsive(true);
    searchOk.setHeight("30px");


    searchOk.addClickListener(new ClickListener() {
      private static final long serialVersionUID = -2409450448301908214L;


      @Override
      public void buttonClick(ClickEvent event) {
        String queryString = (String) searchfield.getValue().toString();

        LOG.debug("the query was " + queryString);

        if (searchfield.getValue() == null || searchfield.getValue().toString().equals("")
            || searchfield.getValue().toString().trim().length() == 0) {
          Notification.show("Query field was empty!", Type.WARNING_MESSAGE);
        } else {

          try {

            switch ((String) navsel.getValue()) {
              case "Whole DB":
                datahandler.setSampleResults(querySamples(queryString));
                datahandler.setExpResults(queryExperiments(queryString));
                datahandler.setProjResults(queryProjects(queryString));
                break;
              case "Projects Only":
                datahandler.setProjResults(queryProjects(queryString));
                break;
              case "Experiments Only":
                datahandler.setExpResults(queryExperiments(queryString));
                break;
              case "Samples Only":
                datahandler.setSampleResults(querySamples(queryString));
                break;
              default:
                datahandler.setSampleResults(querySamples(queryString));
                datahandler.setExpResults(queryExperiments(queryString));
                datahandler.setProjResults(queryProjects(queryString));
                break;
            }

            datahandler.setLastQueryString(queryString);

            State state = (State) UI.getCurrent().getSession().getAttribute("state");
            ArrayList<String> message = new ArrayList<>();
            message.add("clicked");
            message.add("view" + queryString + navsel.getValue().toString().replace(" ", ""));
            message.add("searchresults");
            state.notifyObservers(message);

          } catch (Exception e) {
            LOG.error("after query: ", e);
            Notification.show("No entities found for given term.", Type.WARNING_MESSAGE);
          }
        }


      }

    });

    // setClickShortcut() would add global shortcut, instead we
    // 'scope' the shortcut to the panel:
    mainlayout.addAction(new Button.ClickShortcut(searchOk, KeyCode.ENTER));
    // searchfield.addItems(this.getSearchResults("Q"));
    searchfield.setDescription(infotext);
    searchfield.addValidator(new NullValidator("Field must not be empty", false));
    searchfield.setValidationVisible(false);

    buttonLayout.addComponent(navsel);
    // buttonLayout.addComponent(new Label(""));
    buttonLayout.addComponent(searchOk);

    // searchFieldLayout.setComponentAlignment(searchOk, Alignment.TOP_RIGHT);
    // buttonLayout.setExpandRatio(searchOk, 1);
    // buttonLayout.setExpandRatio(navsel, 1);

    // searchFieldLayout.setSpacing(true);
    buttonLayout.setComponentAlignment(searchOk, Alignment.BOTTOM_RIGHT);
    // buttonLayout.setComponentAlignment(navsel, Alignment.BOTTOM_LEFT);

    buttonLayout.setExpandRatio(searchOk, 1);
    buttonLayout.setExpandRatio(navsel, 2);


    // searchbar.setMargin(new MarginInfo(true, false, true, false));
    mainlayout.setContent(searchbar);
    // mainlayout.setComponentAlignment(searchbar, Alignment.MIDDLE_RIGHT);
    // mainlayout.setWidth(100, Unit.PERCENTAGE);
    setCompositionRoot(mainlayout);
  }

  public List<Sample> querySamples(String queryString) {
    SampleSearchCriteria ssc = new SampleSearchCriteria();
    ssc.withAnyField().thatContains(queryString);

    return datahandler.getOpenBisClient().getSamplesForUser(ssc, PortalUtils.getNonNullScreenName());
  }

  public List<Experiment> queryExperiments(String queryString) {
    ExperimentSearchCriteria esc = new ExperimentSearchCriteria();
    esc.withAnyField().thatContains(queryString);

    return datahandler.getOpenBisClient().getExperimentsForUser(esc, PortalUtils.getNonNullScreenName());
  }

  public List<Project> queryProjects(String queryString) {
    List<Project> result = new ArrayList<>();

    List<Project> projects =
            datahandler.getOpenBisClient().listProjectsForUser(PortalUtils.getNonNullScreenName());

    for (Project p : projects) {
      String projectDesc = p.getDescription();

      // sometimes there is no description available (check for null entry)
      if (projectDesc != null && projectDesc.contains(queryString.toLowerCase())) {
        result.add(p);
      } else if (p.getCode().contains(queryString)) {
        result.add(p);
      }
    }
    return result;
  }

  public Set<String> listSpacesOnBehalfOfUser() {
    return new HashSet<>(datahandler.getOpenBisClient().listSpacesForUser(PortalUtils.getNonNullScreenName()));
  }
}
