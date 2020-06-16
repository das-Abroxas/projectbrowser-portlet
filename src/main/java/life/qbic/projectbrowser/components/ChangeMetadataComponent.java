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

import ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType;
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
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.model.PropertyBean;
import life.qbic.projectbrowser.model.SampleBean;

import life.qbic.projectbrowser.controllers.*;

public class ChangeMetadataComponent extends CustomComponent {
  private DataHandler datahandler;
  private String resourceUrl;
  private State state;
  private VerticalLayout properties;

  private FormLayout form;
  private FieldGroup fieldGroup;
  VerticalLayout vert;
  String id;

  public ChangeMetadataComponent(DataHandler dh, State state, String resourceurl) {
    this.datahandler = dh;
    this.resourceUrl = resourceurl;
    this.state = state;

    this.setCaption("Metadata");

    this.initUI();
  }

  private void initUI() {
    properties = new VerticalLayout();
    properties.setWidth(100.0f, Unit.PERCENTAGE);
    properties.setMargin(new MarginInfo(true, false, true, true));
    properties.setSpacing(true);
    this.setWidth(Page.getCurrent().getBrowserWindowWidth() * 0.8f, Unit.PIXELS);
    this.setCompositionRoot(properties);
  }

  public void updateUI(final SampleBean currentBean) {
    properties.removeAllComponents();

    final Button saveButton = new Button("Commit Changes", new ClickListener() {
      @Override
      public void buttonClick(final ClickEvent event) {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        Collection<Field<?>> registeredFields = fieldGroup.getFields();

        for (Field<?> field : registeredFields) {
          properties.put(field.getDescription(), field.getValue());
        }

        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("user", PortalUtils.getNonNullScreenName());
        parameters.put("identifier", currentBean.getId());
        parameters.put("properties", properties);

        datahandler.getOpenBisClient().triggerIngestionService("update-single-sample-metadata",
            parameters);
        Notification.show("Changes committed!", Type.TRAY_NOTIFICATION);
      }
    });
    buildFormLayout(currentBean);
    properties.addComponent(new Label(
        String.format(
            "This view shows the metadata connected to this sample and can be used to change metadata values."),
        Label.CONTENT_PREFORMATTED));
    properties.addComponent(this.form);
    properties.addComponent(saveButton);
  }

  private Map<String, PropertyBean> getControlledVocabularies(SampleBean currentBean) {
    List<PropertyType> completeProperties = datahandler.getOpenBisClient().listPropertiesForType(
        datahandler.getOpenBisClient().getSampleTypeByString(currentBean.getType()));

    Map<String, PropertyBean> controlledVocabularies = new HashMap<>();

    for (PropertyType p : completeProperties) {
      if ("CONTROLLEDVOCABULARY".equals( p.getDataType().toString() )) {
        Vocabulary controlled_vocab = datahandler.getOpenBisClient().getVocabulary(p.getVocabulary().getCode());
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


  private Map<String, PropertyBean> getProperties(SampleBean currentBean) {
    List<PropertyType> completeProperties = datahandler.getOpenBisClient().listPropertiesForType(
        datahandler.getOpenBisClient().getSampleTypeByString(currentBean.getType()));

    Map<String, PropertyBean> properties = new HashMap<String, PropertyBean>();
    // Change that call
    Map<String, String> assignedProperties = currentBean.getProperties();

    for (PropertyType p : completeProperties) {
      if (p.getDataType().toString().equals("XML")) {
        continue;
      } else if (assignedProperties.keySet().contains(p.getCode())) {
        // properties.put(p.getCode(), assignedProperties.get(p.getCode()));
        properties.put(p.getCode(), new PropertyBean(p.getLabel(), p.getCode(), p.getDescription(),
            assignedProperties.get(p.getCode())));
      } else {
        properties.put(p.getCode(),
            new PropertyBean(p.getLabel(), p.getCode(), p.getDescription(), ""));
      }
    }
    return properties;

  }

  private void buildFormLayout(SampleBean sample) {
    final FieldGroup fieldGroup = new FieldGroup();
    final FormLayout form2 = new FormLayout();

    Map<String, PropertyBean> controlledVocabularies = getControlledVocabularies(sample);
    Map<String, PropertyBean> properties = getProperties(sample);

    for (String key : properties.keySet()) {
      if (controlledVocabularies.keySet().contains(key)) {
        ComboBox select = new ComboBox(controlledVocabularies.get(key).getLabel());
        fieldGroup.bind(select, key);

        // Add items with given item IDs
        select.addItems(controlledVocabularies.get(key).getVocabularyValues());

        /*
         * for(Object itemID: select.getItemIds()) { System.out.println(itemID); }
         */
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
