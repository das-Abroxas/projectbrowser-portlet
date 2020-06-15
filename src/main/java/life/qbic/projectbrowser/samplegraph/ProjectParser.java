package life.qbic.projectbrowser.samplegraph;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;

import life.qbic.datamodel.samples.ISampleBean;
import life.qbic.datamodel.samples.SampleSummary;
import life.qbic.xml.properties.Property;
import life.qbic.xml.properties.PropertyType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBException;
import java.util.*;


/**
 *
 */
public class ProjectParser {

  private static final Logger logger = LogManager.getLogger(ProjectParser.class);

  private Set<String> codesWithDatasets, factorLabels;
  private Set<String> validLeafs =
          new HashSet<>(Arrays.asList("Q_TEST_SAMPLE", "Q_MHC_LIGAND_EXTRACT"));
  private Set<String> validSamples =
          new HashSet<>(Arrays.asList("Q_TEST_SAMPLE", "Q_MHC_LIGAND_EXTRACT",
                  "Q_BIOLOGICAL_ENTITY", "Q_BIOLOGICAL_SAMPLE"));

  private Map<String, String> taxMap, tissueMap;
  private Map<String, Sample> sampCodeToSamp;
  private Map<String, List<DataSet>> sampCodeToDS;
  private Map<Pair<String, String>, Property> factorsForLabelsAndSamples;


  public ProjectParser(Map<String, String> taxMap, Map<String, String> tissueMap) {
    this.taxMap = taxMap;
    this.tissueMap = tissueMap;
  }

  private boolean collectCodesOfDatasetsAttachedToSamples(List<ISampleBean> samples,
      Set<String> nodeCodes, int maxDepth) {
    boolean hasDatasets = false;

    if (maxDepth >= 0) {
      for (ISampleBean s : samples) {
        OpenbisSampleAdapter sample;

        try {
          sample = (OpenbisSampleAdapter) s;
        } catch (Exception e) {
          return false;
        }
        hasDatasets = false;
        String code = s.getCode();
        if (sampCodeToDS.containsKey(code)) {
          hasDatasets = true;
        }
        hasDatasets |=
            collectCodesOfDatasetsAttachedToSamples(sample.getChildren(), nodeCodes, maxDepth - 1);
        if (hasDatasets && validSamples.contains(s.getType())) {
          nodeCodes.add(code);
        }
      }
    }
    return hasDatasets;
  }

  // add percentage of expected datasets that are found in the data store
  private void addDataSetCount(Collection<SampleSummary> summaries) {
    int maxDepth = 1; // maximum child levels from a sample that datasets count for
    for (SampleSummary node : summaries) {
      if (validLeafs.contains(node.getSampleType())) {
        Set<String> nodeCodes = new HashSet<String>();
        collectCodesOfDatasetsAttachedToSamples(node.getSamples(), nodeCodes, maxDepth);
        int expected = node.getSamples().size();
        int numData = nodeCodes.size();
        if (numData > expected) {
          expected = numData;
        }
        node.setMeasuredPercent(numData * 100 / expected);

        codesWithDatasets.addAll(nodeCodes);
      }
    }
  }

  private Property getFactorOfSampleOrNull(final Sample s, final String factorLabel)
          throws JAXBException {
    Pair<String, String> key = new ImmutablePair<>(factorLabel, s.getCode());
    return factorsForLabelsAndSamples.get(key);
  }

