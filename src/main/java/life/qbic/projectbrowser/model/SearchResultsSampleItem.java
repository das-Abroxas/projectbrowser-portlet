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
import java.util.Map;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;

/**
 *
 */
public class SearchResultsSampleItem extends CustomComponent {
	Sample sampleToView;

	public SearchResultsSampleItem(Sample sampleToView, int rowNumber) {
		this.sampleToView = sampleToView;

		Map<String, String> props = sampleToView.getProperties();
		
		HorizontalLayout rowContent = new HorizontalLayout();
		rowContent.setSpacing(true);
		Label itemNumber = new Label(Integer.toString(rowNumber) + ".");
		Label spacer1 = new Label();
		Label qbicCode = new Label(sampleToView.getCode());
		Label spacer2 = new Label();
		
		String sampleLabel;
		
		if (props.get("Q_SECONDARY_NAME") == null || props.get("Q_SECONDARY_NAME").equals("")) {
			sampleLabel = "no description";			
		}
		else {
			sampleLabel = props.get("Q_SECONDARY_NAME");
		}
		
		Label qbicLabel = new Label(sampleLabel);
		
		rowContent.addComponent(itemNumber);

		rowContent.addComponent(qbicCode);

		rowContent.addComponent(qbicLabel);



		setCompositionRoot(rowContent);

	}


}
