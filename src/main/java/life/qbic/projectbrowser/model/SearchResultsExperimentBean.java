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
package life.qbic.projectbrowser.model;

import java.io.Serializable;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 */
public class SearchResultsExperimentBean implements Comparable<Object>, Serializable {

  private static final long serialVersionUID = 3968206160585657676L;
  private String experimentID;
  private String experimentName;
  private String queryString;
  private String matchedField;


  public SearchResultsExperimentBean(Experiment e, String query) {
    experimentID = e.getIdentifier().toString();
    experimentName = this.extractSampleProperty(e, "Q_SECONDARY_NAME");
    queryString = query;
    matchedField = findMatchedFields(e);
  }

  private String findMatchedFields(Experiment e) {

    StringBuilder strbuild = new StringBuilder();
    List<String> queryComponents = Arrays.asList(queryString.trim().split("\\s+"));

    Map<String, String> props = e.getProperties();

    String code = e.getCode();

    for (String str : queryComponents) {
      Pattern tmpPattern = Pattern.compile(str, Pattern.CASE_INSENSITIVE + Pattern.LITERAL);
      Matcher patternMatch = tmpPattern.matcher(code);

      if (patternMatch.find()) {
        strbuild.append("code: " + code + ",");
      }

      for (String k : props.keySet()) {
        if (k.equals("Q_SECONDARY_NAME")) {
          continue;
        }

        if (k.equals("Q_PROPERTIES")) {
          Map<String, String> xmlPropsMap = new HashMap<String, String>();
          try {
            xmlPropsMap = parseXMLString(props.get(k).trim());
          } catch (Exception ex) {
            // TODO Auto-generated catch block
            ex.printStackTrace();
          }

          for (String k2 : xmlPropsMap.keySet()) {
            patternMatch = tmpPattern.matcher(xmlPropsMap.get(k2).trim());

            if (patternMatch.find()) {
              if (!"".equals(strbuild.toString())) {
                strbuild.append(", ");
              }
              strbuild.append(k2 + ": " + xmlPropsMap.get(k2));
            }
          }
        } else {
          patternMatch = tmpPattern.matcher(props.get(k));

          if (patternMatch.find()) {
            if (!"".equals(strbuild.toString())) {
              strbuild.append(", ");
            }

            strbuild.append(k + ": " + props.get(k));
          }
        }
      }
    }

    String result = strbuild.toString();

    return result;
  }

  private Map<String, String> parseXMLString(String input) throws Exception {

    Map<String, String> xmlProps = new HashMap<String, String>();
    Document xmlDoc = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      InputSource is = new InputSource(new StringReader(input));
      xmlDoc = builder.parse(is);

    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    if (xmlDoc != null) {
      NodeList nodes = xmlDoc.getElementsByTagName("qcategorical");

      for (int i = 0; i < nodes.getLength(); ++i) {
        Element element = (Element) nodes.item(i);

        String factorName = element.getAttribute("label");
        String factorValue = element.getAttribute("value");
        // System.out.println("Name: " + factorName + " " + factorValue);
        xmlProps.put(factorName, factorValue);
      }

    }

    return xmlProps;
  }

  private String extractSampleProperty(Experiment e, String propertyKey) {
    Map<String, String> props = e.getProperties();
    String property = props.get(propertyKey);

    return property;
  }



  public String getExperimentID() {
    return experimentID;
  }

  public void setExperimentID(String experimentID) {
    this.experimentID = experimentID;
  }

  public String getExperimentName() {
    return experimentName;
  }

  public void setExperimentName(String experimentName) {
    this.experimentName = experimentName;
  }

  public String getMatchedField() {
    return matchedField;
  }

  public void setMatchedField(String matchedField) {
    this.matchedField = matchedField;
  }

  @Override
  public int compareTo(Object o) {
    // TODO Auto-generated method stub
    return 0;
  }

}