  public StructuredExperiment parseSamplesBreadthFirst(List<Sample> samples, List<DataSet> datasets,
                                                       Set<String> factorLabels, Map<Pair<String, String>,
                                                       Property> factorsForLabelsAndSamples)
                                                       throws JAXBException {
    this.factorLabels = factorLabels;
    this.factorsForLabelsAndSamples = factorsForLabelsAndSamples;
    sampCodeToDS = new HashMap<>();
    codesWithDatasets = new HashSet<>();

    for (DataSet d : datasets) {
      // String code = d.getSample().getIdentifier().toString().split("/")[2];
      String code = d.getSample().getCode();

      if (sampCodeToDS.containsKey(code)) {
        sampCodeToDS.get(code).add(d);
      } else {
        sampCodeToDS.put(code, new ArrayList<>(Arrays.asList(d)));
      }
    }

    // this.xmlParser = new XMLParser();
    Map<String, List<SampleSummary>> factorsToSamples = new HashMap<>();

    Set<String> knownFactors = new HashSet<>();
    knownFactors = factorLabels;
    sampCodeToSamp = new HashMap<>();
    knownFactors.add("None");

    Queue<Sample> samplesBreadthFirst = new LinkedList<>();
    Set<Sample> visited = new HashSet<>();
    // init
    for (Sample sample : samples) {
      sampCodeToSamp.put(sample.getCode(), sample);
      String type = sample.getType().getCode();

      if (validSamples.contains(type)) {
        if (sample.getParents().isEmpty()) {
          samplesBreadthFirst.add(sample);
        }
      }
    }

    //ToDo: Maybe fill queue (then copy queue) and map to parents outside this loop
    Map<String, Integer> idCounterPerLabel = new HashMap<>();
    Map<String, Map<Sample, Set<SampleSummary>>> sampleToParentNodesPerLabel = new HashMap<>();
    Map<String, Set<SampleSummary>> nodesForFactorPerLabel = new HashMap<>();

    for (String label : knownFactors) {
      idCounterPerLabel.put(label, 1);
      sampleToParentNodesPerLabel.put(label, new HashMap<>());
      nodesForFactorPerLabel.put(label, new LinkedHashSet<>());
    }

    // breadth first queue loop
    while (!samplesBreadthFirst.isEmpty()) {
      Sample s = samplesBreadthFirst.poll();
      String type = s.getType().getCode();

      if (validSamples.contains(type) && !visited.contains(s)) {
        visited.add(s);
        List<Sample> children = s.getChildren();

        for (String label : knownFactors) {
          // compute new summary
          Map<Sample, Set<SampleSummary>> sampleToParentNodes = sampleToParentNodesPerLabel.get(label);
          Set<SampleSummary> parentSummaries = sampleToParentNodes.get(s);

          if (parentSummaries == null) {
            parentSummaries = new LinkedHashSet<>();
          }

          SampleSummary node =
              createSummary(s, parentSummaries, label, idCounterPerLabel.get(label));
          // check for hashcode and add current sample s if node exists
          boolean exists = false;
          for (SampleSummary oldNode : nodesForFactorPerLabel.get(label)) {
            if (oldNode.equals(node)) {
              oldNode.addSample(new OpenbisSampleAdapter(s));
              exists = true;
              node = oldNode;
            }
          }
          if (!exists) {
            idCounterPerLabel.put(label, idCounterPerLabel.get(label) + 1);
          }
          // adds node if not already contained in set
          Set<SampleSummary> theseNodes = nodesForFactorPerLabel.get(label);
          theseNodes.add(node);
          nodesForFactorPerLabel.put(label, theseNodes);
          // add this id to parents' child ids
          for (SampleSummary parentSummary : parentSummaries) {
            parentSummary.addChildID(node.getId());
          }

          for (Sample c : children) {
            for (Sample tmp : samples) {
              if (tmp.getIdentifier().equals(c.getIdentifier())) {
                c = tmp;
                break;
              }
            }
            samplesBreadthFirst.add(c);

            if (!sampleToParentNodes.containsKey(c)) {
              sampleToParentNodes.put(c, new LinkedHashSet<>());
            }
            sampleToParentNodes.get(c).add(node);
            sampleToParentNodesPerLabel.put(label, sampleToParentNodes);
          }
        }
      }
    }
    for (String label : nodesForFactorPerLabel.keySet()) {
      Set<SampleSummary> nodes = nodesForFactorPerLabel.get(label);
      addDataSetCount(nodes);
      factorsToSamples.put(label, new ArrayList<>(nodes));
    }
    return new StructuredExperiment(factorsToSamples);
  }

