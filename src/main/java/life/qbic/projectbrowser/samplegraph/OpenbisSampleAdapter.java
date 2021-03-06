package life.qbic.projectbrowser.samplegraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import life.qbic.datamodel.samples.ISampleBean;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;

public class OpenbisSampleAdapter implements ISampleBean {
  
  private Sample openbisSample;
  private Logger logger = LogManager.getLogger(OpenbisSampleAdapter.class);
  
  public OpenbisSampleAdapter(Sample sample) {
   this.openbisSample = sample;
  }
  
  public Sample getSample() {
    return openbisSample;
  }
  
  public List<ISampleBean> getChildren() {
    List<ISampleBean> res = new ArrayList<ISampleBean>();
    for(Sample s : openbisSample.getChildren()) {
      res.add(new OpenbisSampleAdapter(s));
    }
    return res;
  }

  @Override
  public String getSecondaryName() {
    return openbisSample.getProperties().get("Q_SECONDARY_NAME");
  }

  @Override
  public String getCode() {
    return openbisSample.getCode();
  }

  @Override
  public List<String> getParentIDs() {
    List<String> ids = new ArrayList<String>();
    for(Sample p : openbisSample.getParents())
      ids.add(p.getCode());
    return ids;
  }

  @Override
  public String getType() {
    return openbisSample.getSampleTypeCode();
  }

  @Override
  public boolean hasParents() {
    return !openbisSample.getParents().isEmpty();
  }

  @Override
  public String getProject() {
    getCode().substring(0, 5);
    return null;
  }

  @Override
  public String getSpace() {
    return openbisSample.getSpaceCode();
  }

  @Override
  public String getExperiment() {
    String res = openbisSample.getExperimentIdentifierOrNull();
    if(res!=null) {
      String[] splt = res.split("/");
      res = splt[splt.length-1];
    }
    return res;
  }

  @Override
  public void setProject(String project) {
    logger.warn("setting project not possible for wrapped class Sample.");
  }

  @Override
  public void setSpace(String space) {
    logger.warn("setting space not possible for wrapped class Sample.");
  }

  @Override
  public void setExperiment(String experiment) {
    logger.warn("setting experiment not possible for wrapped class Sample.");
  }

  @Override
  public Map<String, Object> getMetadata() {
    return new HashMap<String,Object>(openbisSample.getProperties());
  }

  @Override
  public ISampleBean copy() {
    return new OpenbisSampleAdapter(openbisSample);
  }

}
