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
package life.qbic.projectbrowser.controllers;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.experiment.Experiment;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;

import life.qbic.openbis.openbisclient.OpenBisClient;
import life.qbic.projectbrowser.helpers.HistoryReader;
import life.qbic.projectbrowser.model.EntityType;
import life.qbic.projectbrowser.model.notes.Note;
import life.qbic.projectbrowser.model.notes.Notes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.*;

public class MultiscaleController implements Serializable {

  private static final Logger LOG = LogManager.getLogger(MultiscaleController.class);


  private OpenBisClient openbis;
  private JAXBElement<Notes> jaxbelem;
  private String user;
  private String currentId;
  private String currentCode;

  public MultiscaleController(OpenBisClient openbis, String user) {
    this.openbis = openbis;
    this.user = user;
  }

  /**
   * 
   */
  private static final long serialVersionUID = -8194363636454560096L;

  public boolean isReady() {
    return jaxbelem != null && jaxbelem.getValue() != null;
  }

  public List<Note> getNotes() {
    return jaxbelem.getValue().getNote();
  }

  public boolean update(String id, EntityType type) {
    jaxbelem = null;
    String xml = null;

    if (type.equals(EntityType.EXPERIMENT)) {
      Experiment e = openbis.getExperiment(id);
      xml = e.getProperties().get("Q_NOTES");
      currentCode = e.getCode();

    } else {
/*
      EnumSet<SampleFetchOption> fetchOptions = EnumSet.of(SampleFetchOption.PROPERTIES);
      SearchCriteria sc = new SearchCriteria();
      sc.addMatchClause(MatchClause.createAttributeMatch(MatchClauseAttribute.CODE, id));

      List<Sample> samples = openbis.getOpenbisInfoService()
          .searchForSamplesOnBehalfOfUser(openbis.getSessionToken(), sc, fetchOptions, "admin");

      if (samples != null && samples.size() == 1) {
        Sample sample = samples.get(0);
        currentCode = sample.getCode();
        xml = sample.getProperties().get("Q_NOTES");
      }
*/

      Sample sample = openbis.getSample(id);

      if (sample != null) {
        currentCode = sample.getCode();
        xml = sample.getProperties().get("Q_NOTES");
      }
    }

    try {
      if (xml != null) {
        jaxbelem = HistoryReader.parseNotes(xml);
      } else {
        jaxbelem = new JAXBElement<Notes>(new QName(""), Notes.class, new Notes());
      }
      currentId = id;
      return true;
    } catch (IndexOutOfBoundsException | JAXBException | NullPointerException e) {
      currentId = null;
      currentCode = null;
      LOG.error("Error parsing XML");
      e.printStackTrace();
    }
    return false;
  }

  public String getUser() {
    // TODO Auto-generated method stub
    return user;
  }

  public boolean addNote(Note note) {
    if (currentId != null) {
      jaxbelem.getValue().getNote().add(note);
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("id", currentId);
      params.put("user", note.getUsername());
      params.put("comment", note.getComment());
      params.put("time", note.getTime());
      openbis.triggerIngestionService("add-to-xml-note", params);
      return true;
    }
    return false;
  }

  public String getLiferayUser(String userID) {
    Company company = null;
    long companyId = 1;
    String userString = "";

    try {
      String webId = PropsUtil.get(PropsKeys.COMPANY_DEFAULT_WEB_ID);
      company = CompanyLocalServiceUtil.getCompanyByWebId(webId);
      companyId = company.getCompanyId();
      LOG.debug(
          String.format("Using webId %s and companyId %d to get Portal User", webId, companyId));
    } catch (PortalException | SystemException e) {
      LOG.error("Liferay error, could not retrieve companyId. Trying default companyId, which is " + companyId);
      e.printStackTrace();
    }

    User user = null;
    try {
      user = UserLocalServiceUtil.getUserByScreenName(companyId, userID);

    } catch (PortalException | SystemException e) { }

    if (user == null) {
      LOG.warn(String.format("openBIS user %s appears to not exist in Portal", userID));
      userString = userID;

    } else {
      String fullname = user.getFullName();
      String email = user.getEmailAddress();
      userString += ("<a href=\"mailto:");
      userString += (email);
      userString += ("\" style=\"color: #0068AA; text-decoration: none\">");
      userString += (fullname);
      userString += ("</a>");
    }
    return userString;
  }
}
