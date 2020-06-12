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
package life.qbic.projectbrowser.model;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import life.qbic.projectbrowser.helpers.UglyToPrettyNameMapper;

import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import life.qbic.xml.properties.Property;
// import life.qbic.xml.manager.XMLParser;
import life.qbic.xml.properties.Qproperties;

import com.vaadin.data.util.BeanItemContainer;

public class SampleBean implements Comparable<Object>, Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -61486023394904818L;
  private String id;
  private String code;
  private String type;
  private String prettyType;
  // Map containing parents of the sample and the corresponding sample types
  // private Map<String, String> parents;
  private List<Sample> parents;
  private List<Sample> children;
  private BeanItemContainer<DatasetBean> datasets;
  private Date lastChangedDataset;
  private Map<String, String> properties;
  private Map<String, String> typeLabels;

  private UglyToPrettyNameMapper prettyNameMapper = new UglyToPrettyNameMapper();
  private List<Property> xmlProperties;


  public SampleBean(String id, String code, String type, List<Sample> parents,
      BeanItemContainer<DatasetBean> datasets, Date lastChangedDataset,
      Map<String, String> properties, Map<String, String> typeLabels, List<Sample> children) {
    super();
    this.id = id;
    this.code = code;
    this.type = type;
    this.prettyType = prettyNameMapper.getPrettyName(type);
    this.parents = parents;
    this.datasets = datasets;
    this.lastChangedDataset = lastChangedDataset;
    this.properties = properties;
    this.typeLabels = typeLabels;
    this.children = children;
  }

  public SampleBean() {

  }



  public String getId() {
    return id;
  }



  public void setId(String id) {
    this.id = id;
  }



  public String getCode() {
    return code;
  }



  public void setCode(String code) {
    this.code = code;
  }



  public String getType() {
    return type;
  }



  public void setType(String type) {
    this.type = type;
    this.prettyType = prettyNameMapper.getPrettyName(type);
  }



  public List<Sample> getParents() {
    return parents;
  }



  public void setParents(List<Sample> parents) {
    this.parents = parents;
  }



  public BeanItemContainer<DatasetBean> getDatasets() {
    return datasets;
  }



  public void setDatasets(BeanItemContainer<DatasetBean> datasets) {
    this.datasets = datasets;
  }



  public Date getLastChangedDataset() {
    return lastChangedDataset;
  }



  public void setLastChangedDataset(Date lastChangedDataset) {
    this.lastChangedDataset = lastChangedDataset;
  }



  public Map<String, String> getProperties() {
    return properties;
  }



  public void setProperties(Map<String, String> properties) {
    this.properties = properties;
  }

  public Map<String, String> getTypeLabels() {
    return typeLabels;
  }

  public void setTypeLabels(Map<String, String> typeLabels) {
    this.typeLabels = typeLabels;
  }



  @Override
  public int compareTo(Object o) {
    return id.compareTo(((SampleBean) o).getId());
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SampleBean) {
      SampleBean b = (SampleBean) o;
      return id.equals(b.getId());
    } else
      return false;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  public String getParentsFormattedString() {
    String parentsHeader = "This sample has been derived from ";
    String parentsBottom = "<ul>";

    if (this.getParents() == null || this.getParents().isEmpty()) {
      return parentsHeader += "None";
    } else {
      for (Sample sample : this.getParents()) {
        parentsBottom += "<li><b>" + sample.getCode() + "</b> ("
            + prettyNameMapper.getPrettyName(sample.getType().getCode()) + ") </li>";
      }
      parentsBottom += "</ul>";

      return parentsHeader + parentsBottom;
    }
  }

  public String generatePropertiesFormattedString() {
    String propertiesBottom = "<ul> ";

    Iterator<Entry<String, String>> it = this.getProperties().entrySet().iterator();
    while (it.hasNext()) {
      Entry pairs = it.next();
      if (pairs.getKey().equals("Q_PROPERTIES") || pairs.getKey().equals("Q_NOTES")) {
        continue;
      } else {
        propertiesBottom +=
            "<li><b>" + (typeLabels.get(pairs.getKey()) + ":</b> " + pairs.getValue() + "</li>");
      }
    }
    propertiesBottom += "</ul>";

    return propertiesBottom;
  }

  public String generateXMLPropertiesFormattedString() throws JAXBException {


    String xmlPropertiesString = "<ul> ";

    for (Property p : xmlProperties) {
      xmlPropertiesString += "<li><b>" + p.getLabel() + ":</b> " + p.getValue();
      if (p.hasUnit()) {
        xmlPropertiesString += " " + p.getUnit();
      }
      xmlPropertiesString += "</li>";
    }
    return xmlPropertiesString;

    // String xmlPropertiesBottom = "<ul> ";
    //
    // Iterator<Entry<String, String>> it = this.getProperties().entrySet().iterator();
    // while (it.hasNext()) {
    // Entry pairs = it.next();
    // if (pairs.getKey().equals("Q_PROPERTIES")) {
    // XMLParser xmlParser = new XMLParser();
    // JAXBElement<Qproperties> xmlProperties =
    // xmlParser.parseXMLString(pairs.getValue().toString());
    // Map<String, String> xmlPropertiesMap = xmlParser.getMapOfProperties(xmlProperties);
    //
    // for (Object o : xmlPropertiesMap.entrySet()) {
    // Entry pairsProperties = (Entry) o;
    //
    // xmlPropertiesBottom += "<li><b>"
    // // + (typeLabels.get(pairsProperties.getKey()) + ":</b> "
    // + (pairsProperties.getKey() + ":</b> " + pairsProperties.getValue() + "</li>");
    // }
    // break;
    // }
    // }
    // return xmlPropertiesBottom;
  }

  public List<Sample> getChildren() {
    return children;
  }

  public void setChildren(List<Sample> children) {
    this.children = children;
  }

  public String getPrettyType() {
    return prettyType;
  }

  public void setPrettyType(String prettyType) {
    this.prettyType = prettyType;
  }

  public void setComplexProperties(List<Property> complexProps) {
    xmlProperties = complexProps;
  }


}
