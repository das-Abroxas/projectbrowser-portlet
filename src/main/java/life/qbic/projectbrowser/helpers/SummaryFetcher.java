package life.qbic.projectbrowser.helpers;

import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import life.qbic.projectbrowser.model.notes.Note;
import life.qbic.projectbrowser.model.notes.Notes;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.Br;
import org.docx4j.wml.P;

import life.qbic.xml.manager.StudyXMLParser;
import life.qbic.xml.manager.XMLParser;
import life.qbic.xml.properties.Property;
import life.qbic.xml.study.Qexperiment;
import life.qbic.projectbrowser.helpers.Docx4jHelper;

import life.qbic.datamodel.identifiers.ExperimentCodeFunctions;
import life.qbic.openbis.openbisclient.OpenBisClient;

import com.vaadin.server.FileDownloader;
import com.vaadin.server.FileResource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Table;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

public class SummaryFetcher {

  private OpenBisClient openbis;
  private Map<String, String> allMap;
  private String space;
  private String projectCode;
  private UglyToPrettyNameMapper prettyNameMapper = new UglyToPrettyNameMapper();
  private String projectName;
  private String projectDescription;
  private final Map<String, String> expTypeTranslation = new HashMap<String, String>() {
    {
      put("Q_NGS_MEASUREMENT", "Next Generation Sequencing Run");
      put("Q_EXPERIMENTAL_DESIGN", "Sampling Units");
      put("Q_BMI_GENERIC_IMAGING", "Biomedical Imaging");
      put("Q_SAMPLE_EXTRACTION", "Sample Extraction");
      put("Q_SAMPLE_PREPARATION", "Sample Preparation");
      put("Q_PROJECT_DETAILS", "Project Details");
      put("UNKNOWN", "Experiment Unknown");
      put("Q_EXT_MS_QUALITYCONTROL", "MS Quality Control");
      put("Q_EXT_NGS_QUALITYCONTROL", "NGS Quality Control");
      put("Q_MICROARRAY_MEASUREMENT", "Microarray Measurement");
      put("Q_MS_MEASUREMENT", "MS Measurement");
      put("Q_NGS_EPITOPE_PREDICTION", "Prediction of MHC binding Epitopes");
      put("Q_NGS_FLOWCELL_RUN", "Flowcell Run");
      put("Q_NGS_HLATYPING", "HLA Typing");
      put("Q_NGS_IMMUNE_MONITORING", "Immune Monitoring");
      put("Q_NGS_MAPPING", "Mapping of NGS Reads");
      put("Q_NGS_SINGLE_SAMPLE_RUN", "Next-Generation Sequencing Run");
      put("Q_NGS_VARIANT_CALLING", "Variant Calling");
      put("Q_WF_MA_QUALITYCONTROL", "Microarray Quality Control Workflow");
      put("Q_WF_MS_MAXQUANT", "MaxQuant Workflow");
      put("Q_WF_MS_PEPTIDEID", "Peptide Identification Workflow");
      put("Q_WF_MS_QUALITYCONTROL", "MS Quality Control Worfklow");
      put("Q_WF_NGS_EPITOPE_PREDICTION", "Prediction of MHC binding Epitopes Workflow");
      put("Q_WF_NGS_HLATYPING", "HLA Typing Workflow");
      put("Q_WF_NGS_MERGE", "Merging of NGS Reads");
      put("Q_WF_NGS_QUALITYCONTROL", "NGS Quality Control Workflow");
      put("Q_WF_NGS_RNA_EXPRESSION_ANALYSIS", "RNA Expression Analysis Workflow");
      put("Q_WF_NGS_VARIANT_ANNOTATION", "Variant Annotation Workflow");
      put("Q_WF_NGS_VARIANT_CALLING", "Variant Calling Workflow");
      put("Q_WF_NGS_MAPPING", "NGS Read Alignment Workflow");
      put("Q_WF_MS_INDIVIDUALIZED_PROTEOME", "Individualized Proteins Workflow");
      put("Q_WF_MS_LIGANDOMICS_ID", "Ligandomics Identification Workflow");
      put("Q_WF_MS_LIGANDOMICS_QC", "Ligandomics Quality Control Workflow");
      put("Q_MHC_LIGAND_EXTRACTION", "MHC Ligand Extraction");
    };
  };
  private WordprocessingMLPackage wordMLPackage;
  private Docx4jHelper docxHelper;
  private Component summaryComponent;
  private String tmpFolder;
  private boolean success = false;
  private Set<String> factorLabels;
  private Map<Pair<String, String>, Property> factorsForLabelsAndSamples;

