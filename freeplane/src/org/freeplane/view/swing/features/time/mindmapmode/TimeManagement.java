/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
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
package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.components.calendar.JCalendar;
import org.freeplane.core.ui.components.calendar.JDayChooser;
import org.freeplane.core.ui.components.calendar.JTripleCalendar;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.format.FormatController;
import org.freeplane.features.format.FormattedDate;
import org.freeplane.features.format.PatternFormat;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.INodeSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.script.IScriptEditorStarter;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;

/**
 * @author foltin
 */
class TimeManagement implements PropertyChangeListener, IMapSelectionListener {

	class JTimePanel extends JPanel 
	{
        private static final long serialVersionUID = 1L;
		private JButton setReminderButton;
		private JButton removeReminderButton;
		private ComboBoxEditor scriptEditor;
		private JCalendar calendarComponent;

		public JTimePanel(boolean useTriple, int axis) {
	        super();
	        init(useTriple, axis);
	        final NodeModel selected = reminderHook.getModeController().getMapController().getSelectedNode();
	        update(selected);
        }
		
		public void update(NodeModel node){
			if(node == null)
				return;
			final ReminderExtension reminder = ReminderExtension.getExtension(node);
			final boolean reminderIsSet = reminder != null;
			removeReminderButton.setEnabled(reminderIsSet);
			if(reminderIsSet){
				TimeManagement.this.calendar.setTimeInMillis(reminder.getRemindUserAt());
				calendarComponent.setCalendar(TimeManagement.this.calendar);
				if(scriptEditor != null)
					scriptEditor.setItem(reminder.getScript());
			}
			else
				if(scriptEditor != null)
					scriptEditor.setItem(null);
		}

		private void init(boolean useTriple, int axis) {
			final JComponent calendarContainer;
			if (useTriple) {
				final JTripleCalendar trippleCalendar = new JTripleCalendar();
				calendarComponent = trippleCalendar.getCalendar();
				calendarContainer = trippleCalendar;
			}
			else {
				calendarComponent = new JCalendar();
				calendarContainer = calendarComponent;
			}
			calendarComponent.setCalendar(TimeManagement.this.calendar);
			if (dialog != null) {
				dialog.addWindowFocusListener(new WindowAdapter() {
					@Override
					public void windowGainedFocus(WindowEvent e) {
						calendarComponent.getDayChooser().setFocus();
					}
				});
			}
			calendarComponent.setMaximumSize(calendarComponent.getPreferredSize());
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(Box.createHorizontalGlue());
			calendarComponent.getDayChooser().addPropertyChangeListener(TimeManagement.this);
			calendarContainer.setAlignmentX(0.5f);
			add(calendarContainer);
			Box buttonBox = new Box(axis);
			buttonBox.setAlignmentX(0.5f);
			add(buttonBox);
			add(Box.createVerticalStrut(5));
			final Dimension btnSize = new Dimension();
			{
				final JButton todayButton = new JButton(getResourceString("plugins/TimeManagement.xml_todayButton"));
				todayButton.addActionListener(new ActionListener() {
					public void actionPerformed(final ActionEvent arg0) {
						final Calendar currentTime = Calendar.getInstance();
						currentTime.set(Calendar.SECOND, 0);
						TimeManagement.this.calendar.setTimeInMillis(currentTime.getTimeInMillis());
						calendarComponent.setCalendar(TimeManagement.this.calendar);
					}
				});
				increaseSize(btnSize, todayButton);
				buttonBox.add(todayButton);
			}
			{
				final JComboBox dateFormatChooser = createDateFormatChooser();
				increaseSize(btnSize, dateFormatChooser);
				buttonBox.add(dateFormatChooser);
			}
			{
				final JButton appendButton = new JButton(getResourceString("plugins/TimeManagement.xml_appendButton"));
				if (dialog == null) {
					appendButton.setFocusable(false);
				}
				appendButton.addMouseListener(new MouseAdapter() {
					@Override
	                public void mouseClicked(MouseEvent e) {
						insertTime(dialog, appendButton);
					}
				});
				increaseSize(btnSize, appendButton);
				buttonBox.add(appendButton);
			}
			{
				scriptEditor = null;
				IScriptEditorStarter editor = (IScriptEditorStarter) reminderHook.getModeController().getExtension(IScriptEditorStarter.class);
				if(editor != null){
					scriptEditor = editor.createComboBoxEditor(new Dimension(600, 400));
					Component scriptButton = scriptEditor.getEditorComponent();
					increaseSize(btnSize, scriptButton);
					buttonBox.add(scriptButton);
				}
			}
			{
				setReminderButton = new JButton(getResourceString("plugins/TimeManagement.xml_reminderButton"));
				setReminderButton.setToolTipText(getResourceString("plugins/TimeManagement.xml_reminderButton_tooltip"));
				setReminderButton.addMouseListener(new MouseAdapter() {
					@Override
	                public void mouseClicked(MouseEvent e) {
						addReminder();
					}
				});
				increaseSize(btnSize, setReminderButton);
				buttonBox.add(setReminderButton);
			}
			{
				removeReminderButton = new JButton(
				    getResourceString("plugins/TimeManagement.xml_removeReminderButton"));
				removeReminderButton.setToolTipText(getResourceString("plugins/TimeManagement.xml_removeReminderButton_tooltip"));
				removeReminderButton.addMouseListener(new MouseAdapter() {

					@Override
	                public void mouseClicked(MouseEvent e) {
						removeReminder();
	               }
					
				});
				increaseSize(btnSize, removeReminderButton);
				buttonBox.add(removeReminderButton);
			}
			if (dialog != null) {
				final JButton cancelButton = new JButton(getResourceString("plugins/TimeManagement.xml_closeButton"));
				cancelButton.addMouseListener(new MouseAdapter() {

					@Override
	                public void mouseClicked(MouseEvent e) {
						disposeDialog();
					}
				});
				increaseSize(btnSize, cancelButton);
				buttonBox.add(cancelButton);
			}
			for (int i = 0; i < buttonBox.getComponentCount(); i++) {
				buttonBox.getComponent(i).setMaximumSize(btnSize);
			}
        }
        
