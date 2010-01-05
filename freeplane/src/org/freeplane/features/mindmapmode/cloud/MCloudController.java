/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is created by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.features.mindmapmode.cloud;

import java.awt.Color;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.extension.IExtensionCopier;
import org.freeplane.core.modecontroller.MapController;
import org.freeplane.core.modecontroller.ModeController;
import org.freeplane.core.model.NodeModel;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.common.addins.styles.LogicalStyleKeys;
import org.freeplane.features.common.cloud.CloudController;
import org.freeplane.features.common.cloud.CloudModel;
import org.freeplane.features.common.edge.EdgeModel;
import org.freeplane.features.common.nodestyle.NodeStyleModel;
import org.freeplane.features.mindmapmode.addins.styles.MLogicalStyleController;

/**
 * @author Dimitry Polivaev
 */
public class MCloudController extends CloudController {
	private static class ExtensionCopier implements IExtensionCopier{

		public void copy(Object key, NodeModel from, NodeModel to) {
			if(! key.equals(LogicalStyleKeys.NODE_STYLE)){
				return;
			}
			copy(from, to);
		}

		public void copy(NodeModel from, NodeModel to) {
			final CloudModel fromStyle = (CloudModel) from.getExtension(CloudModel.class);
			if(fromStyle == null){
				return;
			}
			CloudModel toStyle = CloudModel.createModel(to);
			toStyle.setColor(fromStyle.getColor());
		}

		public void remove(Object key, NodeModel from) {
			if(! key.equals(LogicalStyleKeys.NODE_STYLE)){
				return;
			}
			from.removeExtension(CloudModel.class);
		}

		public void remove(Object key, NodeModel from, NodeModel which) {
			if(! key.equals(LogicalStyleKeys.NODE_STYLE)){
				return;
			}
	    	final CloudModel whichStyle = (CloudModel) which.getExtension(CloudModel.class);
	    	if(whichStyle == null){
	    		return;
	    	}
	    	final CloudModel fromStyle = (CloudModel) from.getExtension(CloudModel.class);
	    	if(fromStyle == null){
	    		return;
	    	}
	    	from.removeExtension(fromStyle);
		}
		
	}
	
	public MCloudController(final ModeController modeController) {
		super(modeController);
		final Controller controller = modeController.getController();
		modeController.registerExtensionCopier(new ExtensionCopier());
		modeController.addAction(new CloudAction(controller));
		modeController.addAction(new CloudColorAction(controller));
	}

	public void setCloud(final NodeModel node, final boolean enable) {
		final CloudModel cloud = CloudModel.getModel(node);
		if ((cloud != null) == enable) {
			return;
		}
		final Color color = cloud != null ? cloud.getColor() :  getStandardColor();
		final ModeController modeController = getModeController();
		final IActor actor = new IActor() {
			public void act() {
				if (enable) {
					enable();
				}
				else {
					disable();
				}
			}

			private void disable() {
				final MapController mapController = modeController.getMapController();
				CloudModel.setModel(node, null);
				mapController.nodeChanged(node);
			}

			private void enable() {
				final CloudModel cloud = new CloudModel();
				cloud.setColor(color);
				final MapController mapController = modeController.getMapController();
				CloudModel.setModel(node, cloud);
				mapController.nodeChanged(node);
			}

			public String getDescription() {
				return "setCloud";
			}

			public void undo() {
				if (enable) {
					disable();
				}
				else {
					enable();
				}
			}
		};
		modeController.execute(actor, node.getMap());
	}

	public void setColor(final NodeModel node, final Color color) {
		setCloud(node, true);
		final ModeController modeController = getModeController();
		final Color oldColor = CloudModel.getModel(node).getColor();
		if (color == oldColor || color != null && color.equals(oldColor)) {
			return;
		}
		final IActor actor = new IActor() {
			public void act() {
				CloudModel.getModel(node).setColor(color);
				modeController.getMapController().nodeChanged(node);
			}

			public String getDescription() {
				return "setColor";
			}

			public void undo() {
				CloudModel.getModel(node).setColor(oldColor);
				modeController.getMapController().nodeChanged(node);
			}
		};
		modeController.execute(actor, node.getMap());
	}
}
