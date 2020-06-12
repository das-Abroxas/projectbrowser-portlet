package life.qbic.projectbrowser.samplegraph;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Table;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ValoTheme;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;

import life.qbic.portal.portlet.ProjectBrowserPortlet;
import life.qbic.portal.utils.PortalUtils;
import life.qbic.xml.properties.Property;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GraphPage extends VerticalLayout {

  private static final Logger LOG = LogManager.getLogger(GraphPage.class);

  private List<Sample> currentSamples;
  private ProjectParser parser;
  private StructuredExperiment structure;
  private ComboBox factorBox;

  private ProjectGraph sampleGraph;

  public GraphPage(Map<String, String> taxMap, Map<String, String> tissueMap) {
    setSpacing(true);
    setMargin(true);

    Map<String, String> reverseTaxMap = taxMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    Map<String, String> reverseTissueMap = tissueMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    //parser = new ProjectParser(reverseTaxMap, reverseTissueMap);
    parser = new ProjectParser(taxMap, tissueMap);
  }

  private String buildImagePath() {
    StringBuilder pathBuilder = new StringBuilder();

    if (PortalUtils.isLiferayPortlet()) {
      Properties prop = new Properties();
      InputStream in = this.getClass().getClassLoader()
          .getResourceAsStream("WEB-INF/liferay-plugin-package.properties");
      try {
        prop.load(in);
        in.close();
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      String portletName = prop.getProperty("name");

      URI location = UI.getCurrent().getPage().getLocation();
      // http
      pathBuilder.append(location.getScheme());
      pathBuilder.append("://");
      // host+port
      pathBuilder.append(location.getAuthority());

      String port = (Integer.toString(location.getPort()));
      if (location.toString().contains(port)) {
        pathBuilder.append(":");
        pathBuilder.append(port);
      }
      pathBuilder.append("/");
      pathBuilder.append(portletName);
    }
    pathBuilder.append("/VAADIN/img/");
    return pathBuilder.toString();
  }

  public void loadProjectGraph(String projectIdentifier, List<Sample> samples,
                               List<DataSet> datasets, Set<String> factorLabels,
                               Map<Pair<String, String>, Property> factorsForLabelsAndSamples) {

    this.factorBox = new ComboBox("Experimental Factor");
    factorBox.setVisible(false);
    addComponent(factorBox);

    currentSamples = samples;
    if (currentSamples.isEmpty()) {
      LOG.info("No samples to show found in this project.");

    } else {
      try {
        // Load here
        structure = parser.parseSamplesBreadthFirst(currentSamples, datasets, factorLabels, factorsForLabelsAndSamples);

        if (!structure.getFactorsToSamples().isEmpty()) {
          factorBox.addItems(structure.getFactorsToSamples().keySet());
          factorBox.setVisible(true);
        }
      } catch (JAXBException e) {
        e.printStackTrace();
        LOG.error("JAXB Parsing error!");
      }
    }

    factorBox.setImmediate(true);
    factorBox.setNullSelectionAllowed(false);
    final GraphPage parent = this;
    factorBox.addValueChangeListener(new ValueChangeListener() {

      @Override
      public void valueChange(ValueChangeEvent event) {
        Object factor = factorBox.getValue();
        if (sampleGraph != null) {
          parent.removeComponent(sampleGraph);
        }
        sampleGraph = new ProjectGraph(parent, buildImagePath());
        sampleGraph.setSizeFull();
        parent.addComponent(sampleGraph);
        if (factor != null) {
          sampleGraph.setProject(structure.getFactorsToSamples().get(factor));
        }
      }
    });
  }

  public void showDatasetsForSamples(String label, List<String> sampleCodes) {
    Window subWindow = new Window(" " + label + " Dataset information");
    subWindow.setWidth("680px");

    VerticalLayout layout = new VerticalLayout();
    layout.setSpacing(true);
    layout.setMargin(true);
    List<Sample> samplesWithData = new ArrayList<Sample>();
    List<Sample> samplesWithoutData = new ArrayList<Sample>();
    for (String code : sampleCodes) {
      Sample s = parser.getSampleFromCode(code);
      if (parser.codeHasDatasets(code)) {
        samplesWithData.add(s);
      } else {
        samplesWithoutData.add(s);
      }
    }

    Table haveData = new Table("Samples/Entities with Data");
    haveData.setStyleName(ValoTheme.TABLE_SMALL);
    haveData.addContainerProperty("Sample", String.class, null);
    haveData.addContainerProperty("Secondary Name", String.class, null);
    haveData.addContainerProperty("Lab ID", String.class, null);
    for (Sample s : samplesWithData) {
      List<Object> row = new ArrayList<Object>();
      row.add(s.getCode());
      Map<String, String> props = s.getProperties();
      String secName = "";
      if (props.get("Q_SECONDARY_NAME") != null) {
        secName = props.get("Q_SECONDARY_NAME");
      }
      row.add(secName);

      String extID = "";
      if (props.get("Q_EXTERNALDB_ID") != null) {
        extID = props.get("Q_EXTERNALDB_ID");
      }
      row.add(extID);

      haveData.addItem(row.toArray(new Object[row.size()]), s);
    }

    Table noData = new Table("Samples/Entities without Data");
    noData.setStyleName(ValoTheme.TABLE_SMALL);
    noData.addContainerProperty("Sample", String.class, null);
    noData.addContainerProperty("Secondary Name", String.class, null);
    noData.addContainerProperty("Lab ID", String.class, null);
    for (Sample s : samplesWithoutData) {
      List<Object> row = new ArrayList<Object>();
      row.add(s.getCode());
      Map<String, String> props = s.getProperties();
      String secName = "";
      if (props.get("Q_SECONDARY_NAME") != null) {
        secName = props.get("Q_SECONDARY_NAME");
      }
      row.add(secName);

      String extID = "";
      if (props.get("Q_EXTERNALDB_ID") != null) {
        extID = props.get("Q_EXTERNALDB_ID");
      }
      row.add(extID);

      noData.addItem(row.toArray(new Object[row.size()]), s);
    }
    haveData.setPageLength(samplesWithData.size());
    noData.setPageLength(samplesWithoutData.size());

    layout.addComponent(haveData);
    layout.addComponent(noData);
    Button ok = new Button("Close");
    ok.addClickListener(new ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        subWindow.close();
      }
    });

    layout.addComponent(ok);

    subWindow.setContent(layout);
    // Center it in the browser window
    subWindow.center();
    subWindow.setModal(true);
    subWindow.setIcon(FontAwesome.FILE);
    subWindow.setResizable(false);
    ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
    ui.addWindow(subWindow);
  }
}
