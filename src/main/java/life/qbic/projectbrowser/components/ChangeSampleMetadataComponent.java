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

import ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.vocabulary.Vocabulary;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.vocabulary.VocabularyTerm;
import com.vaadin.data.fieldgroup.FieldGroup;
import com.vaadin.server.Page;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.themes.ValoTheme;
import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.controllers.DataHandler;
import life.qbic.projectbrowser.controllers.State;
import life.qbic.projectbrowser.helpers.Utils;
import life.qbic.projectbrowser.model.PropertyBean;
import life.qbic.xml.manager.StudyXMLParser;
import life.qbic.xml.properties.Property;
import life.qbic.xml.study.Qexperiment;
import life.qbic.xml.study.Qproperty;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.util.*;

public class ChangeSampleMetadataComponent extends CustomComponent {
  /**
   * TODO generic component for experiments and samples
   */
  private static final long serialVersionUID = -5318223225284123020L;
  private final Logger logger = LogManager.getLogger(ChangeSampleMetadataComponent.class);

  private DataHandler datahandler;
  private String resourceUrl;
  private State state;
  private VerticalLayout propLayout;

  private FormLayout form;
  private FieldGroup fieldGroup;
  private VerticalLayout vert;
  private String id;
  private List<PropertyType> completeProperties;
  private Map<String, String> assignedProperties;
  private StudyXMLParser parser = new StudyXMLParser();

  private Sample sample;

  public ChangeSampleMetadataComponent(DataHandler dh, State state, String resourceurl) {
    this.datahandler = dh;
    this.resourceUrl = resourceurl;
    this.state = state;

    // this.setCaption("Metadata");

    this.initUI();
  }

  private void initUI() {
    propLayout = new VerticalLayout();
    propLayout.setWidth(100.0f, Unit.PERCENTAGE);
    propLayout.setMargin(new MarginInfo(true, false, true, true));
    propLayout.setSpacing(true);

    this.setWidth(Page.getCurrent().getBrowserWindowWidth() * 0.8f, Unit.PIXELS);
    this.setCompositionRoot(propLayout);
  }

