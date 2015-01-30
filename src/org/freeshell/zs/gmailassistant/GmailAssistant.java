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

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import org.freeshell.zs.common.Debug;
import org.freeshell.zs.common.LoggerConsole;
import org.freeshell.zs.common.Downloader;
import org.freeshell.zs.common.ResourceManipulator;
import org.freeshell.zs.common.SimpleProperties;
import org.freeshell.zs.common.StringManipulator;
import org.freeshell.zs.common.SwingManipulator;
import org.freeshell.zs.common.TerminatingException;


/**
 * Notifier for multiple Gmail and Google Apps email accounts.
 * This is the main class of the program.
 */
final class GmailAssistant
		extends JFrame
		implements ListSelectionListener
{
	/** default program properties file */
	private static final String DEFAULT_PROGRAM_PROPERTIES =
			"/org/freeshell/zs/gmailassistant/resources/program.properties.txt";

	/** refresh interval, in milliseconds */
	private static final long REFRESH_INTERVAL_MILLISECONDS = 100L;

	/** program properties */
	final SimpleProperties properties;

	/** default account properties */
	final SimpleProperties defaultAccountProperties;

	/** program properties that can be saved and loaded */
	final SimpleProperties savedProgramProperties;

	/** account properties that can be saved and loaded */
	final SimpleProperties savedAccountProperties;

	/** program name */
	final String name;

	/** list view of email accounts */
	private final List<Account> accountsList = new ArrayList<Account>();

	/** navigable set view of email accounts */
	private final NavigableSet<Account> accountsNavigableSet = new TreeSet<Account>();

	/** mutex lock for <code>accounts</code> and <code>accountsNavigableSet</code> */
	private final Object accountsLock = new Object();

	/** table model for table of accounts */
	private final AccountsTableModel accountsTableModel = new AccountsTableModel();

	/** "Load Profile" form */
	private ProfileLoader profileLoader = null;

	/** "Save Profile" form */
	private ProfileSaver profileSaver = null;

	/** "Options" form */
	Options options = null;

	/** "Usage" dialog */
	JDialog usage = null;

	/** "About" form */
	private About about = null;

	/** tray icon for program */
	final SimpleTrayIcon trayIcon;

	/** popup alerter */
	final DesktopPopup popup;

	/** keyboard LED blinker */
	final KeyboardLedBlinker led;

	/** chime player */
	final ChimePlayer chime;

	/** last used account ID */
	private int lastId;

	/** mutex lock for this.lastId */
	private final Object lastIdLock = new Object();

	/** perform periodic refresh of the status label? */
	volatile private boolean refreshStatus = true;

	/** number of enabled accounts */
	private volatile int numEnabledAccounts = 0;

	/** number of unread mails */
	private volatile int numUnreadMails = 0;

	/** is debug mode on? */
	final boolean debug;

	/** logger console used when debug mode is on */
	final LoggerConsole logger;

	/** minimize the program on startup? */
	private static boolean minimizeOnStartup = false;


	/**
	 * Main entry point for the program.
	 *
	 * @param args
	 *      command-line arguments
	 */
	public static void main(
			final String[] args)
	{
		/************************************
		 * SCHEDULE GUI CREATION ON THE EDT *
		 ************************************/

		java.awt.EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					/* use system look and feel if possible */
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

					/* low-delay tooltips */
					ToolTipManager.sharedInstance().setInitialDelay(50);
				}
				catch (Exception e)
				{
					/* ignore */
				}

				try
				{
					final GmailAssistant ga = new GmailAssistant(args);

					if (minimizeOnStartup)
					{
						ga.minimizeProgram();
					}
					else
					{
						ga.setVisible(true);
					}
				}
				catch (Exception e)
				{
					if (e instanceof TerminatingException)
					{
						SwingManipulator.showErrorDialog(
								null,
								"Initialization Error",
								String.format("%s\nPlease file a bug report to help improve this program.\n\n%s\n\n%s",
								e.getMessage(), Debug.getSystemInformationString(), Debug.getStackTraceString(e)));
					}
					else
					{
						SwingManipulator.showErrorDialog(
								null,
								"Initialization Error",
								String.format("Failed to initialize program because of an unexpected error:\n%s" +
								"\nPlease file a bug report to help improve this program.\n\n%s\n\n%s",
								e.toString(), Debug.getSystemInformationString(), Debug.getStackTraceString(e)));
					}

					System.exit(1);
				}
			}
		});
	}


	/**
	 * Constructor.
	 *
	 * @param args
	 *     command-line arguments
	 */
	private GmailAssistant(
			final String[] args)
	{
		/*************************
		 * INITIALIZE PROPERTIES *
		 *************************/

		/* load default program properties */
		try
		{
			properties = new SimpleProperties(ResourceManipulator.resourceAsString(GmailAssistant.DEFAULT_PROGRAM_PROPERTIES));
		}
		catch (Exception e)
		{
			throw new TerminatingException(String.format("(INTERNAL) Failed to load default program properties (%s).", e.toString()));
		}

		/* compile regex patterns, if any */
		final List<String> patterns = new ArrayList<String>();

		for (String k : properties.keySet())
		{
			if (k.endsWith(".pattern"))
			{
				patterns.add(k);
			}
		}

		for (String k: patterns)
		{
			final String v = properties.getString(k);

			try
			{
				properties.set(k + ".object", Pattern.compile(v));
			}
			catch (Exception e)
			{
				throw new TerminatingException(String.format("(INTERNAL) Failed to compile regex pattern \"%s\" (%s).", v, e.toString()));
			}
		}

		/* load default account properties */
		try
		{
			defaultAccountProperties = new SimpleProperties(ResourceManipulator.resourceAsString(properties.getString("account.properties")));
		}
		catch (Exception e)
		{
			throw new TerminatingException(String.format("(INTERNAL) Failed to load default account properties (%s).", e.toString()));
		}

		/* keys of program properties that can be saved/loaded in the profile */
		try
		{
			savedProgramProperties = new SimpleProperties(ResourceManipulator.resourceAsString(properties.getString("saved.program.properties")));
		}
		catch (Exception e)
		{
			throw new TerminatingException(String.format("(INTERNAL) Failed to load saved program properties (%s).", e.toString()));
		}

		/* keys of account properties that should be saved/loaded in the profile */
		try
		{
			savedAccountProperties = new SimpleProperties(ResourceManipulator.resourceAsString(properties.getString("saved.account.properties")));
		}
		catch (Exception e)
		{
			throw new TerminatingException(String.format("(INTERNAL) Failed to load saved account properties (%s).", e.toString()));
		}

		/* program name */
		name = properties.getString("name");

		/*********************
		 * INITIALIZE ALERTS *
		 *********************/

		popup = new DesktopPopup(this);
		chime = new ChimePlayer(this);
		led = new KeyboardLedBlinker(this);

		/******************************
		 * INITIALIZE FORM COMPONENTS *
		 ******************************/

		initComponents();

		/*****************************
		 * CONFIGURE FORM COMPONENTS *
		 *****************************/
		setTitle(name);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowIconified(WindowEvent e)
			{
				minimizeProgram();
			}

			@Override
			public void windowDeiconified(WindowEvent e)
			{
				restoreProgram();
			}

			@Override
			public void windowClosing(WindowEvent e)
			{
				closeProgram();
			}
		});

		/* menu item: "Load Profiles..." */
		loadItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (profileLoader == null)
				{
					profileLoader = new ProfileLoader(GmailAssistant.this);
				}

				profileLoader.showForm();
			}
		});

		/* menu item: "Save Profile..." */
		saveItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (profileSaver == null)
				{
					profileSaver = new ProfileSaver(GmailAssistant.this);
				}

				profileSaver.showForm();
			}
		});

		/* menu item: "Exit" */
		exitItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				closeProgram();
			}
		});

		/* menu item: "Options..." */
		optionsItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (options == null)
				{
					options = new Options(GmailAssistant.this);
				}

				options.showForm();
			}
		});

		/* menu item: "Usage..." */
		usageItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (usage == null)
				{
					try
					{
						usage = SwingManipulator.createModelessInfoDialog(
								GmailAssistant.this,
								String.format("Usage - %s", name),
								String.format("Usage information for %s", name),
								ResourceManipulator.resourceAsString(properties.getString("usage")),
								10);
					}
					catch (Exception ex)
					{
						SwingManipulator.showErrorDialog(
								GmailAssistant.this,
								name,
								String.format("(INTERNAL) Failed to load \"Usage\" text (%s).", ex.toString()));
					}
				}

				usage.setVisible(true);
			}
		});

		/* menu item: "About..." */
		aboutItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (about == null)
				{
					about = new About(GmailAssistant.this);
				}

				about.showForm();
			}
		});

		/* menu item: "Check for Update" */
		updateItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				new Thread(new Runnable()
				{
					public void run()
					{
						checkForUpdate(true, true);
					}
				}).start();
			}
		});

		/* button: "Add" */
		addButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				addNewAccount(null);
				valueChanged(null);
			}
		});

		/* button: "Remove" */
		removeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				removeButton.setEnabled(false);
				removeAccounts();
				valueChanged(null);
			}
		});

		/* button: "Edit" */
		editButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				editButton.setEnabled(false);
				editAccounts();
				valueChanged(null);
			}
		});

		/* button: "Enable" */
		enableButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				enableButton.setEnabled(false);
				enableAccounts();
				valueChanged(null);
			}
		});

		/* button: "Disable" */
		disableButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				disableButton.setEnabled(false);
				disableAccounts();
				valueChanged(null);
			}
		});

		/* key binding: ESCAPE key */
		accountsPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_CANCEL_BUTTON");

		accountsPane.getActionMap().put("ESCAPE_CANCEL_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				minimizeProgram();
			}
		});

		/* key binding: DELETE key */
		accountsPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DELETE_REMOVE_BUTTON");

		accountsPane.getActionMap().put("DELETE_REMOVE_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				removeButton.doClick();
			}
		});

		/* table of accounts */
		accountsTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		final TableColumnModel colModel = accountsTable.getColumnModel();
		colModel.getColumn(0).setMinWidth(0);
		colModel.getColumn(1).setMinWidth(0);
		colModel.getColumn(2).setMinWidth(0);
		colModel.getColumn(3).setMinWidth(0);
		colModel.getColumn(0).setPreferredWidth(5);
		colModel.getColumn(1).setPreferredWidth(80);
		colModel.getColumn(2).setPreferredWidth(40);
		colModel.getColumn(3).setPreferredWidth(200);

		accountsTable.setToolTipText("Mouse-over any row for more information");
		accountsTable.getSelectionModel().addListSelectionListener(this);

		accountsTable.setDefaultRenderer(TableCellContent.class, new AccountsTableCellRenderer(
				accountsTable.getForeground(),
				accountsTable.getBackground(),
				accountsTable.getSelectionForeground(),
				accountsTable.getSelectionBackground()));

		/* accounts popup menu (add, remove, edit, enable, disable) */
		final JPopupMenu accountsPopupMenu = new JPopupMenu();

		/* accounts popup menu: "Add" */
		final JMenuItem addMenuItem = new JMenuItem("Add", 'A');
		addMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				addButton.doClick();
			}
		});
		accountsPopupMenu.add(addMenuItem);

		/* accounts popup menu: "Remove" */
		final JMenuItem removeMenuItem = new JMenuItem("Remove", 'R');
		removeMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				removeButton.doClick();
			}
		});
		accountsPopupMenu.add(removeMenuItem);

		/* accounts popup menu: "Edit" */
		final JMenuItem editMenuItem = new JMenuItem("Edit", 't');
		editMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				editButton.doClick();
			}
		});
		accountsPopupMenu.add(editMenuItem);

		/* accounts popup menu: "Enable" */
		final JMenuItem enableMenuItem = new JMenuItem("Enable", 'E');
		enableMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				enableButton.doClick();
			}
		});
		accountsPopupMenu.add(enableMenuItem);

		/* accounts popup menu: "Disable" */
		final JMenuItem disableMenuItem = new JMenuItem("Disable", 'D');
		disableMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				disableButton.doClick();
			}
		});
		accountsPopupMenu.add(disableMenuItem);
		accountsPopupMenu.addSeparator();

		/* accounts popup menu: "Select All" */
		final JMenuItem selectAllMenuItem = new JMenuItem("Select All", 'S');
		selectAllMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				accountsTable.selectAll();
			}
		});
		accountsPopupMenu.add(selectAllMenuItem);

		/* accounts popup menu: "Clear All" */
		final JMenuItem clearAllMenuItem = new JMenuItem("Clear All", 'C');
		clearAllMenuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				accountsTable.clearSelection();
			}
		});
		accountsPopupMenu.add(clearAllMenuItem);

		accountsTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				processMouseEvent(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				processMouseEvent(e);
			}

			private void processMouseEvent(
					final MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					/* display popup menu */
					final int i = accountsTable.rowAtPoint(e.getPoint());

					if ((i >= 0) && (!accountsTable.isRowSelected(i)))
					{
						accountsTable.setRowSelectionInterval(i, i);
					}

					accountsPopupMenu.show(e.getComponent(), e.getX(), e.getY());
				}
				else if (SwingUtilities.isLeftMouseButton(e))
				{
					final int i = accountsTable.rowAtPoint(e.getPoint());

					if (e.getClickCount() >= 2)
					{
						/* open selected account for editing */
						if (i >= 0)
						{
							accountsTable.setRowSelectionInterval(i, i);
							editButton.doClick();
						}
					}
					else
					{
						/* clear selection */
						if (i < 0)
						{
							accountsTable.clearSelection();
						}
					}
				}
			}
		});

		/* center form on the screen */
		setLocationRelativeTo(null);

		/*************************************************
		 * SETUP PROGRAM ICON, TRAY ICON, AND POPUP MENU *
		 *************************************************/

		/* setup program icon */
		try
		{
			setIconImage(ImageIO.read(GmailAssistant.class.getResource("/org/freeshell/zs/gmailassistant/resources/ga_logo_64.png")));
		}
		catch (IOException e)
		{
			throw new TerminatingException(String.format("(INTERNAL) Failed to load program icon image (%s).", e.toString()));
		}

		/* create popup menu for system tray icon */
		final PopupMenu trayMenu = new PopupMenu(name);

		/* popup menu: "Check Mail Now" */
		final MenuItem checkItem = new MenuItem("Check Mail Now");
		checkItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				checkMailNow();
			}
		});
		trayMenu.add(checkItem);

		/* popup menu: "Tell me again..." */
		final MenuItem againItem = new MenuItem("Tell me Again...");
		againItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				popup.showAllMessages();
			}
		});
		trayMenu.add(againItem);

		/* popup menu: "Reset Alerts" */
		final MenuItem resetItem = new MenuItem("Reset Alerts");
		resetItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				resetAlerts();
			}
		});
		trayMenu.add(resetItem);
		trayMenu.addSeparator();

		/* popup menu: "Restore" */
		final MenuItem restoreItem = new MenuItem("Restore");
		restoreItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				restoreProgram();
			}
		});
		trayMenu.add(restoreItem);

		/* popup menu: "Exit" */
		final MenuItem exitTrayItem = new MenuItem("Exit");
		exitTrayItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				closeProgram();
			}
		});
		trayMenu.add(exitTrayItem);

		try
		{
			trayIcon = new SimpleTrayIcon(this);
			trayIcon.setPopupMenu(trayMenu);
			trayIcon.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					restoreProgram();
				}
			});

			SystemTray.getSystemTray().add(trayIcon);
		}
		catch (IOException e)
		{
			throw new TerminatingException(String.format("(INTERNAL) Failed to load system tray icon images (%s).", e.toString()));
		}
		catch (AWTException ex)
		{
			throw new TerminatingException("Failed to set system tray icon. If you are using a Linux operating system," +
					" try disabling the advanced visual effects of the window manager.");
		}

		/*************************
		 * REFRESH PROGRAM MODES *
		 *************************/

		refreshAlwaysOnTopMode();
		refreshProxyMode();
		valueChanged(null);

		/**************************
		 * START CHECK FOR UPDATE *
		 **************************/

		new Thread(new Runnable()
		{
			public void run()
			{
				checkForUpdate(false, false);
			}
		}).start();

		/**********************************
		 * PROCESS COMMAND-LINE ARGUMENTS *
		 **********************************/

		final List<File> profileFiles = new ArrayList<File>();
		boolean debugSwitch = false;
		boolean noloadSwitch = false;

		for (String s : args)
		{
			try
			{
				if (s.startsWith("--load:"))
				{
					/* "load profile" */
					final String a = s.substring(s.indexOf(':') + 1);

					if (a.isEmpty())
					{
						throw new IllegalArgumentException("Empty --load parameter: A filename must be specified, e.g. --load:\"myprofile.ga\".");
					}

					profileFiles.add(new File(a));
				}
				else if ("--noload".equals(s))
				{
					noloadSwitch = true;
				}
				else if ("--debug".equals(s))
				{
					debugSwitch = true;
				}
				else if ("--minimize".equals(s))
				{
					minimizeOnStartup = true;
				}
				else
				{
					/* invalid switch */
					throw new IllegalArgumentException(String.format("\"%s\" is not a valid command-line switch.", s));
				}
			}
			catch (IllegalArgumentException e)
			{
				SwingManipulator.showErrorDialog(
						null,
						String.format("Initialization Error - %s", name),
						String.format("%s\n%s will ignore this switch. Please see Help > Usage for more information.",
						e.getMessage(), name));
			}
		}

		debug = debugSwitch;
		final boolean noload = noloadSwitch;

		/* create debug console menu item if necessary */
		if (debug)
		{
			logger = new LoggerConsole(String.format("Debug Console - %s", name));

			final JMenuItem debugItem = new JMenuItem("Show Debug Console", 'd');
			debugItem.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					logger.showForm();
				}
			});
			helpMenu.add(debugItem);
		}
		else
		{
			logger = null;
		}

		/*****************
		 * LOAD PROFILES *
		 *****************/

		new Thread(new Runnable()
		{
			public void run()
			{
				/* find "*.ga" profile files in the current directory, if necessary */
				if ((!noload) && profileFiles.isEmpty())
				{
					File d = new File(".");

					try
					{
						d = d.getCanonicalFile();
					}
					catch (Exception e)
					{
						/* ignore */
					}

					final File[] listFiles = d.listFiles();

					if (listFiles != null)
					{
						for (File f : listFiles)
						{
							if (f.getName().endsWith("." + properties.getString("profile.default.extension")))
							{
								profileFiles.add(f);
							}
						}
					}

					/* sort alphabetically */
					Collections.sort(profileFiles);
				}

				/* load profiles */
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						loadProfiles(profileFiles);
					}
				});
			}
		}).start();

		/************************************************************
		 * START MONITORING NUMBER OF UNREAD MAILS & EMAIL ACCOUNTS *
		 ************************************************************/

		new Thread(new Runnable()
		{
			public void run()
			{
				while (true)
				{
					if (refreshStatus)
					{
						final int m = numUnreadMails;
						final int a = numEnabledAccounts;

						SwingManipulator.updateLabel(
								status,
								String.format(" %d unread %s (monitoring %d %s)",
								m, (m == 1) ? "mail" : "mails", a, (a == 1) ? "account" : "accounts"));
					}

					Debug.sleep(10 * REFRESH_INTERVAL_MILLISECONDS);
				}
			}
		}).start();
	}


	/**
	 * Check for update.
	 * This method should run on a dedicated worker thread, not the EDT.
	 *
	 * @param showPromptIfLatest
	 *     should a user prompt be displayed even if the current program is the latest release?
	 * @param showPromptOnError
	 *     should a user prompt be displayed if an error occurs?
	 */
	private void checkForUpdate(
			final boolean showPromptIfLatest,
			final boolean showPromptOnError)
	{
		refreshStatus = false;
		SwingManipulator.updateLabel(status, " Checking for update...");
		final SimpleProperties updateProperties = new SimpleProperties();

		try
		{
			/* try to retrieve latest defintions from homepage */
			final StringBuilder sb = new StringBuilder();

			final Downloader d = new Downloader(
					new URL(properties.getString("update.url")),
					sb);

			new Thread(d).start();

			while (true)
			{
				if (d.isProgressUpdated())
				{
					final int percent = d.getProgressPercent();

					SwingManipulator.updateLabel(
							status,
							String.format(" Checking for update: %s %s",
							d.getProgressString(), (percent >= 0) ? String.format("(%d%%)", percent) : ""));
				}

				if (d.isCompleted())
				{
					d.waitUntilCompleted();
					break;
				}

				Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);
			}

			/* process update information */
			SwingManipulator.updateLabel(status, " Checking for update...");

			for (String s : sb.toString().split("[\n\r\u0085\u2028\u2028]++"))
			{
				if (s.isEmpty() || s.startsWith("#"))
				{
					/* ignore empty lines and comments */
				}
				else if (s.contains(":"))
				{
					/* process "key:value" pair */
					final String[] kv = StringManipulator.parseKeyValueString(s);
					updateProperties.setString(kv[0], kv[1]);
				}
			}
		}
		catch (Exception e)
		{
			if (showPromptOnError)
			{
				SwingManipulator.showErrorDialog(
						this,
						String.format("Check for Update - %s", name),
						String.format("Failed to retrieve update information from the %s homepage (%s).\nPlease try again later. If the problem persists, please visit the homepage manually.",
						name, e.toString()));
			}

			SwingManipulator.updateLabel(status, " ");
			refreshStatus = true;
			return;
		}

		/* show user prompt if an update is available */
		try
		{
			final String version = updateProperties.getString("update.version");
			final String date = updateProperties.getString("update.date");
			final String download = updateProperties.getString("update.download");
			final String comment = updateProperties.getString("update.comment");

			if (properties.getString("date").compareTo(date) < 0)
			{
				/* new release is available */
				final int choice = SwingManipulator.showModalOptionTextDialog(
						this,
						String.format("A new release of %s is available", name),
						String.format("%s %s (%s) is available for download. %s\n\n%s",
						name, version, date,
						Double.parseDouble(properties.getString("version")) < Double.parseDouble(version) ?
							"This is a major update and is strongly recommended." :
							"This is a minor update and is recommended if you are currently experiencing problems.",
						comment),
						8,
						String.format("Check for Update - %s", name),
						JOptionPane.DEFAULT_OPTION,
						JOptionPane.INFORMATION_MESSAGE,
						null,
						new String[] {"Download Now", "Cancel"},
						0);

				/* download now */
				if (choice == 0)
				{
					try
					{
						Desktop.getDesktop().browse(new URI(download));
					}
					catch (Exception ex)
					{
						/* ignore */
					}
				}
			}
			else
			{
				/* this release is up-to-date */
				if (showPromptIfLatest)
				{
					SwingManipulator.showInfoDialog(
							this,
							String.format("Check for Update - %s", name),
							"No update found",
							String.format("This release of %s %s (%s) is already up-to-date.",
							name, properties.getString("version"), properties.getString("date")),
							5);
				}
			}
		}
		catch (Exception e)
		{
			/* unable to parse */
			if (showPromptOnError)
			{
				SwingManipulator.showWarningDialog(
						this,
						String.format("Check for Update - %s", name),
						String.format("Failed to parse update information from the %s homepage (%s).\nPlease try again later. If the problem persists, please visit the homepage manually.",
						name, e.toString()));
			}
		}

		SwingManipulator.updateLabel(status, " ");
		refreshStatus = true;
	}


	/**
	 * Respond to a list selection event on the table of accounts.
	 * Enables or disables the appropriate buttons on the form according to the current
	 * table selection.
	 * This method must run on the EDT.
	 *
	 * @param e
	 *      list selection event
	 */
	@Override
	public void valueChanged(
			ListSelectionEvent e)
	{
		if (accountsTable.getSelectedRowCount() == 0)
		{
			removeButton.setEnabled(false);
			editButton.setEnabled(false);
			enableButton.setEnabled(false);
			disableButton.setEnabled(false);
		}
		else
		{
			removeButton.setEnabled(true);
			editButton.setEnabled(true);
			enableButton.setEnabled(true);
			disableButton.setEnabled(true);
		}
	}


	/**
	 * Check for unread mails in all accounts now.
	 */
	private void checkMailNow()
	{
		synchronized (accountsLock)
		{
			for (Account ac : accountsList)
			{
				ac.checkMailNow();
			}
		}
	}


	/**
	 * Reset all alerts.
	 */
	private void resetAlerts()
	{
		trayIcon.setNormalIcon();
		popup.cancelAllMessages();
		chime.cancelAll();
		chime.stopPeriodicBell();
		led.cancelAll();

		synchronized (accountsLock)
		{
			for (Account ac : accountsList)
			{
				ac.properties.setBoolean("new.unread.mails", false);
			}
		}
	}


	/**
	 * Load the specified profile files.
	 * This method must run on the EDT.
	 *
	 * @param files
	 *      profile files to be loaded
	 */
	private void loadProfiles(
			final List<File> files)
	{
		if (profileLoader == null)
		{
			profileLoader = new ProfileLoader(this);
		}

		profileLoader.loadProfiles(files);
	}


	/**
	 * Add a new account with the specified properties.
	 * The account form is made visible for editing, if necessary.
	 * This method can be called on any thread.
	 *
	 * @param accountProperties
	 *      account properties for the new account; if null, the default properties
	 *      will be applied
	 */
	void addNewAccount(
			final SimpleProperties accountProperties)
	{
		new Thread(new Runnable()
		{
			public void run()
			{
				refreshStatus = false;
				SwingManipulator.updateLabel(status, " Adding new account...");

				/* increment last account ID used */
				final int id;

				synchronized (lastIdLock)
				{
					id = ++lastId;
				}

				final Account ac;
				final boolean editAccount;

				if (accountProperties == null)
				{
					editAccount = true;
					ac = new Account(GmailAssistant.this, id, new SimpleProperties(defaultAccountProperties));
				}
				else
				{
					if (accountProperties.getBoolean("enabled") &&
							accountProperties.getString("password").isEmpty())
					{
						editAccount = true;
						accountProperties.setBoolean("enabled", false);
					}
					else
					{
						editAccount = false;
					}

					ac = new Account(GmailAssistant.this, id, accountProperties);
				}

				/* add newly created account to table of accounts */
				synchronized (accountsLock)
				{
					accountsList.add(ac);
					accountsNavigableSet.add(ac);
				}

				refreshTotalUnreadMailCount();
				SwingManipulator.updateLabel(status, " ");
				refreshStatus = true;

				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						/* make account form visible for editing, if necessary */
						if (editAccount)
						{
							ac.editAccount();
						}

						accountsTableModel.fireTableDataChanged();
					}
				});
			}
		}).start();
	}


	/**
	 * Remove the selected accounts.
	 * This method must run on the EDT.
	 */
	private void removeAccounts()
	{
		refreshStatus = false;
		SwingManipulator.updateLabel(status, " Removing selected accounts...");

		synchronized (accountsLock)
		{
			final List<Account> accountsToRemove = new ArrayList<Account>();

			for (int i : accountsTable.getSelectedRows())
			{
				final Account ac = accountsList.get(accountsTable.convertRowIndexToModel(i));
				accountsToRemove.add(ac);
			}

			for (Account ac : accountsToRemove)
			{
				accountsList.remove(ac);
				accountsNavigableSet.remove(ac);
				ac.removeAccount();
			}
		}

		accountsTableModel.fireTableDataChanged();
		refreshTotalUnreadMailCount();
		SwingManipulator.updateLabel(status, " ");
		refreshStatus = true;
	}


	/**
	 * Edit the selected accounts.
	 * This method must run on the EDT.
	 */
	private void editAccounts()
	{
		refreshStatus = false;
		SwingManipulator.updateLabel(status, " Opening selected accounts for editing...");

		synchronized (accountsLock)
		{
			for (int i : accountsTable.getSelectedRows())
			{
				final Account ac = accountsList.get(accountsTable.convertRowIndexToModel(i));
				ac.editAccount();
			}
		}

		refreshTotalUnreadMailCount();
		SwingManipulator.updateLabel(status, " ");
		refreshStatus = true;
	}


	/**
	 * Enable the selected accounts.
	 * This method must run on the EDT.
	 */
	private void enableAccounts()
	{
		refreshStatus = false;
		SwingManipulator.updateLabel(status, " Enabling selected accounts...");

		synchronized (accountsLock)
		{
			for (int i : accountsTable.getSelectedRows())
			{
				final Account ac = accountsList.get(accountsTable.convertRowIndexToModel(i));
				ac.enableAccount();
			}
		}

		refreshTotalUnreadMailCount();
		SwingManipulator.updateLabel(status, " ");
		refreshStatus = true;
	}


	/**
	 * Disable the selected accounts.
	 * This method must run on the EDT.
	 */
	private void disableAccounts()
	{
		refreshStatus = false;
		SwingManipulator.updateLabel(status, " Disabling selected accounts...");

		synchronized (accountsLock)
		{
			for (int i : accountsTable.getSelectedRows())
			{
				final Account ac = accountsList.get(accountsTable.convertRowIndexToModel(i));
				ac.disableAccount();
			}
		}

		refreshTotalUnreadMailCount();
		SwingManipulator.updateLabel(status, " ");
		refreshStatus = true;
	}


	/**
	 * Refresh the specified account on the table.
	 * This method can be called on any thread.
	 *
	 * @param ac
	 *      account to be refreshed
	 */
	void refreshAccountOnTable(
			final Account ac)
	{
		synchronized (accountsLock)
		{
			final int i = accountsList.indexOf(ac);

			if (i >= 0)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						accountsTableModel.fireTableRowsUpdated(i, i);
						valueChanged(null);
					}
				});
			}
		}
	}


	/**
	 * Refresh the total number of unread mails for all accounts.
	 * This method can be called on any thread.
	 */
	void refreshTotalUnreadMailCount()
	{
		int countUnreadMails = 0;
		int countEnabledAccounts = 0;
		boolean error = false;
		boolean keepHotIcon = false;
		boolean keepPopup = false;
		boolean keepChime = false;
		boolean keepPeriodicBell = false;
		boolean keepLed = false;

		synchronized (accountsLock)
		{
			for (Account ac : accountsList)
			{
				if (ac.properties.getBoolean("enabled"))
				{
					countEnabledAccounts++;
				}

				int unreadMails = ac.properties.getInt("unread.mails");

				/* handle case of "N/A" unread mails */
				if (unreadMails < 0)
				{
					unreadMails = 0;
				}

				/* update total number of unread mails */
				countUnreadMails += unreadMails;

				if (unreadMails == 0)
				{
					ac.properties.setBoolean("new.unread.mails", false);
				}
				else
				{
					/* keep "Popup" alerts if there are any unread mails */
					keepPopup = true;

					if (ac.properties.getBoolean("new.unread.mails"))
					{
						/* keep hot icon if there are NEW unread mails */
						keepHotIcon = true;

						/* keep "Chime", "Periodic Bell", and "LED" alerts if there are NEW unread mails */
						if (ac.properties.getBoolean("alert.chime"))
						{
							keepChime = true;
						}

						if (ac.properties.getBoolean("alert.periodic.bell"))
						{
							keepPeriodicBell = true;
						}

						if (ac.properties.getBoolean("alert.led"))
						{
							keepLed = true;
						}
					}
				}

				/* has an error occured in any account? */
				if (ac.properties.getBoolean("error"))
				{
					error = true;
				}
			}
		}

		numUnreadMails = countUnreadMails;
		numEnabledAccounts = countEnabledAccounts;

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				final int m = numUnreadMails;

				trayIcon.setToolTip(String.format("%s (%d unread %s)",
						properties.getString("name"), m, (m == 1) ? "mail" : "mails"));
			}
		});

		if (!keepHotIcon)
		{
			trayIcon.setNormalIcon();
		}

		if (!keepPopup)
		{
			popup.cancelAllMessages();
		}

		if (!keepChime)
		{
			chime.cancelAll();
		}

		if (!keepPeriodicBell)
		{
			chime.stopPeriodicBell();
		}

		if (!keepLed)
		{
			led.cancelAll();
		}

		if (error)
		{
			trayIcon.setErrorIcon();
		}
		else
		{
			trayIcon.clearErrorIcon();
		}
	}


	/**
	 * Close the program.
	 * This method can be called on any thread.
	 * This method calls System.exit() and never returns.
	 */
	private void closeProgram()
	{
		final int a = numEnabledAccounts;

		if (a > 0)
		{
			/* prompt user about timers that are still running */
			final int choice = JOptionPane.showConfirmDialog(
					GmailAssistant.this,
					String.format("%s is still monitoring %d %s. Exit now?",
					name, a, (a == 1) ? "account" : "accounts"),
					String.format("Confirm Exit - %s", name),
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);

			if (choice != JOptionPane.YES_OPTION)
			{
				return;
			}
		}

		led.terminate();
		System.exit(0);
	}


	/**
	 * Minimize the program to the tray.
	 * This method must run on the EDT.
	 */
	private void minimizeProgram()
	{
		setExtendedState(JFrame.ICONIFIED);
		setVisible(false);
	}


	/**
	 * Restore the program from the tray.
	 * This method must run on the EDT.
	 */
	private void restoreProgram()
	{
		setVisible(true);
		setExtendedState(JFrame.NORMAL);
		toFront();
	}


	/**
	 * Get the first account.
	 * This method can be called on any thread.
	 *
	 * @return
	 *     first account; null if there are no accounts
	 */
	Account getFirstAccount()
	{
		try
		{
			synchronized (accountsLock)
			{
				return accountsNavigableSet.first();
			}
		}
		catch (NoSuchElementException e)
		{
			return null;
		}
	}


	/**
	 * Get the last account.
	 * This method can be called on any thread.
	 *
	 * @return
	 *     last account; null if there are no accounts
	 */
	Account getLastAccount()
	{
		try
		{
			synchronized (accountsLock)
			{
				return accountsNavigableSet.last();
			}
		}
		catch (NoSuchElementException e)
		{
			return null;
		}
	}


	/**
	 * Get the next account that comes after the specified account.
	 * This method can be called on any thread.
	 *
	 * @param ac
	 *     account
	 * @return
	 *     next account that comes after <code>ac</code>; null if there is none
	 */
	Account getNextAccount(
			final Account ac)
	{
		synchronized (accountsLock)
		{
			return accountsNavigableSet.higher(ac);
		}
	}


	/**
	 * Get the previous account that comes before the specified account.
	 * This method can be called on any thread.
	 *
	 * @param ac
	 *     account
	 * @return
	 *     previous account that comes before <code>ac</code>; null if there is none
	 */
	Account getPreviousAccount(
			final Account ac)
	{
		synchronized (accountsLock)
		{
			return accountsNavigableSet.lower(ac);
		}
	}


	/**
	 * Refresh the "Always on Top" mode of the program.
	 * This method must run on the EDT.
	 */
	void refreshAlwaysOnTopMode()
	{
		final boolean state = properties.getBoolean("always.on.top");

		final JFrame[] frames = new JFrame[]
		{
			this,
			profileLoader,
			profileSaver,
			options,
			about
		};

		for (JFrame f : frames)
		{
			if (f != null)
			{
				try
				{
					f.setAlwaysOnTop(state);
				}
				catch (Exception ee)
				{
					/* ignore */
				}
			}
		}

		synchronized (accountsLock)
		{
			/* propagate mode to other forms */
			for (Account ac : accountsList)
			{
				try
				{
					ac.setAlwaysOnTop(state);
				}
				catch (Exception ee)
				{
					/* ignore */
				}
			}
		}
	}


	/**
	 * Refresh SOCKS proxy mode.
	 */
	void refreshProxyMode()
	{
		Debug.setSystemProperty("socksProxyHost", properties.getString("proxy.host"));
		Debug.setSystemProperty("socksProxyPort", properties.getString("proxy.port"));
		Debug.setSystemProperty("java.net.socks.username", properties.getString("proxy.username"));
		Debug.setSystemProperty("java.net.socks.password", properties.getString("proxy.password"));
	}


	/**
	 * Save account properties as byte arrays in the given list.
	 *
	 * @param bytes
	 *     list of byte arrays to be populated
	 * @param saveAccountPasswords
	 *     should account passwords be saved?
	 * @param charset
	 *     character set encoding
	 * @param newlineBytes
	 *     byte array representing the new line character
	 * @param colonBytes
	 *     byte array representing the colon character
	 * @param leftAngleBytes
	 *     byte array representing the left angle character
	 * @param rightAngleBytes
	 *     byte array representing the right angle character
	 * @param leftSquareBytes
	 *     byte array representing the left square character
	 * @param rightSquareBytes
	 *     byte array representing the right square character
	 * @throws java.io.UnsupportedEncodingException
	 *     on any errors in character set encoding
	 */
	void saveAccountProperties(
			final List<byte[]> bytes,
			final boolean saveAccountPasswords,
			final String charset,
			final byte[] newlineBytes,
			final byte[] colonBytes,
			final byte[] leftAngleBytes,
			final byte[] rightAngleBytes,
			final byte[] leftSquareBytes,
			final byte[] rightSquareBytes)
			throws UnsupportedEncodingException
	{
		synchronized (accountsLock)
		{
			for (Account ac : accountsList)
			{
				/* "[username]" */
				bytes.add(leftSquareBytes);
				bytes.add(ac.properties.getAsString("username").getBytes(charset));
				bytes.add(rightSquareBytes);
				bytes.add(newlineBytes);

				/* save password, if requested */
				if (saveAccountPasswords)
				{
					bytes.add("password".getBytes(charset));
					bytes.add(colonBytes);
					bytes.add(ac.properties.getAsString("password").getBytes(charset));
					bytes.add(newlineBytes);
				}

				for (String k : savedAccountProperties.keySet())
				{
					/* write "key:value" pair */
					bytes.add(k.getBytes(charset));
					bytes.add(colonBytes);
					bytes.add(ac.properties.getAsString(k).getBytes(charset));
					bytes.add(newlineBytes);
				}
			}
		}
	}


	/**
	 * Provide a string description for a specified time duration.
	 *
	 * @param millis
	 *      time duration in milliseconds
	 * @return
	 *      string description for the specified time duration (e.g. "5 minutes", "1 hour")
	 */
	static String timeDurationString(
			final long millis)
	{
		String s;

		final long t = (millis == Long.MIN_VALUE) ? Long.MAX_VALUE : Math.abs(millis);
		final long seconds = t / 1000L;
		final long minutes = t / 60000L;
		final long hours = t / 3600000L;
		final long days = t / 86400000L;

		if (days >= 2)
		{
			s = days + " days";
		}
		else if (hours >= 2)
		{
			s = hours + " hours";
		}
		else if (minutes >= 2)
		{
			s = minutes + " minutes";
		}
		else if (seconds >= 2)
		{
			s = seconds + " seconds";
		}
		else if (t > 0)
		{
			s = "1 second";
		}
		else
		{
			s = "0 seconds";
		}

		return s;
	}

	/******************
	 * NESTED CLASSES *
	 ******************/

	/**
	 * Represent the model for the table of accounts.
	 */
	private class AccountsTableModel
			extends AbstractTableModel
	{
		/** name of each column (headers) */
		private final String[] columnNames =
		{
			"",
			"Username",
			"Unread mails",
			"Status"
		};

		/** class of each column */
		private final Class[] columnClasses =
		{
			TableCellContent.class,
			TableCellContent.class,
			TableCellContent.class,
			TableCellContent.class
		};

		/** cached copy of the table cell contents, for rendering */
		private final Map<Account,TableCellContent[]> contents = new HashMap<Account,TableCellContent[]>();

		@Override
		public int getRowCount()
		{
			synchronized (accountsLock)
			{
				return accountsList.size();
			}
		}

		@Override
		public int getColumnCount()
		{
			return columnNames.length;
		}

		@Override
		public String getColumnName(
				int col)
		{
			return columnNames[col];
		}

		@Override
		public Class getColumnClass(
				int col)
		{
			return columnClasses[col];
		}

		@Override
		public Object getValueAt(
				int row,
				int col)
		{
			Account ac;

			synchronized (accountsLock)
			{
				ac = accountsList.get(row);
			}

			TableCellContent[] c;

			synchronized (contents)
			{
				c = contents.get(ac);

				if (c == null)
				{
					c = new TableCellContent[columnNames.length];

					for (int i = 0; i < columnNames.length; i++)
					{
						c[i] = new TableCellContent(ac);
					}

					contents.put(ac, c);

					/* initialize invariant properties of the cell contents */

					/* color */
					c[0].align = JLabel.CENTER;

					/* username */
					c[1].align = JLabel.CENTER;

					/* unread mails */
					c[2].text = "N/A";
					c[2].align = JLabel.CENTER;
				}
			}

			switch (col)
			{
				case 0:
					/* color */
					c[col].text = String.format(
							"<html><span style='color:%1$s;background-color:%1$s'>&nbsp;M&nbsp;</span></html>",
							ac.properties.getString("color.html"));
					break;

				case 1:
					/* username */
					final String username = ac.properties.getString("username");
					c[col].text = username.isEmpty() ? ("New Account #" + ac.accountId) : username;
					break;

				case 2:
					/* unread mails */
					final int n = ac.properties.getInt("unread.mails");
					c[col].value = n;
					c[col].text = (n >= 0) ? Integer.toString(n) : "N/A";
					break;

				case 3:
					/* status */
					c[col].text = ac.properties.getString("status");

					break;

				default:
					return null;
			}

			return c[col];
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

		accountsPane = new javax.swing.JScrollPane();
		accountsTable = new javax.swing.JTable();
		buttonsPanel = new javax.swing.JPanel();
		addButton = new javax.swing.JButton();
		removeButton = new javax.swing.JButton();
		editButton = new javax.swing.JButton();
		enableButton = new javax.swing.JButton();
		disableButton = new javax.swing.JButton();
		status = new javax.swing.JLabel();
		menuBar = new javax.swing.JMenuBar();
		fileMenu = new javax.swing.JMenu();
		loadItem = new javax.swing.JMenuItem();
		saveItem = new javax.swing.JMenuItem();
		exitItem = new javax.swing.JMenuItem();
		toolsMenu = new javax.swing.JMenu();
		optionsItem = new javax.swing.JMenuItem();
		helpMenu = new javax.swing.JMenu();
		usageItem = new javax.swing.JMenuItem();
		aboutItem = new javax.swing.JMenuItem();
		updateItem = new javax.swing.JMenuItem();

		setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

		accountsTable.setAutoCreateRowSorter(true);
		accountsTable.setModel(this.accountsTableModel);
		accountsTable.setFillsViewportHeight(true);
		accountsPane.setViewportView(accountsTable);
		accountsTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

		buttonsPanel.setLayout(new java.awt.GridLayout(1, 0));

		addButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/add.png"))); // NOI18N
		addButton.setMnemonic('A');
		addButton.setText("Add");
		addButton.setToolTipText("Add a new account");
		addButton.setIconTextGap(8);
		addButton.setNextFocusableComponent(addButton);
		buttonsPanel.add(addButton);

		removeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/cross.png"))); // NOI18N
		removeButton.setMnemonic('R');
		removeButton.setText("Remove");
		removeButton.setToolTipText("Remove selected accounts");
		removeButton.setIconTextGap(8);
		removeButton.setNextFocusableComponent(removeButton);
		buttonsPanel.add(removeButton);

		editButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/pencil.png"))); // NOI18N
		editButton.setMnemonic('t');
		editButton.setText("Edit");
		editButton.setToolTipText("Edit selected accounts");
		editButton.setIconTextGap(8);
		editButton.setNextFocusableComponent(editButton);
		buttonsPanel.add(editButton);

		enableButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/lightbulb.png"))); // NOI18N
		enableButton.setMnemonic('E');
		enableButton.setText("Enable");
		enableButton.setToolTipText("Enable selected accounts");
		enableButton.setIconTextGap(8);
		enableButton.setNextFocusableComponent(enableButton);
		buttonsPanel.add(enableButton);

		disableButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/lightbulb_off.png"))); // NOI18N
		disableButton.setMnemonic('D');
		disableButton.setText("Disable");
		disableButton.setToolTipText("Disable selected accounts");
		disableButton.setIconTextGap(8);
		disableButton.setNextFocusableComponent(disableButton);
		buttonsPanel.add(disableButton);

		status.setText(" ");

		fileMenu.setMnemonic('F');
		fileMenu.setText("File");

		loadItem.setMnemonic('L');
		loadItem.setText("Load Profiles...");
		fileMenu.add(loadItem);

		saveItem.setMnemonic('S');
		saveItem.setText("Save Profile...");
		fileMenu.add(saveItem);

		exitItem.setMnemonic('x');
		exitItem.setText("Exit");
		fileMenu.add(exitItem);

		menuBar.add(fileMenu);

		toolsMenu.setMnemonic('O');
		toolsMenu.setText("Tools");

		optionsItem.setMnemonic('o');
		optionsItem.setText("Options...");
		toolsMenu.add(optionsItem);

		menuBar.add(toolsMenu);

		helpMenu.setMnemonic('H');
		helpMenu.setText("Help");

		usageItem.setMnemonic('u');
		usageItem.setText("Usage...");
		helpMenu.add(usageItem);

		aboutItem.setMnemonic('a');
		aboutItem.setText("About...");
		helpMenu.add(aboutItem);

		updateItem.setMnemonic('c');
		updateItem.setText("Check for Update");
		helpMenu.add(updateItem);

		menuBar.add(helpMenu);

		setJMenuBar(menuBar);

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(layout.createSequentialGroup()
				.addContainerGap()
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addComponent(buttonsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 485, Short.MAX_VALUE)
					.addComponent(accountsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 485, Short.MAX_VALUE))
				.addContainerGap())
			.addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, 505, Short.MAX_VALUE)
		);
		layout.setVerticalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
				.addContainerGap()
				.addComponent(accountsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 128, Short.MAX_VALUE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addComponent(buttonsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(status))
		);

		pack();
	}// </editor-fold>//GEN-END:initComponents

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JMenuItem aboutItem;
	private javax.swing.JScrollPane accountsPane;
	private javax.swing.JTable accountsTable;
	private javax.swing.JButton addButton;
	private javax.swing.JPanel buttonsPanel;
	private javax.swing.JButton disableButton;
	private javax.swing.JButton editButton;
	private javax.swing.JButton enableButton;
	private javax.swing.JMenuItem exitItem;
	private javax.swing.JMenu fileMenu;
	private javax.swing.JMenu helpMenu;
	private javax.swing.JMenuItem loadItem;
	private javax.swing.JMenuBar menuBar;
	private javax.swing.JMenuItem optionsItem;
	private javax.swing.JButton removeButton;
	private javax.swing.JMenuItem saveItem;
	private javax.swing.JLabel status;
	private javax.swing.JMenu toolsMenu;
	private javax.swing.JMenuItem updateItem;
	private javax.swing.JMenuItem usageItem;
	// End of variables declaration//GEN-END:variables
}
