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

import com.sun.mail.imap.IMAPFolder;
import javax.mail.event.MessageCountEvent;
import org.freeshell.zs.gmailassistant.Account;
import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.mail.AuthenticationFailedException;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountListener;
import javax.mail.search.FlagTerm;
import javax.mail.search.SearchTerm;
import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import org.freeshell.zs.common.Debug;
import org.freeshell.zs.common.SimpleProperties;
import org.freeshell.zs.common.SwingManipulator;


/**
 * Represent a Gmail or Google Apps email account.
 */
class Account
		extends JFrame
		implements Comparable<Account>
{
	/** timer refresh interval */
	private static final long REFRESH_INTERVAL_MILLISECONDS = 200L;

	/** parent GmailAssistant object */
	private final GmailAssistant parent;

	/** account ID */
	final int accountId;

	/** radio buttons corresponding to the "Notify On" modes */
	private final JRadioButton[] notifyOnRadios;

	/** property values corresponding to the "Notify On" modes */
	private final String[] notifyOnProperties;

	/** text fields corresponding to the notify labels */
	private final JTextField[] notifyLabelFields;

	/** property keys corresponding to the notify labels */
	private final String[] notifyLabelProperties;

	/* check boxes corresponding to the alerts */
	private final JCheckBox[] alertsBoxes;

	/** property keys corresponding to the alerts */
	private final String[] alertsProperties;

	/** has the username been edited? */
	private volatile boolean usernameEdited = false;

	/** has the password been edited? */
	private volatile boolean passwordEdited = false;

	/** are the login credentials (username and password) valid? */
	private	boolean loginValid = false;

	/** is the notify selection valid? */
	private	boolean notifyValid = false;

	/** mail identifier ---> mail mapping for the unread mails for this account */
	private final Map<MailIdentifier,Mail> mailsMap = new HashMap<MailIdentifier,Mail>();

	/** navigable set view of the unread mails for this account */
	private final NavigableSet<Mail> mailsNavigableSet = new TreeSet<Mail>();

	/** mutex lock for <code>mailsMap</code> and <code>mailsNavigableSet</code> */
	private final Object mailsLock = new Object();

	/** last received mail ID */
	private int lastSequenceNumber = 0;

	/** mutex lock for <code>lastSequenceNumber</code> */
	final private Object lastSequenceNumberLock = new Object();

	/** account properties */
	final SimpleProperties properties;

	/** Color object representing the currently displayed color */
	private Color colorObject;

	/** HTML string representing the currently displayed color */
	private String colorHtml;

	/** current mail store for this email account */
	private volatile Store currentMailStore;

	/** check mail now? */
	private volatile boolean checkMailNow = false;

	/** ID of the current mail checker loop */
	private int currentMailCheckerLoopId = 0;

	/** mutex lock for <code>currentMailCheckerLoopId</code> */
	private final Object currentMailCheckerLoopIdLock = new Object();

	/** ID of the current mail store */
	private int currentMailStoreId = 0;

	/** mutex lock for <code>currentMailStoreId</code> */
	private final Object currentMailStoreIdLock = new Object();

	/** "alive" time continuously updated by the mail checker */
	private volatile long mailCheckerAliveTime = 0L;


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 * @param id
	 *      account ID assigned by the parent GmailAssistant object
	 * @param properties
	 *      account properties for the new account
	 */
	Account(
			final GmailAssistant parent,
			final int accountId,
			final SimpleProperties properties)
	{
		/*********************
		 * INITIALIZE FIELDS *
		 *********************/

		this.parent = parent;
		this.accountId = accountId;
		this.properties = properties;

		/* initialize additional properties */
		properties.setString("status", "<html><font color='red'>Disabled</font></html>");
		properties.setBoolean("error", false);
		properties.setString("error.message", "");
		properties.setInt("unread.mails", -1);
		properties.setBoolean("new.unread.mails", false);
		properties.setLong("last.mail.check.success", 0L);
		properties.setLong("last.mail.check.attempt", 0L);

		/******************************
		 * INITIALIZE FORM COMPONENTS *
		 ******************************/

		initComponents();

		/*****************************
		 * CONFIGURE FORM COMPONENTS *
		 *****************************/

		final String username = properties.getString("username");

		if (username.isEmpty())
		{
			setTitle(String.format("New Account #%d - %s", accountId, parent.name));
		}
		else
		{
			setTitle(String.format("%s - %s", username, parent.name));
		}

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				/* equivalent to clicking on the "Cancel" button */
				cancelButton.doClick();
			}

			@Override
			public void windowIconified(WindowEvent e)
			{
				/* equivalent to clicking on the "Cancel" button */
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

		/* fields */
		for (final JTextField c : new JTextField[] {usernameField, passwordField})
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

		/* button: "color" */
		colorObject = new Color(properties.getInt("color"));
		colorHtml = String.format("rgb(%d,%d,%d)", colorObject.getRed(), colorObject.getGreen(), colorObject.getBlue());

		properties.set("color.object", colorObject);
		properties.set("color.html", colorHtml);

		colorButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				final Color newColorObject = JColorChooser.showDialog(
					Account.this,
					"Account Color - " + getTitle(),
					colorObject);

				if (newColorObject != null)
				{
					colorObject = newColorObject;
					colorHtml = String.format("rgb(%d,%d,%d)", newColorObject.getRed(), newColorObject.getGreen(), newColorObject.getBlue());
					colorButton.setText(String.format("<html><span style='color:%1$s;background-color:%1$s'>&nbsp;M&nbsp;</span></html>", colorHtml));
				}
			}
		});

		/* image: "lock" */
		lockImage.setToolTipText(String.format("%s accesses your Gmail or Google Apps email account securely using IMAP over SSL", parent.name));

		/* radio button group: "Notify On" */
		notifyOnRadios = new JRadioButton[]
		{
			notifyInboxRadio,
			notifyAnyRadio,
			notifyLabelsRadio
		};

		notifyOnProperties = new String[]
		{
			"inbox",
			"any",
			"labels"
		};

		for (final JRadioButton b : notifyOnRadios)
		{
			b.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					checkForm(b);
				}
			});
		}

		/* notify labels */
		notifyLabelFields = new JTextField[]
		{
			notifyField1,
			notifyField2,
			notifyField3,
			notifyField4,
			notifyField5,
			notifyField6
		};

		notifyLabelProperties = new String[]
		{
			"notify.label1",
			"notify.label2",
			"notify.label3",
			"notify.label4",
			"notify.label5",
			"notify.label6"
		};

		for (final JTextField c : notifyLabelFields)
		{
			c.getDocument().addDocumentListener(new DocumentListenerAdapter()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					checkForm(notifyLabelsRadio);
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					checkForm(notifyLabelsRadio);
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					checkForm(notifyLabelsRadio);
				}
			});
		}

		/* create mail labels for monitoring */
		final List<MailLabel> labels = new ArrayList<MailLabel>();
		final String notifyOn = properties.getString("notify.on");

		if ("inbox".equals(notifyOn))
		{
			labels.add(new MailLabel("INBOX"));
		}
		else if ("any".equals(notifyOn))
		{
			labels.add(new MailLabel("All Mail"));
		}
		else if ("labels".equals(notifyOn))
		{
			for (String p : notifyLabelProperties)
			{
				final String s = properties.getString(p);

				if (!s.isEmpty())
				{
					labels.add(new MailLabel(s));
				}
			}
		}

		properties.set("mail.labels.object", labels);

		/* check boxes: alerts */
		alertsBoxes = new JCheckBox[]
		{
			popupBox,
			chimeBox,
			bellBox,
			ledBox
		};

		alertsProperties = new String[]
		{
			"alert.popup",
			"alert.chime",
			"alert.periodic.bell",
			"alert.led"
		};

		/* button: "Test Alerts" */
		testButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (popupBox.isSelected())
				{
					parent.popup.test();
				}

				if (chimeBox.isSelected())
				{
					parent.chime.testChime();
				}

				if (bellBox.isSelected())
				{
					parent.chime.testPeriodicBell();
				}

				if (ledBox.isSelected())
				{
					parent.led.test();
				}
			}
		});

		/* button: "OK" */
		okButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				okButton.setEnabled(false);
				cancelButton.setEnabled(false);
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
			usernameField,
			passwordField,
			notifyField1,
			notifyField2,
			notifyField3,
			notifyField4,
			notifyField5,
			notifyField6
		});

		/* key binding: ENTER key */
		scrollPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER_OK_BUTTON");

		scrollPane.getActionMap().put("ENTER_OK_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (usernameField.isFocusOwner())
				{
					passwordField.selectAll();
					passwordField.requestFocus();
				}
				else
				{
					okButton.doClick();
				}
			}
		});

		/* key binding: ESCAPE key */
		scrollPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_CANCEL_BUTTON");

		scrollPane.getActionMap().put("ESCAPE_CANCEL_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				cancelButton.doClick();
			}
		});

		/* center form on the parent form */
		setLocationRelativeTo(parent);

		/* repopulate form */
		repopulateForm();

		/*******************************
		 * CREATE MAIL CHECKER MONITOR *
		 *******************************/

		new Thread(new Runnable()
		{
			public void run()
			{
				while (true)
				{
					/* start a new mail checker if the current one becomes unresponsive */
					if ((System.currentTimeMillis() - mailCheckerAliveTime) >=
							parent.properties.getLong("mail.check.timeout.milliseconds"))
					{
						mailCheckerAliveTime = System.currentTimeMillis();
						currentMailStore = null;

						final int mailCheckerId;

						synchronized (currentMailCheckerLoopIdLock)
						{
							mailCheckerId = ++currentMailCheckerLoopId;
						}

						startNewMailCheckerLoop(mailCheckerId);

						if (parent.debug)
						{
							parent.logger.log("[%s] Started new mail checker #%d", properties.getString("username"), mailCheckerId);
						}
					}

					Debug.sleep(10 * REFRESH_INTERVAL_MILLISECONDS);
				}
			}
		}).start();
	}


	/**
	 * Start a new loop to check for mail iteratively.
	 * This method runs on a newly created thread.
	 *
	 * @param mailCheckerId
	 *     ID for the new mail checker loop
	 */
	private void startNewMailCheckerLoop(
			final int mailCheckerId)
	{
		new Thread(new Runnable()
		{
			/** interrupted exception, to be thrown by <code>checkIfInterrupted()</code> */
			private final InterruptedException interruptedException = new InterruptedException();

			/** search term for messages with the SEEN flag turned off */
			private final SearchTerm unseenFlag = new FlagTerm(new Flags(Flags.Flag.SEEN), false);


			/**
			 * Check if this mail checker has been interrupted.
			 * This method updates the "alive" time of the mail checker.
			 *
			 * @throws InterruptedException
			 *     if this mail checker has been interrupted
			 */
			private void checkIfInterrupted()
					throws InterruptedException
			{
				mailCheckerAliveTime = System.currentTimeMillis();

				if (!properties.getBoolean("enabled"))
				{
					properties.setString("status", "<html><font color='red'>Disabled</font></html>");
					parent.refreshAccountOnTable(Account.this);
					throw interruptedException;
				}

				synchronized (currentMailCheckerLoopIdLock)
				{
					if (mailCheckerId != currentMailCheckerLoopId)
					{
						throw interruptedException;
					}
				}
			}


			/**
			 * Perform mail check iteratively.
			 */
			public void run()
			{
				/* mail identifer ---> message mapping for all unread mails */
				final Map<MailIdentifier,Message> messageMap = new HashMap<MailIdentifier,Message>();

				/* mail identifer ---> message mapping for unread mails to be fetched */
				final Map<MailIdentifier,Message> fetchMessageMap = new HashMap<MailIdentifier,Message>();

				/* mail identifer ---> mail mapping for unread mails to be fetched */
				final Map<MailIdentifier,Mail> fetchMailMap = new HashMap<MailIdentifier,Mail>();

				NextMailCheckIteration:
				while (true)
				{
					Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);

					/***********************************
					 * (1) CHECK TERMINATING CONDITION *
					 ***********************************/

					mailCheckerAliveTime = System.currentTimeMillis();

					/* check if this mail checker has been replaced */
					synchronized (currentMailCheckerLoopIdLock)
					{
						if (mailCheckerId != currentMailCheckerLoopId)
						{
							break NextMailCheckIteration;
						}
					}

					/******************************
					 * (2) PROCEED TO CHECK MAIL? *
					 ******************************/

					boolean proceedToCheckNow = false;
					final long mailCheckIntervalMilliseconds = parent.properties.getLong("mail.check.interval.milliseconds");

					if (properties.getBoolean("enabled"))
					{
						if (checkMailNow)
						{
							proceedToCheckNow = true;
							checkMailNow = false;
						}
						else
						{
							if (properties.getBoolean("error"))
							{
								if ((System.currentTimeMillis() - properties.getLong("last.mail.check.attempt")) >=
										(mailCheckIntervalMilliseconds / 10))
								{
									proceedToCheckNow = true;
								}
							}
							else
							{
								if ((System.currentTimeMillis() - properties.getLong("last.mail.check.attempt")) >=
										mailCheckIntervalMilliseconds)
								{
									proceedToCheckNow = true;
								}
							}
						}
					}

					if (!proceedToCheckNow)
					{
						continue NextMailCheckIteration;
					}

					/********************
					 * GMAIL WORKAROUND *
					 ********************/

					/* Gmail prevents repeated calls to retrieve mails on a single login,  */
					/* so we close the existing mail store and create a new one each time. */
					closeMailStore(currentMailStore);
					currentMailStore = null;

					/**********************************
					 * (3) CHECK IF MAIL STORE EXISTS *
					 **********************************/

					if (currentMailStore == null)
					{
						createNewMailStore();
					}

					final Store mailStore = currentMailStore;

					if (mailStore == null)
					{
						continue NextMailCheckIteration;
					}

					try
					{
						/***************************
						 * (4) BEGIN CHECKING MAIL *
						 ***************************/

						properties.setString("status", "<html>Checking mail...</html>");
						parent.refreshAccountOnTable(Account.this);
						checkIfInterrupted();

						messageMap.clear();
						fetchMessageMap.clear();
						fetchMailMap.clear();

						/*****************************************************
						 * (5) FETCH UNREAD MAIL IDENTIFIERS FOR EACH FOLDER *
						 *****************************************************/

						final String notifyOn = properties.getString("notify.on");
						final List<MailLabel> mailLabels = (List<MailLabel>) properties.get("mail.labels.object");
						final IMAPFolder[] folders = new IMAPFolder[mailLabels.size()];

						for (int i = 0; i < folders.length; i++)
						{
							folders[i] = (IMAPFolder) mailStore.getFolder(mailLabels.get(i).folder); /* throws IllegalStateException if store not connected */
						}

						if ("inbox".equals(notifyOn))
						{
							properties.setString("status", "<html>Fetching unread mails in Inbox...<</html>");
							parent.refreshAccountOnTable(Account.this);
						}
						else if ("any".equals(notifyOn))
						{
							properties.setString("status", "<html>Fetching unread mails...</html>");
							parent.refreshAccountOnTable(Account.this);
						}

						NextFolder:
						for (int i = 0; i < folders.length; i++)
						{
							if ("labels".equals(notifyOn))
							{
								properties.setString("status", String.format("<html>Fetching unread \"%s\" mails...</html>", mailLabels.get(i).label));
								parent.refreshAccountOnTable(Account.this);
							}

							checkIfInterrupted();

							/* open folder in "read only" mode */
							if (!folders[i].exists()) /* throws MessagingException if connection to server is lost */
							{
								continue NextFolder;
							}

							if (!folders[i].isOpen())
							{
								folders[i].open(Folder.READ_ONLY); /* throws MessagingException */
							}

							checkIfInterrupted();

							/* fetch unseen mails from this folder */
							for (Message msg : folders[i].search(unseenFlag)) /* throws MessagingException */
							{
								checkIfInterrupted();

								messageMap.put(new MailIdentifier(
										mailLabels.get(i).folder,
										folders[i].getUID(msg)), /* throws MessagingException */
										msg);
							}
						}

						/****************************************************
						 * (6) FETCH UNREAD MAILS THAT HAVE NOT BEEN CACHED *
						 ****************************************************/

						fetchMessageMap.putAll(messageMap);

						synchronized (mailsLock)
						{
							fetchMessageMap.keySet().removeAll(mailsMap.keySet());
						}

						boolean newUnreadMails = !fetchMessageMap.isEmpty();

						for (Map.Entry<MailIdentifier,Message> me : fetchMessageMap.entrySet())
						{
							checkIfInterrupted();

							final int seq;

							synchronized (lastSequenceNumberLock)
							{
								seq = ++lastSequenceNumber;
							}

							fetchMailMap.put(
									me.getKey(),
									new Mail(Account.this, me.getValue(), seq)); /* throws MessagingException */
						}

						/***********************
						 * (7) UPDATE MAIL MAP *
						 ***********************/

						synchronized (mailsLock)
						{
							mailsMap.keySet().retainAll(messageMap.keySet());
							mailsMap.putAll(fetchMailMap);
							mailsNavigableSet.retainAll(mailsMap.values());
							mailsNavigableSet.addAll(fetchMailMap.values());
							properties.setInt("unread.mails", mailsMap.size());
						}

						properties.setString("status", "<html>Mail check completed</html>");
						parent.refreshAccountOnTable(Account.this);
						parent.refreshTotalUnreadMailCount();

						/********************
						 * (8) ISSUE ALERTS *
						 ********************/

						if (newUnreadMails)
						{
							properties.setBoolean("new.unread.mails", true);
							parent.trayIcon.setHotIcon();
							parent.trayIcon.blinkIcon();

							if (properties.getBoolean("alert.chime"))
							{
								parent.chime.playChime();
							}

							if (properties.getBoolean("alert.periodic.bell"))
							{
								parent.chime.startPeriodicBell();
							}

							if (properties.getBoolean("alert.led"))
							{
								parent.led.start();
							}
						}

						/* update popup messages for unread mails */
						parent.popup.updateMessages(
								Account.this,
								properties.getBoolean("alert.popup"));

						/***********************************
						 * (9) REGISTER MAIL CHECK SUCCESS *
						 ***********************************/

						registerMailCheckSuccess();
					}
					catch (Exception e)
					{
						if (e instanceof InterruptedException)
						{
							/* mail checker has been interrupted */
							if (parent.debug)
							{
								parent.logger.log("[%s] Mail checker #%d interruption (%s)",
										properties.getString("username"), mailCheckerId, e.toString());
							}
						}
						else
						{
							if (parent.debug)
							{
								parent.logger.log("[%s] Mail checker #%d exception caught (%s)",
										properties.getString("username"), mailCheckerId, e.toString());
							}

							registerMailCheckFailure("Mail check failed", mailStore);
						}
					}
				}
			}
		}).start();
	}


	/**
	 * Register a successful mail check by updating the last mail check attempt and success times.
	 * This method can be called on any thread.
	 */
	private void registerMailCheckSuccess()
	{
		final long time = System.currentTimeMillis();

		/* update account properties */
		properties.setLong("last.mail.check.attempt", time);
		properties.setLong("last.mail.check.success", time);
		properties.setBoolean("error", false);
		properties.setString("error.message", "");
		properties.setString("status", "<html>Waiting for next mail check</html>");
		parent.refreshAccountOnTable(this);
		parent.refreshTotalUnreadMailCount();
	}


	/**
	 * Register a failed mail check by updating the last mail check attempt time,
	 * and setting the given error message.
	 * The specified mail store, if any, is also closed.
	 * This method can be called on any thread.
	 *
	 * @param error
	 *     error message (must not be null)
	 * @param mailStore
	 *     mail store to be closed if any; null otherwise
	 */
	private void registerMailCheckFailure(
			final String error,
			final Store mailStore)
	{
		/* trigger a new login attempt */
		currentMailStore = null;

		/* update account properties */
		properties.setLong("last.mail.check.attempt", System.currentTimeMillis());
		properties.setBoolean("error", true);
		properties.setString("error.message", error);
		properties.setString("status", String.format("<html><font color='red'>%s</font></html>", error));
		parent.refreshAccountOnTable(this);
		parent.refreshTotalUnreadMailCount();

		/* close mail store, if any */
		closeMailStore(mailStore);
	}


	/**
	 * Close the specified mail store.
	 * This method runs on a newly created thread.
	 *
	 * @param mailStore
	 *     mail store to be closed.
	 */
	private void closeMailStore(
			final Store mailStore)
	{
		if (mailStore != null)
		{
			new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						mailStore.close();
					}
					catch (Exception e)
					{
						/* ignore */
					}
				}
			}).start();
		}
	}


	/**
	 * Create a new mail store for this email account.
	 * This method can be called on any thread.
	 */
	private void createNewMailStore()
	{
		/* get username for login */
		String username = properties.getString("username");

		if (!username.contains("@"))
		{
			/* treat as Gmail account */
			username += parent.properties.getString("gmail.username.suffix");
		}

		/* mail server parameters */
		final String protocol = parent.properties.getString("incoming.protocol");
		final String server = parent.properties.getString("incoming.server");
		final int port = parent.properties.getInt("incoming.port");

		Store mailStore = null;

		try
		{
			SwingManipulator.updateLabel(loginError, "<html><font color='blue'>Logging in...</font></html>");
			properties.setString("status", "<html><font color='blue'>Logging in...</font></html>");
			parent.refreshAccountOnTable(Account.this);

			/* create new mail store */
			final Session session = Session.getInstance(System.getProperties(), null);
			session.setDebug(false);

			mailStore = session.getStore(protocol); /* throws NoSuchProviderException */

			mailStore.connect(
					server,
					port,
					username,
					properties.getString("password")); /* throws AuthenticationFailedException, MessagingException, IllegalStateException */

			registerMailStoreCreationSuccess(mailStore);
		}
		catch (Exception e)
		{
			if (parent.debug)
			{
				parent.logger.log("[%s] Mail store creation failure (%s)", properties.getString("username"), e.toString());
			}

			if (e instanceof NoSuchProviderException)
			{
				registerMailStoreCreationFailure(
						String.format("(INTERNAL) Invalid incoming mail protocol \"%s\" (%s).", protocol, e.toString()),
						mailStore);
			}
			else if (e instanceof AuthenticationFailedException)
			{
				registerMailStoreCreationFailure(
						"Failed to connect to the Gmail server." +
						"\nPlease check that IMAP access is enabled for your Gmail account " +
						"(Settings > Forwarding and POP/IMAP > IMAP Access), and that your username and password are correct.",
						mailStore);
			}
			else
			{
				registerMailStoreCreationFailure(String.format(
						"Failed to connect to the Gmail server because of an unexpected error (%s).\n" +
						"Please ensure that %s has internet access to the Gmail IMAP server at %s port %d.",
						e.toString(), parent.name, server, port),
						mailStore);
			}
		}

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				loginError.setText(" ");
				okButton.setEnabled(true);
				cancelButton.setEnabled(true);
				passwordField.selectAll();
				passwordField.requestFocus();
			}
		});
	}


	/**
	 * Register successful creation of a new mail store.
	 * This method can be called on any thread.
	 *
	 * @param mailStore
	 *     mail store that was created
	 */
	private void registerMailStoreCreationSuccess(
			final Store mailStore)
	{
		final int mailStoreId;

		synchronized (currentMailStoreIdLock)
		{
			mailStoreId = ++currentMailStoreId;
		}

		if (parent.debug)
		{
			parent.logger.log("[%s] Mail store #%d creation success", properties.getString("username"), mailStoreId);
		}

		currentMailStore = mailStore;
		startNewIdleLoop(mailStore, mailStoreId);

		properties.setString("status", "<html><font color='blue'>Login successful</font></html>");
		parent.refreshAccountOnTable(this);

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				loginError.setText("<html><font color='blue'>Login successful</font></html>");
				setVisible(false);
				enableAccount();
			}
		});
	}


	/**
	 * Register failure in creating a new mail store.
	 * This method can be called on any thread.
	 *
	 * @param error
	 *     error message
	 * @param mailStore
	 *     mail store that was created
	 */
	private void registerMailStoreCreationFailure(
			final String error,
			final Store mailStore)
	{
		/* trigger a new login attempt */
		currentMailStore = null;

		/* close mail store, if any */
		closeMailStore(mailStore);

		/* update account properties */
		properties.setLong("last.mail.check.attempt", System.currentTimeMillis());
		properties.setBoolean("error", true);
		properties.setString("error.message", "Login failed");
		properties.setString("status", "<html><font color='red'>Login failed</font></html>");
		parent.refreshAccountOnTable(this);
		parent.refreshTotalUnreadMailCount();

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				loginError.setText("<html><font color='red'>Login failed</font></html>");

				if (isVisible())
				{
					SwingManipulator.showErrorDialog(
							Account.this,
							getTitle(),
							error);
				}
			}
		});
	}


	/**
	 * Start a new loop that calls the IMAP IDLE command iteratively.
	 * This method runs on a newly created thread.
	 *
	 * @param mailStore
	 *     mail store to be used
	 * @param mailStoreId
	 *     ID of the mail store to be used
	 */
	private void startNewIdleLoop(
			final Store mailStore,
			final int mailStoreId)
	{
		new Thread(new Runnable()
		{
			public void run()
			{
				final MessageCountListener idleListener = new MessageCountListener()
				{
					public void messagesAdded(MessageCountEvent e)
					{
						checkMailNow = true;
					}

					public void messagesRemoved(MessageCountEvent e)
					{
						checkMailNow = true;
					}
				};

				final String allMailFolder = new MailLabel("All Mail").folder;

				NextIdleIteration:
				while (true)
				{
					Debug.sleep(10 * REFRESH_INTERVAL_MILLISECONDS);

					/* check terminating condition */
					synchronized (currentMailStoreIdLock)
					{
						if (mailStoreId != currentMailStoreId)
						{
							break NextIdleIteration;
						}
					}

					try
					{
						final IMAPFolder folder = (IMAPFolder) mailStore.getFolder(allMailFolder); /* throws IllegalStateException */
						folder.addMessageCountListener(idleListener);

						if (!folder.exists()) /* throws MessagingException */
						{
							continue NextIdleIteration;
						}

						if (!folder.isOpen())
						{
							folder.open(Folder.READ_ONLY); /* throws MessagingException */
						}

						if (parent.debug)
						{
							parent.logger.log("[%s] IDLE for mail store #%d called", properties.getString("username"), mailStoreId);
						}

						folder.idle(); /* throws MessagingException, IllegalStateException */

						if (parent.debug)
						{
							parent.logger.log("[%s] IDLE for mail store #%d returned", properties.getString("username"), mailStoreId);
						}
					}
					catch (Exception e)
					{
						/* ignore */
						if (parent.debug)
						{
							parent.logger.log("[%s] IDLE for mail store #%d exception caught (%s)", properties.getString("username"), mailStoreId, e.toString());
						}
					}
				}
			}
		}).start();
	}


	/**
	 * Repopulate the form according to the current account properties.
	 * This method must run on the EDT.
	 */
	private void repopulateForm()
	{
		/* login */
		usernameField.setText(properties.getString("username"));
		usernameEdited = false;

		passwordField.setText(properties.getString("password"));
		passwordEdited = false;

		colorObject = (Color) properties.get("color.object");
		colorHtml = properties.getString("color.html");
		colorButton.setText(String.format("<html><span style='color:%1$s;background-color:%1$s'>&nbsp;M&nbsp;</span></html>", colorHtml));

		/* notify */
		final String notifyOn = properties.getString("notify.on");

		for (int i = 0; i < notifyOnRadios.length; i++)
		{
			if (notifyOn.equals(notifyOnProperties[i]))
			{
				notifyOnRadios[i].setSelected(true);
			}
		}

		for (int i = 0; i < notifyLabelFields.length; i++)
		{
			notifyLabelFields[i].setText(properties.getString(notifyLabelProperties[i]));
		}

		/* alerts */
		for (int i = 0; i < alertsBoxes.length; i++)
		{
			alertsBoxes[i].setSelected(properties.getBoolean(alertsProperties[i]));
		}

		checkForm(null);
	}


	/**
	 * Accept changes and close the form.
	 * This method must run on the EDT.
	 */
	private void accept()
	{
		if (!(loginValid && notifyValid))
		{
			return;
		}

		/* login */
		if (usernameEdited)
		{
			final String username = SwingManipulator.getTextJTextField(usernameField);
			properties.setString("username", username);

			if (username.isEmpty())
			{
				setTitle(String.format("New Account #%d - %s", accountId, parent.name));
			}
			else
			{
				setTitle(String.format("%s - %s", username, parent.name));
			}
		}

		if (passwordEdited)
		{
			final char[] password = SwingManipulator.getPasswordJPasswordField(passwordField);
			properties.setString("password", String.valueOf(password));
			Arrays.fill(password, '\0');
		}

		properties.set("color.object", colorObject);
		properties.set("color.html", colorHtml);
		properties.setInt("color", colorObject.getRGB());

		/* notify */
		for (int i = 0; i < notifyOnRadios.length; i++)
		{
			if (notifyOnRadios[i].isSelected())
			{
				properties.setString("notify.on", notifyOnProperties[i]);
			}
		}

		for (int i = 0; i < notifyLabelFields.length; i++)
		{
			properties.setString(notifyLabelProperties[i], SwingManipulator.getTextJTextField(notifyLabelFields[i]).trim());
		}

		/* mail labels for monitoring */
		final List<MailLabel> labels = new ArrayList<MailLabel>();
		final String notifyOn = properties.getString("notify.on");

		if ("inbox".equals(notifyOn))
		{
			labels.add(new MailLabel("INBOX"));
		}
		else if ("any".equals(notifyOn))
		{
			labels.add(new MailLabel("All Mail"));
		}
		else if ("labels".equals(notifyOn))
		{
			for (String p : notifyLabelProperties)
			{
				final String s = properties.getString(p);

				if (!s.isEmpty())
				{
					labels.add(new MailLabel(s));
				}
			}
		}

		properties.set("mail.labels.object", labels);

		/* alerts */
		for (int i = 0; i < alertsBoxes.length; i++)
		{
			properties.setBoolean(alertsProperties[i], alertsBoxes[i].isSelected());
		}

		parent.refreshAccountOnTable(this);

		if ((currentMailStore == null) || usernameEdited || passwordEdited)
		{
			disableAccount();
			usernameEdited = false;
			passwordEdited = false;

			new Thread(new Runnable()
			{
				public void run()
				{
					createNewMailStore();
				}
			}).start();
		}
		else
		{
			okButton.setEnabled(true);
			cancelButton.setEnabled(true);
			setVisible(false);
		}
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
		boolean checkLogin = false;
		boolean checkNotify = false;

		if (c == usernameField)
		{
			checkLogin = true;
			usernameEdited = true;
		}
		else if (c == passwordField)
		{
			checkLogin = true;
			passwordEdited = true;
		}
		else if ((c == notifyInboxRadio) ||
				(c == notifyAnyRadio) ||
				(c == notifyLabelsRadio))
		{
			checkNotify = true;
		}
		else if (c == null)
		{
			checkLogin = true;
			checkNotify = true;
		}

		/***************************
		 * CHECK LOGIN CREDENTIALS *
		 ***************************/

		if (checkLogin)
		{
			String error = null;

			/* password */
			final char[] password = SwingManipulator.getPasswordJPasswordField(passwordField);
			final int passwordLength = password.length;
			Arrays.fill(password, '\0');

			if (passwordLength == 0)
			{
				error = "Password must not be empty";
			}

			/* username */
			final String username = SwingManipulator.getTextJTextField(usernameField);

			if (username.contains("@"))
			{
				/* treat as Google Apps email account */
				if (!((Pattern) parent.properties.get("google.apps.email.username.pattern.object")).matcher(username).matches())
				{
					error = "Google Apps email username must be in the form <b>name@domain.com</b>, where <b>name</b> contains only letters (a-z), numbers (0-9), dashes (-), and periods (.)";
				}
			}
			else
			{
				/* treat as Gmail account */
				if (!((Pattern) parent.properties.get("gmail.username.pattern.object")).matcher(username).matches())
				{
					error = "Gmail username must contain only letters (a-z), numbers (0-9), and periods (.)";
				}
			}

			if (username.contains(" "))
			{
				error = "Username must not contain spaces";
			}

			if (username.isEmpty())
			{
				error = "Username must not be empty";
			}

			if (error == null)
			{
				loginValid = true;
				loginError.setText(" ");
			}
			else
			{
				loginValid = false;
				loginError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
			}
		}

		/***************************
		 * CHECK NOTIFY SELECTIONS *
		 ***************************/

		if (checkNotify)
		{
			String error = null;

			if (notifyInboxRadio.isSelected() || notifyAnyRadio.isSelected())
			{
				for (JTextField f : notifyLabelFields)
				{
					f.setEnabled(false);
				}
			}
			else if (notifyLabelsRadio.isSelected())
			{
				for (JTextField f : notifyLabelFields)
				{
					f.setEnabled(true);
				}

				boolean allEmpty = true;

				for (JTextField f : notifyLabelFields)
				{
					if (!SwingManipulator.getTextJTextField(f).trim().isEmpty())
					{
						allEmpty = false;
						break;
					}
				}

				if (allEmpty)
				{
					error = "At least one label must be specified";
				}
			}

			if (error == null)
			{
				notifyValid = true;
				notifyError.setText(" ");
			}
			else
			{
				notifyValid = false;
				notifyError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
			}
		}

		/***********************
		 * ALL SETTINGS VALID? *
		 ***********************/

		if (loginValid && notifyValid)
		{
			okButton.setEnabled(true);
		}
	}


	/**
	 * Present this account form for editing.
	 * This method must run on the EDT.
	 */
	void editAccount()
	{
		setVisible(true);
		setExtendedState(JFrame.NORMAL);
		toFront();
	}


	/**
	 * Enable the account.
	 * This method must run on the EDT.
	 */
	void enableAccount()
	{
		if (properties.getString("username").isEmpty() ||
				properties.getString("password").isEmpty())
		{
			editAccount();
		}
		else
		{
			if (!properties.getBoolean("enabled"))
			{
				properties.setString("status", "<html>Waiting for next mail check</html>");
				parent.refreshAccountOnTable(this);
				checkMailNow = true;
				properties.setBoolean("enabled", true);
			}
		}
	}


	/**
	 * Disable the account.
	 * This method can be called on any thread.
	 */
	void disableAccount()
	{
		properties.setBoolean("error", false);
		properties.setBoolean("enabled", false);
		properties.setString("status", "<html><font color='red'>Disabled</font></html>");
		parent.refreshAccountOnTable(this);
		parent.refreshTotalUnreadMailCount();
	}


	/**
	 * Remove this account.
	 * This method can be called on any thread.
	 */
	void removeAccount()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				disableAccount();
				closeMailStore(currentMailStore);
				setVisible(false);
				dispose();
			}
		});
	}


	/**
	 * Check for unread mails now, if the login credentials have been verified.
	 */
	void checkMailNow()
	{
		checkMailNow = true;
	}


	/**
	 * Generate tooltip text for this account in the table.
	 *
	 * @return
	 *      tooltip text for this account
	 */
	String getToolTipText()
	{
		final long time = System.currentTimeMillis();
		final StringBuilder sb = new StringBuilder();

		sb.append("<html><div style='margin:3px'><span style='color:");

		/* account color */
		final String c = properties.getString("color.html");

		sb.append(c);
		sb.append(";background-color:");
		sb.append(c);
		sb.append("'>&nbsp;M&nbsp;</span>&nbsp;&nbsp;<b>");

		/* username */
		final String username = properties.getString("username");
		sb.append(username.isEmpty() ? ("New Account #" + accountId) : username);
		sb.append("</b><br />");

		/* error message, if any */
		final String error = properties.getString("error.message");

		if (!error.isEmpty())
		{
			sb.append("<font color='red'>");
			sb.append(error);
			sb.append(" (");

			final long duration = time - properties.getLong("last.mail.check.attempt");
			sb.append(GmailAssistant.timeDurationString(duration));
			sb.append((duration >= 0) ? " ago)</font><br />" : " in the future)</font><br />");
		}

		/* number of unread mails */
		final int n = properties.getInt("unread.mails");

		if (n >= 0)
		{
			sb.append(n);
			sb.append(" unread ");
			sb.append((n == 1) ? "mail (" : "mails (");

			final long duration = time - properties.getLong("last.mail.check.success");
			sb.append(GmailAssistant.timeDurationString(duration));
			sb.append((duration >= 0) ? " ago)<br />" : " in the future)<br />");
		}

		/* notify options */
		final String notifyOn = properties.getString("notify.on");

		if ("inbox".equals(notifyOn))
		{
			sb.append("Notify on any unread mail in Inbox<br />");
		}
		else if ("any".equals(notifyOn))
		{
			sb.append("Notify on any unread mail<br />");
		}
		else if ("labels".equals(notifyOn))
		{
			sb.append("Notify on unread mail with labels: ");

			boolean allEmpty = true;

			for (String p : notifyLabelProperties)
			{
				final String s = properties.getString(p);

				if (!s.isEmpty())
				{
					sb.append("<i>");
					sb.append(s);
					sb.append("</i>, ");
					allEmpty = false;
				}
			}

			if (allEmpty)
			{
				sb.append("<i>none selected yet</i><br />");
			}
			else
			{
				sb.delete(sb.length() - 2, sb.length());
				sb.append("<br />");
			}
		}

		/* alert options */
		sb.append("Alerts: ");

		int i = 0;

		if (properties.getBoolean("alert.popup"))
		{
			sb.append("<i>Popup</i>, ");
			i++;
		}

		if (properties.getBoolean("alert.chime"))
		{
			sb.append("<i>Chime</i>, ");
			i++;
		}

		if (properties.getBoolean("alert.periodic.bell"))
		{
			sb.append("<i>Periodic Bell</i>, ");
			i++;
		}

		if (properties.getBoolean("alert.led"))
		{
			sb.append("<i>LED</i>, ");
			i++;
		}

		if (i == 0)
		{
			sb.append("<i>none selected yet</i>");
		}
		else
		{
			sb.delete(sb.length() - 2, sb.length());
		}

		sb.append("</div></html>");
		return sb.toString();
	}


	/**
	 * Compare this account to the specified account by account ID.
	 */
	public int compareTo(
			Account o)
	{
		if (accountId < o.accountId) return -1;
		if (accountId > o.accountId) return 1;
		return 0;
	}


	/**
	 * Check for equality between this account and the specified account by account ID.
	 */
	@Override
	public boolean equals(
			Object obj)
	{
		if (obj instanceof Account)
		{
			return (accountId == ((Account) obj).accountId);
		}
		else
		{
			return false;
		}
	}


	/**
	 * Generate hash code for this account, using the account ID.
	 */
	@Override
	public int hashCode()
	{
		return accountId;
	}


	/**
	 * Get total number of mails.
	 * This method can be called on any thread.
	 *
	 * @return
	 *     total number of mails
	 */
	int getTotalNumMails()
	{
		synchronized (mailsLock)
		{
			return mailsNavigableSet.size();
		}
	}


	/**
	 * Get the first mail.
	 * This method can be called on any thread.
	 *
	 * @return
	 *     first mail; null if there are no mails
	 */
	Mail getFirstMail()
	{
		try
		{
			synchronized (mailsLock)
			{
				return mailsNavigableSet.first();
			}
		}
		catch (NoSuchElementException e)
		{
			return null;
		}
	}


	/**
	 * Get the last mail.
	 * This method can be called on any thread.
	 *
	 * @return
	 *     last mail; null if there are no mails
	 */
	Mail getLastMail()
	{
		try
		{
			synchronized (mailsLock)
			{
				return mailsNavigableSet.last();
			}
		}
		catch (NoSuchElementException e)
		{
			return null;
		}
	}


	/**
	 * Get the next mail that comes after the specified mail.
	 * This method can be called on any thread.
	 *
	 * @param m
	 *     mail
	 * @return
	 *     next mail that comes after <code>m</code>; null if there is none
	 */
	Mail getNextMail(
			final Mail m)
	{
		synchronized (mailsLock)
		{
			return mailsNavigableSet.higher(m);
		}
	}


	/**
	 * Get the previous mail that comes before the specified mail.
	 * This method can be called on any thread.
	 *
	 * @param m
	 *     mail
	 * @return
	 *     previous mail that comes before <code>m</code>; null if there is none
	 */
	Mail getPreviousMail(
			final Mail m)
	{
		synchronized (mailsLock)
		{
			return mailsNavigableSet.lower(m);
		}
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

        notifyRadioGroup = new javax.swing.ButtonGroup();
        buttonsPanel = new javax.swing.JPanel();
        testButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        scrollPane = new javax.swing.JScrollPane();
        panel = new javax.swing.JPanel();
        loginPanel = new javax.swing.JPanel();
        usernameLabel = new javax.swing.JLabel();
        usernameField = new javax.swing.JTextField();
        passwordLabel = new javax.swing.JLabel();
        passwordField = new javax.swing.JPasswordField();
        loginError = new javax.swing.JLabel();
        lockImage = new javax.swing.JLabel();
        colorButton = new javax.swing.JButton();
        notifyPanel = new javax.swing.JPanel();
        notifyInboxRadio = new javax.swing.JRadioButton();
        notifyAnyRadio = new javax.swing.JRadioButton();
        notifyLabelsRadio = new javax.swing.JRadioButton();
        notifyError = new javax.swing.JLabel();
        labelsPanel = new javax.swing.JPanel();
        notifyField1 = new javax.swing.JTextField();
        notifyField2 = new javax.swing.JTextField();
        notifyField3 = new javax.swing.JTextField();
        notifyField4 = new javax.swing.JTextField();
        notifyField5 = new javax.swing.JTextField();
        notifyField6 = new javax.swing.JTextField();
        alertPanel = new javax.swing.JPanel();
        popupBox = new javax.swing.JCheckBox();
        chimeBox = new javax.swing.JCheckBox();
        ledBox = new javax.swing.JCheckBox();
        bellBox = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        buttonsPanel.setLayout(new java.awt.GridLayout(1, 0));

        testButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/bell.png"))); // NOI18N
        testButton.setMnemonic('T');
        testButton.setText("Test Alerts");
        testButton.setToolTipText("Test selected alerts");
        testButton.setIconTextGap(8);
        testButton.setNextFocusableComponent(okButton);
        buttonsPanel.add(testButton);

        okButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/tick.png"))); // NOI18N
        okButton.setMnemonic('O');
        okButton.setText("OK");
        okButton.setToolTipText("Save current settings and enable this account");
        okButton.setIconTextGap(8);
        okButton.setNextFocusableComponent(okButton);
        buttonsPanel.add(okButton);

        cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/cross.png"))); // NOI18N
        cancelButton.setMnemonic('C');
        cancelButton.setText("Cancel");
        cancelButton.setToolTipText("Cancel changes");
        cancelButton.setIconTextGap(8);
        cancelButton.setNextFocusableComponent(cancelButton);
        buttonsPanel.add(cancelButton);

        scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        loginPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Login"));

        usernameLabel.setDisplayedMnemonic('u');
        usernameLabel.setLabelFor(usernameField);
        usernameLabel.setText("Username:");
        usernameLabel.setToolTipText("Gmail or Google Apps email account username");

        usernameField.setToolTipText("Gmail or Google Apps email account username");
        usernameField.setNextFocusableComponent(passwordField);

        passwordLabel.setDisplayedMnemonic('p');
        passwordLabel.setLabelFor(passwordField);
        passwordLabel.setText("Password:");
        passwordLabel.setToolTipText("Gmail or Google Apps email account password");

        passwordField.setToolTipText("Gmail or Google Apps email account password");

        loginError.setForeground(new java.awt.Color(255, 0, 0));
        loginError.setText("login error");
        loginError.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        lockImage.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lockImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/lock.png"))); // NOI18N

        colorButton.setText("***");
        colorButton.setToolTipText("Pick a color for this account");
        colorButton.setFocusPainted(false);

        javax.swing.GroupLayout loginPanelLayout = new javax.swing.GroupLayout(loginPanel);
        loginPanel.setLayout(loginPanelLayout);
        loginPanelLayout.setHorizontalGroup(
            loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, loginPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(passwordLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(usernameLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(loginError, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 241, Short.MAX_VALUE)
                    .addGroup(loginPanelLayout.createSequentialGroup()
                        .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(passwordField, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE)
                            .addComponent(usernameField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(lockImage, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(colorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        loginPanelLayout.setVerticalGroup(
            loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(loginPanelLayout.createSequentialGroup()
                .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(usernameLabel)
                    .addComponent(colorButton)
                    .addComponent(usernameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(passwordLabel)
                        .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lockImage))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(loginError))
        );

        panel.add(loginPanel);

        notifyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Notify"));

        notifyRadioGroup.add(notifyInboxRadio);
        notifyInboxRadio.setMnemonic('i');
        notifyInboxRadio.setSelected(true);
        notifyInboxRadio.setText("<html>on any unread mail in <u>I</u>nbox</html>");

        notifyRadioGroup.add(notifyAnyRadio);
        notifyAnyRadio.setMnemonic('a');
        notifyAnyRadio.setText("on any unread mail");

        notifyRadioGroup.add(notifyLabelsRadio);
        notifyLabelsRadio.setMnemonic('l');
        notifyLabelsRadio.setText("<html>on unread mail with any of the following <u>l</u>abels</html>");

        notifyError.setForeground(new java.awt.Color(255, 0, 0));
        notifyError.setText("notify error");

        labelsPanel.setLayout(new java.awt.GridLayout(2, 0));

        notifyField1.setToolTipText("Enter a Gmail label");
        labelsPanel.add(notifyField1);

        notifyField2.setToolTipText("Enter a Gmail label");
        labelsPanel.add(notifyField2);

        notifyField3.setToolTipText("Enter a Gmail label");
        labelsPanel.add(notifyField3);

        notifyField4.setToolTipText("Enter a Gmail label");
        labelsPanel.add(notifyField4);

        notifyField5.setToolTipText("Enter a Gmail label");
        labelsPanel.add(notifyField5);

        notifyField6.setToolTipText("Enter a Gmail label");
        labelsPanel.add(notifyField6);

        javax.swing.GroupLayout notifyPanelLayout = new javax.swing.GroupLayout(notifyPanel);
        notifyPanel.setLayout(notifyPanelLayout);
        notifyPanelLayout.setHorizontalGroup(
            notifyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(notifyPanelLayout.createSequentialGroup()
                .addGroup(notifyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(notifyPanelLayout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(labelsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 282, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, notifyPanelLayout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(notifyError, javax.swing.GroupLayout.DEFAULT_SIZE, 282, Short.MAX_VALUE))
                    .addGroup(notifyPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(notifyInboxRadio, javax.swing.GroupLayout.DEFAULT_SIZE, 303, Short.MAX_VALUE))
                    .addGroup(notifyPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(notifyAnyRadio, javax.swing.GroupLayout.DEFAULT_SIZE, 303, Short.MAX_VALUE))
                    .addGroup(notifyPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(notifyLabelsRadio, javax.swing.GroupLayout.DEFAULT_SIZE, 303, Short.MAX_VALUE)))
                .addContainerGap())
        );
        notifyPanelLayout.setVerticalGroup(
            notifyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(notifyPanelLayout.createSequentialGroup()
                .addComponent(notifyInboxRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(notifyAnyRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(notifyLabelsRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(labelsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(notifyError))
        );

        panel.add(notifyPanel);

        alertPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Alerts"));

        popupBox.setMnemonic('m');
        popupBox.setText("Display popup message");
        popupBox.setToolTipText("Display a popup message for each unread mail that arrives");

        chimeBox.setMnemonic('h');
        chimeBox.setText("Play audible chime");
        chimeBox.setToolTipText("Play an audible chime when unread mails arrive");

        ledBox.setMnemonic('b');
        ledBox.setText("Blink keyboard LED");
        ledBox.setToolTipText("Start blinking the keyboard LED when unread mails arrive");

        bellBox.setMnemonic('e');
        bellBox.setText("Play periodic audible bell");
        bellBox.setToolTipText("Start playing an audible bell at regular intervals when unread mails arrive");

        javax.swing.GroupLayout alertPanelLayout = new javax.swing.GroupLayout(alertPanel);
        alertPanel.setLayout(alertPanelLayout);
        alertPanelLayout.setHorizontalGroup(
            alertPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, alertPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(alertPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(ledBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 307, Short.MAX_VALUE)
                    .addComponent(bellBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 307, Short.MAX_VALUE)
                    .addComponent(chimeBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 307, Short.MAX_VALUE)
                    .addComponent(popupBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 307, Short.MAX_VALUE))
                .addContainerGap())
        );
        alertPanelLayout.setVerticalGroup(
            alertPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(alertPanelLayout.createSequentialGroup()
                .addComponent(popupBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chimeBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bellBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ledBox)
                .addContainerGap())
        );

        panel.add(alertPanel);

        scrollPane.setViewportView(panel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buttonsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 333, Short.MAX_VALUE)
                    .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 333, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 388, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(buttonsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel alertPanel;
    private javax.swing.JCheckBox bellBox;
    private javax.swing.JPanel buttonsPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JCheckBox chimeBox;
    private javax.swing.JButton colorButton;
    private javax.swing.JPanel labelsPanel;
    private javax.swing.JCheckBox ledBox;
    private javax.swing.JLabel lockImage;
    private javax.swing.JLabel loginError;
    private javax.swing.JPanel loginPanel;
    private javax.swing.JRadioButton notifyAnyRadio;
    private javax.swing.JLabel notifyError;
    private javax.swing.JTextField notifyField1;
    private javax.swing.JTextField notifyField2;
    private javax.swing.JTextField notifyField3;
    private javax.swing.JTextField notifyField4;
    private javax.swing.JTextField notifyField5;
    private javax.swing.JTextField notifyField6;
    private javax.swing.JRadioButton notifyInboxRadio;
    private javax.swing.JRadioButton notifyLabelsRadio;
    private javax.swing.JPanel notifyPanel;
    private javax.swing.ButtonGroup notifyRadioGroup;
    private javax.swing.JButton okButton;
    private javax.swing.JPanel panel;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JCheckBox popupBox;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JButton testButton;
    private javax.swing.JTextField usernameField;
    private javax.swing.JLabel usernameLabel;
    // End of variables declaration//GEN-END:variables
}
