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

import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import org.freeshell.zs.common.ResourceManipulator;
import org.freeshell.zs.common.SwingManipulator;


/**
 * Represent an "About" form.
 */
class About
		extends JFrame
{
	/** parent GmailAssistant object */
	private final GmailAssistant parent;


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 */
	About(
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

		setTitle(String.format("About - %s", parent.name));

		/* "About" text */
		final StringBuilder header = new StringBuilder();
		header.append(parent.name);
		header.append(' ');
		header.append(parent.properties.getString("version"));
		header.append(" (");
		header.append(parent.properties.getString("date"));
		header.append(")\n");
		header.append(parent.properties.getString("copyright"));
		header.append('\n');
		header.append(parent.properties.getString("email"));
		header.append('\n');
		header.append(parent.properties.getString("homepage"));
		header.append("\n\n");
		aboutText.setText(header.toString());

		try
		{
			aboutText.append(ResourceManipulator.resourceAsString(parent.properties.getString("about")));
		}
		catch (Exception e)
		{
			SwingManipulator.showErrorDialog(
					parent,
					parent.name,
					String.format("(INTERNAL) Failed to load \"About\" text (%s).", e.toString()));
		}

		aboutText.setToolTipText(String.format("About %s", parent.name));
		aboutText.setCaretPosition(0);
		aboutText.setFont(new Font(
				Font.DIALOG,
				Font.PLAIN,
				aboutText.getFont().getSize() - 2));

		SwingManipulator.addStandardEditingPopupMenu(new JTextArea[] {aboutText});

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				closeForm();
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

		/* button: "Visit Homepage" */
		final String homepage = parent.properties.getString("homepage");
		homeButton.setToolTipText(String.format("Visit the %s homepage at %s", parent.name, homepage));
		homeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (Desktop.isDesktopSupported())
				{
					try
					{
						Desktop.getDesktop().browse(new URI(homepage));
					}
					catch (Exception ex)
					{
						/* ignore */
					}
				}
			}
		});

		/* button: "Visit Forum" */
		final String forum = parent.properties.getString("forum");
		forumButton.setToolTipText(String.format("Visit the feedback forum at %s", forum));
		forumButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (Desktop.isDesktopSupported())
				{
					try
					{
						Desktop.getDesktop().browse(new URI(forum));
					}
					catch (Exception ex)
					{
						/* ignore */
					}
				}
			}
		});

		/* button: "Email Developer" */
		final String email = parent.properties.getString("email");
		emailButton.setToolTipText(String.format("Email the developer at %s", email));
		emailButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (Desktop.isDesktopSupported())
				{
					try
					{
						Desktop.getDesktop().mail(new URI(
								"mailto",
								String.format("%s?subject=%s: <insert subject here>", email, parent.name),
								null));
					}
					catch (Exception ex)
					{
						/* ignore */
					}
				}
			}
		});

		/* button: "Close" */
		closeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				closeForm();
			}
		});

		/* key binding: ESCAPE key */
		aboutPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_CANCEL_BUTTON");

		aboutPane.getActionMap().put("ESCAPE_CANCEL_BUTTON", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				closeButton.doClick();
			}
		});

		/* center form on the parent form */
		setLocationRelativeTo(parent);
	}


	/**
	 * Present the "About" form.
	 */
	void showForm()
	{
		setVisible(true);
		setExtendedState(JFrame.NORMAL);
		toFront();
	}


	/**
	 * Close the "About" form.
	 */
	private void closeForm()
	{
		setVisible(false);
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

		title = new javax.swing.JLabel();
		aboutPane = new javax.swing.JScrollPane();
		aboutText = new javax.swing.JTextArea();
		closeButton = new javax.swing.JButton();
		buttonsPanel = new javax.swing.JPanel();
		homeButton = new javax.swing.JButton();
		forumButton = new javax.swing.JButton();
		emailButton = new javax.swing.JButton();

		setResizable(false);

		title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		title.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/splashscreen.png"))); // NOI18N

		aboutText.setColumns(20);
		aboutText.setEditable(false);
		aboutText.setFont(aboutText.getFont());
		aboutText.setLineWrap(true);
		aboutText.setRows(5);
		aboutText.setTabSize(4);
		aboutText.setWrapStyleWord(true);
		aboutPane.setViewportView(aboutText);

		closeButton.setMnemonic('C');
		closeButton.setText("Close");
		closeButton.setNextFocusableComponent(closeButton);

		buttonsPanel.setLayout(new java.awt.GridLayout(1, 0));

		homeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/house.png"))); // NOI18N
		homeButton.setMnemonic('h');
		homeButton.setText("<html>Visit<br /><u>H</u>omepage</html>");
		homeButton.setIconTextGap(8);
		homeButton.setNextFocusableComponent(homeButton);
		buttonsPanel.add(homeButton);

		forumButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/comments.png"))); // NOI18N
		forumButton.setMnemonic('f');
		forumButton.setText("<html>Visit<br /><u>F</u>orum</html>");
		forumButton.setIconTextGap(8);
		forumButton.setNextFocusableComponent(forumButton);
		buttonsPanel.add(forumButton);

		emailButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/email_edit.png"))); // NOI18N
		emailButton.setMnemonic('e');
		emailButton.setText("<html><u>E</u>mail<br />Developer</html>");
		emailButton.setIconTextGap(8);
		emailButton.setNextFocusableComponent(emailButton);
		buttonsPanel.add(emailButton);

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
				.addContainerGap()
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
					.addComponent(closeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(buttonsPanel, javax.swing.GroupLayout.Alignment.CENTER, javax.swing.GroupLayout.DEFAULT_SIZE, 374, Short.MAX_VALUE)
					.addComponent(aboutPane, javax.swing.GroupLayout.Alignment.CENTER, javax.swing.GroupLayout.DEFAULT_SIZE, 374, Short.MAX_VALUE)
					.addComponent(title, javax.swing.GroupLayout.Alignment.CENTER, javax.swing.GroupLayout.DEFAULT_SIZE, 374, Short.MAX_VALUE))
				.addContainerGap())
		);
		layout.setVerticalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(layout.createSequentialGroup()
				.addContainerGap()
				.addComponent(title)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addComponent(aboutPane, javax.swing.GroupLayout.DEFAULT_SIZE, 264, Short.MAX_VALUE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addComponent(buttonsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addComponent(closeButton)
				.addContainerGap())
		);

		pack();
	}// </editor-fold>//GEN-END:initComponents

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JScrollPane aboutPane;
	private javax.swing.JTextArea aboutText;
	private javax.swing.JPanel buttonsPanel;
	private javax.swing.JButton closeButton;
	private javax.swing.JButton emailButton;
	private javax.swing.JButton forumButton;
	private javax.swing.JButton homeButton;
	private javax.swing.JLabel title;
	// End of variables declaration//GEN-END:variables
}
