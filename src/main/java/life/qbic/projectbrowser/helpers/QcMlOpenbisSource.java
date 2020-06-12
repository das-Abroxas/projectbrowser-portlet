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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import com.vaadin.server.StreamResource;

/**
 * This class should get an openbis datastore_server url for an xml file.
 * 
 * @author wojnar
 * 
 */
public class QcMlOpenbisSource implements StreamResource.StreamSource {
  URL u;

  /**
   * 
   * @param sourceURL url to openbis datastore_server for an xml file.
   */
  public QcMlOpenbisSource(URL sourceURL) {
    u = sourceURL;
  }

  /**
   * We need to implement this method that returns the resource as a stream.
   */
  public InputStream getStream() {
    try {
      URLConnection urlConnection = u.openConnection();

      urlConnection.setUseCaches(false);
      urlConnection.setDoOutput(false);
      urlConnection.connect();
      InputStream is = urlConnection.getInputStream();
      return is;

    } catch (MalformedURLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
}
