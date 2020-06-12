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
package life.qbic.projectbrowser.helpers;


/**
 * This class should not exist.
 */
public class OpenBisFunctions {

  /**
   * Returns the 4 or 5 character project prefix used for samples in openBIS.
   *
   * @param sample sample ID starting with a standard project prefix.
   * @return Project prefix of the sample
   */
  public static String getProjectPrefix(String sample) {
    if (Utils.isInteger("" + sample.charAt(4)))
      return sample.substring(0, 4);
    else
      return sample.substring(0, 5);
  }

  public static double statusToDoubleValue(String status) {

    switch (status) {
      case "FINISHED":
        return 1.0;
      case "RUNNING":
        return 0.5;
      case "STARTED":
        return 0.25;
      default:
        return 0.0;
    }
  }
}