  public SummaryFetcher(OpenBisClient openbis, String tmpFolder) {
    this.tmpFolder = tmpFolder;
    this.openbis = openbis;
    allMap = new HashMap<String, String>();


    Map<String, String> taxMap = openbis.getVocabCodesAndLabelsForVocab("Q_NCBI_TAXONOMY");
    Map<String, String> tissueMap = openbis.getVocabCodesAndLabelsForVocab("Q_PRIMARY_TISSUES");
    Map<String, String> deviceMap = openbis.getVocabCodesAndLabelsForVocab("Q_MS_DEVICES");
    Map<String, String> cellLinesMap = openbis.getVocabCodesAndLabelsForVocab("Q_CELL_LINES");
    Map<String, String> chromTypesMap =
        openbis.getVocabCodesAndLabelsForVocab("Q_CHROMATOGRAPHY_TYPES");
    Map<String, String> antibodiesMap = openbis.getVocabCodesAndLabelsForVocab("Q_ANTIBODY");

    allMap.putAll(reverseMap(cellLinesMap));
    allMap.putAll(reverseMap(deviceMap));
    allMap.putAll(reverseMap(taxMap));
    allMap.putAll(reverseMap(tissueMap));
    allMap.putAll(reverseMap(antibodiesMap));
    allMap.putAll(reverseMap(chromTypesMap));
  }

  private Map<String, String> reverseMap(Map<String, String> map) {
    Map<String, String> reverse = new HashMap<String, String>();
    for (Map.Entry<String, String> e : map.entrySet())
      reverse.put(e.getValue(), e.getKey());
    return reverse;
  }

  public void fetchSummaryComponent(String space, String code, String name, String description,
      final ProjectSummaryReadyRunnable ready) {
    this.space = space;
    this.projectCode = code;
    this.projectName = name;
    this.projectDescription = description;
    Thread t = new Thread(new Runnable() {

      @Override
      public void run() {
        parseExperimentalDesign();
        summaryComponent = computePopupComponent();
        UI.getCurrent().access(ready);
        UI.getCurrent().setPollInterval(-1);
      }
    });
    t.start();
    UI.getCurrent().setPollInterval(200);
  }

