/**
 * GmailAssistant 2.0 (2008-09-07)
 * Copyright 2008 Zach Scrivena
 * zachscrivena@gmail.com
 * http://gmailassistant.sourceforge.net/
 *
 * Notifier for multiple Gmail and Google Apps email accounts.
 *
 * TERMS AND CONDITIONS:
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.freeshell.zs.gmailassistant;

import java.awt.Component;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import org.freeshell.zs.common.SwingManipulator;


/**
 * "Options" form.
 */
class Options
		extends JFrame
{
	/** parent GmailAssistant object */
	private final GmailAssistant parent;

	/** blinking LED key options */
	private final String[] ledKeyOptions;

	/** are the "General" options selection valid? */
	private	boolean generalValid = false;

	/** are the "Proxy" options selection valid? */
	private	boolean proxyValid = false;

	/** are the "Mail Check" options selection valid? */
	private boolean mailCheckValid = false;

	/** are the "Alerts" options selection valid? */
	private	boolean alertsValid = false;

	/** has the proxy password been edited? */
	private boolean proxyPasswordEdited = false;


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 */
	Options(
			final GmailAssistant parent)
	{
		/*********************
		 * INITIALIZE FIELDS *
		 *********************/

		this.parent = parent;

		/******************************
		 * INITIALIZE FORM COMPONENTS *
		 ******************************/

		initComponents();

		/***************************
		 * CONFIGURE FORM SETTINGS *
		 ***************************/

		setTitle(String.format("Options - %s", parent.name));

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				cancelButton.doClick();
			}
		});

		/* inherit "always on top" behavior of parent */
		try
		{
			setAlwaysOnTop(parent.isAlwaysOnTop());
		}
		catch (Exception e)
		{
			/* ignore */
		}

		/* inherit program icon of parent */
		final List<Image> icons = parent.getIconImages();

		if (!icons.isEmpty())
		{
			setIconImage(icons.get(0));
		}

		/* option fields */
		for (final JTextField c : new JTextField[]
		{
			proxyHostField, proxyPortField, proxyUsernameField, proxyPasswordField,
			mailIntervalField, mailTimeoutField,
			bellField
		})
		{
			c.getDocument().addDocumentListener(new DocumentListenerAdapter()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					checkForm(c);
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					checkForm(c);
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					checkForm(c);
				}
			});
		}

		/* option combo boxes */
		for (final JComboBox c : new JComboBox[] {ledKeyComboBox})
		{
			c.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					checkForm(c);
				}
			});
		}

		final String[] ledKeyStrings = new String[]
		{
			"None",
			"Num-Lock key",
			"Caps-Lock key",
			"Scroll-Lock key"
		};

		ledKeyOptions = new String[]
		{
			"none",
			"num",
			"caps",
			"scroll"
		};

		for (String s : ledKeyStrings)
		{
			ledKeyComboBox.addItem(s);
		}

		/* button: "OK" */
		okButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				accept();
			}
		});

		/* button: "Cancel" */
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				repopulateForm();
				setVisible(false);
			}
		});

		/* add standard editing popup menu to text fields */
		SwingManipulator.addStandardEditingPopupMenu(new JTextField[]
		{
			proxyHostField,
			proxyPortField,
			proxyUsernameField,
			proxyPasswordField,
			mailIntervalField,
			mailTimeoutField,
			bellField
		});

		/* key binding: ENTER key */
		tabbedPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER_OK_BUTTON");

		tabbedPane.getActionMap().put("ENTER_OK_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				okButton.doClick();
			}
		});

		/* key binding: ESCAPE key */
		tabbedPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_CANCEL_BUTTON");

		tabbedPane.getActionMap().put("ESCAPE_CANCEL_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				cancelButton.doClick();
			}
		});

		/* center form on the parent form */
		setLocationRelativeTo(parent);

		/* populate the form */
		repopulateForm();
	}


	/**
	 * Repopulate the form according to the current program properties.
	 * This method must run on the EDT.
	 */
	void repopulateForm()
	{
		/* "General" options */
		topBox.setSelected(parent.properties.getBoolean("always.on.top"));

		/* "Proxy" options */
		proxyHostField.setText(parent.properties.getString("proxy.host"));
		proxyPortField.setText(parent.properties.getString("proxy.port"));
		proxyUsernameField.setText(parent.properties.getString("proxy.username"));
		proxyPasswordField.setText(parent.properties.getString("proxy.password"));
		proxyPasswordEdited = false;

		/* "Mail Check" options */
		mailIntervalField.setText(parent.properties.getAsString("mail.check.interval.milliseconds"));
		mailTimeoutField.setText(parent.properties.getAsString("mail.check.timeout.milliseconds"));

		/* "Alerts" options */
		persistentBox.setSelected(parent.properties.getBoolean("alert.popup.persistent.messages"));
		bellField.setText(parent.properties.getAsString("alert.periodic.bell.interval.milliseconds"));

		ledKeyComboBox.setSelectedIndex(0);
		final String ledKey = parent.properties.getString("alert.led.key");

		for (int i = 0; i < ledKeyOptions.length; i++)
		{
			if (ledKey.equals(ledKeyOptions[i]))
			{
				ledKeyComboBox.setSelectedIndex(i);
				break;
			}
		}

		checkForm(null);
	}


	/**
	 * Accept changes and close the "Options" form.
	 * This method must run on the EDT.
	 */
	private void accept()
	{
		if (!(generalValid && proxyValid && mailCheckValid && alertsValid))
		{
			return;
		}

		/* "General" options */
		parent.properties.setBoolean("always.on.top", topBox.isSelected());
		parent.refreshAlwaysOnTopMode();

		/* "Proxy" options */
		parent.properties.setString("proxy.host", SwingManipulator.getTextJTextField(proxyHostField).trim());
		parent.properties.setString("proxy.port", SwingManipulator.getTextJTextField(proxyPortField).trim());
		parent.properties.setString("proxy.username", SwingManipulator.getTextJTextField(proxyUsernameField).trim());

		if (proxyPasswordEdited)
		{
			final char[] pw = SwingManipulator.getPasswordJPasswordField(proxyPasswordField);
			parent.properties.setString("proxy.password", String.valueOf(pw));
			Arrays.fill(pw, '\0');
		}

		parent.refreshProxyMode();

		/* "Mail Check" options */
		parent.properties.setLong("mail.check.interval.milliseconds", Long.parseLong(SwingManipulator.getTextJTextField(mailIntervalField).trim()));
		parent.properties.setLong("mail.check.timeout.milliseconds", Long.parseLong(SwingManipulator.getTextJTextField(mailTimeoutField).trim()));

		/* "Alerts" options */
		parent.properties.setBoolean("alert.popup.persistent.messages", persistentBox.isSelected());
		parent.properties.setLong("alert.periodic.bell.interval.milliseconds", Long.parseLong(SwingManipulator.getTextJTextField(bellField).trim()));

		final int i = ledKeyComboBox.getSelectedIndex();

		if (i >= 0)
		{
			parent.properties.setString("alert.led.key", ledKeyOptions[i]);
		}

		/* hide form */
		setVisible(false);
	}


	/**
	 * Check user selections on the form for errors.
	 * This method must run on the EDT.
	 *
	 * @param c
	 *     component that has triggered the check; null to perform all checks
	 */
	private void checkForm(
			final Component c)
	{
		boolean checkGeneral = false;
		boolean checkProxy = false;
		boolean checkMailCheck = false;
		boolean checkAlerts = false;

		if ((c == proxyHostField) ||
				(c == proxyPortField) ||
				(c == proxyUsernameField) ||
				(c == proxyPasswordField))
		{
			checkProxy = true;

			if (c == proxyPasswordField)
			{
				proxyPasswordEdited = true;
			}
		}
		else if ((c == mailIntervalField) ||
				(c == mailTimeoutField))
		{
			checkMailCheck = true;
		}
		else if (c == bellField)
		{
			checkAlerts = true;
		}
		else if (c == null)
		{
			checkGeneral = true;
			checkProxy = true;
			checkMailCheck = true;
			checkAlerts = true;
		}

		/*************************
		 * CHECK GENERAL OPTIONS *
		 *************************/

		if (checkGeneral)
		{
			String error = null;

			if (error == null)
			{
				generalValid = true;
				generalError.setText(" ");
			}
			else
			{
				generalValid = false;
				generalError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
			}
		}

		/***********************
		 * CHECK PROXY OPTIONS *
		 ***********************/

		if (checkProxy)
		{
			String error = null;

			final String host = SwingManipulator.getTextJTextField(proxyHostField).trim();
			final String port = SwingManipulator.getTextJTextField(proxyPortField).trim();
			final String username = SwingManipulator.getTextJTextField(proxyUsernameField).trim();
			final char[] password = SwingManipulator.getPasswordJPasswordField(proxyPasswordField);
			final int passwordLength = password.length;
			Arrays.fill(password, '\0');

			if (!port.isEmpty())
			{
				try
				{
					if (Integer.parseInt(port) < 0)
					{
						error = "Proxy port must be a nonnegative integer";
					}
				}
				catch (NumberFormatException e)
				{
					error = "Proxy port must be a nonnegative integer";
				}
			}

			if (username.isEmpty() && (passwordLength > 0))
			{
				error = "Proxy username must be specified if password is specified";
			}

			if (host.isEmpty() != port.isEmpty())
			{
				error = "Both proxy host and port must be specified";
			}

			if (error == null)
			{
				proxyValid = true;
				proxyError.setText(" ");
			}
			else
			{
				proxyValid = false;
				proxyError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
			}
		}

		/****************************
		 * CHECK MAIL CHECK OPTIONS *
		 ****************************/

		if (checkMailCheck)
		{
			String error = null;

			final String interval = SwingManipulator.getTextJTextField(mailIntervalField).trim();
			final String timeout = SwingManipulator.getTextJTextField(mailTimeoutField).trim();

			if (!interval.isEmpty())
			{
				try
				{
					if (Long.parseLong(interval) < 0L)
					{
						error = "Mail check interval must be a nonnegative integer";
					}
				}
				catch (NumberFormatException e)
				{
					error = "Mail check interval must be a nonnegative integer";
				}
			}

			if (!timeout.isEmpty())
			{
				try
				{
					if (Long.parseLong(timeout) < 0L)
					{
						error = "Mail check timeout must be a nonnegative integer";
					}
				}
				catch (NumberFormatException e)
				{
					error = "Mail check timeout must be a nonnegative integer";
				}
			}

			if (timeout.isEmpty())
			{
				error = "Mail check timeout must not be empty";
			}

			if (interval.isEmpty())
			{
				error = "Mail check interval must not be empty";
			}

			if (error == null)
			{
				mailCheckValid = true;
				mailCheckError.setText(" ");
			}
			else
			{
				mailCheckValid = false;
				mailCheckError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
			}
		}

		/************************
		 * CHECK ALERTS OPTIONS *
		 ************************/

		if (checkAlerts)
		{
			String error = null;

			final String bell = SwingManipulator.getTextJTextField(bellField).trim();

			if (!bell.isEmpty())
			{
				try
				{
					if (Long.parseLong(bell) < 0)
					{
						error = "Periodic bell interval must be a nonnegative integer";
					}
				}
				catch (NumberFormatException e)
				{
					error = "Periodic bell interval must be a nonnegative integer";
				}
			}

			if (bell.isEmpty())
			{
				error = "Periodic bell interval must not be empty";
			}

			if (error == null)
			{
				alertsValid = true;
				alertsError.setText(" ");
			}
			else
			{
				alertsValid = false;
				alertsError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
			}
		}

		/**********************
		 * ALL OPTIONS VALID? *
		 **********************/

		if (generalValid && proxyValid && mailCheckValid && alertsValid)
		{
			okButton.setEnabled(true);
		}
	}


	/**
	 * Present the form for changing options.
	 */
	void showForm()
	{
		setVisible(true);
		setExtendedState(JFrame.NORMAL);
		toFront();
	}

	/***************************
	 * NETBEANS-GENERATED CODE *
	 ***************************/

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabbedPane = new javax.swing.JTabbedPane();
        generalPanel = new javax.swing.JPanel();
        topBox = new javax.swing.JCheckBox();
        generalError = new javax.swing.JLabel();
        proxyPanel = new javax.swing.JPanel();
        proxyLabel = new javax.swing.JLabel();
        proxyHostLabel = new javax.swing.JLabel();
        proxyHostField = new javax.swing.JTextField();
        proxyPortField = new javax.swing.JTextField();
        proxyPortLabel = new javax.swing.JLabel();
        proxyUsernameLabel = new javax.swing.JLabel();
        proxyPasswordLabel = new javax.swing.JLabel();
        proxyUsernameField = new javax.swing.JTextField();
        proxyPasswordField = new javax.swing.JPasswordField();
        proxyError = new javax.swing.JLabel();
        mailCheckPanel = new javax.swing.JPanel();
        mailIntervalLabel = new javax.swing.JLabel();
        mailIntervalField = new javax.swing.JTextField();
        mailTimeoutLabel = new javax.swing.JLabel();
        mailTimeoutField = new javax.swing.JTextField();
        mailCheckError = new javax.swing.JLabel();
        alertsPanel = new javax.swing.JPanel();
        alertsError = new javax.swing.JLabel();
        alertsPopupPanel = new javax.swing.JPanel();
        persistentBox = new javax.swing.JCheckBox();
        alertsPeriodicBellPanel = new javax.swing.JPanel();
        bellLabel = new javax.swing.JLabel();
        bellField = new javax.swing.JTextField();
        alertsLedPanel = new javax.swing.JPanel();
        ledKeyComboBox = new javax.swing.JComboBox();
        ledKeyLabel = new javax.swing.JLabel();
        buttonsPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        topBox.setMnemonic('T');
        topBox.setText("Always on top");
        topBox.setToolTipText("Keep the GmailAssistant windows above other programs on the desktop");

        generalError.setText("<html><font color='red'>General error</font></html>");

        javax.swing.GroupLayout generalPanelLayout = new javax.swing.GroupLayout(generalPanel);
        generalPanel.setLayout(generalPanelLayout);
        generalPanelLayout.setHorizontalGroup(
            generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(generalPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(generalError, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
                    .addComponent(topBox))
                .addContainerGap())
        );
        generalPanelLayout.setVerticalGroup(
            generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(generalPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(topBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 181, Short.MAX_VALUE)
                .addComponent(generalError)
                .addContainerGap())
        );

        tabbedPane.addTab("General", generalPanel);

        proxyLabel.setText("<html>GmailAssistant can use a proxy server that supports the SOCKS v4 or v5 protocol.<html>");

        proxyHostLabel.setDisplayedMnemonic('h');
        proxyHostLabel.setLabelFor(proxyHostField);
        proxyHostLabel.setText("Host:");
        proxyHostLabel.setToolTipText("SOCKS proxy host (e.g. socks.mydomain.com)");

        proxyHostField.setToolTipText("SOCKS proxy host (e.g. socks.mydomain.com)");

        proxyPortField.setToolTipText("SOCKS proxy port (e.g. 1080)");

        proxyPortLabel.setDisplayedMnemonic('t');
        proxyPortLabel.setLabelFor(proxyPortField);
        proxyPortLabel.setText("Port:");
        proxyPortLabel.setToolTipText("SOCKS proxy port (e.g. 1080)");

        proxyUsernameLabel.setDisplayedMnemonic('u');
        proxyUsernameLabel.setLabelFor(proxyUsernameField);
        proxyUsernameLabel.setText("Username:");
        proxyUsernameLabel.setToolTipText("SOCKS proxy username");

        proxyPasswordLabel.setDisplayedMnemonic('p');
        proxyPasswordLabel.setLabelFor(proxyPasswordField);
        proxyPasswordLabel.setText("Password:");
        proxyPasswordLabel.setToolTipText("SOCKS proxy password");

        proxyUsernameField.setToolTipText("SOCKS proxy username");

        proxyPasswordField.setToolTipText("SOCKS proxy password");

        proxyError.setText("<html><font color='red'>Proxy error</font></html>");

        javax.swing.GroupLayout proxyPanelLayout = new javax.swing.GroupLayout(proxyPanel);
        proxyPanel.setLayout(proxyPanelLayout);
        proxyPanelLayout.setHorizontalGroup(
            proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, proxyPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(proxyError, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
                    .addComponent(proxyLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, proxyPanelLayout.createSequentialGroup()
                        .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(proxyUsernameLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(proxyPortLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(proxyHostLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(proxyPasswordLabel, javax.swing.GroupLayout.Alignment.LEADING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(proxyHostField, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                            .addComponent(proxyPortField, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                            .addComponent(proxyUsernameField, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                            .addComponent(proxyPasswordField, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE))))
                .addContainerGap())
        );
        proxyPanelLayout.setVerticalGroup(
            proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(proxyPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(proxyLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(proxyHostLabel)
                    .addComponent(proxyHostField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(proxyPortLabel)
                    .addComponent(proxyPortField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(proxyUsernameLabel)
                    .addComponent(proxyUsernameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(proxyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(proxyPasswordLabel)
                    .addComponent(proxyPasswordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 34, Short.MAX_VALUE)
                .addComponent(proxyError)
                .addContainerGap())
        );

        tabbedPane.addTab("Proxy", proxyPanel);

        mailIntervalLabel.setDisplayedMnemonic('v');
        mailIntervalLabel.setLabelFor(mailIntervalField);
        mailIntervalLabel.setText("Mail check interval (milliseconds):");
        mailIntervalLabel.setToolTipText("Interval (in milliseconds) between consecutive mail checks for each account");

        mailIntervalField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        mailIntervalField.setText("60000");
        mailIntervalField.setToolTipText("Interval (in milliseconds) between consecutive mail checks for each account");

        mailTimeoutLabel.setDisplayedMnemonic('u');
        mailTimeoutLabel.setLabelFor(mailTimeoutField);
        mailTimeoutLabel.setText("Mail check timeout (milliseconds):");
        mailTimeoutLabel.setToolTipText("Delay (in milliseconds) before an in-progress mail check is restarted");

        mailTimeoutField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        mailTimeoutField.setText("600000");
        mailTimeoutField.setToolTipText("Delay (in milliseconds) before an in-progress mail check is restarted");

        mailCheckError.setText("<html><font color='red'>Mail check error</font></html>");

        javax.swing.GroupLayout mailCheckPanelLayout = new javax.swing.GroupLayout(mailCheckPanel);
        mailCheckPanel.setLayout(mailCheckPanelLayout);
        mailCheckPanelLayout.setHorizontalGroup(
            mailCheckPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mailCheckPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mailCheckPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(mailCheckError, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mailCheckPanelLayout.createSequentialGroup()
                        .addComponent(mailIntervalLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mailIntervalField, javax.swing.GroupLayout.DEFAULT_SIZE, 115, Short.MAX_VALUE))
                    .addGroup(mailCheckPanelLayout.createSequentialGroup()
                        .addComponent(mailTimeoutLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mailTimeoutField, javax.swing.GroupLayout.DEFAULT_SIZE, 115, Short.MAX_VALUE)))
                .addContainerGap())
        );
        mailCheckPanelLayout.setVerticalGroup(
            mailCheckPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mailCheckPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mailCheckPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mailIntervalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(mailIntervalField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(mailCheckPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mailTimeoutLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(mailTimeoutField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 142, Short.MAX_VALUE)
                .addComponent(mailCheckError, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        tabbedPane.addTab("Mail Check", mailCheckPanel);

        alertsError.setText("<html><font color='red'>Alerts error</font></html>");

        alertsPopupPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Popup"));

        persistentBox.setMnemonic('p');
        persistentBox.setText("Persistent popup messages");
        persistentBox.setToolTipText("Keep the popup messages visible after they are displayed");

        javax.swing.GroupLayout alertsPopupPanelLayout = new javax.swing.GroupLayout(alertsPopupPanel);
        alertsPopupPanel.setLayout(alertsPopupPanelLayout);
        alertsPopupPanelLayout.setHorizontalGroup(
            alertsPopupPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertsPopupPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(persistentBox, javax.swing.GroupLayout.DEFAULT_SIZE, 252, Short.MAX_VALUE)
                .addContainerGap())
        );
        alertsPopupPanelLayout.setVerticalGroup(
            alertsPopupPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(persistentBox)
        );

        alertsPeriodicBellPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Periodic Bell"));

        bellLabel.setDisplayedMnemonic('b');
        bellLabel.setLabelFor(bellField);
        bellLabel.setText("Periodic bell interval (milliseconds):");
        bellLabel.setToolTipText("Interval (in milliseconds) between consecutive bells");

        bellField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        bellField.setText("30000");
        bellField.setToolTipText("Interval (in milliseconds) between consecutive bells");

        javax.swing.GroupLayout alertsPeriodicBellPanelLayout = new javax.swing.GroupLayout(alertsPeriodicBellPanel);
        alertsPeriodicBellPanel.setLayout(alertsPeriodicBellPanelLayout);
        alertsPeriodicBellPanelLayout.setHorizontalGroup(
            alertsPeriodicBellPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertsPeriodicBellPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(bellLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bellField, javax.swing.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                .addContainerGap())
        );
        alertsPeriodicBellPanelLayout.setVerticalGroup(
            alertsPeriodicBellPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertsPeriodicBellPanelLayout.createSequentialGroup()
                .addGroup(alertsPeriodicBellPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bellLabel)
                    .addComponent(bellField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        alertsLedPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Keyboard LED"));

        ledKeyLabel.setDisplayedMnemonic('d');
        ledKeyLabel.setLabelFor(ledKeyComboBox);
        ledKeyLabel.setText("Blink LED of");

        javax.swing.GroupLayout alertsLedPanelLayout = new javax.swing.GroupLayout(alertsLedPanel);
        alertsLedPanel.setLayout(alertsLedPanelLayout);
        alertsLedPanelLayout.setHorizontalGroup(
            alertsLedPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertsLedPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ledKeyLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ledKeyComboBox, 0, 185, Short.MAX_VALUE)
                .addContainerGap())
        );
        alertsLedPanelLayout.setVerticalGroup(
            alertsLedPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertsLedPanelLayout.createSequentialGroup()
                .addGroup(alertsLedPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ledKeyLabel)
                    .addComponent(ledKeyComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout alertsPanelLayout = new javax.swing.GroupLayout(alertsPanel);
        alertsPanel.setLayout(alertsPanelLayout);
        alertsPanelLayout.setHorizontalGroup(
            alertsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, alertsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(alertsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(alertsLedPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(alertsPeriodicBellPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(alertsPopupPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(alertsError, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE))
                .addContainerGap())
        );
        alertsPanelLayout.setVerticalGroup(
            alertsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(alertsPopupPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(alertsPeriodicBellPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(alertsLedPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 23, Short.MAX_VALUE)
                .addComponent(alertsError)
                .addContainerGap())
        );

        tabbedPane.addTab("Alerts", alertsPanel);

        buttonsPanel.setLayout(new java.awt.GridLayout(1, 2));

        okButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/tick.png"))); // NOI18N
        okButton.setMnemonic('O');
        okButton.setText("OK");
        okButton.setToolTipText("Save current settings");
        buttonsPanel.add(okButton);

        cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/cross.png"))); // NOI18N
        cancelButton.setMnemonic('C');
        cancelButton.setText("Cancel");
        cancelButton.setToolTipText("Cancel changes");
        buttonsPanel.add(cancelButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tabbedPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE)
                    .addComponent(buttonsPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 261, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel alertsError;
    private javax.swing.JPanel alertsLedPanel;
    private javax.swing.JPanel alertsPanel;
    private javax.swing.JPanel alertsPeriodicBellPanel;
    private javax.swing.JPanel alertsPopupPanel;
    private javax.swing.JTextField bellField;
    private javax.swing.JLabel bellLabel;
    private javax.swing.JPanel buttonsPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel generalError;
    private javax.swing.JPanel generalPanel;
    private javax.swing.JComboBox ledKeyComboBox;
    private javax.swing.JLabel ledKeyLabel;
    private javax.swing.JLabel mailCheckError;
    private javax.swing.JPanel mailCheckPanel;
    private javax.swing.JTextField mailIntervalField;
    private javax.swing.JLabel mailIntervalLabel;
    private javax.swing.JTextField mailTimeoutField;
    private javax.swing.JLabel mailTimeoutLabel;
    private javax.swing.JButton okButton;
    private javax.swing.JCheckBox persistentBox;
    private javax.swing.JLabel proxyError;
    private javax.swing.JTextField proxyHostField;
    private javax.swing.JLabel proxyHostLabel;
    private javax.swing.JLabel proxyLabel;
    private javax.swing.JPanel proxyPanel;
    private javax.swing.JPasswordField proxyPasswordField;
    private javax.swing.JLabel proxyPasswordLabel;
    private javax.swing.JTextField proxyPortField;
    private javax.swing.JLabel proxyPortLabel;
    private javax.swing.JTextField proxyUsernameField;
    private javax.swing.JLabel proxyUsernameLabel;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JCheckBox topBox;
    // End of variables declaration//GEN-END:variables
}
