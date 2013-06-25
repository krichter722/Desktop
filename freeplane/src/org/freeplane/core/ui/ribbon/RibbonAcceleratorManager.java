package org.freeplane.core.ui.ribbon;

import java.awt.Event;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.components.GrabKeyDialog;
import org.freeplane.core.resources.components.IKeystrokeValidator;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IAcceleratorChangeListener;
import org.freeplane.core.ui.IEditHandler.FirstAction;
import org.freeplane.core.ui.IKeyStrokeProcessor;
import org.freeplane.core.ui.components.FreeplaneMenuBar;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public class RibbonAcceleratorManager implements IKeyStrokeProcessor, IAcceleratorChangeListener {
	
	private static final String SHORTCUT_PROPERTY_PREFIX = "ribbon.acceleratorFor.";
	
	private final Map<KeyStroke, AFreeplaneAction> accelerators = new HashMap<KeyStroke, AFreeplaneAction>();
	private final Map<String, KeyStroke> actionMap = new HashMap<String, KeyStroke>();
	private final List<IAcceleratorChangeListener> changeListeners = new ArrayList<IAcceleratorChangeListener>();
	
	private final RibbonBuilder builder;
	private IAcceleratorChangeListener acceleratorChangeListener;
	private final Properties keysetProps = new Properties();
	private final Properties defaultProps = new Properties();

	
	/***********************************************************************************
	 * CONSTRUCTORS
	 **********************************************************************************/
	
 	public RibbonAcceleratorManager(RibbonBuilder ribbonBuilder) {
 		this.builder = ribbonBuilder;
 	}
 	
	/***********************************************************************************
	 * METHODS
	 **********************************************************************************/

 	public void setAccelerator(final AFreeplaneAction action, final KeyStroke keyStroke) {
 		if(action == null || keyStroke == null) {
 			return;
 		}
		final AFreeplaneAction oldAction = accelerators.put(keyStroke, action);
		if(action == oldAction) {
			return;
		}
		if (keyStroke != null && oldAction != null) {
			UITools.errorMessage(TextUtils.format("action_keystroke_in_use_error", keyStroke, getActionTitle(action.getKey()), getActionTitle(oldAction.getKey())));
			accelerators.put(keyStroke, oldAction);
			final String shortcutKey = getPropertyKey(action.getKey());
			
			keysetProps.setProperty(shortcutKey, "");
			return;
		}
		final KeyStroke removedAccelerator = removeAccelerator(action);
		actionMap.put(action.getKey(), keyStroke);
		if (acceleratorChangeListener != null && (removedAccelerator != null || keyStroke != null)) {
			acceleratorChangeListener.acceleratorChanged(action, removedAccelerator, keyStroke);
		}
	}
 	
 	private String getActionTitle(String key) {
 		String title = TextUtils.getText(key+".text");
		if(title == null || title.isEmpty()) {
			title = key;
		}
		return title;
 	}
 	
 	public void setDefaultAccelerator(final String itemKey, final String accelerator) {
		final String shortcutKey = getPropertyKey(itemKey);
		if (null == getProperty(shortcutKey)) {
			defaultProps.setProperty(shortcutKey, accelerator);
		}
		KeyStroke ks = KeyStroke.getKeyStroke(accelerator);
		AFreeplaneAction action = builder.getMode().getAction(itemKey);
		setAccelerator(action, ks);
		
	}
 	
 	public KeyStroke removeAccelerator(final AFreeplaneAction action) throws AssertionError {
 		if(action == null) {
 			return null;
 		}
		final KeyStroke oldAccelerator = actionMap.get(action.getKey());
		if (oldAccelerator != null) {
			final AFreeplaneAction oldAction = accelerators.remove(oldAccelerator);
			if (!action.equals(oldAction)) {
				throw new AssertionError("unexpected action " + "for accelerator " + oldAccelerator);
			}
		}
		return oldAccelerator;
	}
 	
 	public void setAcceleratorChangeListener(final IAcceleratorChangeListener acceleratorChangeListener) {
		this.acceleratorChangeListener = acceleratorChangeListener;
	}
 	
 	public String getPropertyKey(final String key) {
		return SHORTCUT_PROPERTY_PREFIX + builder.getMode().getModeName() + "/" + key;
	}
 	
 	public KeyStroke getAccelerator(String key) {
 		return actionMap.get(key);
 	}
 	
 	public void addAcceleratorChangeListener(IAcceleratorChangeListener changeListener) {
		synchronized (changeListeners) {
			if(!changeListeners.contains(changeListener)) {
				changeListeners.add(changeListener);
			}
		}
	}
 	
 	private String getProperty(String key) {
 		return keysetProps.getProperty(key, defaultProps.getProperty(key, null));
 	}
 	
 	public void newAccelerator(final AFreeplaneAction action, final KeyStroke newAccelerator) {
		final String shortcutKey = getPropertyKey(action.getKey());
		final String oldShortcut = getProperty(shortcutKey);
		if (newAccelerator == null || !new KeystrokeValidator(action).isValid(newAccelerator, newAccelerator.getKeyChar())) {
			final GrabKeyDialog grabKeyDialog = new GrabKeyDialog(oldShortcut);
			final IKeystrokeValidator validator = new KeystrokeValidator(action);
			grabKeyDialog.setValidator(validator);
			grabKeyDialog.setVisible(true);
			if (grabKeyDialog.isOK()) {
				final String shortcut = grabKeyDialog.getShortcut();
				final KeyStroke accelerator = UITools.getKeyStroke(shortcut);
				setAccelerator(action, accelerator);
				keysetProps.setProperty(shortcutKey, shortcut);
				LogUtils.info("created shortcut '" + shortcut + "' for action '" + action.getKey() + "', shortcutKey '"
				+ shortcutKey + "' (" + RibbonActionContributorFactory.getActionTitle(action) + ")");
			}
		}
		else{
			if(oldShortcut != null){
				final int replace = JOptionPane.showConfirmDialog(UITools.getFrame(), oldShortcut, TextUtils.getText("remove_shortcut_question"), JOptionPane.YES_NO_OPTION);
				if (replace != JOptionPane.YES_OPTION) {
					return;
				}
			}
			setAccelerator(action, newAccelerator);
			keysetProps.setProperty(shortcutKey, toString(newAccelerator));
			LogUtils.info("created shortcut '" + toString(newAccelerator) + "' for action '" + action+ "', shortcutKey '" + shortcutKey + "' (" + RibbonActionContributorFactory.getActionTitle(action) + ")");
		}
		try {
			if(!getPresetsFile().exists()) {
					getPresetsFile().createNewFile();	
			}
			storeAcceleratorPreset(new FileOutputStream(getPresetsFile()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
 	
 	public File getPresetsFile() {
 		File ribbonsDir = new File(ResourceController.getResourceController().getFreeplaneUserDirectory(), "ribbons");
		if(!ribbonsDir.exists()) {
			ribbonsDir.mkdirs();
		}
		return new File(ribbonsDir, "accelerator.properties");
 	}
 	
 	public void loadAcceleratorPresets(final InputStream in) {
		final Properties prop = new Properties();
		try {
			prop.load(in);
			for (final Entry<Object, Object> property : prop.entrySet()) {
				final String shortcutKey = (String) property.getKey();
				final String keystrokeString = (String) property.getValue();
				if (!shortcutKey.startsWith(SHORTCUT_PROPERTY_PREFIX)) {
					LogUtils.warn("wrong property key " + shortcutKey);
					continue;
				}
				final int pos = shortcutKey.indexOf("/", SHORTCUT_PROPERTY_PREFIX.length());
				if (pos <= 0) {
					LogUtils.warn("wrong property key " + shortcutKey);
					continue;
				}
				final String modeName = shortcutKey.substring(SHORTCUT_PROPERTY_PREFIX.length(), pos);
				final String itemKey = shortcutKey.substring(pos + 1);
				Controller controller = Controller.getCurrentController();
				final ModeController modeController = controller.getModeController(modeName);
				if (modeController == null) {
					LogUtils.warn("unknown mode name in " + shortcutKey);
					continue;
				}
				final AFreeplaneAction action = modeController.getAction(itemKey);
				if (action == null) {
					LogUtils.warn("wrong key in " + shortcutKey);
					continue;
				}
				final KeyStroke keyStroke;
				if (!keystrokeString.equals("")) {
					keyStroke = UITools.getKeyStroke(keystrokeString);
					final AFreeplaneAction oldAction = accelerators.get(keyStroke);
					if (oldAction != null) {
						setAccelerator(oldAction, null);
						final Object key = oldAction.getKey();
						final String oldShortcutKey = getPropertyKey(key.toString());
						keysetProps.setProperty(oldShortcutKey, "");
					}
				}
				else {
					keyStroke = null;
				}
				setAccelerator(action, keyStroke);
				keysetProps.setProperty(shortcutKey, keystrokeString);
			}
		}
		catch (final IOException e) {
			LogUtils.warn("shortcut presets not stored: "+e.getMessage());
		}
	}
 	
 	public void storeAcceleratorPreset(OutputStream out) {
 		try {
 			final OutputStream output = new BufferedOutputStream(out);
 			keysetProps.store(output, "");
 			output.close();
 		}
 		catch (final IOException e1) {
 			UITools.errorMessage(TextUtils.getText("can_not_save_key_set"));
 		}
 	}
 	
	private static String toString(final KeyStroke newAccelerator) {
		return newAccelerator.toString().replaceFirst("pressed ", "");
	}
	
	private static boolean askForReplaceShortcutViaDialog(String oldMenuItemTitle) {
		final int replace = JOptionPane.showConfirmDialog(UITools.getFrame(), 
				TextUtils.format("replace_shortcut_question", oldMenuItemTitle),
				TextUtils.format("replace_shortcut_title"), JOptionPane.YES_NO_OPTION);
		return replace == JOptionPane.YES_OPTION;
	}
	/***********************************************************************************
	 * REQUIRED METHODS FOR INTERFACES
	 **********************************************************************************/

	public boolean processKeyBinding(KeyStroke ks, KeyEvent event, int condition, boolean pressed, boolean consumed) {
		if (!consumed && condition == JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
			AFreeplaneAction action = accelerators.get(ks);
			if(action != null) {
				if(action != null && SwingUtilities.notifyAction(action, ks, event, event.getComponent(), event.getModifiers())) {
					return true;
				}
			}
		}
		return false;
	}

	public void acceleratorChanged(JMenuItem action, KeyStroke oldStroke, KeyStroke newStroke) {
		// TODO Auto-generated method stub
		
	}

	public void acceleratorChanged(AFreeplaneAction action, KeyStroke oldStroke, KeyStroke newStroke) {
		KeyStroke ks = actionMap.put(action.getKey(), newStroke);
		if(ks != null) {
			accelerators.remove(ks);
		}		
		accelerators.put(newStroke, action);
	}
	
	/***********************************************************************************
	 * NESTED TYPE DECLARATIONS
	 **********************************************************************************/
	private class KeystrokeValidator implements IKeystrokeValidator {
		private final AFreeplaneAction action;

		private KeystrokeValidator(AFreeplaneAction action) {
			this.action = action;
		}

		private boolean checkForOverwriteShortcut(final KeyStroke keystroke) {
			final AFreeplaneAction priorAssigned = accelerators.get(keystroke);
			if (priorAssigned == null || action.getKey().equals(priorAssigned.getKey())) {
				return true;
			}
			return replaceOrCancel(priorAssigned, RibbonActionContributorFactory.getActionTitle(priorAssigned));
		}

		private boolean replaceOrCancel(AFreeplaneAction action, String oldMenuItemTitle) {
			if (askForReplaceShortcutViaDialog(oldMenuItemTitle)) {
				setAccelerator(action, null);
				final String shortcutKey = getPropertyKey(action.getKey());
				keysetProps.setProperty(shortcutKey, "");
				return true;
			} else {
				return false;
			}
		}

		public boolean isValid(final KeyStroke keystroke, final Character keyChar) {
			if (keystroke == null) {
				return true;
			}
			if (actionMap.containsKey(action.getKey())) {
				return true;
			}
			if (keyChar != KeyEvent.CHAR_UNDEFINED && (keystroke.getModifiers() & (Event.ALT_MASK | Event.CTRL_MASK | Event.META_MASK)) == 0) {
				final String keyTypeActionString = ResourceController.getResourceController().getProperty("key_type_action",
						FirstAction.EDIT_CURRENT.toString());
				FirstAction keyTypeAction = FirstAction.valueOf(keyTypeActionString);
				return FirstAction.IGNORE.equals(keyTypeAction);
			}
			if (!checkForOverwriteShortcut(keystroke)) {
				return false;
			}
			final KeyStroke derivedKS = FreeplaneMenuBar.derive(keystroke, keyChar);
			if (derivedKS == keystroke) {
				return true;
			}
			return checkForOverwriteShortcut(derivedKS);
		}
	}
}
