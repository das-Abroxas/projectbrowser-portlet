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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.vocabulary.Vocabulary;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.vocabulary.VocabularyTerm;
import com.vaadin.data.fieldgroup.FieldGroup;
import com.vaadin.server.Page;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Field;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.helpers.Utils;
import life.qbic.projectbrowser.model.PropertyBean;
import life.qbic.projectbrowser.controllers.*;

import life.qbic.xml.manager.XMLParser;
import life.qbic.xml.properties.*;

public class ChangeExperimentMetadataComponent extends CustomComponent {
  private DataHandler datahandler;
  private String resourceUrl;
  private State state;
  private VerticalLayout propLayout;

  private FormLayout form;
  private FieldGroup fieldGroup;
  VerticalLayout vert;
  String id;
  private List<ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType> completeProperties;
  private Map<String, String> assignedProperties;

  public ChangeExperimentMetadataComponent(DataHandler dh, State state, String resourceurl) {
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
        .listPropertiesForType(datahandler.getOpenBisClient().getExperimentTypeByString(type));

    assignedProperties =
        datahandler.getOpenBisClient().getExperiment(id).getProperties();

    saveButton.addClickListener(new ClickListener() {
      @Override
      public void buttonClick(final ClickEvent event) {
        HashMap<String, Object> props = new HashMap<String, Object>();
        Collection<Field<?>> registeredFields = fieldGroup.getFields();
        XMLParser xmlParser = new XMLParser();

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
            PropertyType type = (PropertyType) tf.getData();
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

        if (qpropertiesDefined) {
          String qProperties = "";

          try {
            qProperties = xmlParser.toString(xmlParser.createXMLFromProperties(factors));
            props.put("Q_PROPERTIES", qProperties);
          } catch (JAXBException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }


        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("user", PortalUtils.getNonNullScreenName());
        parameters.put("identifier", id);
        parameters.put("properties", props);

        datahandler.getOpenBisClient().triggerIngestionService("update-experiment-metadata",
            parameters);
        Utils.Notification("Metadata changed succesfully",
            String.format("Metadata values of experiment %s have been commited successfully.", id),
            "success");
      }
    });
    buildFormLayout();
    propLayout.addComponent(new Label(String.format(
        "This view shows metadata connected to this experiment and can be used to change the corresponding values. \nIdentifier: %s",
        id), Label.CONTENT_PREFORMATTED));

    propLayout.addComponent(this.form);
    propLayout.addComponent(saveButton);
  }

  private Map<String, PropertyBean> getControlledVocabularies() {
    Map<String, PropertyBean> controlledVocabularies = new HashMap<>();

    for (ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType p : completeProperties) {

      Vocabulary controlled_vocab = datahandler.getOpenBisClient().getVocabulary(p.getVocabulary().getCode());
      List<String> terms = new ArrayList<>();

      for (VocabularyTerm term : controlled_vocab.getTerms())
        terms.add(term.getCode());

      PropertyBean newVocab = new PropertyBean();
      newVocab.setCode(p.getCode());
      newVocab.setDescription(p.getDescription());
      newVocab.setLabel(p.getLabel());
      newVocab.setVocabularyValues(terms);

      controlledVocabularies.put(p.getCode(), newVocab);

    }
    return controlledVocabularies;
  }


  private Map<String, PropertyBean> getProperties() {
    Map<String, PropertyBean> properties = new HashMap<String, PropertyBean>();
    for (ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType p : completeProperties) {
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
    XMLParser xmlParser = new XMLParser();
    List<Property> properties = new ArrayList<Property>();

    if (assignedProperties.containsKey("Q_PROPERTIES")) {
      try {
        properties = xmlParser.getAllPropertiesFromXML(assignedProperties.get("Q_PROPERTIES"));
      } catch (JAXBException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return properties;
  }

  private void buildFormLayout() {
    final FieldGroup fieldGroup = new FieldGroup();
    final FormLayout form2 = new FormLayout();

    Map<String, PropertyBean> controlledVocabularies = getControlledVocabularies();
    Map<String, PropertyBean> properties = getProperties();
    List<Property> xmlProps = getXMLProperties();

    for (Property f : xmlProps) {
      PropertyType type = f.getType();

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