		private void addReminder() {
			final Date date = getCalendarDate();
			String script = null;
			if(scriptEditor != null){
				script = (String) scriptEditor.getItem();
				if(script != null && "".equals(script.trim()))
					script = null;
			}
			Controller controller = Controller.getCurrentController();
			for (final NodeModel node : controller.getModeController().getMapController().getSelectedNodes()) {
				final ReminderExtension alreadyPresentHook = ReminderExtension.getExtension(node);
				if (alreadyPresentHook != null) {
					final Object[] messageArguments = { new Date(alreadyPresentHook.getRemindUserAt()), date };
					final MessageFormat formatter = new MessageFormat(
					    getResourceString("plugins/TimeManagement.xml_reminderNode_onlyOneDate"));
					final String message = formatter.format(messageArguments);
					final int result = JOptionPane.showConfirmDialog(controller.getViewController().getFrame(), message,
					    "Freeplane", JOptionPane.YES_NO_OPTION);
					if (result == JOptionPane.NO_OPTION) {
						return;
					}
					reminderHook.undoableToggleHook(node);
				}
				final ReminderExtension reminderExtension = new ReminderExtension(node);
				reminderExtension.setRemindUserAt(date.getTime());
				reminderExtension.setScript(script);
				reminderHook.undoableActivateHook(node, reminderExtension);
			}
		}

		private void removeReminder() {
	        for (final NodeModel node : getMindMapController().getMapController().getSelectedNodes()) {
				final ReminderExtension alreadyPresentHook = ReminderExtension.getExtension(node);
				if (alreadyPresentHook != null) {
					reminderHook.undoableToggleHook(node);
				}
			}
	    }
		private void increaseSize(final Dimension btnSize, final Component comp) {
		    final Dimension preferredSize = comp.getPreferredSize();
		    btnSize.width =  Math.max(btnSize.width, preferredSize.width);
		    btnSize.height =  Math.max(btnSize.height, preferredSize.height);
	    }

	}
	private Calendar calendar;
	public final static String REMINDER_HOOK_NAME = "plugins/TimeManagementReminder.xml";
	private static TimeManagement sCurrentlyOpenTimeManagement = null;
	private JDialog dialog;
	private final ReminderHook reminderHook;
	private SimpleDateFormat dateFormat;
	private INodeChangeListener nodeChangeListener;
	private INodeSelectionListener nodeSelectionListener;

