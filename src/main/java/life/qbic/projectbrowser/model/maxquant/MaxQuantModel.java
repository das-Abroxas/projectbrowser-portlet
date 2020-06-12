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
package life.qbic.projectbrowser.model.maxquant;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;

import org.apache.commons.lang3.NotImplementedException;

import com.vaadin.data.util.BeanItemContainer;

import de.uni_tuebingen.qbic.beans.DatasetBean;
import fasta.FastaBean;

public class MaxQuantModel implements Serializable {
  private static final long serialVersionUID = 6180632029094989505L;


  private BeanItemContainer<RawFilesBean> rawFilesBeans =
      new BeanItemContainer<RawFilesBean>(RawFilesBean.class);
  private BeanItemContainer<DatasetBean> datasetBeans =
      new BeanItemContainer<DatasetBean>(DatasetBean.class);
  private HashMap<RawFilesBean, DatasetBean> selecteddatasets =
      new HashMap<RawFilesBean, DatasetBean>();

  private BeanItemContainer<FastaBean> fastaBeans =
      new BeanItemContainer<FastaBean>(FastaBean.class);
  private BeanItemContainer<FastaBean> selectedFastaBeans =
      new BeanItemContainer<FastaBean>(FastaBean.class);

  // group specific parameters
  private HashMap<Integer, Group> groups = new HashMap<Integer, Group>();

  // global parameters
  private LinkedHashSet<String> fixedMods = new LinkedHashSet<String>();
  private Boolean matchBetweenRuns = false;
  private Boolean reQuantify = false;

  public MaxQuantModel(BeanItemContainer<RawFilesBean> rawFilesBeans,
      BeanItemContainer<DatasetBean> datasetBeans, BeanItemContainer<FastaBean> selectedfastas,
      BeanItemContainer<FastaBean> fastas) {
    super();
    this.rawFilesBeans = rawFilesBeans;
    this.datasetBeans = datasetBeans;
    this.fastaBeans = fastas;
    this.selectedFastaBeans = selectedfastas;
  }

  public BeanItemContainer<RawFilesBean> getRawFilesBeans() {
    return rawFilesBeans;
  }

  public void setRawFilesBeans(BeanItemContainer<RawFilesBean> rawFilesBeans) {
    this.rawFilesBeans = rawFilesBeans;
  }

  public BeanItemContainer<DatasetBean> getDatasetBeans() {
    return datasetBeans;
  }

  public Collection<DatasetBean> selectedDatasets() {
    return selecteddatasets.values();
  }

  public void setDatasetBeans(BeanItemContainer<DatasetBean> datasetBeans) {
    this.datasetBeans = datasetBeans;
  }

  public void fromJson(String json) {
    throw new NotImplementedException("Not implemented.");
  }

  public void fromJson(File json) {
    throw new NotImplementedException("Not implemented.");
  }

  public String toJson() {
    throw new NotImplementedException("Not implemented.");
  }

  /**
   * removes all Datasetbeans in that collection from the container datasetbeans and adds them to
   * selected beans.
   * 
   * @param available
   */
  public void selectRawFiles(Collection<Object> available) {
    if (available == null || available.isEmpty())
      return;
    for (Object o : available) {
      if (o instanceof DatasetBean && this.datasetBeans.containsId(o)) {
        DatasetBean bean = (DatasetBean) o;
        this.datasetBeans.removeItem(o);
        RawFilesBean rawbean = new RawFilesBean(bean.getFileName(), bean.getSampleIdentifier());
        this.rawFilesBeans.addBean(rawbean);
        this.selecteddatasets.put(rawbean, bean);
      }
    }
  }

  public void unselectRawFiles(Collection<Object> available) {
    if (available == null || available.isEmpty())
      return;
    for (Object o : available) {
      if (o instanceof RawFilesBean && this.rawFilesBeans.containsId(o)) {
        RawFilesBean bean = (RawFilesBean) o;
        this.datasetBeans.addBean(this.selecteddatasets.get(bean));
        this.selecteddatasets.remove(bean);
        this.rawFilesBeans.removeItem(bean);
      }
    }
  }

  public BeanItemContainer<FastaBean> getSelectedFastaBeans() {
    return selectedFastaBeans;
  }

  public BeanItemContainer<FastaBean> getFastaBeans() {
    return fastaBeans;
  }

  public void selectFastaFiles(Collection<Object> available) {
    if (available == null || available.isEmpty())
      return;
    for (Object o : available) {
      if (this.fastaBeans.containsId(o)) {
        FastaBean bean = (FastaBean) o;
        this.fastaBeans.removeItem(o);
        this.selectedFastaBeans.addBean(bean);
      }
    }

  }

  public void unselectFastaFiles(Collection<Object> available) {
    if (available == null || available.isEmpty())
      return;
    for (Object o : available) {
      if (this.selectedFastaBeans.containsId(o)) {
        FastaBean bean = (FastaBean) o;
        this.fastaBeans.addBean(bean);
        this.selectedFastaBeans.removeItem(bean);
      }
    }
  }

  public LinkedHashSet<String> getFixedMods() {
    return this.fixedMods;
  }

  public HashMap<Integer, Group> getGroups() {
    return groups;
  }

  public void setGroups(HashMap<Integer, Group> groups) {
    this.groups = groups;
  }

  public Boolean getMatchBetweenRuns() {
    return matchBetweenRuns;
  }

  public void setMatchBetweenRuns(Boolean matchBetweenRuns) {
    this.matchBetweenRuns = matchBetweenRuns;
  }

  public Boolean getReQuantify() {
    return reQuantify;
  }

  public void setReQuantify(Boolean reQuantify) {
    this.reQuantify = reQuantify;
  }

}
