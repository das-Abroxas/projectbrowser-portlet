package life.qbic.projectbrowser.helpers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import life.qbic.projectbrowser.helpers.BarcodeFunctions;

/**
 * Compares Experiments by their type, meaning that types can be sorted by experimental design
 * hierarchy
 * 
 * @author Andreas Friedrich
 *
 */
public class ExperimentTypeComparator implements Comparator<Experiment> {

  private static final ExperimentTypeComparator instance = new ExperimentTypeComparator();

  public static ExperimentTypeComparator getInstance() {
    return instance;
  }

  private ExperimentTypeComparator() {}

  private final Map<String, Integer> hierarchy = new HashMap<String, Integer>() {
    {
      // basics
      put("Q_EXPERIMENTAL_DESIGN", 1);
      put("Q_SAMPLE_EXTRACTION", 2);
      put("Q_SAMPLE_PREPARATION", 3);
      put("Q_MHC_LIGAND_EXTRACTION", 4);

      // measurements
      put("Q_NGS_FLOWCELL_RUN", 10);
      put("Q_NGS_SINGLE_SAMPLE_RUN", 11);
      put("Q_NGS_MEASUREMENT", 12);
      put("Q_MICROARRAY_MEASUREMENT", 13);
      put("Q_MS_MEASUREMENT", 14);

      // external QC
      put("Q_EXT_MS_QUALITYCONTROL", 20);
      put("Q_EXT_NGS_QUALITYCONTROL", 21);

      // QC at QBiC
      put("Q_WF_MA_QUALITYCONTROL", 30);
      put("Q_WF_MS_QUALITYCONTROL", 31);
      put("Q_WF_NGS_QUALITYCONTROL", 32);

      // actually doing something with the data
      put("Q_NGS_MAPPING", 40);
      put("Q_NGS_VARIANT_CALLING", 41);
      put("Q_NGS_HLATYPING", 42);
      put("Q_NGS_EPITOPE_PREDICTION", 43);
      put("Q_NGS_IMMUNE_MONITORING", 44);
      put("Q_WF_MS_LIGANDOMICS_ID", 45);
      put("Q_WF_MS_LIGANDOMICS_QC", 46);

      // doing something with the data using QBiC workflows
      put("Q_WF_NGS_MERGE", 50);
      put("Q_WF_MS_MAXQUANT", 51);
      put("Q_WF_MS_PEPTIDEID", 52);
      put("Q_WF_NGS_EPITOPE_PREDICTION", 53);
      put("Q_WF_NGS_HLATYPING", 54);
      put("Q_WF_NGS_RNA_EXPRESSION_ANALYSIS", 55);
      put("Q_WF_NGS_VARIANT_ANNOTATION", 56);
      put("Q_WF_NGS_VARIANT_CALLING", 57);
      put("Q_WF_NGS_MAPPING", 58);
      put("Q_WF_MS_INDIVIDUALIZED_PROTEOME", 59);
    };
  };

  @Override
  public int compare(Experiment o1, Experiment o2) {
    String t1 = o1.getType().getCode();
    String t2 = o2.getType().getCode();
    int i1 = Integer.MAX_VALUE;
    int i2 = Integer.MAX_VALUE;
    if (hierarchy.containsKey(t1))
      i1 = hierarchy.get(t1);
    if (hierarchy.containsKey(t2))
      i2 = hierarchy.get(t2);
    int base = i1 - i2;
    if (base == 0)
      return compareNatural(o1.getCode(), o2.getCode());
    else
      return base;
  }

  public static int compareCodeByNumberSuffix(String a, String b) {
    if (BarcodeFunctions.isExperimentCode(a) && BarcodeFunctions.isExperimentCode(b)) {
      int aNum = Integer.parseInt(a.substring(6));
      int bNum = Integer.parseInt(b.substring(6));
      return aNum - bNum;
    } else
      return compareNatural(a, b);
  }

  public static int compareNatural(String a, String b) {
    int la = a.length();
    int lb = b.length();
    int ka = 0;
    int kb = 0;
    while (true) {
      if (ka == la)
        return kb == lb ? 0 : -1;
      if (kb == lb)
        return 1;
      if (a.charAt(ka) >= '0' && a.charAt(ka) <= '9' && b.charAt(kb) >= '0'
          && b.charAt(kb) <= '9') {
        int na = 0;
        int nb = 0;
        while (ka < la && a.charAt(ka) == '0')
          ka++;
        while (ka + na < la && a.charAt(ka + na) >= '0' && a.charAt(ka + na) <= '9')
          na++;
        while (kb < lb && b.charAt(kb) == '0')
          kb++;
        while (kb + nb < lb && b.charAt(kb + nb) >= '0' && b.charAt(kb + nb) <= '9')
          nb++;
        if (na > nb)
          return 1;
        if (nb > na)
          return -1;
        if (ka == la)
          return kb == lb ? 0 : -1;
        if (kb == lb)
          return 1;

      }
      if (a.charAt(ka) != b.charAt(kb))
        return a.charAt(ka) - b.charAt(kb);
      ka++;
      kb++;
    }
  }

  public int compareAlphanumerical(String object1, String object2) {
    final Pattern p = Pattern.compile("^\\d+");

    Matcher m = p.matcher(object1);
    Integer number1 = null;
    if (!m.find()) {
      return object1.compareTo(object2);
    } else {
      Integer number2 = null;
      number1 = Integer.parseInt(m.group());
      m = p.matcher(object2);
      if (!m.find()) {
        return object1.compareTo(object2);
      } else {
        number2 = Integer.parseInt(m.group());
        int comparison = number1.compareTo(number2);
        if (comparison != 0) {
          return comparison;
        } else {
          return object1.compareTo(object2);
        }
      }
    }
  }

}
