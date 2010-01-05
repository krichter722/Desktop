package org.freeplane.core.extension;

import org.freeplane.core.model.NodeModel;

public interface IExtensionCopier {
	void copy(Object key, NodeModel from, NodeModel to);
	void copy(NodeModel from, NodeModel to);
	void remove(Object key, NodeModel from);
	void remove(Object key, NodeModel from, NodeModel which);
}
