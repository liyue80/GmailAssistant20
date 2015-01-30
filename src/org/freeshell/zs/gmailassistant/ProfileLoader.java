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

import org.freeshell.zs.common.Encryptor;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.freeshell.zs.common.Debug;
import org.freeshell.zs.common.FileIO;
import org.freeshell.zs.common.SimpleProperties;
import org.freeshell.zs.common.StringManipulator;
import org.freeshell.zs.common.SwingManipulator;
import org.freeshell.zs.common.TerminatingException;


/**
 * "Load Profile" form.
 */
class ProfileLoader
		extends JFrame
{
	/** refresh interval in milliseconds */
	private static final long REFRESH_INTERVAL_MILLISECONDS = 200L;

	/** empty password for decrypting */
	private static final char[] EMPTY_PASSWORD = new char[0];

	/** parent GmailAssistant object */
	private final GmailAssistant parent;

	/** selected profile files to be loaded */
	private final Deque<File> files = new ArrayDeque<File>();

	/** user has responded to the prompt by clicking a button */
	private volatile boolean promptResponded;

	/** profile password to be used for decrypting */
	private volatile char[] promptPassword;

	/** file chooser for selecting profile files */
	private JFileChooser fileChooser = null;


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 */
	ProfileLoader(
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

		setTitle(String.format("Load Profiles - %s", parent.name));

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

		/* field: "Profile Password" */
		passwordField.setText("");

		/* image: "lock" */
		lockImage.setToolTipText(String.format("%s encrypts your profile using AES-128 encryption", parent.name));

		/* button: "OK" */
		okButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				okButton.setEnabled(false);
				cancelButton.setEnabled(false);
				promptPassword = SwingManipulator.getPasswordJPasswordField(passwordField);
				promptResponded = true;
			}
		});

		/* button: "Cancel" */
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				cancelButton.setEnabled(false);
				okButton.setEnabled(false);
				promptPassword = null;
				promptResponded = true;
			}
		});

		/* add standard editing popup menu to text fields */
		SwingManipulator.addStandardEditingPopupMenu(new JTextField[]
		{
			passwordField,
		});

		/* key binding: ENTER key */
		scrollPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER_OK_BUTTON");

		scrollPane.getActionMap().put("ENTER_OK_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				okButton.doClick();
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

		/**********************************
		 * INITIATE PROFILE LOADER THREAD *
		 **********************************/

		new Thread(new Runnable()
		{
			public void run()
			{
				NextProfile:
				while (true)
				{
					final File f;

					synchronized (files)
					{
						f = files.pollFirst();
					}

					if (f == null)
					{
						Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);
						continue NextProfile;
					}

					final int firstByteMarker;
					byte[] salt = null;
					byte[] ciphertextBytes = null;
					final int lastByteMarker;
					char[] password = null;
					byte[] cleartextBytes = null;

					try
					{
						/****************
						 * DISPLAY FORM *
						 ****************/

						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								loadFilename.setText(f.getPath());
								loadError.setText(" ");
								passwordField.setEnabled(false);
								okButton.setEnabled(false);
								cancelButton.setEnabled(false);
								setVisible(true);
								setExtendedState(JFrame.NORMAL);
								toFront();
							}
						});

						/**********************
						 * READ FILE CONTENTS *
						 **********************/

						SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Reading file contents...</font></html>");

						try
						{
							final FileInputStream fis = new FileInputStream(f);
							final int length = (int) f.length();

							/* read first byte marker */
							firstByteMarker = fis.read();

							if (firstByteMarker == -1)
							{
								throw new IOException("Encountered premature EOF.");
							}

							/* read salt */
							final int saltLength = parent.properties.getInt("encryption.salt.length");
							salt = new byte[saltLength];
							FileIO.blockingRead(fis, salt, 0, saltLength);

							/* read ciphertext */
							final int ciphertextLength = length - 2 - saltLength;
							ciphertextBytes = new byte[ciphertextLength];
							FileIO.blockingRead(fis, ciphertextBytes, 0, ciphertextLength);

							/* read first byte marker */
							lastByteMarker = fis.read();

							if (lastByteMarker == -1)
							{
								throw new IOException("Encountered premature EOF.");
							}

							try
							{
								fis.close();
							}
							catch (Exception e)
							{
								/* ignore */
							}
						}
						catch (Exception e)
						{
							throw new TerminatingException(String.format(
									"Failed to read file contents of profile \"%s\" (%s).\nPlease check that the file exists and can be read.",
									f.getPath(), e.toString()));
						}

						/******************
						 * CHECK VALIDITY *
						 ******************/

						SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Checking validity...</font></html>");
						final int withPasswordByteMarker = parent.properties.getInt("profile.with.password.byte.marker");
						final int withoutPasswordByteMarker = parent.properties.getInt("profile.without.password.byte.marker");

						if ((firstByteMarker != lastByteMarker) ||
								((firstByteMarker != withPasswordByteMarker) &&
								(firstByteMarker != withoutPasswordByteMarker)))
						{
							throw new TerminatingException(String.format("Malformed profile \"%s\".", f.getPath()));
						}

						/******************************************
						 * PROMPT USER FOR PASSWORD, IF NECESSARY *
						 ******************************************/

						if (firstByteMarker == withPasswordByteMarker)
						{
							promptResponded = false;

							SwingUtilities.invokeLater(new Runnable()
							{
								public void run()
								{
									passwordField.setEnabled(true);
									okButton.setEnabled(true);
									cancelButton.setEnabled(true);
									setVisible(true);
									setExtendedState(JFrame.NORMAL);
									toFront();
									passwordField.selectAll();
									passwordField.requestFocus();
								}
							});

							/* wait for user to respond */
							SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Waiting for profile password...</font></html>");

							while (!promptResponded)
							{
								Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);
							}

							password = promptPassword;

							if (password == null)
							{
								continue NextProfile;
							}
						}
						else
						{
							password = EMPTY_PASSWORD;
						}

						/*******************
						 * DECRYPT PROFILE *
						 *******************/

						SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Decrypting profile...</font></html>");
						final int iterations = parent.properties.getInt("encryption.salt.iterations");

						try
						{
							cleartextBytes = Encryptor.decrypt(salt, iterations, String.valueOf(password), ciphertextBytes);
						}
						catch (Exception e)
						{
							synchronized (files)
							{
								files.addFirst(f);
							}

							throw new TerminatingException(String.format(
									"Failed to decrypt profile \"%s\".\nPlease check that the provided password is correct.",
									f.getPath()));
						}

						final String charset = parent.properties.getString("profile.charset");
						String[] cleartext = new String(cleartextBytes, charset).split("[\n\r\u0085\u2028\u2028]++");

						/***************************************
						 * LOAD PROGRAM AND ACCOUNT PROPERTIES *
						 ***************************************/

						SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Parsing profile...</font></html>");

						/* convert profile from an older program version, if necessary */
						final String version = parent.properties.getString("version");
						String profileVersion = version; /* assume current version first */

						for (String s : cleartext)
						{
							if (s.startsWith("<") && s.endsWith(">"))
							{
								profileVersion = s.substring(1, s.length() - 1);
							}
						}

						if (!profileVersion.equals(version))
						{
							updateProfile(cleartext, profileVersion);
						}

						/* parse each line in the profile file */
						final List<SimpleProperties> accounts = new ArrayList<SimpleProperties>();
						SimpleProperties current = null;
						final List<String> errors = new ArrayList<String>();

						for (String s : cleartext)
						{
							if (s.isEmpty() || s.startsWith("#"))
							{
								/* ignore empty lines and comments */
							}
							else if (s.startsWith("<") && s.endsWith(">"))
							{
								/* ignore program version */
							}
							else if (s.startsWith("[") && s.endsWith("]"))
							{
								/* start recording properties for a new account */
								final String username = s.substring(1, s.length() - 1);
								current = new SimpleProperties(parent.defaultAccountProperties);
								current.setString("username", username);
								accounts.add(current);
							}
							else if (s.contains(":"))
							{
								/* process "key:value" pair */
								final String[] kv = StringManipulator.parseKeyValueString(s);

								if (current == null)
								{
									/* program property */
									if (parent.savedProgramProperties.get(kv[0]) == null)
									{
										if (parent.properties.get(kv[0]) == null)
										{
											errors.add(String.format("\"%s\" is not a valid program property.", kv[0]));
										}
										else
										{
											errors.add(String.format("\"%s\" is not a valid saved program property.", kv[0]));
										}
									}
									else
									{
										parent.properties.set(kv[0], kv[1]);
									}
								}
								else
								{
									/* account property */
									if ("password".equals(kv[0]))
									{
										current.set(kv[0], kv[1]);
									}
									else
									{
										if (parent.savedAccountProperties.get(kv[0]) == null)
										{
											if (current.get(kv[0]) == null)
											{
												errors.add(String.format("\"%s\" is not a valid account property.", kv[0]));
											}
											else
											{
												errors.add(String.format("\"%s\" is not a valid saved account property.", kv[0]));
											}
										}
										else
										{
											current.set(kv[0], kv[1]);
										}
									}
								}
							}
						}

						SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Profile loaded</font></html>");

						/* report any errors */
						if (!errors.isEmpty())
						{
							SwingManipulator.updateLabel(loadError, "<html><font color='blue'>Profile loaded (errors encountered)</font></html>");

							final StringBuilder sb = new StringBuilder();

							for (String s : errors)
							{
								sb.append("\n\n");
								sb.append(s);
							}

							SwingManipulator.showWarningDialog(
									ProfileLoader.this,
									String.format("Load Profiles - %s", parent.name),
									String.format("The following %s were encountered when loading profile \"%s\" :%s",
									(errors.size() == 1) ? "error" : "errors", f.getPath(), sb.toString()));
						}

						/* add new accounts loaded from the file */
						for (SimpleProperties p : accounts)
						{
							parent.addNewAccount(p);
						}

						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								/* repopulate "Options" form */
								if (parent.options != null)
								{
									parent.options.repopulateForm();
								}

								/* refresh program modes */
								parent.refreshAlwaysOnTopMode();
								parent.refreshProxyMode();
							}
						});
					}
					catch (TerminatingException e)
					{
						SwingManipulator.showErrorDialog(
								ProfileLoader.this,
								String.format("Load Profiles - %s", parent.name),
								String.format("%s\nNote that profiles created by %s 1.1 or earlier are not compatible with this version of the program.",
								e.getMessage(), parent.name));
					}
					catch (Exception e)
					{
						SwingManipulator.showErrorDialog(
								ProfileLoader.this,
								String.format("Load Profiles - %s", parent.name),
								String.format("Failed to load profile \"%s\" because of an unexpected error:\n%s" +
								"\nNote that profiles created by %s 1.1 or earlier are not compatible with this version of the program." +
								"\nPlease file a bug report to help improve %s.\n\n%s\n\n%s",
								f.getPath(), e.toString(), parent.name, parent.name, Debug.getSystemInformationString(), Debug.getStackTraceString(e)));
					}
					finally
					{
						if (password != null)
						{
							Arrays.fill(password, '\0');
						}

						for (byte[] b : new byte[][] {salt, ciphertextBytes, cleartextBytes})
						{
							if (b != null)
							{
								Arrays.fill(b, (byte) 0x00);
							}
						}

						SwingManipulator.setVisibleWindow(ProfileLoader.this, false);
					}
				}
			}
		}).start();
	}


	/**
	 * Present a file chooser for the user to select the profile files for loading.
	 * This method must run on the EDT.
	 */
	void showForm()
	{
		if (fileChooser == null)
		{
			try
			{
				fileChooser = new JFileChooser(new File(".").getCanonicalFile());
			}
			catch (Exception e)
			{
				fileChooser = new JFileChooser();
			}

			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fileChooser.setMultiSelectionEnabled(true);

			final String ext = parent.properties.getString("profile.default.extension");

			fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
					String.format("%s Profile (*.%s)", parent.name, ext),
					ext));
		}

		final int val = fileChooser.showOpenDialog(parent);

		if (val == JFileChooser.APPROVE_OPTION)
		{
			synchronized (files)
			{
				for (File f : fileChooser.getSelectedFiles())
				{
					files.offerLast(f);
				}
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
	void loadProfiles(
			final List<File> fs)
	{
		synchronized (files)
		{
			for (File f : fs)
			{
				files.offerLast(f);
			}
		}
	}


	/**
	 * Update the specified profile data to the current program version.
	 *
	 * @param profile
	 *      profile data to be updated
	 * @param version
	 *      program version associated with the given profile data
	 */
	private void updateProfile(
			final String[] profile,
			final String version)
	{
		/* nothing to do */
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

        buttonsPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        loadTitle = new javax.swing.JLabel();
        loadFilename = new javax.swing.JLabel();
        scrollPane = new javax.swing.JScrollPane();
        panel = new javax.swing.JPanel();
        loadPanel = new javax.swing.JPanel();
        passwordLabel = new javax.swing.JLabel();
        passwordField = new javax.swing.JPasswordField();
        lockImage = new javax.swing.JLabel();
        loadError = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        buttonsPanel.setLayout(new java.awt.GridLayout(1, 2));

        okButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/tick.png"))); // NOI18N
        okButton.setMnemonic('O');
        okButton.setText("OK");
        okButton.setToolTipText("Load profile from the specified file");
        buttonsPanel.add(okButton);

        cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/cross.png"))); // NOI18N
        cancelButton.setMnemonic('C');
        cancelButton.setText("Cancel");
        cancelButton.setToolTipText("Cancel loading of profile");
        buttonsPanel.add(cancelButton);

        loadTitle.setText("Loading profile");
        loadTitle.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        loadFilename.setFont(new java.awt.Font("Tahoma", 1, 11));
        loadFilename.setText("C:\\Path\\To\\File");
        loadFilename.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        scrollPane.setBorder(null);

        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        loadPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        passwordLabel.setDisplayedMnemonic('p');
        passwordLabel.setLabelFor(passwordField);
        passwordLabel.setText("Profile password:");
        passwordLabel.setToolTipText("Password to be used for decrypting the profile");

        passwordField.setToolTipText("Password to be used for decrypting the profile");

        lockImage.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lockImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/lock.png"))); // NOI18N

        loadError.setText("<html><font color='red'>load error</font></html>");
        loadError.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout loadPanelLayout = new javax.swing.GroupLayout(loadPanel);
        loadPanel.setLayout(loadPanelLayout);
        loadPanelLayout.setHorizontalGroup(
            loadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, loadPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(loadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(loadError, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 267, Short.MAX_VALUE)
                    .addGroup(loadPanelLayout.createSequentialGroup()
                        .addComponent(passwordLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(passwordField, javax.swing.GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lockImage)))
                .addContainerGap())
        );
        loadPanelLayout.setVerticalGroup(
            loadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(loadPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(loadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(loadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(passwordLabel)
                        .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lockImage))
                .addGap(18, 18, 18)
                .addComponent(loadError)
                .addContainerGap(13, Short.MAX_VALUE))
        );

        panel.add(loadPanel);

        scrollPane.setViewportView(panel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 299, Short.MAX_VALUE)
                    .addComponent(buttonsPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 299, Short.MAX_VALUE)
                    .addComponent(loadTitle, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 299, Short.MAX_VALUE)
                    .addComponent(loadFilename, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 299, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(loadTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(loadFilename)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(buttonsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel buttonsPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel loadError;
    private javax.swing.JLabel loadFilename;
    private javax.swing.JPanel loadPanel;
    private javax.swing.JLabel loadTitle;
    private javax.swing.JLabel lockImage;
    private javax.swing.JButton okButton;
    private javax.swing.JPanel panel;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JScrollPane scrollPane;
    // End of variables declaration//GEN-END:variables
}