  protected void parseExperimentalDesign() {
    String id = ExperimentCodeFunctions.getInfoExperimentID(space, projectCode);
    Experiment experiment = openbis.getExperiment(id);

    if (experiment != null) {
      StudyXMLParser parser = new StudyXMLParser();

      try {
        JAXBElement<Qexperiment> design =
                parser.parseXMLString(experiment.getProperties().get("Q_EXPERIMENTAL_SETUP"));
        factorLabels = parser.getFactorLabels(design);
        factorsForLabelsAndSamples = parser.getFactorsForLabelsAndSamples(design);
      } catch (JAXBException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  private VerticalLayout computePopupComponent() {
    initDocx4J();
    P p = docxHelper.createParagraph("Summary for project " + projectCode, true, false, "40");
    wordMLPackage.getMainDocumentPart().addObject(p);

    VerticalLayout res = new VerticalLayout();
    res.setCaption("Summary");
    res.setSpacing(true);
    res.setMargin(true);

    // collect and connect everything (if samples exist)
    List<Sample> samples = openbis.getSamplesOfProject(projectCode);

    if (!projectCode.startsWith("Q") || projectCode.length() != 5) {
      success = false;
      return res;
    }

    if (samples.size() == 0) {
      success = false;
      return res;

    } else {
      List<Experiment> experiments = openbis.getExperimentsOfProjectByCode(projectCode);
      List<DataSet> datasets       = openbis.getDataSetsOfProject(projectCode);

      Map<String, List<PropertyType>> expTypeToProperties = new HashMap<>();
      Map<Experiment, List<Sample>> expToSamples = new HashMap<>();
      Map<String, List<DataSet>> sampIDToDS = new HashMap<>();
      Map<String, List<DataSet>> expIDToDS = new HashMap<>();
      Map<String, Experiment> expIDToExp = new HashMap<>();

      for (DataSet d : datasets) {
        String sampID = d.getSample().getIdentifier().toString();
        String expID = d.getExperiment().getIdentifier().toString();
        // fill sample id to datasets
        if (sampIDToDS.containsKey(sampID))
          sampIDToDS.get(sampID).add(d);
        else
          sampIDToDS.put(sampID, new ArrayList<>(Arrays.asList(d)));
        // fill experiment id to datasets
        if (expIDToDS.containsKey(expID))
          expIDToDS.get(expID).add(d);
        else
          expIDToDS.put(expID, new ArrayList<>(Arrays.asList(d)));
      }
      for (Experiment e : experiments)
        expIDToExp.put(e.getIdentifier().toString(), e);
      for (Sample s : samples) {
        Experiment exp = expIDToExp.get(s.getExperiment().getIdentifier().toString());
        if (expToSamples.containsKey(exp))
          expToSamples.get(exp).add(s);
        else
          expToSamples.put(exp, new ArrayList<>(Arrays.asList(s)));
      }
      // sort and display information
      Collections.sort(experiments, ExperimentTypeComparator.getInstance());
      for (Experiment e : experiments) {

        String type = e.getType().getCode();
        if (!expTypeToProperties.containsKey(type))
          expTypeToProperties.put(type,
              openbis.listPropertiesForType(openbis.getExperimentTypeByString(type)));
        if (expTypeTranslation.containsKey(type))
          type = expTypeTranslation.get(type);
        if (expToSamples.containsKey(e) && !type.equals("Project Details")) {// TODO here, project
                                                                             // details/attachments
                                                                             // information could be
                                                                             // added
          VerticalLayout section = new VerticalLayout();
          // create vaadin and docx section
          generateExperimentHeaderWithMetadata(e, section, wordMLPackage.getMainDocumentPart(),
              expTypeToProperties);

          Table sampleTable = generateSampleTable(expToSamples.get(e), sampIDToDS,
              expIDToDS.get(e.getIdentifier()), wordMLPackage.getMainDocumentPart());
          section.addComponent(sampleTable);
          res.addComponent(section);
        }
      }
      success = true;
    }
    addSummaryDownload(res);
    return res;
  }

  private String createTimeStamp() {
    Date date = new Date();
    return new Timestamp(date.getTime()).toString().split(" ")[1].replace(":", "").replace(".", "");
  }

  private void addSummaryDownload(VerticalLayout res) {
    Button download = new Button("Download Summary");
    res.addComponent(download);
    String docxPath = tmpFolder + projectCode + "_" + createTimeStamp() + ".docx";
    try {
      wordMLPackage.save(new File(docxPath));
    } catch (Docx4JException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    FileResource resource = new FileResource(new File(docxPath));
    FileDownloader docxDownload = new FileDownloader(resource);
    docxDownload.extend(download);
  }

  private void initDocx4J() {
    docxHelper = new Docx4jHelper();
    try {
      wordMLPackage = WordprocessingMLPackage.createPackage();
    } catch (InvalidFormatException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void generateExperimentHeaderWithMetadata(Experiment e, VerticalLayout section,
      MainDocumentPart mainDocumentPart, Map<String, List<PropertyType>> expTypeToProperties) {
    String expHeadline =
        expTypeTranslation.get(e.getType().getCode()) + " (" + e.getCode() + ")";

    // docx
    P p1 = docxHelper.createParagraph(expHeadline, true, false, "32");
    mainDocumentPart.addObject(p1);

    // view
    Panel expPanel = new Panel();
    expPanel.setStyleName(ValoTheme.PANEL_WELL);
    Label header = new Label(expHeadline);
    header.setStyleName(ValoTheme.LABEL_BOLD);
    section.addComponent(header);

    // all possible properties that can be set for this experiment type
    List<PropertyType> possibleProps = expTypeToProperties.get(e.getType().getCode());
    Map<String, String> properties = e.getProperties();
    // collect ready-to-display key-value pairs
    List<String> metadata = new ArrayList<String>();

    Date date = e.getRegistrationDate();
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");
    String formattedDate = dateFormat.format(date);
    metadata.add("Date: " + formattedDate);
    for (PropertyType p : possibleProps) {
      String code = p.getCode();
      if (properties.containsKey(code)) {
        String label = p.getLabel();
        String value = properties.get(code);
        if (label.equals("Notes")) {
          metadata.addAll(parseXMLNotes(value));
        } else {
          if (allMap.containsKey(value)) {
            value = allMap.get(value);
          }
          metadata.add(label + ": " + value);
        }
      }
    }

    Collections.sort(metadata);
    for (String line : metadata) {
      // vaadin
      Label l = new Label(line);
      section.addComponent(l);
      // docx
      mainDocumentPart.addObject(docxHelper.createParagraph(line, false, false, "32"));
    }
    expPanel.setContent(section);
  }

  private List<String> parseXMLNotes(String value) {
    List<String> res = new ArrayList<String>();
    List<Note> notes = new ArrayList<Note>();
    try {
      JAXBElement<Notes> jaxbelem = HistoryReader.parseNotes(value);
      notes = jaxbelem.getValue().getNote();
    } catch (JAXBException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    for (Note n : notes) {
      res.add(Utils.usernameToFullName(n.getUsername()) + " commented: " + n.getComment());
    }
    return res;
  }

  private Table generateSampleTable(List<Sample> samples, Map<String, List<DataSet>> sampIDToDS,
      List<DataSet> expDS, MainDocumentPart mainDocumentPart) {
    String tableHeadline = prettyNameMapper.getPrettyName(samples.get(0).getType().getCode()) + "s"; // plural
    mainDocumentPart.addObject(docxHelper.createParagraph(tableHeadline, false, false, "32"));

    Table table = new Table(tableHeadline);
    table.setStyleName(ValoTheme.TABLE_SMALL);

    List<String> factorsInTable = new ArrayList<>();
    List<String> header = new ArrayList<String>();
    List<List<String>> data = new ArrayList<List<String>>();
    int numDS = 0;
    if (expDS != null)
      numDS = expDS.size();
    if (samples.size() > 50) {
      header.add("Samples");
      header.add("Datasets");
      table.addContainerProperty("Samples", String.class, null);
      table.addContainerProperty("Datasets", String.class, null);
      List<String> row = new ArrayList<String>();

      row.add(Integer.toString(samples.size()));
      row.add(Integer.toString(numDS));
      table.addItem(row.toArray(new Object[row.size()]), 1);
      table.setPageLength(1);
      data.add(row); // test
    } else {
      header.add("Code");
      header.add("Name");
      header.add("External ID");
      table.addContainerProperty("Code", String.class, null);
      table.addContainerProperty("Name", String.class, null);
      table.addContainerProperty("External ID", String.class, null);
      table.setImmediate(true);
      // List<String> factorLabels = new ArrayList<String>();
      // List<Property> factors = new ArrayList<Property>();
      // int maxCols = 0;
      // Map<Sample, List<Property>> samplesToFactors = new HashMap<Sample, List<Property>>();
      Sample first = samples.get(0);
      boolean specialSet = false;
      String sType = first.getType().getCode();
      // if (mostInformative.getProperties().containsKey("Q_PROPERTIES")) {
      // XMLParser xmlParser = new XMLParser();
      //
      // for (Sample s : samples) {
      // String xml = s.getProperties().get("Q_PROPERTIES");
      // List<Property> curr = new ArrayList<Property>();
      // try {
      // curr = xmlParser.getAllPropertiesFromXML(xml);
      // samplesToFactors.put(s, curr);
      // } catch (JAXBException e) {
      // // TODO Auto-generated catch block
      // e.printStackTrace();
      // }
      // int size = curr.size();
      // if (size > maxCols) {
      // maxCols = size;
      // mostInformative = s;
      // }
      // }
      // factors = samplesToFactors.get(mostInformative);
      // }


      for (String label : factorLabels) {
        boolean used = false;
        for (Sample s : samples) {
          if (factorsForLabelsAndSamples.get(new ImmutablePair<>(label, s.getCode())) != null) {
            used = true;
            factorsInTable.add(label);
            break;
          }
        }
        if (used) {
          if (label.equals("species") && sType.equals("Q_BIOLOGICAL_ENTITY"))
            specialSet = true;
          else if (label.equals("tissues") && sType.equals("Q_BIOLOGICAL_SAMPLE"))
            specialSet = true;
          header.add(label);
          table.addContainerProperty(label, String.class, null);
        }
      }

      // for (int i = 0; i < factors.size(); i++) {
      // String l = factors.get(i).getLabel();
      // if (l.equals("species") && sType.equals("Q_BIOLOGICAL_ENTITY"))
      // specialSet = true;
      // else if (l.equals("tissues") && sType.equals("Q_BIOLOGICAL_SAMPLE"))
      // specialSet = true;
      //
      // int j = 2;
      // while (factorLabels.contains(l)) {
      // l = factors.get(i).getLabel() + " (" + Integer.toString(j) + ")";
      // j++;
      // }
      // factorLabels.add(l);
      // header.add(l);
      // table.addContainerProperty(l, String.class, null);
      // }
      List<String> typesToAdd = new ArrayList<String>();
      switch (sType) {
        case "Q_BIOLOGICAL_ENTITY":
          if (!specialSet)
            typesToAdd.add("Species");
          break;
        case "Q_BIOLOGICAL_SAMPLE":
          if (!specialSet)
            typesToAdd.add("Tissue");
          break;
        case "Q_TEST_SAMPLE":
          typesToAdd.add("Analyte");
          break;
        default:
          break;
      }
      if (numDS > 0)
        typesToAdd.add("Datasets");
      for (String typeToAdd : typesToAdd) {
        header.add(typeToAdd);
        table.addContainerProperty(typeToAdd, String.class, null);
      }
      for (Sample s : samples) {
        // Create the table row.
        Map<String, String> props = s.getProperties();
        List<String> row = new ArrayList<String>();

        row.add(s.getCode());
        row.add(props.get("Q_SECONDARY_NAME"));
        row.add(props.get("Q_EXTERNALDB_ID"));

        // List<Property> currFactors = new ArrayList<Property>();
        // int missing = 0;
        for (String label : factorsInTable) {
          Property f = factorsForLabelsAndSamples.get(new ImmutablePair<>(label, s.getCode()));
          if (f == null) {
            row.add("");
          } else {
            String v = f.getValue();
            if (f.hasUnit())
              v += " " + f.getUnit();
            row.add(v);
          }
        }

        // if (samplesToFactors.containsKey(s))
        // currFactors = samplesToFactors.get(s);
        // int missing = maxCols - currFactors.size();
        // for (Property f : currFactors) {
        // String v = f.getValue();
        // if (f.hasUnit())
        // v += " " + f.getUnit();
        // row.add(v);
        // }
        // for (int j = 0; j < missing; j++) {
        // row.add("");
        // }


        int dataCount = 0;
        if (sampIDToDS.containsKey(s.getIdentifier()))
          dataCount = sampIDToDS.get(s.getIdentifier()).size();
        switch (sType) {
          case "Q_BIOLOGICAL_ENTITY":
            if (!specialSet)
              row.add(allMap.get(props.get("Q_NCBI_ORGANISM")));
            break;
          case "Q_BIOLOGICAL_SAMPLE":
            if (!specialSet)
              row.add(parseTissue(s));
            break;
          case "Q_TEST_SAMPLE":
            row.add(props.get("Q_SAMPLE_TYPE"));
            break;
          default:
            break;
        }
        if (dataCount > 0)
          row.add(Integer.toString(dataCount));
        data.add(row);
        table.addItem(row.toArray(new Object[row.size()]), s);
      }
      table.setPageLength(samples.size());
    }
    // docx
    wordMLPackage.getMainDocumentPart().addObject(docxHelper.createTableWithContent(header, data));
    wordMLPackage.getMainDocumentPart().addObject(new Br());
    return table;
  }

  private String parseTissue(Sample s) {
    String tissue = s.getProperties().get("Q_PRIMARY_TISSUE");
    String other = s.getProperties().get("Q_TISSUE_DETAILED");
    if (tissue.equals("OTHER"))
      if (other == null || other.isEmpty())
        tissue = "Other";
      else
        tissue = other;
    else if (tissue.equals("CELL_CULTURE"))
      if (other == null || other.isEmpty())
        tissue = "Cell Culture";
      else
        tissue = other;
    if (allMap.containsKey(tissue))
      return allMap.get(tissue);
    else
      return tissue;
  }

  /**
   * returns ready to use summary component
   * 
   * @return
   */
  public Component getWindowContent() {
    return summaryComponent;
  }

  public boolean wasSuccessful() {
    return success;
  }

}
