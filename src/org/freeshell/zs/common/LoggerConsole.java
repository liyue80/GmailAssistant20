/**
 * LoggerConsole.java
 * Copyright 2008 Zach Scrivena
 * zachscrivena@gmail.com
 * http://zs.freeshell.org/
 *
 * TERMS AND CONDITIONS:
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
 */
package org.freeshell.zs.common;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;


/**
 * Simple console for logging messages.
 * Messages are stored in a circular array of fixed capacity.
 * All the methods of this class are thread-safe.
 */
public class LoggerConsole
		extends JFrame
{
	/** time format string (yyyy-MM-dd HH:mm:ss.SSS) */
	private static final String TIME_FORMAT_STRING = "%1$tF %1$tT.%1$tL";

	/** default capacity of the logger (1000) */
	private static final int DEFAULT_CAPACITY = 1000;

	/** capacity of the logger, i.e. maximum number of messages to store */
	private final int capacity;

	/** circular array of logged messages */
	private final String[] messages;

	/** index in <code>messages</code> where the next message should be stored */
	private int messagesIndex;

	/** is the logger currently on? */
	private volatile boolean isLoggerOn = true;


	/**
	 * Construct a logger console with the specified title and capacity of 1000.
	 *
	 * @param title
	 *     title of the logger console
	 */
	public LoggerConsole(
			final String title)
	{
		this(title, DEFAULT_CAPACITY);
	}


	/**
	 * Construct a logger console with the specified title and capacity.
	 *
	 * @param title
	 *     title of the logger console
	 * @param capacity
	 *     capacity of the logger, i.e. maximum number of messages to store
	 */
	public LoggerConsole(
			final String title,
			final int capacity)
	{
		this.capacity = capacity;
		messages = new String[capacity];
		Arrays.fill(messages, null);
		messagesIndex = 0;

		/******************************
		 * INITIALIZE FORM COMPONENTS *
		 ******************************/

		initComponents();

		/*****************************
		 * CONFIGURE FORM COMPONENTS *
		 *****************************/

		setTitle(title);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowIconified(WindowEvent e)
			{
				hideForm();
			}

			@Override
			public void windowDeiconified(WindowEvent e)
			{
				showForm();
			}

			@Override
			public void windowClosing(WindowEvent e)
			{
				hideForm();
			}
		});

		/* console text area */
		consoleText.setFont(new Font(
				Font.DIALOG,
				Font.PLAIN,
				consoleText.getFont().getSize() - 2));

		/* radio buttons: Logger "On", "Off" */
		onButton.setSelected(isLoggerOn);
		offButton.setSelected(!isLoggerOn);

		for (JRadioButton b : new JRadioButton[] {onButton, offButton})
		{
			b.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					isLoggerOn = onButton.isSelected();
				}
			});
		}

		/* button: "Reset" */
		resetButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				reset();
			}
		});

		/* button: "Refresh" */
		refreshButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				refresh();
			}
		});

		/* button: "Close" */
		closeButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				hideForm();
			}
		});

		/* key binding: ESCAPE key */
		consolePane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_CLOSE_BUTTON");

		consolePane.getActionMap().put("ESCAPE_CLOSE_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				closeButton.doClick();
			}
		});

		/* position in the center of the screen */
		setLocationRelativeTo(null);
	}


	/**
	 * Log the specified message.
	 * The message will be automatically timestamped.
	 * This method can be called on any thread.
	 *
	 * @param format
	 *     format string
	 * @param args
	 *     arguments referenced by the format specifiers in the format string
	 */
	public void log(
			final String format,
			final Object... args)
	{
		if (isLoggerOn)
		{
			synchronized (messages)
			{
				final String message = String.format(format, args);

				messages[messagesIndex++] = String.format(
						Locale.ENGLISH,
						"[" + TIME_FORMAT_STRING + "] %2$s\n",
						new Date(), message);

				if (messagesIndex >= capacity)
				{
					messagesIndex = 0;
				}
			}
		}
	}


	/**
	 * Reset the stored messages.
	 * This method can be called on any thread.
	 */
	public void reset()
	{
		synchronized (messages)
		{
			Arrays.fill(messages, null);
		}

		refresh();
	}


	/**
	 * Refresh the console.
	 * This method can be called on any thread.
	 */
	public void refresh()
	{
		consoleText.setText("");

		synchronized (messages)
		{
			for (int i = 0; i < capacity; i++)
			{
				final String s = messages[(messagesIndex + i) % capacity];

				if (s != null)
				{
					consoleText.append(s);
				}
			}
		}

		/* scroll to the bottom of the console */
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					consoleText.setCaretPosition(consoleText.getDocument().getLength());
				}
				catch (Exception e)
				{
					/* ignore */
				}
			}
		});
	}


	/**
	 * Show the console.
	 * This method can be called on any thread.
	 */
	public void showForm()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				setVisible(true);
				setExtendedState(JFrame.NORMAL);
				toFront();
				closeButton.requestFocus();
			}
		});

		refresh();
	}


	/**
	 * Hide the console.
	 * This method can be called on any thread.
	 */
	public void hideForm()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				setVisible(false);
			}
		});
	}

	/***************************
	 * NETBEANS-GENERATED CODE *
	 ***************************/

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
	// <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		onOffGroup = new javax.swing.ButtonGroup();
		buttonsPanel = new javax.swing.JPanel();
		onOffPanel = new javax.swing.JPanel();
		onButton = new javax.swing.JRadioButton();
		offButton = new javax.swing.JRadioButton();
		resetButton = new javax.swing.JButton();
		refreshButton = new javax.swing.JButton();
		closeButton = new javax.swing.JButton();
		consolePanel = new javax.swing.JPanel();
		consolePane = new javax.swing.JScrollPane();
		consoleText = new javax.swing.JTextArea();

		setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		setMinimumSize(new java.awt.Dimension(600, 100));

		onOffPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Logger"));

		onOffGroup.add(onButton);
		onButton.setMnemonic('o');
		onButton.setSelected(true);
		onButton.setText("On");

		onOffGroup.add(offButton);
		offButton.setMnemonic('f');
		offButton.setText("Off");

		javax.swing.GroupLayout onOffPanelLayout = new javax.swing.GroupLayout(onOffPanel);
		onOffPanel.setLayout(onOffPanelLayout);
		onOffPanelLayout.setHorizontalGroup(
			onOffPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(onOffPanelLayout.createSequentialGroup()
				.addContainerGap()
				.addGroup(onOffPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addComponent(onButton)
					.addComponent(offButton))
				.addContainerGap(14, Short.MAX_VALUE))
		);
		onOffPanelLayout.setVerticalGroup(
			onOffPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(onOffPanelLayout.createSequentialGroup()
				.addContainerGap()
				.addComponent(onButton)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addComponent(offButton)
				.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);

		resetButton.setMnemonic('t');
		resetButton.setText("Reset");

		refreshButton.setMnemonic('r');
		refreshButton.setText("Refresh");

		closeButton.setMnemonic('c');
		closeButton.setText("Close");

		javax.swing.GroupLayout buttonsPanelLayout = new javax.swing.GroupLayout(buttonsPanel);
		buttonsPanel.setLayout(buttonsPanelLayout);
		buttonsPanelLayout.setHorizontalGroup(
			buttonsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, buttonsPanelLayout.createSequentialGroup()
				.addContainerGap()
				.addGroup(buttonsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
					.addComponent(refreshButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(resetButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(closeButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(onOffPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
				.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);
		buttonsPanelLayout.setVerticalGroup(
			buttonsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, buttonsPanelLayout.createSequentialGroup()
				.addContainerGap()
				.addComponent(onOffPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 13, Short.MAX_VALUE)
				.addComponent(resetButton)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(refreshButton)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(closeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addContainerGap())
		);

		getContentPane().add(buttonsPanel, java.awt.BorderLayout.LINE_END);

		consoleText.setColumns(20);
		consoleText.setEditable(false);
		consoleText.setRows(5);
		consolePane.setViewportView(consoleText);

		javax.swing.GroupLayout consolePanelLayout = new javax.swing.GroupLayout(consolePanel);
		consolePanel.setLayout(consolePanelLayout);
		consolePanelLayout.setHorizontalGroup(
			consolePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGap(0, 481, Short.MAX_VALUE)
			.addGroup(consolePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(consolePanelLayout.createSequentialGroup()
					.addContainerGap()
					.addComponent(consolePane, javax.swing.GroupLayout.DEFAULT_SIZE, 471, Short.MAX_VALUE)))
		);
		consolePanelLayout.setVerticalGroup(
			consolePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGap(0, 205, Short.MAX_VALUE)
			.addGroup(consolePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(consolePanelLayout.createSequentialGroup()
					.addContainerGap()
					.addComponent(consolePane, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE)
					.addContainerGap()))
		);

		getContentPane().add(consolePanel, java.awt.BorderLayout.CENTER);

		pack();
	}// </editor-fold>//GEN-END:initComponents
	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JPanel buttonsPanel;
	private javax.swing.JButton closeButton;
	private javax.swing.JScrollPane consolePane;
	private javax.swing.JPanel consolePanel;
	private javax.swing.JTextArea consoleText;
	private javax.swing.JRadioButton offButton;
	private javax.swing.JRadioButton onButton;
	private javax.swing.ButtonGroup onOffGroup;
	private javax.swing.JPanel onOffPanel;
	private javax.swing.JButton refreshButton;
	private javax.swing.JButton resetButton;
	// End of variables declaration//GEN-END:variables
}
