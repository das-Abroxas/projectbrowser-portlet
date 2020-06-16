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

import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.vocabulary.Vocabulary;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.vocabulary.VocabularyTerm;

import com.vaadin.data.Item;
import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.data.util.GeneratedPropertyContainer;
import com.vaadin.data.util.PropertyValueGenerator;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.event.SelectionEvent;
import com.vaadin.event.SelectionEvent.SelectionListener;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Window.CloseEvent;
import com.vaadin.ui.Window.CloseListener;
import com.vaadin.ui.renderers.ButtonRenderer;
import com.vaadin.ui.renderers.ClickableRenderer.RendererClickListener;
import com.vaadin.ui.renderers.HtmlRenderer;

import life.qbic.portal.portlet.ProjectBrowserPortlet;
import life.qbic.projectbrowser.controllers.DataHandler;
import life.qbic.projectbrowser.controllers.State;
import life.qbic.projectbrowser.helpers.GridFunctions;
import life.qbic.projectbrowser.helpers.Utils;
import life.qbic.projectbrowser.model.BiologicalEntitySampleBean;
import life.qbic.projectbrowser.model.BiologicalSampleBean;
import life.qbic.xml.manager.StudyXMLParser;
import life.qbic.xml.properties.Property;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;


public class BiologicalSamplesComponent extends CustomComponent {

  private enum sampleTypes {
    Q_BIOLOGICAL_ENTITY, Q_BIOLOGICAL_SAMPLE
  };

  private enum propertyTypes {
    Q_ADDIIONAL_INFO, Q_EXTERNALDB_ID, Q_SECONDARY_NAME, Q_NCBI_ORGANISM
  };

  private static final long serialVersionUID = 8672873911284888801L;
  private static final Logger LOG = LogManager.getLogger(BiologicalSamplesComponent.class);

  private BeanItemContainer<BiologicalSampleBean> samplesBio;
  private BeanItemContainer<BiologicalEntitySampleBean> samplesEntity;

  private VerticalLayout mainLayout, vert;
  private Grid sampleBioGrid, sampleEntityGrid;
  private Button exportSources = new Button("Export as TSV");
  private Button exportSamples = new Button("Export as TSV");
  private FileDownloader fileDownloaderSources;
  private FileDownloader fileDownloaderSamples;

  private ChangeSampleMetadataComponent changeMetadata;
  private DataHandler datahandler;
  private State state;
  private String resourceUrl, currentID;

  private int numberOfBioSamples;
  private int numberOfEntitySamples;

  /**
   * 
   * @param dh
   * @param state
   * @param resourceurl
   * @param caption
   */
  public BiologicalSamplesComponent(DataHandler dh, State state,
                                    String resourceurl, String caption) {
    this.datahandler = dh;
    this.resourceUrl = resourceurl;
    this.state = state;
    changeMetadata = new ChangeSampleMetadataComponent(dh, state, resourceurl);

    this.setCaption(caption);
    this.initUI();
  }

  /**
   * 
   */
  private void initUI() {
    vert = new VerticalLayout();
    vert.setMargin(new MarginInfo(false, true, false, false));

    sampleBioGrid = new Grid();
    sampleEntityGrid = new Grid();
    sampleEntityGrid.addSelectionListener(new SelectionListener() {

      @Override
      public void select(SelectionEvent event) {
        BeanItem<BiologicalEntitySampleBean> selectedBean =
            samplesEntity.getItem(sampleEntityGrid.getSelectedRow());

        if (selectedBean == null) {
          TextField filterField = (TextField) sampleBioGrid
                  .getHeaderRow(1).getCell("biologicalEntity").getComponent();
          filterField.setValue("");

        } else {
          TextField filterField = (TextField) sampleBioGrid
                  .getHeaderRow(1).getCell("biologicalEntity").getComponent();
          filterField.setValue(selectedBean.getBean().getCode());
        }
      }

    });

    mainLayout = new VerticalLayout(vert);
    mainLayout.setResponsive(true);
    setResponsive(true);

    exportSources.setIcon(FontAwesome.DOWNLOAD);
    exportSamples.setIcon(FontAwesome.DOWNLOAD);

    this.setCompositionRoot(mainLayout);
  }

