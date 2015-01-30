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
import org.freeshell.zs.common.Encryptor;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import org.freeshell.zs.common.Debug;
import org.freeshell.zs.common.SwingManipulator;
import org.freeshell.zs.common.TerminatingException;


/**
 * "Save Profile" form.
 */
class ProfileSaver
		extends JFrame
{
	/** parent GmailAssistant object */
	private final GmailAssistant parent;

	/** are the "Options" selection valid? */
	private boolean optionsValid = false;

	/** is the "Filename" selection valid? */
	private	boolean filenameValid = false;

	/** file chooser for selecting profile file */
	private JFileChooser fileChooser = null;


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 */
	ProfileSaver(
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

		setTitle(String.format("Save Profile - %s", parent.name));

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

		/* image: "lock" */
		lockImage.setToolTipText(String.format("%s encrypts your profile using AES-128 encryption", parent.name));

		/* checkbox: "Save account passwords" */
		savePasswordsBox.setSelected(parent.properties.getBoolean("default.save.account.passwords"));

		/* monitor editing of fields: "Profile password", "Filename" */
		for (final JTextComponent c : new JTextComponent[] {passwordField, filenameField})
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

		/* button: "Browse..." */
		browseButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				browseButton.setEnabled(false);
				final String ext = parent.properties.getString("profile.default.extension");

				if (fileChooser == null)
				{
					try
					{
						fileChooser = new JFileChooser(new File(".").getCanonicalFile());
					}
					catch (Exception ex)
					{
						fileChooser = new JFileChooser();
					}

					fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					fileChooser.setMultiSelectionEnabled(false);

					fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
							String.format("%s Profile (*.%s)", parent.name, ext),
							ext));
				}

				final int val = fileChooser.showSaveDialog(ProfileSaver.this);

				if (val == JFileChooser.APPROVE_OPTION)
				{
					File f = fileChooser.getSelectedFile();

					if (!f.getName().contains("."))
					{
						f = new File(f.getParentFile(), String.format("%s.%s", f.getName(), ext));
					}

					filenameField.setText(f.getPath());
				}

				browseButton.setEnabled(true);
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

				final char[] password = SwingManipulator.getPasswordJPasswordField(passwordField);
				final boolean saveAccountPasswords = savePasswordsBox.isSelected();
				final File profileFile = new File(SwingManipulator.getTextJTextField(filenameField));

				new Thread(new Runnable()
				{
					public void run()
					{
						saveProfile(password, saveAccountPasswords, profileFile);
					}
				}).start();
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
				setVisible(false);
			}
		});

		/* add standard editing popup menu to text fields */
		SwingManipulator.addStandardEditingPopupMenu(new JTextField[]
		{
			passwordField,
			filenameField
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

		/* check form for errors */
		checkForm(null);

		/* center form on the parent form */
		setLocationRelativeTo(parent);

		/****************************
		 * GENERATE UNUSED FILENAME *
		 ****************************/

		File dir;

		try
		{
			dir = new File(".").getCanonicalFile();
		}
		catch (Exception e)
		{
			dir = new File(".");
		}

		final String filename = parent.properties.getString("profile.default.filename");
		final String ext = parent.properties.getString("profile.default.extension");
		File f = null;

		for (int i = 0; i < Integer.MAX_VALUE; i++)
		{
			if (i == 0)
			{
				f = new File(dir, String.format("%s.%s", filename, ext));
			}
			else
			{
				f = new File(dir, String.format("%s.%d.%s", filename, i, ext));
			}

			if (!f.exists())
			{
				break;
			}
		}

		if (f.exists())
		{
			f = new File(dir, String.format("%s.%s", filename, ext));
		}

		filenameField.setText(f.getPath());
	}


	/**
	 * Check user selections on the form for errors.
	 * This method must run on the EDT.
	 *
	 * @param c
	 *     component that has triggered the check
	 */
	private void checkForm(
			final Component c)
	{
		boolean checkOptions = false;
		boolean checkFilename = false;

		if (c == passwordField)
		{
			checkOptions = true;
		}
		else if (c == filenameField)
		{
			checkFilename = true;
		}
		else if (c == null)
		{
			checkOptions = true;
			checkFilename = true;
		}

		if (checkOptions)
		{
			String warning = null;

			final char[] password = SwingManipulator.getPasswordJPasswordField(passwordField);
			final int passwordLength = password.length;
			Arrays.fill(password, '\0');

			final int min = parent.properties.getInt("recommended.minimum.password.length");

			if (passwordLength == 0)
			{
				warning = "A password is strongly recommended";
			}
			else if (passwordLength < min)
			{
				warning = String.format("A password of at least %d characters is recommended", min);
			}

			if (warning == null)
			{
				optionsValid = true;
				optionsError.setText(" ");
			}
			else
			{
				optionsValid = true;
				optionsError.setText(String.format("<html><font color='red'>%s</font></html>", warning));
			}
		}

		if (checkFilename)
		{
			String error = null;

			if (SwingManipulator.getTextJTextField(filenameField).trim().isEmpty())
			{
				error = "A filename must be specified";
			}

			if (error == null)
			{
				filenameValid = true;
				filenameError.setText(" ");
			}
			else
			{
				filenameValid = false;
				filenameError.setText(String.format("<html><font color='red'>%s</font></html>", error));
				okButton.setEnabled(false);
				filenameField.selectAll();
				filenameField.requestFocus();
			}
		}

		if (optionsValid && filenameValid)
		{
			/* all selections are valid */
			okButton.setEnabled(true);
		}
	}


	/**
	 * Present the form for saving a profile.
	 * This method must run on the EDT.
	 */
	void showForm()
	{
		okButton.setEnabled(true);
		cancelButton.setEnabled(true);
		browseButton.setEnabled(true);
		checkForm(null);
		setVisible(true);
		setExtendedState(JFrame.NORMAL);
		toFront();
		passwordField.selectAll();
		passwordField.requestFocus();
	}


	/**
	 * Save profile using the user-specified settings on the form.
	 * This method should run on a dedicated worker thread, not the EDT.
	 *
	 * @param password
	 *     password for encrypting profile
	 * @param saveAccountPasswords
	 *     should account passwords be saved in the profile?
	 */
	private void saveProfile(
			final char[] password,
			final boolean saveAccountPasswords,
			final File profileFile)
	{
		byte[] salt = null;
		final List<byte[]> cleartext = new ArrayList<byte[]>();
		byte[] cleartextBytes = null;
		byte[] ciphertextBytes = null;

		try
		{
			/**************************
			 * CHECK VALIDITY OF FILE *
			 **************************/

			SwingManipulator.updateLabel(filenameError, "<html><font color='blue'>Checking file...</font></html>");

			if (profileFile.isDirectory())
			{
				throw new TerminatingException(String.format(
						"Failed to save profile to \"%s\" because it is a directory and not a file.",
						profileFile.getPath()));
			}

			if (profileFile.exists())
			{
				/* file already exists; prompt on overwriting */
				final int choice = JOptionPane.showConfirmDialog(
						this,
						String.format("File \"%s\" already exists. Overwrite this file?", profileFile.getPath()),
						String.format("Save Profile - %s", parent.name),
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);

				if (choice != JOptionPane.YES_OPTION)
				{
					return;
				}
			}

			/***************************
			 * SAVE PROGRAM PROPERTIES *
			 ***************************/

			SwingManipulator.updateLabel(filenameError, "<html><font color='blue'>Preparing profile...</font></html>");

			final String charset = parent.properties.getString("profile.charset");
			final byte[] newlineBytes = "\n".getBytes(charset);
			final byte[] colonBytes = ":".getBytes(charset);
			final byte[] leftAngleBytes = "<".getBytes(charset);
			final byte[] rightAngleBytes = ">".getBytes(charset);
			final byte[] leftSquareBytes = "[".getBytes(charset);
			final byte[] rightSquareBytes = "]".getBytes(charset);

			/* "<version>" */
			cleartext.add(leftAngleBytes);
			cleartext.add(parent.properties.getString("version").getBytes(charset));
			cleartext.add(rightAngleBytes);
			cleartext.add(newlineBytes);

			for (String k : parent.savedProgramProperties.keySet())
			{
				/* write "key:value" pair */
				cleartext.add(k.getBytes(charset));
				cleartext.add(colonBytes);
				cleartext.add(parent.properties.getAsString(k).getBytes(charset));
				cleartext.add(newlineBytes);
			}

			/***************************
			 * SAVE ACCOUNT PROPERTIES *
			 ***************************/

			parent.saveAccountProperties(
					cleartext,
					saveAccountPasswords,
					charset,
					newlineBytes,
					colonBytes,
					leftAngleBytes,
					rightAngleBytes,
					leftSquareBytes,
					rightSquareBytes);

			/* combine list of byte arrays to a single byte array */
			int len = 0;

			for (byte[] b : cleartext)
			{
				len += b.length;
			}

			cleartextBytes = new byte[len];
			int pos = 0;

			for (byte[] b : cleartext)
			{
				System.arraycopy(b, 0, cleartextBytes, pos, b.length);
				pos += b.length;
			}

			/*******************
			 * ENCRYPT PROFILE *
			 *******************/

			SwingManipulator.updateLabel(filenameError, "<html><font color='blue'>Encrypting profile...</font></html>");

			salt = new byte[parent.properties.getInt("encryption.salt.length")];
			final int iterations = parent.properties.getInt("encryption.salt.iterations");
			final int withPasswordByteMarker = parent.properties.getInt("profile.with.password.byte.marker");
			final int withoutPasswordByteMarker = parent.properties.getInt("profile.without.password.byte.marker");

			ciphertextBytes = Encryptor.encrypt(salt, iterations, String.valueOf(password), cleartextBytes);

			/*********************
			 * WRITE CIPHER TEXT *
			 *********************/

			SwingManipulator.updateLabel(filenameError, "<html><font color='blue'>Writing profile...</font></html>");

			try
			{
				final FileOutputStream fos = new FileOutputStream(profileFile);
				fos.write((password.length == 0) ? withoutPasswordByteMarker : withPasswordByteMarker);
				fos.write(salt);
				fos.write(ciphertextBytes);
				fos.write((password.length == 0) ? withoutPasswordByteMarker : withPasswordByteMarker);
				fos.flush();
				fos.close();
			}
			catch (Exception e)
			{
				throw new TerminatingException(String.format(
						"Failed to write profile to file \"%s\":\n%s" +
						"\nPlease check that the file can be created and written to.",
						profileFile.getPath(), e.toString()));
			}

			SwingManipulator.updateLabel(filenameError, "<html><font color='blue'>Profile saved</font></html>");
			SwingManipulator.setVisibleWindow(this, false);
		}
		catch (TerminatingException e)
		{
			SwingManipulator.showErrorDialog(
					this,
					String.format("Save Profile - %s", parent.name),
					e.getMessage());
		}
		catch (Exception e)
		{
			SwingManipulator.showErrorDialog(
					this,
					String.format("Save Profile - %s", parent.name),
					String.format("Failed to save profile \"%s\" because of an unexpected error:\n%s" +
					"\nPlease file a bug report to help improve %s.\n\n%s\n\n%s",
					profileFile.getPath(), e.toString(), parent.name, Debug.getSystemInformationString(), Debug.getStackTraceString(e)));
		}
		finally
		{
			Arrays.fill(password, '\0');

			for (byte[] b : new byte[][] {salt, cleartextBytes, ciphertextBytes})
			{
				if (b != null)
				{
					Arrays.fill(b, (byte) 0x00);
				}
			}

			for (byte[] b : cleartext)
			{
				Arrays.fill(b, (byte) 0x00);
			}

			SwingManipulator.updateLabel(filenameError, " ");
			SwingManipulator.setEnabledButton(okButton, true);
			SwingManipulator.setEnabledButton(cancelButton, true);
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

        scrollPane = new javax.swing.JScrollPane();
        panel = new javax.swing.JPanel();
        optionsPanel = new javax.swing.JPanel();
        lockImage = new javax.swing.JLabel();
        passwordLabel = new javax.swing.JLabel();
        savePasswordsBox = new javax.swing.JCheckBox();
        passwordField = new javax.swing.JPasswordField();
        optionsError = new javax.swing.JLabel();
        filenamePanel = new javax.swing.JPanel();
        browseButton = new javax.swing.JButton();
        filenameField = new javax.swing.JTextField();
        filenameError = new javax.swing.JLabel();
        buttonsPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        scrollPane.setBorder(null);

        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        optionsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Options"));

        lockImage.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lockImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/lock.png"))); // NOI18N

        passwordLabel.setDisplayedMnemonic('p');
        passwordLabel.setLabelFor(passwordField);
        passwordLabel.setText("Profile password:");
        passwordLabel.setToolTipText("Password to be used for encrypting the profile");

        savePasswordsBox.setMnemonic('s');
        savePasswordsBox.setText("Save account passwords in profile");
        savePasswordsBox.setToolTipText("Save Gmail account passwords in the profile so they do not need to be entered again when the profile is loaded");

        passwordField.setToolTipText("Password to be used for encrypting the profile");

        optionsError.setText("<html><font color='red'>options error</font></html>");
        optionsError.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout optionsPanelLayout = new javax.swing.GroupLayout(optionsPanel);
        optionsPanel.setLayout(optionsPanelLayout);
        optionsPanelLayout.setHorizontalGroup(
            optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(optionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(optionsError, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE)
                    .addComponent(savePasswordsBox, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE)
                    .addGroup(optionsPanelLayout.createSequentialGroup()
                        .addComponent(passwordLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(passwordField, javax.swing.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lockImage)))
                .addContainerGap())
        );
        optionsPanelLayout.setVerticalGroup(
            optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(optionsPanelLayout.createSequentialGroup()
                .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(passwordLabel)
                        .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lockImage))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(savePasswordsBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(optionsError)
                .addContainerGap(13, Short.MAX_VALUE))
        );

        panel.add(optionsPanel);

        filenamePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Filename"));

        browseButton.setMnemonic('b');
        browseButton.setText("Browse...");
        browseButton.setToolTipText("Choose a filename for the profile");

        filenameField.setText("C:\\Path\\To\\File");
        filenameField.setToolTipText("Filename for the profile");

        filenameError.setText("<html><font color='red'>filename error</font></html>");
        filenameError.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout filenamePanelLayout = new javax.swing.GroupLayout(filenamePanel);
        filenamePanel.setLayout(filenamePanelLayout);
        filenamePanelLayout.setHorizontalGroup(
            filenamePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, filenamePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(filenamePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(filenameField, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE)
                    .addGroup(filenamePanelLayout.createSequentialGroup()
                        .addComponent(filenameError, javax.swing.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton)))
                .addContainerGap())
        );
        filenamePanelLayout.setVerticalGroup(
            filenamePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(filenamePanelLayout.createSequentialGroup()
                .addComponent(filenameField, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(filenamePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseButton)
                    .addComponent(filenameError))
                .addContainerGap(14, Short.MAX_VALUE))
        );

        panel.add(filenamePanel);

        scrollPane.setViewportView(panel);

        buttonsPanel.setLayout(new java.awt.GridLayout(1, 2));

        okButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/tick.png"))); // NOI18N
        okButton.setMnemonic('O');
        okButton.setText("OK");
        okButton.setToolTipText("Save profile to the specified file");
        buttonsPanel.add(okButton);

        cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/cross.png"))); // NOI18N
        cancelButton.setMnemonic('C');
        cancelButton.setText("Cancel");
        cancelButton.setToolTipText("Cancel saving of profile");
        buttonsPanel.add(cancelButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonsPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 303, Short.MAX_VALUE)
                    .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 303, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 205, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JPanel buttonsPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel filenameError;
    private javax.swing.JTextField filenameField;
    private javax.swing.JPanel filenamePanel;
    private javax.swing.JLabel lockImage;
    private javax.swing.JButton okButton;
    private javax.swing.JLabel optionsError;
    private javax.swing.JPanel optionsPanel;
    private javax.swing.JPanel panel;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JCheckBox savePasswordsBox;
    private javax.swing.JScrollPane scrollPane;
    // End of variables declaration//GEN-END:variables
}
