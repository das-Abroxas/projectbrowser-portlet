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
package life.qbic.projectbrowser.views;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import com.vaadin.data.validator.NullValidator;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.themes.ValoTheme;

import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.controllers.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SearchBarView extends CustomComponent {


  /**
   * 
   */
  private static final long serialVersionUID = 5371970241077786446L;
  private static final Logger LOG = LogManager.getLogger(SearchBarView.class);
  private Panel mainlayout;
  private DataHandler datahandler;
  private final String infotext =
      "This search box lets you search for qbic barcodes. If a barcode exits, comments/notes for that barcode will be displayed. You can as well add notes/comments to a barcode.";

  public SearchBarView(DataHandler datahandler) {
    this.datahandler = datahandler;
    initUI();
  }

  public void initUI() {
    mainlayout = new Panel();
    mainlayout.addStyleName(ValoTheme.PANEL_BORDERLESS);

    // static information for the user
    // Label info = new Label();
    // info.setValue(infotext);
    // info.setStyleName(ValoTheme.LABEL_LIGHT);
    // info.setStyleName(ValoTheme.LABEL_H4);
    // mainlayout.addComponent(info);

    // Search bar
    // *----------- search text field .... search button-----------*
    HorizontalLayout searchbar = new HorizontalLayout();
    searchbar.setSpacing(true);
    final TextField searchfield = new TextField();
    searchfield.setHeight("44px");
    searchfield.setImmediate(true);

    searchfield.setInputPrompt("search for sample");
    // TODO would be nice to have a autofill or something similar
    searchbar.addComponent(searchfield);
    Button searchOk = new Button("GoTo");
    searchOk.addStyleName(ValoTheme.BUTTON_BORDERLESS);
    searchOk.setIcon(FontAwesome.SEARCH);
    searchOk.addClickListener(new ClickListener() {
      private static final long serialVersionUID = -2409450448301908214L;

      @Override
      public void buttonClick(ClickEvent event) {
        // TODO how to deal with entities
        Pattern pattern = Pattern.compile("Q[A-Z0-9]{4}[0-9]{3}[A-Z0-9]{2}");
        Pattern pattern2 = Pattern.compile("Q[A-Z0-9]{4}ENTITY-[0-9]+");

        LOG.info("searching for sample: " + (String) searchfield.getValue());

        if (searchfield.getValue() == null || searchfield.getValue().toString().equals("")) {
          Notification.show("Please provide a Barcode before clicking GoTo.", Type.WARNING_MESSAGE);
        }

        else {
          String entity = (String) searchfield.getValue().toString();

          Matcher matcher = pattern.matcher(entity);
          Matcher matcher2 = pattern2.matcher(entity);

          Boolean patternFound1 = matcher.find();
          Boolean patternFound2 = matcher2.find();

          if (patternFound1) {
            try {
              Sample foundSample =
                  datahandler.getOpenBisClient().getSampleByIdentifier(matcher.group(0).toString());
              String identifier = foundSample.getIdentifier().toString();

              State state = (State) UI.getCurrent().getSession().getAttribute("state");
              ArrayList<String> message = new ArrayList<String>();
              message.add("clicked");
              message.add(identifier);
              message.add("sample");
              state.notifyObservers(message);
            } catch (Exception e) {
              Notification.show("No Sample found for given barcode.", Type.WARNING_MESSAGE);
            }
          }

          else if (patternFound2) {
            try {
              Sample foundSample = datahandler.getOpenBisClient()
                  .getSampleByIdentifier(matcher2.group(0).toString());
              String identifier = foundSample.getIdentifier().toString();

              State state = (State) UI.getCurrent().getSession().getAttribute("state");
              ArrayList<String> message = new ArrayList<String>();
              message.add("clicked");
              message.add(identifier);
              message.add("sample");
              state.notifyObservers(message);
            } catch (Exception e) {
              Notification.show("No Sample found for given barcode.", Type.WARNING_MESSAGE);
            }
          } else {
            Notification.show("Please provide a valid Sample Barcode.", Type.WARNING_MESSAGE);
          }
        }
      }
    });

    // setClickShortcut() would add global shortcut, instead we
    // 'scope' the shortcut to the panel:
    mainlayout.addAction(new Button.ClickShortcut(searchOk, KeyCode.ENTER));
    // searchfield.addItems(this.getSearchResults("Q"));
    searchfield.setDescription(infotext);
    searchfield.addValidator(new NullValidator("Field must not be empty", false));
    searchfield.setValidationVisible(false);

    searchbar.addComponent(searchOk);
    // searchbar.setMargin(new MarginInfo(true, false, true, false));
    mainlayout.setContent(searchbar);
    // mainlayout.setComponentAlignment(searchbar, Alignment.MIDDLE_RIGHT);
    // mainlayout.setWidth(100, Unit.PERCENTAGE);
    setCompositionRoot(mainlayout);
  }
}
