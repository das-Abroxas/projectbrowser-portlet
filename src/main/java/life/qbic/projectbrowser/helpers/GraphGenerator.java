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
package life.qbic.projectbrowser.helpers;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.property.PropertyType;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.SampleType;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.vaadin.server.StreamResource;
import com.vaadin.server.StreamResource.StreamSource;

import life.qbic.openbis.openbisclient.OpenBisClient;
import life.qbic.projectbrowser.model.ProjectBean;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class GraphGenerator {

  private String url;
  private StreamResource res;

  public StreamResource getRes() {
    return res;
  }

  public void setRes(StreamResource res) {
    this.res = res;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public GraphGenerator(ProjectBean projectBean, OpenBisClient openBisClient) throws IOException {
    mxGraph graph = new mxGraph();
    Object parent = graph.getDefaultParent();
    graph.getModel().beginUpdate();

    mxStylesheet stylesheet = graph.getStylesheet();
    Hashtable<String, Object> style = new Hashtable<>();
    style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
    style.put(mxConstants.STYLE_OPACITY, 100);
    style.put(mxConstants.STYLE_FONTCOLOR, "#ffffff");
    style.put(mxConstants.STYLE_DIRECTION, "east");
    stylesheet.putCellStyle("ROUNDED", style);

    double width = 120.0;
    double height = 40.0;


    try {
      // could be done more efficiently with SearchCriteria (at least I hope so)
      Map<String, Integer> sample_count = new HashMap<>();
      List<Sample> all_project_samples = openBisClient.getSamplesOfProject(projectBean.getId());

      // count the different sample types
      Integer num_measurement_samples = 0;

      List<Sample> samps = new ArrayList<>();

      for (Sample s : all_project_samples) {
        String key = s.getType().getCode();

        if (sample_count.containsKey(key)) {
          sample_count.put(key, sample_count.get(key) + 1);
        } else {
          sample_count.put(key, 1);
        }

        if (key.equals("Q_TEST_SAMPLE")) {
          List<Sample> tmp = s.getParents();
          num_measurement_samples += tmp.size();
        }

        if (key.equals("Q_BIOLOGICAL_ENTITY")) {
          samps.add(s);
        }
      }

      if (sample_count.containsKey("Q_BIOLOGICAL_ENTITY")) {
        Object dummy_node_level1 =
            graph.insertVertex(parent, null, sample_count.get("Q_BIOLOGICAL_ENTITY") + "\n"
                + "biological entities", 20, 20, width, height,
                "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");


        if (sample_count.containsKey("Q_BIOLOGICAL_SAMPLE")) {
          Object dummy_node_level2 =
              graph.insertVertex(parent, null, sample_count.get("Q_BIOLOGICAL_SAMPLE") + "\n"
                  + "biological samples", 20, 20, width + 50, height,
                  "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");
          graph.insertEdge(parent, null, "", dummy_node_level1, dummy_node_level2,
              "strokeWidth=0;strokeColor=#FFFFFF");

          if (sample_count.containsKey("Q_TEST_SAMPLE")) {
            Object dummy_node_level3 =
                graph.insertVertex(parent, null, sample_count.get("Q_TEST_SAMPLE") + "\n"
                    + "test samples", 20, 20, width + 50, height,
                    "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");
            graph.insertEdge(parent, null, "", dummy_node_level2, dummy_node_level3,
                "strokeWidth=0;strokeColor=#FFFFFF");

            if (num_measurement_samples > 0) {
              Object dummy_node_level4 =
                  graph.insertVertex(parent, null, num_measurement_samples + "\n"
                      + "measured samples", 20, 20, width + 50, height,
                      "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");

              graph.insertEdge(parent, null, "", dummy_node_level3, dummy_node_level4,
                  "strokeWidth=0;strokeColor=#FFFFFF");
            }
          }
        }
      }

      List<PropertyType> bioSampleProperties =
              openBisClient.listPropertiesForType(
                  openBisClient.getSampleTypeByString("Q_BIOLOGICAL_SAMPLE"));
      List<PropertyType> testSampleProperties =
          openBisClient.listPropertiesForType(
                  openBisClient.getSampleTypeByString("Q_TEST_SAMPLE"));


      for (Sample s : samps) {
        String species =
            (s.getProperties().containsKey("Q_NCBI_ORGANISM")) ? s.getProperties().get(
                "Q_NCBI_ORGANISM") : "";

        // old get species code
        /*
         * for (Map.Entry<String, String> entry : s.getProperties().entrySet()) { if
         * (entry.getKey().equals("Q_NCBI_ORGANISM")) { species = entry.getValue(); } }
         */

        // TODO get Labels of properties.... for organism
        // List<PropertyType> completeProperties =
        // dh.openBisClient.listPropertiesForType(dh.openBisClient.getSampleTypeByString(s.getType()));


        Object mother_node =
            graph.insertVertex(parent, s.getIdentifier().toString(),
                String.format("%s\n%s", s.getCode(), species), 20, 20, width, height,
                "ROUNDED;strokeColor=#ffffff;fillColor=#0365C0");

        List<Sample> children = new ArrayList<>(s.getChildren());


        for (Sample c : children) {
          if (c.getType().getCode().equals("Q_BIOLOGICAL_SAMPLE")) {
            String primaryTissue = "";
            String secondaryName = "";

            for (PropertyType pType : bioSampleProperties) {
              if (pType.getCode().equals("Q_PRIMARY_TISSUE")) {

                primaryTissue =
                    openBisClient.getCVLabelForProperty(
                        pType,
                        openBisClient.getSampleByIdentifier(c.getIdentifier().toString()).getProperties()
                            .get("Q_PRIMARY_TISSUE"));
                String secID =
                    openBisClient.getSampleByIdentifier(c.getIdentifier().toString()).getProperties()
                        .get("Q_SECONDARY_NAME");

                if (secID != null) {
                  secondaryName = secID.replace("nan", "");
                }
              }
            }

            Object daughter_node =
                graph.insertVertex(parent, c.getPermId().toString(),
                    String.format("%s\n%s\n%s", c.getCode(), primaryTissue, secondaryName), 20, 20,
                    width + 50, height + 20, "ROUNDED;strokeColor=#ffffff;fillColor=#51A7F9");
            graph.insertEdge(parent, null, "", mother_node, daughter_node);

            List<Sample> grandchildren = new ArrayList<>(c.getChildren());

            for (Sample gc : grandchildren) {
              if (gc.getType().getCode().equals("Q_TEST_SAMPLE")) {
                String testSampleType = "";
                String testSecID = "";

                for (PropertyType pType : testSampleProperties) {
                  if (pType.getCode().equals("Q_SAMPLE_TYPE")) {

                    testSampleType =
                        openBisClient.openbisCodeToString(
                                openBisClient.getCVLabelForProperty(pType, gc.getProperties().get("Q_SAMPLE_TYPE")));
                    testSecID = gc.getProperties().get("Q_SECONDARY_NAME");

                    if (testSecID != null) {
                      testSecID = testSecID.replace("nan", "");
                    }
                  }
                }

                Object granddaughter_node =
                    graph.insertVertex(parent, gc.getPermId().toString(),
                        String.format("%s\n%s\n%s", gc.getCode(), testSampleType, testSecID), 20,
                        20, width + 50, height + 20,
                        "ROUNDED;strokeColor=#ffffff;fillColor=#70BF41");
                graph.insertEdge(parent, null, "", daughter_node, granddaughter_node);

                List<Sample> grandgrandchildren = new ArrayList<>(gc.getChildren());

                for (Sample ggc : grandgrandchildren) {
                  String measuredSecID = "";
                  measuredSecID = gc.getProperties().get("Q_SECONDARY_NAME");

                  if (measuredSecID != null) {
                    measuredSecID = measuredSecID.replace("nan", "");
                  }

                  Object grandgranddaughter_node =
                      graph.insertVertex(parent, ggc.getPermId().toString(),
                          String.format("%s\n%s\n%s", ggc.getCode(),
                              openBisClient.openbisCodeToString(ggc.getType().getCode()),
                              measuredSecID), 20, 20, width + 50, height + 20,
                          "ROUNDED;strokeColor=#ffffff;fillColor=#F39019");
                  graph.insertEdge(parent, null, "", granddaughter_node, grandgranddaughter_node);
                }
              }
            }
          }
        }
      }

      mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, SwingConstants.WEST);
      layout.setDisableEdgeStyle(true);
      layout.setInterRankCellSpacing(100);
      layout.execute(graph.getDefaultParent()); // Run the layout on the facade.\n

      BufferedImage image =
          mxCellRenderer.createBufferedImage(graph, null, 1, Color.WHITE, true, null);

      final ByteArrayOutputStream bas = new ByteArrayOutputStream();

      if (image != null) {
        ImageIO.write(image, "PNG", bas);
        this.res = showFile(projectBean.getId(), "PNG", bas);
      } else {
        this.res = null;
      }
    }// end try
    finally {
      graph.getModel().endUpdate();
    }
  }


  private StreamResource showFile(final String name, final String type,
                                  final ByteArrayOutputStream bas) {
    // resource for serving the file contents
    final StreamSource streamSource = new StreamSource() {
      private static final long serialVersionUID = 6632340984219486654L;

      @Override
      public InputStream getStream() {
        if (bas != null) {
          final byte[] byteArray = bas.toByteArray();
          return new ByteArrayInputStream(byteArray);
        }
        return null;
      }
    };
    StreamResource resource = new StreamResource(streamSource, name);
    return resource;
  }

  public GraphGenerator(List<Sample> samples, Map<String, SampleType> types,
                        OpenBisClient openBisClient, String projectId)
          throws IOException {

    mxGraph graph = new mxGraph();
    Object parent = graph.getDefaultParent();
    graph.getModel().beginUpdate();

    mxStylesheet stylesheet = graph.getStylesheet();
    Hashtable<String, Object> style = new Hashtable<String, Object>();
    style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
    style.put(mxConstants.STYLE_OPACITY, 100);
    style.put(mxConstants.STYLE_FONTCOLOR, "#ffffff");
    style.put(mxConstants.STYLE_DIRECTION, "east");
    stylesheet.putCellStyle("ROUNDED", style);


    Double width = new Double(120.0);
    Double height = new Double(40.0);

    try {

      // could be done more efficiently with SearchCriteria (at least I hope so)
      Map<String, Integer> sample_count = new HashMap<>();

      // count the different sample types
      Integer num_measurement_samples = 0;
      List<Sample> samps = new ArrayList<>();

      for (Sample s : samples) {
        String key = s.getType().getCode();

        if (sample_count.containsKey(key)) {
          sample_count.put(key, sample_count.get(key) + 1);
        } else {
          sample_count.put(key, 1);
        }

        if (key.equals("Q_TEST_SAMPLE")) {
          List<Sample> tmp = s.getChildren();
          num_measurement_samples += tmp.size();
        }

        if (key.equals("Q_BIOLOGICAL_ENTITY")) {
          samps.add(s);
        }
      }

      if (sample_count.containsKey("Q_BIOLOGICAL_ENTITY")) {
        Object dummy_node_level1 =
            graph.insertVertex(parent, null, sample_count.get("Q_BIOLOGICAL_ENTITY") + "\n"
                + "biological entities", 20, 20, width, height,
                "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");


        if (sample_count.containsKey("Q_BIOLOGICAL_SAMPLE")) {
          Object dummy_node_level2 =
              graph.insertVertex(parent, null, sample_count.get("Q_BIOLOGICAL_SAMPLE") + "\n"
                  + "biological samples", 20, 20, width + 75, height,
                  "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");
          graph.insertEdge(parent, null, "", dummy_node_level1, dummy_node_level2,
              "strokeWidth=0;strokeColor=#FFFFFF");

          if (sample_count.containsKey("Q_TEST_SAMPLE")) {
            Object dummy_node_level3 =
                graph.insertVertex(parent, null, sample_count.get("Q_TEST_SAMPLE") + "\n"
                    + "test samples", 20, 20, width + 75, height,
                    "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");
            graph.insertEdge(parent, null, "", dummy_node_level2, dummy_node_level3,
                "strokeWidth=0;strokeColor=#FFFFFF");

            if (num_measurement_samples > 0) {
              Object dummy_node_level4 =
                  graph.insertVertex(parent, null, num_measurement_samples + "\n"
                      + "measured samples", 20, 20, width + 75, height,
                      "NONE;strokeWidth=0;strokeColor=#FFFFFF;fillColor=#FFFFFF");

              graph.insertEdge(parent, null, "", dummy_node_level3, dummy_node_level4,
                  "strokeWidth=0;strokeColor=#FFFFFF");
            }
          }
        }
      }

      List<PropertyType> bioSampleProperties = openBisClient.listPropertiesForType(types.get("Q_BIOLOGICAL_SAMPLE"));
      List<PropertyType> testSampleProperties = openBisClient.listPropertiesForType(types.get("Q_TEST_SAMPLE"));

      for (Sample s : samps) {
        String species =
            (s.getProperties().containsKey("Q_NCBI_ORGANISM")) ? s.getProperties().get(
                "Q_NCBI_ORGANISM") : "";

        // old get species code
        /*
         * for (Map.Entry<String, String> entry : s.getProperties().entrySet()) { if
         * (entry.getKey().equals("Q_NCBI_ORGANISM")) { species = entry.getValue(); } }
         */

        // TODO get Labels of properties.... for organism
        // List<PropertyType> completeProperties =
        // dh.openBisClient.listPropertiesForType(dh.openBisClient.getSampleTypeByString(s.getType()));


        Object mother_node =
            graph.insertVertex(parent, s.getIdentifier().toString(),
                String.format("%s\n%s", s.getCode(), species), 20, 20, width, height,
                "ROUNDED;strokeColor=#ffffff;fillColor=#0365C0");
        // subtree_vertices.add(mother_node);
        List<Sample> children = new ArrayList<>();

        children.addAll(s.getChildren());

        for (Sample c : children) {
          if (c.getType().getCode().equals("Q_BIOLOGICAL_SAMPLE")) {

            String primaryTissue = "";
            String secondaryName = "";
            for (PropertyType pType : bioSampleProperties) {
              if (pType.getCode().equals("Q_PRIMARY_TISSUE")) {
                primaryTissue =
                    openBisClient.getCVLabelForProperty(pType,
                        c.getProperties().get("Q_PRIMARY_TISSUE"));
              }
            }

            String secID = c.getProperties().get("Q_SECONDARY_NAME");
            if (secID != null) {
              secondaryName = secID.replace("nan", "");
            }
            Object daughter_node =
                graph.insertVertex(parent, c.getPermId().toString(),
                    String.format("%s\n%s\n%s", c.getCode(), primaryTissue, secondaryName), 20, 20,
                    width + 75, height + 20, "ROUNDED;strokeColor=#ffffff;fillColor=#51A7F9");
            graph.insertEdge(parent, null, "", mother_node, daughter_node);

            // List<Sample> grandchildren = new ArrayList<>();
            List<Sample> grandchildren = openBisClient.getSamples(
                    c.getChildren().stream().map(Sample::getCode).collect(Collectors.toList()));
            // grandchildren.addAll(c.getChildren());

            for (Sample gc : grandchildren) {
              if (gc.getType().getCode().equals("Q_TEST_SAMPLE")) {

                String testSampleType = "";
                String testSecID = "";
                for (PropertyType pType : testSampleProperties) {
                  if (pType.getCode().equals("Q_SAMPLE_TYPE")) {

                    testSampleType =
                        openBisClient.openbisCodeToString(
                                openBisClient.getCVLabelForProperty(pType, gc.getProperties().get("Q_SAMPLE_TYPE")));
                  }
                }
                testSecID = gc.getProperties().get("Q_SECONDARY_NAME");
                if (testSecID != null) {
                  testSecID = testSecID.replace("nan", "");
                }
                Object granddaughter_node =
                    graph.insertVertex(parent, gc.getPermId().toString(),
                        String.format("%s\n%s\n%s", gc.getCode(), testSampleType, testSecID), 20,
                        20, width + 75, height + 20,
                        "ROUNDED;strokeColor=#ffffff;fillColor=#70BF41");
                graph.insertEdge(parent, null, "", daughter_node, granddaughter_node);

                // List<Sample> grandgrandchildren = new ArrayList<>();
                // grandgrandchildren.addAll(gc.getChildren());

                List<Sample> grandgrandchildren = (gc.getChildren() == null) ? new ArrayList<>() :
                        openBisClient.getSamples(c.getChildren().stream().map(Sample::getCode).collect(Collectors.toList()));

                for (Sample ggc : grandgrandchildren) {

                  String measuredSecID = "";
                  String openbisSecID = ggc.getProperties().get("Q_SECONDARY_NAME");
                  if (openbisSecID != null) {
                    measuredSecID = openbisSecID.replace("nan", "");
                  }
                  Object grandgranddaughter_node =
                      graph.insertVertex(parent, ggc.getPermId().toString(), String.format("%s\n%s\n%s",
                          ggc.getCode(),
                          openBisClient.openbisCodeToString(ggc.getType().getCode()),
                          measuredSecID), 20, 20, width + 75, height + 20,
                          "ROUNDED;strokeColor=#ffffff;fillColor=#F39019");
                  graph.insertEdge(parent, null, "", granddaughter_node, grandgranddaughter_node);

                }
              }
            }
          }
        }
      }

      mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, SwingConstants.WEST);
      layout.setDisableEdgeStyle(true);
      layout.setInterRankCellSpacing(100);
      layout.execute(graph.getDefaultParent()); // Run the layout on the facade.\n

      BufferedImage image =
          mxCellRenderer.createBufferedImage(graph, null, 1, Color.WHITE, true, null);

      final ByteArrayOutputStream bas = new ByteArrayOutputStream();

      if (image != null) {
        ImageIO.write(image, "PNG", bas);
        this.res = showFile(projectId, "PNG", bas);
      } else {
        this.res = null;
      }
    }// end try
    finally {
      graph.getModel().endUpdate();
    }
  }
}
