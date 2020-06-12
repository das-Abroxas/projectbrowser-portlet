/*******************************************************************************
 * QBiC Project qNavigator enables users to manage their projects.
 * Copyright (C) "2016”  Christopher Mohr, David Wojnar, Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package life.qbic.projectbrowser.components;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import life.qbic.projectbrowser.model.maxquant.Group;
import life.qbic.projectbrowser.model.maxquant.MaxQuantModel;
import life.qbic.projectbrowser.model.maxquant.RawFilesBean;
import life.qbic.projectbrowser.helpers.JsonHelper;
import submitter.Workflow;
import submitter.parameters.InputList;
import submitter.parameters.ParameterSet;

import com.vaadin.data.fieldgroup.FieldGroup;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.data.util.ObjectProperty;
import com.vaadin.data.util.PropertysetItem;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TwinColSelect;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.projectbrowser.controllers.WorkflowViewController;
import de.uni_tuebingen.qbic.beans.DatasetBean;
import fasta.FastaBean;
import guse.workflowrepresentation.GuseNode;
import guse.workflowrepresentation.GuseWorkflowRepresentation;
import guse.workflowrepresentation.InputPort;

public class MaxQuantComponent extends CustomComponent {
  private static final long serialVersionUID = 5876981463933993626L;
  private static final Logger LOG = LogManager.getLogger(MaxQuantComponent.class);

  private static final String INITNODE_CAPTION = "Initialization";

  private final String SELECTED_FILES_CAPTION = "Selected files for analysis";
  private final String RAW_FILES_INFO =
      "Select files for analysis. You can edit fraction and group parameters for each selected files by double clicking a selected file in the right table";
  private final String BUTTON_START_CAPTION = "start";
  public final String RAW_FILES_CAPTION = "Raw files";
  private final String AVAILABLE_FILES_CAPTION = "Avaliable files";

  private String FASTA_FILES_INFO = "Select fasta files for analysis.";
  // private String AVAILABLE_FASTAS_CAPTION = "Fasta files";
  private String AVAILABLE_FASTAS_CAPTION = "Available References";
  private String SELECTED_FASTAS_CAPTION = "Selected Reference";

  private final MaxQuantModel model;

  private Button start;
  private TabSheet tabs;

  private GroupSpecificParameterComponent groupSpecificParameterComponent;

  private SelectFileComponent rawFiles;
  private SelectFileComponent fastaFiles;

  private TwinColSelect fixedModifications;
  private CheckBox reQuantify;
  private CheckBox matchBetweenRuns;

  private PropertysetItem globalParameters;

  private Workflow guseWorkflow;

  private WorkflowViewController controller;



  public MaxQuantComponent(final MaxQuantModel model, WorkflowViewController wfController) {
    // model
    this.model = model;
    controller = wfController;

    // view
    VerticalLayout mainLayout = new VerticalLayout();
    // mainLayout.setSpacing(true);
    tabs = new TabSheet();

    // http://141.61.102.17/maxquant_doku/doku.php?id=maxquant:manual:beginner
    // Filter for raw Files


    rawFiles =
        new SelectFileComponent(RAW_FILES_CAPTION, RAW_FILES_INFO, AVAILABLE_FILES_CAPTION,
            SELECTED_FILES_CAPTION, model.getDatasetBeans(), model.getRawFilesBeans());

    rawFiles.getDestination().setEditorEnabled(true);
    rawFiles.getDestination().getColumn("experiment").setEditable(false);
    rawFiles.getDestination().getColumn("file").setEditable(false);
    rawFiles.getDestination().setImmediate(true);

    tabs.addTab(rawFiles);
    groupSpecificParameterComponent = new GroupSpecificParameterComponent();
    tabs.addTab(groupSpecificParameterComponent);
    tabs.addTab(globalParameters());

    mainLayout.addComponent(tabs);

    start = new Button(BUTTON_START_CAPTION);
    mainLayout.addComponent(start);
    mainLayout.setWidth("100%");
    this.setCompositionRoot(mainLayout);

    // controller
    setLogic();
    bindGlobalParameters();
  }

  /**
   * checks how many groups currently are present in raw files, and updates them
   */
  public void updateGroups() {
    if (model.getRawFilesBeans().size() == 0) {
      model.getGroups().clear();
      return;
    } else {
      HashMap<Integer, List<RawFilesBean>> rawFilesSorted =
          new HashMap<Integer, List<RawFilesBean>>();
      for (RawFilesBean bean : model.getRawFilesBeans().getItemIds()) {
        if (rawFilesSorted.containsKey(bean.getParameterGroup())) {
          rawFilesSorted.get(bean.getParameterGroup()).add(bean);
        } else {
          ArrayList<RawFilesBean> beans = new ArrayList<RawFilesBean>();
          beans.add(bean);
          rawFilesSorted.put(bean.getParameterGroup(), beans);
        }

      }
      // remove old groups that are not used anymore
      for (Integer key : model.getGroups().keySet()) {
        if (!rawFilesSorted.containsKey(key)) {
          model.getGroups().remove(key);
        }
      }
      // updated groups
      for (Integer key : rawFilesSorted.keySet()) {
        if (model.getGroups().containsKey(key)) {
          model.getGroups().get(key).removeFiles();
          model.getGroups().get(key).setFiles(rawFilesSorted.get(key));
        } else {
          Group group = new Group();
          group.setFiles(rawFilesSorted.get(key));
          model.getGroups().put(key, group);
        }

      }
    }
    // add new groups
    HashMap<Integer, Group> groups = new HashMap<Integer, Group>();
    for (RawFilesBean bean : model.getRawFilesBeans().getItemIds()) {
      if (!groups.containsKey(bean.getParameterGroup())) {
        Group group = new Group();
        group.addFile(bean);
        groups.put(bean.getParameterGroup(), group);
      }
    }

  }

  /**
   * creates the global parameter component
   * 
   * @return
   */
  private Component globalParameters() {
    FormLayout globalparameters = new FormLayout();
    globalparameters.setCaption("Global parameters");
    fastaFiles =
        new SelectFileComponent("", FASTA_FILES_INFO, AVAILABLE_FASTAS_CAPTION,
            SELECTED_FASTAS_CAPTION, model.getFastaBeans(), model.getSelectedFastaBeans());
    globalparameters.addComponent(fastaFiles);
    // fixed modifications
    // fixedModifications = new TwinColSelect("fixed modifications");
    fixedModifications = new TwinColSelect();

    Label fixedInfo = new Label("Fixed Modifications");
    fixedInfo.addStyleName(ValoTheme.LABEL_COLORED);

    globalparameters.addComponent(fixedInfo);
    fixedModifications.addItems("Acetyl (Protein N-term)", "Acetyl (K)", "Oxidation (M)",
        "Ala->Arg", "Carbamidomethyl (C)");
    globalparameters.addComponent(fixedModifications);
    reQuantify = new CheckBox("Requantify");
    globalparameters.addComponent(reQuantify);
    matchBetweenRuns = new CheckBox("Match Between Runs");
    globalparameters.addComponent(matchBetweenRuns);
    return globalparameters;
  }

  void bindGlobalParameters() {
    globalParameters = new PropertysetItem();
    globalParameters.addItemProperty("fixedModifications",
        new ObjectProperty<LinkedHashSet<String>>(model.getFixedMods()));
    globalParameters.addItemProperty("matchBetweenRuns",
        new ObjectProperty<Boolean>(model.getMatchBetweenRuns()));
    globalParameters.addItemProperty("reQuantify",
        new ObjectProperty<Boolean>(model.getReQuantify()));
    FieldGroup fieldGroup = new FieldGroup(globalParameters);
    fieldGroup.bind(fixedModifications, "fixedModifications");
    fieldGroup.bind(matchBetweenRuns, "matchBetweenRuns");
    fieldGroup.bind(reQuantify, "reQuantify");
    fieldGroup.setBuffered(false);
  }

  void setLogic() {
    // update groups for group specific paramter tab according to how many groups are currently
    // created
    tabs.addSelectedTabChangeListener(new SelectedTabChangeListener() {
      private static final long serialVersionUID = -8616030904807506084L;

      @Override
      public void selectedTabChange(SelectedTabChangeEvent event) {
        if (event.getTabSheet().getSelectedTab().getCaption()
            .equals(GroupSpecificParameterComponent.CAPTION)) {
          updateGroups();
          // update component with new groups
          groupSpecificParameterComponent.update(model.getGroups());
        }
      }
    });

    // button to move files from datasets to selected raw files
    rawFiles.getToRightButton().addClickListener(new ClickListener() {
      private static final long serialVersionUID = -3673780036437094193L;

      @Override
      public void buttonClick(ClickEvent event) {
        Collection<Object> available = rawFiles.getSource().getSelectedRows();
        if (available == null || available.isEmpty())
          return;

        for (Object o : available) {
          rawFiles.getSource().deselect(o);
        }
        model.selectRawFiles(available);

        for (Grid.Column col : rawFiles.getSelected().getColumns()) {
          col.setWidthUndefined();
        }
      }
    });
    // opposite of toRight
    rawFiles.getToLeftButton().addClickListener(new ClickListener() {
      private static final long serialVersionUID = 2728686539720595641L;

      @Override
      public void buttonClick(ClickEvent event) {
        Collection<Object> available = rawFiles.getDestination().getSelectedRows();
        if (available == null || available.isEmpty())
          return;

        for (Object o : available) {
          rawFiles.getDestination().deselect(o);
        }
        model.unselectRawFiles(available);
      }
    });


    // button to move files from datasets to selected raw files
    fastaFiles.getToRightButton().addClickListener(new ClickListener() {
      private static final long serialVersionUID = -3673780036437094193L;

      @Override
      public void buttonClick(ClickEvent event) {
        Collection<Object> available = fastaFiles.getSource().getSelectedRows();
        if (available == null || available.isEmpty())
          return;

        for (Object o : available) {
          fastaFiles.getSource().deselect(o);
        }
        model.selectFastaFiles(available);
      }
    });
    // opposite of toRight
    fastaFiles.getToLeftButton().addClickListener(new ClickListener() {
      private static final long serialVersionUID = 2728686539720595641L;

      @Override
      public void buttonClick(ClickEvent event) {
        Collection<Object> available = fastaFiles.getDestination().getSelectedRows();
        if (available == null || available.isEmpty())
          return;

        for (Object o : available) {
          fastaFiles.getDestination().deselect(o);
        }
        model.unselectFastaFiles(available);

        for (Grid.Column col : fastaFiles.getSelected().getColumns()) {
          col.setWidthUndefined();
        }
      }
    });



    // start workflow
    start.addClickListener(new ClickListener() {
      private static final long serialVersionUID = 2728686539720595641L;

      @Override
      public void buttonClick(ClickEvent event) {
        updateGroups();
      }
    });
  }

  /**
   * write the content the model to file
   * 
   * @param filePath
   */
  void writeToFile(String filePath) {
    try {
      FileWriter newConfigFile = new FileWriter(filePath);
      // WATCH OUT! Somehow if indentFactor=2, information gets lost
      newConfigFile.write(toJson().toString(3));
      newConfigFile.flush();
      newConfigFile.close();
    } catch (JSONException | IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  /**
   * Creates a json object of this component. Be sure to be up to date. Otherwise, you might not get
   * all actual groups. To be up to date execute {@link #updateGroups() updateGroups}
   * 
   * @return
   * @throws JSONException
   */
  public JSONObject toJson() throws JSONException {

    Set<Entry<Integer, Group>> entryset = model.getGroups().entrySet();
    Iterator<Entry<Integer, Group>> iter = entryset.iterator();
    JSONArray rawFilesArray = new JSONArray();
    while (iter.hasNext()) {
      Entry<Integer, Group> entry = iter.next();
      rawFilesArray.put(entry.getValue().toJson());
    }
    JSONObject params = new JSONObject();
    params.put("rawFiles", rawFilesArray);
    params.put("fastaFiles", fastaToJson());
    params.put("globalParams", globalParamsToJson());
    return params;
  }

  /**
   * writes all fasta related information into a JSONObject. See
   * https://github.com/qbicsoftware/mqrun/blob/master/examples/simple.json
   * http://mqrun.readthedocs.org/en/latest/param_format.html and
   * https://github.com/qbicsoftware/mqrun/tree/master/mqrun/data for more information
   * 
   * @return
   * @throws JSONException
   */
  JSONObject fastaToJson() throws JSONException {
    JSONArray fastafiles = new JSONArray();
    BeanItemContainer<FastaBean> selected = model.getSelectedFastaBeans();
    for (FastaBean bean : selected.getItemIds()) {
      // shouldn't contain file ending and it should be the file name rather than the name of the
      // bean
      Path p = Paths.get(bean.getPath());
      String file = p.getFileName().toString();

      fastafiles.put(file.split("\\.")[0]);
      // fastafiles.put(bean.getName());
    }
    JSONObject ret = new JSONObject();
    ret.put("fileNames", fastafiles);
    ret.put("firstSearch", new JSONArray());
    return ret;
  }

  JSONObject globalParamsToJson() throws JSONException {
    JSONObject glparams = new JSONObject();
    for (Object id : globalParameters.getItemPropertyIds()) {
      Object value = globalParameters.getItemProperty(id).getValue();
      glparams.put((String) id,
          (value instanceof LinkedHashSet) ? JsonHelper.fromSet((LinkedHashSet<String>) value)
              : value);
    }
    glparams.put("defaults", "default");
    return glparams;
  }

  public void addSubmissionListener(ClickListener maxQuantSubmissionListener) {
    start.addClickListener(maxQuantSubmissionListener);
  }

  public void setWorkflow(Workflow workFlow) {
    guseWorkflow = workFlow;
  }

  public Workflow getWorkflow() {
    return guseWorkflow;
  }

  public Collection<DatasetBean> getSelectedDatasets() {
    return model.selectedDatasets();
  }

  /**
   * write json to correct workflow parameter!
   * 
   * @throws JSONException
   * @throws IllegalArgumentException
   */
  public void writeParametersToWorkflow() throws IllegalArgumentException, JSONException {
    if (guseWorkflow instanceof GuseWorkflowRepresentation) {
      GuseWorkflowRepresentation w = (GuseWorkflowRepresentation) guseWorkflow;

      GuseNode node = w.getNode(INITNODE_CAPTION);
      InputPort port = node.getPort("WORKFLOW-CTD");

      // port.getParams().get("jsonParams").setValue(toJson().toString(3));

      ParameterSet updated = w.getParameters();
      updated.getParam("MaxQuant.1.jsonParams").setValue(toJson().toString(3));

      InputList inpList = w.getData();

      // set Reference DB for 'data staging'
      BeanItemContainer<FastaBean> selected = model.getSelectedFastaBeans();
      for (FastaBean bean : selected.getItemIds()) {
        inpList.getData().get("InputFiles.1.db").setValue(bean.getPath());
      }

      // set input raw files for 'data staging'
      // Collection<Object> selectionMulti = rawFiles.getDestination().getSelectedRows();
      // if (selectionMulti == null || selectionMulti.isEmpty()) {
      // showError("Warning: Nothing selected for multi input parameter " +
      // rawFiles.getCaption());
      // return false;
      // Notification.show("No Input files selected!");
      // }
      List<String> selectedPaths = new ArrayList<String>();

      Collection<DatasetBean> selectionMulti = model.selectedDatasets();

      for (Object o : selectionMulti) {
        DatasetBean selectedBean = (DatasetBean) o;

        try {
          selectedPaths.add(controller.getDatasetsNfsPath(selectedBean));
        } catch (Exception e) {
          LOG.error(
              "could not retrieve nfs path. Using datasetbeans getfullpath instead. "
                  + e.getMessage(), e.getStackTrace());
          selectedPaths.add(selectedBean.getFullPath());
        }
      }
      inpList.getData().get("InputFiles.1.input").setValue(selectedPaths);
    }

  }
}