  /**
   * New "sample to bucket" function. Creates new summaries from
   * sample metadata in reference to parent summaries and experimental factor.
   * @param s
   * @param parents
   * @param label
   * @param currentID
   * @return
   * @throws JAXBException
   */
  private SampleSummary createSummary(Sample s, Set<SampleSummary> parents, String label, int currentID)
          throws JAXBException {

    /*
     * Name: Should be the visible discriminating factor between nodes
     * 1. Contains the source, if the source is not the selected factor (e.g. tissues)
     * 2. Contains the selected factor's value, except
     * a) If parent sample has the same factor value
     * b) If it has no factor
     * Factor: The current selected factor object. If none exists, parents' sources are used.
     */

    // The name alone is not enough to discriminate between different nodes!
    //   (e.g. different parent nodes, same child node name)
    String type = s.getType().getCode();
    String source = "unknown";
    Property factor = getFactorOfSampleOrNull(s, label);
    boolean newFactor = true;
    Set<String> parentSources = new HashSet<>();
    Set<Integer> parentIDs = new HashSet<>();

    for (SampleSummary parentSum : parents) {
      parentIDs.add(parentSum.getId());
      String factorVal = parentSum.getFactorValue();

      if (factorVal != null && !factorVal.isEmpty())
        newFactor = false;

      parentSources.add(parentSum.getSource());
    }

    if (factor == null) {
      factor = new Property("parents", StringUtils.join(parentSources, "+"), PropertyType.Factor);
      newFactor = false;
    }
    String value = newFactor ? factor.getValue() : "";

    Map<String, String> props = s.getProperties();
    switch (type) {
      case "Q_BIOLOGICAL_ENTITY":
        source = taxMap.get(props.get("Q_NCBI_ORGANISM"));
        value = source + ' ' + value;
        break;

      case "Q_BIOLOGICAL_SAMPLE":
        source = tissueMap.get(props.get("Q_PRIMARY_TISSUE"));
        boolean isCellLine = source.equals("Cell Line");

        if (source.equals("Other") || isCellLine) {
          String detail = props.get("Q_TISSUE_DETAILED");

          if (detail != null && !detail.isEmpty()) {
            source = detail;
          }
        }
        value = !newFactor || source.equals(value) ? source : source + ' ' + value;
        break;

      case "Q_TEST_SAMPLE":
        source = props.get("Q_SAMPLE_TYPE");
        value = source + ' ' + value;
        break;

      case "Q_MHC_LIGAND_EXTRACT":
        source = props.get("Q_MHC_CLASS");
        value = source;
        break;
    }

    boolean leaf = true;
    for (Sample c : s.getChildren()) {
      if (validSamples.contains(c.getType().getCode())) {
        leaf = false;
        break;
      }
    }

    return new SampleSummary(currentID, parentIDs,
        new ArrayList<>(Arrays.asList(new OpenbisSampleAdapter(s))), factor.getValue(),
        tryShortenName(value, s), type, leaf);
  }

  private String tryShortenName(String key, Sample s) {
    switch (s.getType().getCode()) {
      case "Q_BIOLOGICAL_ENTITY":
        return key;
      case "Q_BIOLOGICAL_SAMPLE":
        return key;
      case "Q_TEST_SAMPLE":
        String type = s.getProperties().get("Q_SAMPLE_TYPE");
        return key.replace(type, "") + " " + shortenInfo(type);
      case "Q_MHC_LIGAND_EXTRACT":
        return s.getProperties().get("Q_MHC_CLASS").replace("_", " ").replace("CLASS", "Class");
    }
    return key;
  }

  private String shortenInfo(String info) {
    switch (info) {
      case "CARBOHYDRATES":
        return "Carbohydrates";
      case "SMALLMOLECULES":
        return "Smallmolecules";
      case "DNA":
        return "DNA";
      case "RNA":
        return "RNA";
      default:
        return WordUtils.capitalizeFully(info.replace("_", " "));
    }
  }

  public Sample getSampleFromCode(String code) {
    return sampCodeToSamp.get(code);
  }

  public List<DataSet> getDatasetsOfCode(String code) {
    return sampCodeToDS.get(code);
  }

  public boolean codeHasDatasets(String code) {
    return codesWithDatasets.contains(code);
  }

}