  /**
   * 
   * @param id
   */
  public void updateUI(String id) {
    currentID = id;
    sampleBioGrid = new Grid();
    sampleEntityGrid = new Grid();

    sampleEntityGrid.addSelectionListener((SelectionListener) event -> {
      BeanItem<BiologicalEntitySampleBean> selectedBean =
          samplesEntity.getItem(sampleEntityGrid.getSelectedRow());

      if (selectedBean == null) {
        TextField filterField = (TextField) sampleBioGrid
                .getHeaderRow(1).getCell("biologicalEntity").getComponent();
        filterField.setValue("");

      } else {
        TextField filterField = (TextField) sampleBioGrid
                .getHeaderRow(1).getCell("biologicalEntity").getComponent();
        filterField.setValue(selectedBean.getBean().getCode());
      }
    });

    if (id == null)
      return;

    BeanItemContainer<BiologicalSampleBean> samplesBioContainer =
        new BeanItemContainer<>(BiologicalSampleBean.class);
    BeanItemContainer<BiologicalEntitySampleBean> samplesEntityContainer =
        new BeanItemContainer<>(BiologicalEntitySampleBean.class);

    List<Sample> allSamples = datahandler.getOpenBisClient().getSamplesOfProject(id);

    List<VocabularyTerm> terms   = null;
    Map<String, String> termsMap = new HashMap<>();
    StudyXMLParser xmlParser     = new StudyXMLParser();

    for (Sample sample : allSamples) {
      if (sample.getType().getCode().equals(sampleTypes.Q_BIOLOGICAL_ENTITY.toString())) {

        Map<String, String> sampleProperties = sample.getProperties();
        BiologicalEntitySampleBean newEntityBean = new BiologicalEntitySampleBean();
        newEntityBean.setCode(sample.getCode());
        newEntityBean.setId(sample.getIdentifier().toString());
        newEntityBean.setType(sample.getType().getCode());
        newEntityBean.setAdditionalInfo(sampleProperties.get("Q_ADDITIONAL_INFO"));
        newEntityBean.setExternalDB(sampleProperties.get("Q_EXTERNALDB_ID"));
        newEntityBean.setSecondaryName(sampleProperties.get("Q_SECONDARY_NAME"));

        String organismID = sampleProperties.get("Q_NCBI_ORGANISM");
        newEntityBean.setOrganism(organismID);

        if (terms != null) {
          if (termsMap.containsKey(organismID)) {
            newEntityBean.setOrganismName(termsMap.get(organismID));
          } else {
            for (VocabularyTerm term : terms) {
              if (term.getCode().equals(organismID)) {
                newEntityBean.setOrganismName(term.getLabel());
                break;
              }
            }
          }
        } else {

          Vocabulary vocab = datahandler.getOpenBisClient().getVocabulary("Q_NCBI_TAXONOMY");
          for (VocabularyTerm term : vocab.getTerms()) {
            if (term.getCode().equals(organismID)) {
              newEntityBean.setOrganismName(term.getLabel());
              termsMap.put(organismID, term.getLabel());
              break;
            }
          }
        }

        // data for complex xml properties
        Map<String, List<Property>> propertiesForSamples = datahandler.getPropertiesForSamples();
        Set<String> factorLabels = datahandler.getFactorLabels();
        Map<Pair<String, String>, Property> factorsForSamples =
                datahandler.getFactorsForLabelsAndSamples();

        List<Property> complexProps =
                xmlParser.getFactorsAndPropertiesForSampleCode(datahandler.getExperimentalSetup(), sample.getCode());
        newEntityBean.setProperties(sampleProperties, complexProps);

        newEntityBean.setGender(sampleProperties.get("Q_GENDER"));
        samplesEntityContainer.addBean(newEntityBean);

        for (Sample realChild : sample.getChildren()) {
          realChild = datahandler.getOpenBisClient().getSample(realChild.getIdentifier().toString());
          if (realChild.getType().getCode().equals(sampleTypes.Q_BIOLOGICAL_SAMPLE.toString())) {
            // Sample realChild =
            // datahandler.getOpenBisClient().getSampleByIdentifier(child.getIdentifier());

            Map<String, String> sampleBioProperties = realChild.getProperties();

            BiologicalSampleBean newBean = new BiologicalSampleBean();
            newBean.setCode(realChild.getCode());
            newBean.setId(realChild.getIdentifier().toString());
            newBean.setType(realChild.getType().getCode());
            newBean.setPrimaryTissue(sampleBioProperties.get("Q_PRIMARY_TISSUE"));
            newBean.setTissueDetailed(sampleBioProperties.get("Q_TISSUE_DETAILED"));
            newBean.setBiologicalEntity(sample.getCode());

            newBean.setAdditionalInfo(sampleBioProperties.get("Q_ADDITIONAL_INFO"));
            newBean.setExternalDB(sampleBioProperties.get("Q_EXTERNALDB_ID"));
            newBean.setSecondaryName(sampleBioProperties.get("Q_SECONDARY_NAME"));

            complexProps = xmlParser.getFactorsAndPropertiesForSampleCode(datahandler.getExperimentalSetup(), realChild.getCode());

            newBean.setProperties(sampleBioProperties, complexProps);

            samplesBioContainer.addBean(newBean);
          }
        }
      }
    }

    numberOfBioSamples = samplesBioContainer.size();
    numberOfEntitySamples = samplesEntityContainer.size();

    samplesBio = samplesBioContainer;
    samplesEntity = samplesEntityContainer;

    sampleEntityGrid.removeAllColumns();

    final GeneratedPropertyContainer gpcEntity = new GeneratedPropertyContainer(samplesEntity);
    gpcEntity.removeContainerProperty("id");
    gpcEntity.removeContainerProperty("type");
    gpcEntity.removeContainerProperty("organismName");
    gpcEntity.removeContainerProperty("organism");

    sampleEntityGrid.setContainerDataSource(gpcEntity);
    sampleEntityGrid.setColumnReorderingAllowed(true);

    gpcEntity.addGeneratedProperty("Organism", new PropertyValueGenerator<String>() {

      @Override
      public Class<String> getType() {
        return String.class;
      }

      @Override
      public String getValue(Item item, Object itemId, Object propertyId) {
        String ncbi = String.format(
            "http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?mode=Undef&name=%s&lvl=0&srchmode=1&keep=1&unlock' target='_blank'>%s</a>",
            item.getItemProperty("organism").getValue(),
            item.getItemProperty("organismName").getValue());
        String link = String.format("<a href='%s", ncbi);

        return link;
      }
    });

    sampleEntityGrid.getColumn("Organism").setRenderer(new HtmlRenderer());

    final GeneratedPropertyContainer gpcBio = new GeneratedPropertyContainer(samplesBio);
    gpcBio.removeContainerProperty("id");
    gpcBio.removeContainerProperty("type");

    sampleBioGrid.setContainerDataSource(gpcBio);
    sampleBioGrid.setColumnReorderingAllowed(true);
    sampleBioGrid.setColumnOrder("secondaryName", "code");

    gpcEntity.addGeneratedProperty("edit", new PropertyValueGenerator<String>() {
      @Override
      public String getValue(Item item, Object itemId, Object propertyId) {
        return "Edit";
      }

      @Override
      public Class<String> getType() {
        return String.class;
      }
    });

    gpcBio.addGeneratedProperty("edit", new PropertyValueGenerator<String>() {
      @Override
      public String getValue(Item item, Object itemId, Object propertyId) {
        return "Edit";
      }

      @Override
      public Class<String> getType() {
        return String.class;
      }
    });

    sampleEntityGrid.addItemClickListener(new ItemClickListener() {

      @Override
      public void itemClick(ItemClickEvent event) {

        BeanItem selected = samplesEntity.getItem(event.getItemId());
        BiologicalEntitySampleBean selectedExp = (BiologicalEntitySampleBean) selected.getBean();

        State state = (State) UI.getCurrent().getSession().getAttribute("state");
        ArrayList<String> message = new ArrayList<>();
        message.add("clicked");
        message.add(selectedExp.getId());
        message.add("sample");
        state.notifyObservers(message);
      }
    });

    sampleEntityGrid.getColumn("edit").setRenderer(new ButtonRenderer((RendererClickListener) event -> {
      BeanItem selected = samplesEntity.getItem(event.getItemId());
      BiologicalEntitySampleBean selectedSample = (BiologicalEntitySampleBean) selected.getBean();

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

      subWindow.addCloseListener(new CloseListener() {
        /**
         *
         */
        private static final long serialVersionUID = -1329152609834711109L;

        @Override
        public void windowClose(CloseEvent e) {
          updateUI(currentID);
        }
      });

      ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
      ui.addWindow(subWindow);
    }));
    sampleEntityGrid.getColumn("edit").setWidth(70);
    sampleEntityGrid.getColumn("edit").setHeaderCaption("");
    sampleEntityGrid.setColumnOrder("edit", "secondaryName", "Organism", "properties", "code",
            "additionalInfo", "gender", "externalDB");

    sampleBioGrid.addItemClickListener(new ItemClickListener() {

      @Override
      public void itemClick(ItemClickEvent event) {

        BeanItem selected = samplesBio.getItem(event.getItemId());
        BiologicalSampleBean selectedExp = (BiologicalSampleBean) selected.getBean();

        State state = (State) UI.getCurrent().getSession().getAttribute("state");
        ArrayList<String> message = new ArrayList<>();
        message.add("clicked");
        message.add(selectedExp.getId());
        message.add("sample");
        state.notifyObservers(message);
      }
    });

    sampleBioGrid.getColumn("edit").setRenderer(new ButtonRenderer((RendererClickListener) event -> {
      BeanItem selected = samplesBio.getItem(event.getItemId());

      try {
        BiologicalSampleBean selectedSample = (BiologicalSampleBean) selected.getBean();

        Window subWindow = new Window();

        changeMetadata.updateUI(selectedSample.getId(), selectedSample.getType());
        VerticalLayout subContent = new VerticalLayout();
        subContent.setMargin(true);
        subContent.addComponent(changeMetadata);
        subWindow.setContent(subContent);
        // Center it in the browser window
        subWindow.center();
        subWindow.setModal(true);
        subWindow.setResizable(false);

        subWindow.addCloseListener(new CloseListener() {
          /**
          *
          */
          private static final long serialVersionUID = -1329152609834711109L;

          @Override
          public void windowClose(CloseEvent e) {
            updateUI(currentID);
          }
        });

        ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
        ui.addWindow(subWindow);
      } catch (NullPointerException e) {
        System.err
            .println("NullPointerException while trying to set metadata: " + e.getMessage());

      }
    }));

    sampleBioGrid.getColumn("edit").setWidth(70);
    sampleBioGrid.getColumn("edit").setHeaderCaption("");
    sampleBioGrid.setColumnOrder("edit", "secondaryName", "primaryTissue", "properties",
        "tissueDetailed", "code", "additionalInfo", "biologicalEntity", "externalDB");

    sampleBioGrid.getColumn("biologicalEntity").setHeaderCaption("Source");

    GridFunctions.addColumnFilters(sampleBioGrid, gpcBio);
    GridFunctions.addColumnFilters(sampleEntityGrid, gpcEntity);


    if (fileDownloaderSources != null)
      exportSources.removeExtension(fileDownloaderSources);
    StreamResource srSource = Utils.getTSVStream(Utils.containerToString(samplesEntityContainer),
        String.format("%s_%s_", id.substring(1).replace("/", "_"), "sample_sources"));
    fileDownloaderSources = new FileDownloader(srSource);
    fileDownloaderSources.extend(exportSources);


    if (fileDownloaderSamples != null)
      exportSamples.removeExtension(fileDownloaderSamples);
    StreamResource srSamples = Utils.getTSVStream(Utils.containerToString(samplesBioContainer),
        String.format("%s_%s_", id.substring(1).replace("/", "_"), "extracted_samples"));
    fileDownloaderSamples = new FileDownloader(srSamples);
    fileDownloaderSamples.extend(exportSamples);

    this.buildLayout();
  }