	public TimeManagement( final ReminderHook reminderHook) {
		this.reminderHook = reminderHook;
		Controller.getCurrentController().getMapViewManager().addMapSelectionListener(this);
	}

	
	public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
	}

	public void afterMapClose(final MapModel oldMap) {
	}

	public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
		disposeDialog();
	}

	/**
	 *
	 */
	private void disposeDialog() {
		if (dialog == null) {
			return;
		}
		getMindMapController().getMapController().removeNodeSelectionListener(nodeSelectionListener);
		nodeSelectionListener = null;
		getMindMapController().getMapController().removeNodeChangeListener(nodeChangeListener);
		nodeChangeListener = null;
		dialog.setVisible(false);
		dialog.dispose();
		dialog = null;
		TimeManagement.sCurrentlyOpenTimeManagement = null;
	}

	private FormattedDate getCalendarDate() {
		return new FormattedDate(calendar.getTime().getTime(), dateFormat);
	}

	private ModeController getMindMapController() {
		return Controller.getCurrentModeController();
	}

	private String getResourceString(final String string) {
		return TextUtils.getText(string);
	}


	public void propertyChange(final PropertyChangeEvent event) {
		if (event.getPropertyName().equals(JDayChooser.DAY_PROPERTY)) {
		}
	}

	void showDialog() {
		if (TimeManagement.sCurrentlyOpenTimeManagement != null) {
			TimeManagement.sCurrentlyOpenTimeManagement.dialog.getContentPane().setVisible(true);
			return;
		}
		TimeManagement.sCurrentlyOpenTimeManagement = this;
		dialog = new JDialog(Controller.getCurrentController().getViewController().getFrame(), false /*not modal*/);
		final JTimePanel timePanel =createTimePanel(dialog, true, BoxLayout.X_AXIS);
		nodeSelectionListener = new INodeSelectionListener() {
			public void onSelect(NodeModel node) {
				timePanel.update(node);
			}
			
			public void onDeselect(NodeModel node) {
			}
		};
		getMindMapController().getMapController().addNodeSelectionListener(nodeSelectionListener);
		nodeChangeListener = new INodeChangeListener() {
			public void nodeChanged(NodeChangeEvent event) {
				final NodeModel node = event.getNode();
				if(event.getProperty().equals(ReminderExtension.class) && node.equals(getMindMapController().getMapController().getSelectedNode()))
						timePanel.update(node);
			}
		};
		getMindMapController().getMapController().addNodeChangeListener(nodeChangeListener);
		
		dialog.setTitle(getResourceString("plugins/TimeManagement.xml_WindowTitle"));
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent event) {
				disposeDialog();
			}
		});
		final Action action = new AbstractAction() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent arg0) {
				disposeDialog();
			}
		};
		UITools.addEscapeActionToDialog(dialog, action);
		dialog.setContentPane(timePanel);
		dialog.pack();
		UITools.setBounds(dialog, -1, -1, dialog.getWidth(), dialog.getHeight());
		dialog.setVisible(true);
	}
	
	public JTimePanel createTimePanel(final Dialog dialog, boolean useTriple, int axis) {
		if (this.calendar == null) {
			this.calendar = Calendar.getInstance();
			this.calendar.set(Calendar.SECOND, 0);
			this.calendar.set(Calendar.MILLISECOND, 0);
		}
		JTimePanel contentPane = new JTimePanel(useTriple, axis);
		return contentPane;
	}

	private JComboBox createDateFormatChooser() {
		class DateFormatComboBoxElement {
			private final SimpleDateFormat dateFormat;

			DateFormatComboBoxElement(SimpleDateFormat dateFormat) {
				this.dateFormat = dateFormat;
			}

			SimpleDateFormat getDateFormat() {
				return dateFormat;
			}

			public String toString() {
				//final Date sampleDate = new GregorianCalendar(1999, 11, 31, 23, 59, 59).getTime();
				return dateFormat.format(getCalendarDate());
			}
		}
		final String dateFormatPattern = ResourceController.getResourceController().getProperty(
		    "date_format");
		final Vector<DateFormatComboBoxElement> values = new Vector<DateFormatComboBoxElement>();
		final List<PatternFormat> datePatterns = new FormatController().getDateFormats();
		int selectedIndex = 0;
		for (int i = 0; i < datePatterns.size(); ++i) {
			SimpleDateFormat patternFormat = FormatController.getController(reminderHook.getModeController()).getDateFormat(
			    datePatterns.get(i).getPattern());
			values.add(new DateFormatComboBoxElement(patternFormat));
			if (patternFormat.toPattern().equals(dateFormatPattern)) {
				dateFormat = patternFormat;
				selectedIndex = i;
			}
		}
		final JComboBox dateFormatChooser = new JComboBox(values);
		dateFormatChooser.setFocusable(false);
		if (!datePatterns.isEmpty()){
			dateFormatChooser.setSelectedIndex(selectedIndex);
			dateFormat = ((DateFormatComboBoxElement) (dateFormatChooser.getSelectedItem())).getDateFormat();
		}
		dateFormatChooser.addItemListener(new ItemListener() {
			public void itemStateChanged(final ItemEvent e) {
				dateFormat = ((DateFormatComboBoxElement) e.getItem()).getDateFormat();
				final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
				if(focusOwner instanceof JTable){
					JTable table = (JTable) focusOwner;
					final int[] selectedRows = table.getSelectedRows();
					final int[] selectedColumns = table.getSelectedColumns();
					for(int r : selectedRows)
						for(int c : selectedColumns){
							Object date = table.getValueAt(r, c);
							if(date instanceof FormattedDate){
								final FormattedDate fd = (FormattedDate) date;
								if(! fd.getDateFormat().equals(dateFormat)){
									table.setValueAt(new FormattedDate(fd.getTime(), dateFormat), r, c);
								}
							}
						}
				}
				else{
					ModeController mController = Controller.getCurrentModeController();
					for (final NodeModel node : mController.getMapController().getSelectedNodes()) {
						final MTextController textController = (MTextController) TextController.getController();
						Object date = node.getUserObject();
						if(date instanceof FormattedDate){
							final FormattedDate fd = (FormattedDate) date;
							if(! fd.getDateFormat().equals(dateFormat)){
								textController.setNodeObject(node, new FormattedDate(fd.getTime(), dateFormat));
							}
						}
					}
				}

			}
		});
		dateFormatChooser.setAlignmentX(Component.LEFT_ALIGNMENT);
		return dateFormatChooser;
	}

	void insertTime(final Dialog dialog, final JButton appendButton) {
	    FormattedDate date = getCalendarDate();
	    final String dateAsString = dateFormat.format(date);
	    final Window parentWindow;
	    if (dialog != null) {
	    	parentWindow = (Window) dialog.getParent();
	    }
	    else {
	    	parentWindow = SwingUtilities.getWindowAncestor(appendButton);
	    }
	    final Component mostRecentFocusOwner = parentWindow.getMostRecentFocusOwner();
		if (mostRecentFocusOwner instanceof JTextComponent
		        && !(mostRecentFocusOwner.getClass().getName().contains("JSpinField"))) {
	    	final JTextComponent textComponent = (JTextComponent) mostRecentFocusOwner;
	    	textComponent.replaceSelection(dateAsString);
	    	return;
	    }
	    if(mostRecentFocusOwner instanceof JTable){
	    	JTable table = (JTable) mostRecentFocusOwner;
	    	final int[] selectedRows = table.getSelectedRows();
	    	final int[] selectedColumns = table.getSelectedColumns();
	    	for(int r : selectedRows)
	    		for(int c : selectedColumns)
	    			table.setValueAt(date, r, c);
	    }
	    else{
	    	ModeController mController = Controller.getCurrentModeController();
	    	for (final NodeModel node : mController.getMapController().getSelectedNodes()) {
	    		final MTextController textController = (MTextController) TextController.getController();
	    		textController.setNodeObject(node, date);
	    	}
	    }
    }
}