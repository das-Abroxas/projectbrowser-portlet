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

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.vaadin.ui.CheckBox;

public class DatasetBean implements Serializable {


  /**
   * 
   */
  private static final long serialVersionUID = 4275310001607043674L;

  // is the bean selected in the table?
  private CheckBox isSelected;

  // all information in its linked parents.
  private ProjectBean project;
  private SampleBean sample;
  private ExperimentBean experiment;

  // openbis code
  private String code;
  // file or directory name on dss
  private String name;
  // type of this dataset
  private String type;

  // size of this dataset on dss.
  private long fileSize;
  // same as {@link fileSize} but human readable format
  private String humanReadableFileSize;


  // path to the actual file or directory of this dataset on the dss.
  private String dssPath;
  // date of the registration of this dataset (not necessary when data arrives)
  private Date registrationDate;
  // name of the registrator
  // TODO class user or liferay user?
  private String registrator;

  // If it is a directory, the file structure has to taken with care.
  private boolean isDirectory;

  private DatasetBean parent;

  private DatasetBean root;
  private List<DatasetBean> children;

  private Map<String, String> properties;



  public Map<String, String> getProperties() {
    return properties;
  }

  public void setProperties(Map<String, String> properties) {
    this.properties = properties;
  }

  public DatasetBean(CheckBox isSelected, ProjectBean project, SampleBean sample,
      ExperimentBean experiment, String code, String name, String type, long fileSize,
      String humanReadableFileSize, String dssPath, Date registrationDate, String registrator,
      boolean isDirectory, DatasetBean parent, DatasetBean root, List<DatasetBean> children) {
    this.isSelected = isSelected;
    this.project = project;
    this.sample = sample;
    this.experiment = experiment;
    this.code = code;
    this.name = name;
    this.type = type;
    this.fileSize = fileSize;
    this.humanReadableFileSize = humanReadableFileSize;
    this.dssPath = dssPath;
    this.registrationDate = registrationDate;
    this.registrator = registrator;
    this.isDirectory = isDirectory;
    this.parent = parent;
    this.root = root;
    this.children = children;
  }

  public DatasetBean() {
    // TODO Auto-generated constructor stub
  }

  public boolean hasParent() {
    return parent != null;
  }

  public boolean hasChildren() {
    return children != null && children.size() > 0;
  }

  public boolean isRoot() {
    return root == null;
  }


  public DatasetBean getParent() {
    return parent;
  }

  public void setParent(DatasetBean parent) {
    this.parent = parent;
  }

  public DatasetBean getRoot() {
    return root;
  }

  public void setRoot(DatasetBean root) {
    this.root = root;
  }

  public List<DatasetBean> getChildren() {
    return children;
  }

  public void setChildren(List<DatasetBean> children) {
    this.children = children;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getHumanReadableFileSize() {
    return humanReadableFileSize;
  }

  public void setHumanReadableFileSize(String humanReadableFileSize) {
    this.humanReadableFileSize = humanReadableFileSize;
  }

  public String getDssPath() {
    return dssPath;
  }

  public void setDssPath(String dssPath) {
    this.dssPath = dssPath;
  }

  public boolean getIsDirectory() {
    return isDirectory;
  }

  public void setDirectory(boolean isDirectory) {
    this.isDirectory = isDirectory;
  }

  public static long getSerialversionuid() {
    return serialVersionUID;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public ProjectBean getProject() {
    return project;
  }

  public void setProject(ProjectBean project) {
    this.project = project;
  }

  public ExperimentBean getExperiment() {
    return experiment;
  }

  public void setExperiment(ExperimentBean experiment) {
    this.experiment = experiment;
  }

  public SampleBean getSample() {
    return sample;
  }

  public void setSample(SampleBean sample) {
    this.sample = sample;
  }

  /**
   * same as {@link #getName() getName}
   * 
   * @return
   */
  public String getFileName() {
    return name;
  }

  /**
   * same as {@link #setName(String) setName}
   * 
   * @return
   */
  public void setFileName(String fileName) {
    this.name = fileName;
  }

  public String getFileType() {
    return type;
  }

  public void setFileType(String fileType) {
    this.type = fileType;
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public Date getRegistrationDate() {
    return registrationDate;
  }

  public void setRegistrationDate(Date registrationDate) {
    this.registrationDate = registrationDate;
  }

  public String getRegistrator() {
    return registrator;
  }

  public void setRegistrator(String registrator) {
    this.registrator = registrator;
  }

  @Override
  public String toString() {
    return "DatasetBean [code=" + code + "; path=" + dssPath + "; size=" + fileSize + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((code == null) ? 0 : code.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DatasetBean other = (DatasetBean) obj;
    if (code == null) {
      if (other.code != null)
        return false;
    } else if (!code.equals(other.code))
      return false;
    return true;
  }

  public CheckBox getIsSelected() {
    return isSelected;
  }

  public void setSelected(boolean isSelected) {
    this.isSelected = new CheckBox();
    this.isSelected.setValue(isSelected);
  }

}
