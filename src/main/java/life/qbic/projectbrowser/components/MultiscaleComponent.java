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
package life.qbic.projectbrowser.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.entitytype.EntityKind;

import life.qbic.projectbrowser.controllers.MultiscaleController;
import life.qbic.projectbrowser.model.EntityType;
import life.qbic.projectbrowser.model.notes.Note;

public class MultiscaleComponent extends CustomComponent {

  private static final long serialVersionUID = 4700958245761376884L;
  private MultiscaleController controller;
  private VerticalLayout mainlayout;
  // private Grid pastcomments;
  private List<Note> notes;
  private Panel commentsPanel;

  public MultiscaleComponent(MultiscaleController c) {
    this.controller = c;
    initUI();
  }

  public void initUI() {
    mainlayout = new VerticalLayout();
    mainlayout.setWidth(100, Unit.PERCENTAGE);
    commentsPanel = new Panel();

    Label commentsLabel = new Label("No comments added so far.", ContentMode.HTML);
    commentsPanel.setContent(commentsLabel);

    commentsPanel.setImmediate(true);
    commentsPanel.setWidth(50, Unit.PERCENTAGE);
    commentsPanel.setHeight(UI.getCurrent().getPage().getBrowserWindowHeight() * 0.2f, Unit.PIXELS);

    setCompositionRoot(mainlayout);
  }

  public void updateUI(String id, EntityType type) {
    controller.update(id, type);
    setNotes();
  }

  void buildEmptyComments() {

    // add comments
    VerticalLayout addComment = new VerticalLayout();
    addComment.setMargin(true);
    addComment.setWidth(100, Unit.PERCENTAGE);
    final TextArea comments = new TextArea();
    comments.setInputPrompt("Write your comment here...");
    comments.setWidth(100, Unit.PERCENTAGE);
    comments.setRows(2);
    Button commentsOk = new Button("Add Comment");
    commentsOk.addStyleName(ValoTheme.BUTTON_FRIENDLY);
    commentsOk.addClickListener(new ClickListener() {
      /**
       * 
       */
      private static final long serialVersionUID = -5369241494545155677L;

      public void buttonClick(ClickEvent event) {
        if ("".equals(comments.getValue()))
          return;

        String newComment = comments.getValue();
        // reset comments
        comments.setValue("");
        // use some date format
        Date dNow = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        Note note = new Note();
        note.setComment(newComment);
        note.setUsername(controller.getUser());
        note.setTime(ft.format(dNow));

        // show it now
        // pastcomments.getContainerDataSource().addItem(note);
        notes.add(note);

        // TODO write back
        Label commentsLabel = new Label(translateComments(notes), ContentMode.HTML);
        commentsPanel.setContent(commentsLabel);

        // write back to openbis
        if (!controller.addNote(note)) {
          Notification.show("Could not add comment to sample. How did you do that?");
        }

      }

    });

    HorizontalLayout inputPrompt = new HorizontalLayout();
    inputPrompt.addComponent(comments);
    inputPrompt.addComponent(commentsOk);

    inputPrompt.setWidth(50, Unit.PERCENTAGE);
    inputPrompt.setComponentAlignment(commentsOk, Alignment.TOP_RIGHT);
    inputPrompt.setExpandRatio(comments, 1.0f);



    // addComment.addComponent(comments);
    // addComment.addComponent(commentsOk);

    addComment.addComponent(commentsPanel);
    addComment.addComponent(inputPrompt);

    // addComment.setComponentAlignment(comments, Alignment.TOP_CENTER);
    // addComment.setComponentAlignment(commentsOk, Alignment.MIDDLE_CENTER);

    addComment.setComponentAlignment(commentsPanel, Alignment.TOP_CENTER);
    addComment.setComponentAlignment(inputPrompt, Alignment.MIDDLE_CENTER);

    mainlayout.addComponent(addComment);

    // mainlayout.addComponent(pastcomments);
    Label commentsLabel = new Label("No comments added so far.", ContentMode.HTML);
    commentsPanel.setContent(commentsLabel);

    // mainlayout.addComponent(commentsPanel);
    // mainlayout.setComponentAlignment(commentsPanel,
    // Alignment.TOP_CENTER);
  }

  void setNotes() {
    if (notes == null || notes.isEmpty()) {
      buildEmptyComments();
    }
    Label commentsLabel = new Label(translateComments(controller.getNotes()), ContentMode.HTML);
    commentsPanel.setContent(commentsLabel);
    notes = controller.getNotes();
    // pastcomments.setContainerDataSource(controller.getNotes());
    // pastcomments.setColumnOrder("time", "username", "comment");
    // pastcomments.setHeightMode(HeightMode.ROW);
    // pastcomments.setHeightByRows(controller.getContainer().size());
  }

  public void resize(float width, float height) {
    setWidth(width * 0.8f, Unit.PIXELS);
    // setHeight(height*0.6f, Unit.PIXELS);
  }

  public String translateComments(List<Note> notes) {

    String lastDay = "";
    String labelString = "";
    for (Note n : notes) {
      String date = n.getTime();
      String[] datetime = date.split("T");
      String day = datetime[0];
      String time = datetime[1].split("\\.")[0];
      if (!lastDay.equals(day)) {
        lastDay = day;
        labelString += String.format("%s\n", "<u>" + day + "</u>");
      }
      labelString += String.format("%s\n%s %s\n",
          "<p><b>" + controller.getLiferayUser(n.getUsername()) + "</b>.</p>", n.getComment(),
          "<p><i><small>" + time + "</small></i>.</p>");
    }

    return labelString;

  }

}
