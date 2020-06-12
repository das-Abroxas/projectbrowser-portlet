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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.project.Project;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.event.SelectionEvent;
import com.vaadin.event.SelectionEvent.SelectionListener;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.WebBrowser;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.helpers.Utils;
import life.qbic.projectbrowser.model.NewIvacSampleBean;
import life.qbic.projectbrowser.controllers.*;
import life.qbic.projectbrowser.components.CustomVisibilityComponent;
import life.qbic.projectbrowser.components.GridEditForm;


public class AddPatientView extends VerticalLayout implements View {

  public final static String navigateToLabel = "addivacproject";

  VerticalLayout addPatientViewContent;

  private String resourceUrl;
  private DataHandler datahandler;
  private State state;

  private Button registerPatients;

  private VerticalLayout buttonLayoutSection;

  private CustomVisibilityComponent projects;
  private ComboBox typingMethod = new ComboBox("Typing Method");
  private CustomVisibilityComponent numberOfPatients;
  private CustomVisibilityComponent secondaryNames;
  private CustomVisibilityComponent description;
  private CheckBox registerHLAI = new CheckBox("MHC Class I");
  private CheckBox registerHLAII = new CheckBox("MHC Class II");
  private VerticalLayout hlaTypingSection;
  private VerticalLayout expSetup = new VerticalLayout();
  private VerticalLayout optionLayout = new VerticalLayout();
  private VerticalLayout hlaLayout = new VerticalLayout();

  private TextArea hlaItypes = new TextArea();
  private TextArea hlaIItypes = new TextArea();

  private String header;


  private BeanItemContainer sampleOptions =
      new BeanItemContainer<NewIvacSampleBean>(NewIvacSampleBean.class);

  private VerticalLayout optionLayoutSection;

  private CustomVisibilityComponent hlaInfo;

  private CustomVisibilityComponent gridInfo;



  public AddPatientView(DataHandler datahandler, State state, String resourceurl) {
    this(datahandler, state);
    this.resourceUrl = resourceurl;
  }

  public AddPatientView(DataHandler datahandler, State state) {
    this.datahandler = datahandler;
    this.state = state;
    resourceUrl = "javascript;";
    sampleOptions.addBean(new NewIvacSampleBean("Normal", 1, "", false, false, false, "", ""));
    sampleOptions.addBean(new NewIvacSampleBean("Tumor", 1, "", false, false, false, "", ""));

    initView();
  }

  public String getHeader() {
    return header;
  }

  public void setHeader(String header) {
    this.header = header;
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
    header = "";

    addPatientViewContent = new VerticalLayout();

    addPatientViewContent.addComponent(expSetup);
    addPatientViewContent.addComponent(optionLayout);

    addPatientViewContent.addComponent(hlaLayout);

    addPatientViewContent.setWidth("100%");
    addPatientViewContent.setMargin(new MarginInfo(true, false, false, false));
    this.addComponent(addPatientViewContent);
    this.addComponent(initButtonLayout());
  }