  /**
   * Precondition: {DatasetView#table} has to be initialized. e.g. with
   * {DatasetView#buildFilterTable} If it is not, strange behaviour has to be expected. builds the
   * Layout of this view.
   */
  private void buildLayout() {
    this.vert.removeAllComponents();
    this.vert.setSizeFull();
    vert.setResponsive(true);

    // Table (containing datasets) section
    VerticalLayout tableSection = new VerticalLayout();
    HorizontalLayout tableSectionContent = new HorizontalLayout();
    HorizontalLayout sampletableSectionContent = new HorizontalLayout();
    tableSection.setResponsive(true);
    tableSectionContent.setResponsive(true);
    sampletableSectionContent.setResponsive(true);

    tableSectionContent.setMargin(new MarginInfo(true, false, false, false));
    sampletableSectionContent.setMargin(new MarginInfo(true, false, false, false));

    tableSection.addComponent(new Label(String.format(
        "This view shows the sample sources (e.g., human, mouse) to be studied and the corresponding extracted samples. With sample sources, information specific to the subject (e.g., age or BMI in the case of patient data) can be stored. The extracted sample is a sample which has been extracted from the corresponding sample source. This is the raw sample material that can be later prepared for specific analytical methods such as MS or NGS.<br> "
            + "\n\n There are %s extracted  samples coming from %s distinct sample sources in this study.",
        numberOfBioSamples, numberOfEntitySamples), ContentMode.HTML));

    tableSectionContent.addComponent(sampleBioGrid);
    sampletableSectionContent.addComponent(sampleEntityGrid);

    sampleEntityGrid.setCaption("Sample Sources");
    sampleBioGrid.setCaption("Extracted Samples");

    tableSection.setMargin(new MarginInfo(true, false, true, true));
    tableSection.setSpacing(true);

    tableSection.addComponent(sampletableSectionContent);
    tableSection.addComponent(exportSources);
    tableSection.addComponent(tableSectionContent);
    tableSection.addComponent(exportSamples);
    this.vert.addComponent(tableSection);

    sampleBioGrid.setWidth(100, Unit.PERCENTAGE);
    sampleEntityGrid.setWidth(100, Unit.PERCENTAGE);

    tableSection.setSizeFull();
    sampletableSectionContent.setSizeFull();
    tableSectionContent.setSizeFull();
  }
}
