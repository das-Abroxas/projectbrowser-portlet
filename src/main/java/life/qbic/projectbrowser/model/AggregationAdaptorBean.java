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

/**
 * Helper class to parse datasets/folders/filestructure from queries aggregation service
 * @author Andreas Friedrich
 *
 */
public class AggregationAdaptorBean {

  private String ds;
  private String path;
  private String name;
  private long size;
  private String parent;
  private String lastmodified;

  public String getDs() {
    return ds;
  }

  public void setDs(String ds) {
    this.ds = ds;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public String getLastmodified() {
    return lastmodified;
  }

  public void setLastmodified(String lastmodified) {
    this.lastmodified = lastmodified;
  }
  
  @Override
  public String toString() {
    return ds+" "+path;
  }

  public AggregationAdaptorBean(String ds, String path, String name, long ss, String parent,
      String lastmodified) {
    this.ds = ds;
    this.path = path;
    this.name = name;
    this.size = ss;
    this.parent = parent;
    this.lastmodified = lastmodified;
  }
}