  /**
   * 
   * @return
   */
  void initOptionLayout() {
    optionLayout.removeAllComponents();
    optionLayout.setWidth("100%");
    optionLayout.setVisible(false);

    VerticalLayout optionLayoutContent = new VerticalLayout();

    Button addSample = new Button("Add Sample");
    addSample.addClickListener(new ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        sampleOptions.addBean(new NewIvacSampleBean("", 0, "", false, false, false, "", ""));
      }
    });

    optionLayoutContent.setMargin(new MarginInfo(true, false, false, false));
    optionLayoutContent.setHeight(null);
    optionLayoutContent.setWidth("100%");
    optionLayoutContent.setSpacing(true);


    final Grid optionGrid = new Grid();
    optionGrid.setWidth("80%");
    // optionGrid.setCaption("Which biological samples are available for the patient(s) and which
    // experiments will be performed?");

    gridInfo = new CustomVisibilityComponent(new Label(""));
    ((Label) gridInfo.getInnerComponent()).addStyleName(ValoTheme.LABEL_LARGE);

    Component gridInfoContent = Utils.questionize(gridInfo,
        "Which biological samples are available for the patient(s) and which experiments will be performed?",
        "Extracted Samples");

    // optionGrid.setSelectionMode(SelectionMode.MULTI);
    optionGrid.setEditorEnabled(true);

    optionGrid.setContainerDataSource(sampleOptions);
    optionGrid.setColumnOrder("type", "secondaryName", "tissue", "amount", "dnaSeq", "rnaSeq",
        "deepSeq");

    optionLayoutContent.addComponent(gridInfoContent);
    optionLayoutContent.addComponent(optionGrid);
    optionLayoutContent.addComponent(addSample);

    final GridEditForm form =
        new GridEditForm(datahandler.getOpenBisClient().getVocabCodesForVocab("Q_PRIMARY_TISSUES"),
            datahandler.getOpenBisClient().getVocabCodesForVocab("Q_SEQUENCER_DEVICES"));

    optionLayoutContent.addComponent(form);
    form.setVisible(false);

    optionGrid.addSelectionListener(new SelectionListener() {

      @Override
      public void select(SelectionEvent event) {
        BeanItem<NewIvacSampleBean> item = sampleOptions.getItem(optionGrid.getSelectedRow());
        form.fieldGroup.setItemDataSource(item);
        form.setVisible(true);
      }
    });

    optionLayout.addComponent(optionLayoutContent);
  }

  /**
   * 
   * @return
   */
  VerticalLayout initButtonLayout() {
    registerPatients = new Button("Register Patients");
    // registerPatients.setWidth("100%");
    registerPatients.setStyleName(ValoTheme.BUTTON_FRIENDLY);
    registerPatients.setVisible(false);

    registerPatients.addClickListener(new ClickListener() {

      @Override
      public void buttonClick(ClickEvent event) {
        callPatientRegistration();
      }
    });

    buttonLayoutSection = new VerticalLayout();
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setMargin(new MarginInfo(true, false, true, false));
    buttonLayout.setHeight(null);
    buttonLayout.setWidth("100%");
    buttonLayoutSection.setWidth("100%");

    buttonLayoutSection.addComponent(buttonLayout);
    buttonLayout.addComponent(registerPatients);

    return buttonLayoutSection;
  }



  /**
   * initializes the description layout
   * 
   * @return
   */
  void initExperimentalSetupLayout() {
    expSetup.removeAllComponents();
    expSetup.setWidth("100%");
    expSetup.setSpacing(true);
    expSetup.setMargin(new MarginInfo(true, false, false, false));

    Set<String> visibleSpaces = new LinkedHashSet<>();

    for (Project project : datahandler.getOpenBisClient().
            listProjectsForUser(PortalUtils.getNonNullScreenName())) {

      if (project.getIdentifier().toString().contains("IVAC")) {
        visibleSpaces.add(project.getSpace().getCode());
      }
    }

    projects = new CustomVisibilityComponent(new ComboBox("Select Project", visibleSpaces));
    ((ComboBox) projects.getInnerComponent()).setImmediate(true);

    Component context =
        Utils.questionize(projects, "Add Patient(s) to the following project", "Select Project");

    ((ComboBox) projects.getInnerComponent()).addValueChangeListener(new ValueChangeListener() {

      @Override
      public void valueChange(ValueChangeEvent event) {
        numberOfPatients.setVisible(true);
      }
    });

    expSetup.addComponent(context);

    numberOfPatients = new CustomVisibilityComponent(new TextField("Number of Patients"));
    numberOfPatients.setVisible(false);
    ((TextField) numberOfPatients.getInnerComponent()).setImmediate(true);

    Component numberContext = Utils.questionize(numberOfPatients,
        "How many patients with the same setup should be registered?", "Number of Patients");

    ((TextField) numberOfPatients.getInnerComponent())
        .addTextChangeListener(new TextChangeListener() {

          @Override
          public void textChange(TextChangeEvent event) {
            secondaryNames.setVisible(true);
          }
        });

    expSetup.addComponent(numberContext);

    secondaryNames = new CustomVisibilityComponent(new TextField("Identifiers"));
    ((TextField) secondaryNames.getInnerComponent()).setImmediate(true);

    secondaryNames.setVisible(false);
    Component secondaryContext = Utils.questionize(secondaryNames,
        "Please provide a list of comma separated IDs.", "Identifiers");

    ((TextField) secondaryNames.getInnerComponent())
        .addTextChangeListener(new TextChangeListener() {

          @Override
          public void textChange(TextChangeEvent event) {
            description.setVisible(true);
          }
        });

    expSetup.addComponent(secondaryContext);

    description = new CustomVisibilityComponent(new TextField("Description"));
    ((TextField) description.getInnerComponent()).setImmediate(true);
    description.setVisible(false);
    Component descriptionContext = Utils.questionize(description,
        "Please provide a general description for the new patient cases", "Description");

    ((TextField) description.getInnerComponent()).addTextChangeListener(new TextChangeListener() {

      @Override
      public void textChange(TextChangeEvent event) {
        optionLayout.setVisible(true);
        hlaLayout.setVisible(true);
        registerPatients.setVisible(true);
      }
    });
    expSetup.addComponent(descriptionContext);

  }

  /**
   * initializes the hla typing registration layout
   * 
   * @return
   */
  void hlaTypingLayout() {

    hlaLayout.removeAllComponents();
    hlaLayout.setWidth("100%");
    hlaLayout.setVisible(false);

    hlaLayout.setMargin(new MarginInfo(true, false, false, false));
    hlaLayout.setHeight(null);
    hlaLayout.setSpacing(true);

    hlaInfo = new CustomVisibilityComponent(new Label("HLA Typing"));
    ((Label) hlaInfo.getInnerComponent()).setHeight("24px");

    Component hlaContext = Utils.questionize(hlaInfo,
        "Register available HLA typing for this patient (one allele per line)", "HLA Typing");

    hlaLayout.addComponent(hlaContext);

    HorizontalLayout hlalayout = new HorizontalLayout();

    VerticalLayout hlaLayout1 = new VerticalLayout();
    hlaLayout1.addComponent(registerHLAI);
    hlaLayout1.addComponent(hlaItypes);

    VerticalLayout hlaLayout2 = new VerticalLayout();
    hlaLayout2.addComponent(registerHLAII);
    hlaLayout2.addComponent(hlaIItypes);

    hlalayout.addComponent(hlaLayout1);
    hlalayout.addComponent(hlaLayout2);

    hlalayout.setSpacing(true);

    typingMethod
        .addItems(datahandler.getOpenBisClient().getVocabCodesForVocab("Q_HLA_TYPING_METHODS"));

    hlaLayout.addComponent(typingMethod);
    hlaLayout.addComponent(hlalayout);
  }

  public void callPatientRegistration() {

    List<String> secondaryIDs = Arrays.asList(secondaryNames.getValue().split("\\s*,\\s*"));
    Map<String, List<String>> hlaTyping = new HashMap<String, List<String>>();

    List<String> hlaTypingI = new ArrayList<String>();
    List<String> hlaTypingII = new ArrayList<String>();

    boolean hlaIvalid = true;
    boolean hlaIIvalid = true;

    if (registerHLAI.getValue()) {
      if (hlaItypes.getValue() != null & typingMethod.getValue() != null) {
        hlaTypingI.add(hlaItypes.getValue());
        hlaTypingI.add(typingMethod.getValue().toString());
        hlaTyping.put("MHC_CLASS_I", hlaTypingI);
      } else {
        Utils.Notification("HLA Typing not fully specified for class I.",
            "The HLA alleles or the method which has been used for typing have not been specified.",
            "error");
        hlaIvalid = false;
      }
    }

    if (registerHLAII.getValue()) {

      if (hlaIItypes.getValue() != null & typingMethod.getValue() != null) {
        hlaTypingII.add(hlaIItypes.getValue());
        hlaTypingII.add(typingMethod.getValue().toString());
        hlaTyping.put("MHC_CLASS_II", hlaTypingII);
      } else {
        Utils.Notification("HLA Typing not fully specified for class II.",
            "The HLA alleles or the method which has been used for typing have not been specified.",
            "error");
        hlaIIvalid = false;
      }
    }

    Integer numberPatients = Integer.parseInt(numberOfPatients.getValue());

    if (numberPatients.equals(secondaryIDs.size()) & checkRegisteredSamplesTable() & hlaIvalid
        & hlaIIvalid) {
      datahandler.registerNewPatients(numberPatients, secondaryIDs, sampleOptions,
          projects.getValue().toString(), description.getValue(), hlaTyping);
      Utils.Notification("Patients successfully registered.",
          "The provided patient information has been written to the database successfully.",
          "success");
      // sucess.show(Page.getCurrent());
    } else {
      Utils.Notification("Registration failed",
          "Number of Patients and secondary IDs has to be the same and tissues have to be fully specified.",
          "error");
    }

  }

  public boolean checkRegisteredSamplesTable() {
    boolean valid = true;

    if (sampleOptions.size() == 0) {
      return false;
    }


    for (Iterator iter = sampleOptions.getItemIds().iterator(); iter.hasNext();) {
      boolean expsSpecified = false;
      boolean tissueSpecified = false;
      boolean instrumentSpecified = false;

      NewIvacSampleBean sampleBean = (NewIvacSampleBean) iter.next();

      expsSpecified = ((sampleBean.getDeepSeq() == true) | (sampleBean.getDnaSeq() == true)
          | (sampleBean.getRnaSeq() == true));

      tissueSpecified = (!sampleBean.getTissue().equals(""));
      instrumentSpecified = (!sampleBean.getSeqDevice().equals(""));

      valid = valid & (expsSpecified & tissueSpecified & instrumentSpecified);
    }

    return valid;
  }

  @Override
  public void enter(ViewChangeEvent event) {
    String currentValue = event.getParameters();
    // this.setContainerDataSource(datahandler.getProject(currentValue));
    updateContent();
  }

  private void updateContent() {
    header = "Patient Registration";
    initExperimentalSetupLayout();
    initOptionLayout();
    hlaTypingLayout();

    registerPatients.setVisible(false);
    numberOfPatients.setVisible(false);
    secondaryNames.setVisible(false);
    description.setVisible(false);
    hlaLayout.setVisible(false);
    optionLayout.setVisible(false);
  }

}