  public void updateUI(final String id, String type) {
    propLayout.removeAllComponents();
    Button saveButton = new Button("Submit Changes");
    saveButton.setStyleName(ValoTheme.BUTTON_FRIENDLY);

    completeProperties = datahandler.getOpenBisClient()
        .listPropertiesForType(datahandler.getOpenBisClient().getSampleTypeByString(type));

    sample = datahandler.getOpenBisClient().getSampleByIdentifier(id);
    assignedProperties = sample.getProperties();

    saveButton.addClickListener(new ClickListener() {
      @Override
      public void buttonClick(final ClickEvent event) {
        HashMap<String, Object> props = new HashMap<String, Object>();
        Collection<Field<?>> registeredFields = fieldGroup.getFields();
        // XMLParser xmlParser = new XMLParser();

        List<Property> factors = new ArrayList<Property>();

        boolean qpropertiesDefined = false;

        for (Field<?> field : registeredFields) {
          if (field.getDescription().equals("Q_PROPERTIES")) {
            TextField tf = (TextField) field;
            qpropertiesDefined = true;
            String label = tf.getCaption();
            String val = (String) tf.getValue();
            String[] splt = label.split(" in ");
            Property f = null;
            life.qbic.xml.properties.PropertyType type =
                (life.qbic.xml.properties.PropertyType) tf.getData();
            if (splt.length > 1) {
              label = splt[0];
              life.qbic.xml.properties.Unit unit = life.qbic.xml.properties.Unit.valueOf(splt[1]);
              f = new Property(label, val, unit, type);
            } else {
              f = new Property(label, val, type);
            }
            factors.add(f);
          }

          else {
            props.put(field.getDescription(), field.getValue());
          }
        }

        String user = PortalUtils.getNonNullScreenName();

        if (qpropertiesDefined) {
          String qProperties = "";


          JAXBElement<Qexperiment> setup = datahandler.getExperimentalSetup();
          Map<String, Map<Pair<String, String>, List<String>>> factorStructure = new HashMap<>();
          Map<String, List<Qproperty>> otherProps = new HashMap<>();
          String code = sample.getCode();
          for (Property p : factors) {
            String lab = p.getLabel();
            String val = p.getValue();
            String unit = null;
            if (p.hasUnit())
              unit = p.getUnit().getValue();
            if (p.getType().equals(life.qbic.xml.properties.PropertyType.Factor)) {

              Pair<String, String> valunit = new ImmutablePair<String, String>(val, unit);
              if (factorStructure.containsKey(lab)) {
                Map<Pair<String, String>, List<String>> levels = factorStructure.get(lab);
                if (levels.containsKey(valunit)) {
                  levels.get(valunit).add(code);
                } else {
                  levels.put(valunit, new ArrayList<String>(Arrays.asList(code)));
                }
              } else {
                Map<Pair<String, String>, List<String>> newLevel =
                    new HashMap<Pair<String, String>, List<String>>();
                newLevel.put(valunit, new ArrayList<String>(Arrays.asList(code)));
                factorStructure.put(lab, newLevel);
              }

            } else {
              Qproperty newProp = null;
              if (p.hasUnit()) {
                newProp = new Qproperty(code, p.getLabel(), p.getValue(), p.getUnit());
              } else {
                newProp = new Qproperty(code, p.getLabel(), p.getValue());
              }
              if (otherProps.containsKey(code)) {
                otherProps.get(code).add(newProp);
              } else {
                otherProps.put(code, new ArrayList<Qproperty>(Arrays.asList(newProp)));
              }
            }
          }
          JAXBElement<Qexperiment> newDesign =
              parser.mergeDesigns(setup, new HashSet<String>(), new ArrayList<>(), factorStructure, otherProps);
          try {
            String newXML = parser.toString(newDesign);
            HashMap<String, Object> params = new HashMap<>();
            String expID = sample.getExperiment().getIdentifier().toString();
            String[] split = expID.split("E");
            String numSuffixOfExperiment = split[split.length - 1];
            String infoExpID = expID.replace("E" + numSuffixOfExperiment, "_INFO");

            Map<String, Object> properties = new HashMap<>();
            properties.put("Q_EXPERIMENTAL_SETUP", newXML);
            params.put("user", user);
            params.put("identifier", infoExpID);
            params.put("properties", properties);
            datahandler.getOpenBisClient().triggerIngestionService("update-experiment-metadata",
                params);

          } catch (JAXBException e) {
            logger.error("could not update experimental design due to parser error!");
            e.printStackTrace();
          }
          // try {
          // qProperties = xmlParser.toString(xmlParser.createXMLFromProperties(factors));
          // props.put("Q_PROPERTIES", qProperties);
          // } catch (JAXBException e) {
          // // TODO Auto-generated catch block
          // e.printStackTrace();
          // }
        }

        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("user", user);
        parameters.put("identifier", id);
        parameters.put("properties", props);

        datahandler.getOpenBisClient().triggerIngestionService("update-single-sample-metadata",
            parameters);
        Utils.Notification("Metadata changed succesfully",
            String.format("Metadata values of sample %s have been commited successfully.", id),
            "success");
      }
    });
    buildFormLayout();
    propLayout.addComponent(new Label(String.format(
        "This view shows metadata connected to this sample and can be used to change the corresponding values. \nIdentifier: %s",
        id), Label.CONTENT_PREFORMATTED));

    propLayout.addComponent(this.form);
    propLayout.addComponent(saveButton);
  }

  private Map<String, PropertyBean> getControlledVocabularies() {
    Map<String, PropertyBean> controlledVocabularies = new HashMap<>();

    for (PropertyType p : completeProperties) {

      if ("CONTROLLEDVOCABULARY".equals(p.getDataType().toString())) {
        Vocabulary controlled_vocab =
                datahandler.getOpenBisClient().getVocabulary(p.getVocabulary().getCode());
        List<String> terms = new ArrayList<>();

        for (VocabularyTerm term : controlled_vocab.getTerms()) {
          terms.add(term.getCode());
        }

        PropertyBean newVocab = new PropertyBean();
        newVocab.setCode(p.getCode());
        newVocab.setDescription(p.getDescription());
        newVocab.setLabel(p.getLabel());
        newVocab.setVocabularyValues(terms);

        controlledVocabularies.put(p.getCode(), newVocab);
      }

    }
    return controlledVocabularies;
  }


  private Map<String, PropertyBean> getProperties() {
    Map<String, PropertyBean> properties = new HashMap<String, PropertyBean>();
    for (PropertyType p : completeProperties) {
      if (p.getDataType().toString().equals("XML")) {
        continue;
      } else if (assignedProperties.keySet().contains(p.getCode())) {
        properties.put(p.getCode(), new PropertyBean(p.getLabel(), p.getCode(), p.getDescription(),
            assignedProperties.get(p.getCode())));
      } else {
        properties.put(p.getCode(),
            new PropertyBean(p.getLabel(), p.getCode(), p.getDescription(), ""));
      }
    }
    return properties;

  }

  private List<Property> getXMLProperties() {
    JAXBElement<Qexperiment> setup = datahandler.getExperimentalSetup();
    List<Property> properties =
        parser.getFactorsAndPropertiesForSampleCode(setup, sample.getCode());
    return properties;
    // XMLParser xmlParser = new XMLParser();
    // List<Property> factors = new ArrayList<Property>();
    //
    // if (assignedProperties.containsKey("Q_PROPERTIES")) {
    // try {
    // factors = xmlParser.getAllPropertiesFromXML(assignedProperties.get("Q_PROPERTIES"));
    // } catch (JAXBException e) {
    // // TODO Auto-generated catch block
    // e.printStackTrace();
    // }
    // }
    // return factors;
  }

  private void buildFormLayout() {
    final FieldGroup fieldGroup = new FieldGroup();
    final FormLayout form2 = new FormLayout();
    this.fieldGroup = new FieldGroup();
    this.form = new FormLayout();

    Map<String, PropertyBean> controlledVocabularies = getControlledVocabularies();
    Map<String, PropertyBean> properties = getProperties();
    List<Property> xmlProps = getXMLProperties();

    for (Property f : xmlProps) {
      life.qbic.xml.properties.PropertyType type = f.getType();

      String label = f.getLabel();
      if (f.hasUnit())
        label += " in " + f.getUnit();
      TextField tf = new TextField(label);
      tf.setData(type);// save property type for later, when it is written back
      fieldGroup.bind(tf, label);
      tf.setCaption(label);
      tf.setDescription("Q_PROPERTIES");
      tf.setValue((String) f.getValue());
      form2.addComponent(tf);
    }

    for (String key : properties.keySet()) {
      if (controlledVocabularies.keySet().contains(key)) {
        ComboBox select = new ComboBox(controlledVocabularies.get(key).getLabel());
        fieldGroup.bind(select, key);

        // Add items with given item IDs
        select.addItems(controlledVocabularies.get(key).getVocabularyValues());

        select.setDescription(controlledVocabularies.get(key).getCode());
        select.setValue(properties.get(key).getValue());

        form2.addComponent(select);

      } else {
        TextField tf = new TextField(key);
        fieldGroup.bind(tf, key);
        tf.setCaption(properties.get(key).getLabel());
        tf.setDescription(properties.get(key).getCode());
        tf.setValue((String) properties.get(key).getValue());
        form2.addComponent(tf);
      }
    }
    this.fieldGroup = fieldGroup;
    this.form = form2;
  }
}
