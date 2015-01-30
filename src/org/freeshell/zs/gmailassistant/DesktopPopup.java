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

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import org.freeshell.zs.common.Debug;
import org.freeshell.zs.common.Debug.ValueCapsule;
import org.freeshell.zs.common.SlidingAnimator;
import org.freeshell.zs.common.SwingManipulator;


/**
 * Display popup messages on the desktop.
 */
class DesktopPopup
		extends JPanel
{
	/** refresh interval in milliseconds */
	private static final long REFRESH_INTERVAL_MILLISECONDS = 200L;

	/** popup duration for first popup message (4 seconds) */
	private static final long FIRST_POPUP_DURATION_MILLISECONDS = 4000L;

	/** popup duration for subsequent popup messages (2 seconds) */
	private static final long POPUP_DURATION_MILLISECONDS = 2000L;

	/** additional popup duration for the last popup message (1 second) */
	private static final long LAST_POPUP_DURATION_MILLISECONDS = 1000L;

	/** border width of the popup */
	private static final int POPUP_BORDER_WIDTH = 4;

	/** popup test message string */
	private static final String TEST_STRING =
			"<html><div style='white-space:nowrap'><b>1 of 1</b> unread mail for <b>testaccount</b><br />" +
			"Sender name here (1 second ago)<br />" +
			"<b>Subject line here</b><br />" +
			"<i>This is a popup test message...</i></div></html>";

	/** popup message string for the case of no unread mails */
	private static final String NO_UNREAD_MAILS_STRING =
			"<html><br />There are no unread mails at this time.</html>";

	/** parent GmailAssistant object */
	private final GmailAssistant parent;

	/** default border color */
	private final Color defaultBorderColor;

	/** tooltip text for the popup */
	private static final String POPUP_TOOLTIP =
			"<html>Click on the icon to advance to the next message.<br />Click on the text to dismiss the popup.</html>";

	/** window that will contain the popup */
	private final JWindow window = new JWindow();

	/** queued actions to be performed by the timer */
	private final Deque<ActionType> actions = new ArrayDeque<ActionType>();

	/** last mail sequence number for each account */
	private final Map<Account,Integer> lastMailSequenceNumbers = new HashMap<Account,Integer>();


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 */
	DesktopPopup(
			final GmailAssistant parent)
	{
		this.parent = parent;
		defaultBorderColor = new Color(parent.properties.getInt("color"));

		/*******************************
		 * INITIALIZE PANEL COMPONENTS *
		 *******************************/

		initComponents();

		/******************************
		 * CONFIGURE PANEL COMPONENTS *
		 ******************************/

		setToolTipText(POPUP_TOOLTIP);

		icon.setToolTipText(POPUP_TOOLTIP);
		icon.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				advanceMessage();
			}
		});

		text.setToolTipText(POPUP_TOOLTIP);
		text.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				dismissPopup();
			}
		});

		window.getContentPane().add(this);
		window.setAlwaysOnTop(true);
		window.pack();
		window.setFocusable(false);
		window.setVisible(false);

		/*********************************************
		 * INITIALIZE THREAD TO MONITOR POPUP EVENTS *
		 *********************************************/

		new Thread(new Runnable()
		{
			public void run()
			{
				NextPopupEventIteration:
				while (true)
				{
					Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);

					ActionType action;
					ActionType interrupt;

					synchronized (actions)
					{
						action = actions.pollFirst();
					}

					if ((action == ActionType.ALL) ||
							(action == ActionType.RECENT))
					{
						Mail m = null;
						Mail prev = null;
						boolean shownFirstMail = false;
						boolean manualAdvance = false;
						int[] coordinates = null;
						final StringBuilder message = new StringBuilder();
						int k = 0;

						ShowNextMailIteration:
						while (true)
						{
							/* advance to the next mail without looping back */
							m = advanceToNextMail(m, false);

							if (m == null)
							{
								/* no more mails without looping back */
								if (shownFirstMail)
								{
									if (manualAdvance)
									{
										/* advance to the next mail with loop back */
										m = advanceToNextMail(m, true);

										if (m == null)
										{
											/* no more mails even with loop back */
											break ShowNextMailIteration;
										}
										else
										{
											/* start showing all unread messages; not just recent ones */
											action = ActionType.ALL;
										}
									}
									else
									{
										/* wait for user to advance or dismiss popup, if necessary */
										if (parent.properties.getBoolean("alert.popup.persistent.messages"))
										{
											while (true)
											{
												interrupt = sleepTillInterrupted(POPUP_DURATION_MILLISECONDS);

												/* check for interruption */
												if (interrupt == ActionType.ADVANCE)
												{
													manualAdvance = true;
													continue ShowNextMailIteration;
												}
												else if (interrupt == ActionType.CANCEL)
												{
													SwingManipulator.setVisibleWindow(window, false);
													continue NextPopupEventIteration;
												}
											}
										}
										else
										{
											break ShowNextMailIteration;
										}
									}
								}
								else
								{
									/* have not shown a single mail until now */
									if (action == ActionType.ALL)
									{
										showSingleMessage(NO_UNREAD_MAILS_STRING, defaultBorderColor);
									}

									continue NextPopupEventIteration;
								}
							}

							/* determine index of the next mail within its account */
							if ((prev != null) &&
									prev.account.equals(m.account) &&
									(m.compareTo(prev) > 0))
							{
								k++;
							}
							else
							{
								k = 1;
							}

							prev = m;

							/* should this mail be shown? */
							boolean showMail = true;

							/* check if only recent mails should be shown */
							if (action == ActionType.RECENT)
							{
								final Integer last;

								synchronized (lastMailSequenceNumbers)
								{
									last = lastMailSequenceNumbers.get(m.account);
								}

								if ((last != null) && (m.sequenceNumber <= last))
								{
									showMail = false;
								}
							}

							if (showMail)
							{
								/* populate popup message */
								final long duration = System.currentTimeMillis() - m.date.getTime();
								int n = m.account.getTotalNumMails();
								n = Math.max(n, k);

								message.setLength(0);
								message.append("<html><div style='white-space:nowrap'><b>");
								message.append(k);
								message.append(" of ");
								message.append(n);
								message.append("</b> unread ");
								message.append((n == 1) ? "mail" : "mails");
								message.append(" for <b>");
								message.append(m.account.properties.getString("username"));
								message.append("</b><br/>");
								message.append(m.from);
								message.append(" (");
								message.append(GmailAssistant.timeDurationString(duration));
								message.append((duration >= 0) ? " ago" : " in the future");
								message.append(")<br /><b>");
								message.append(m.subject);
								message.append("</b><br /><i>");
								message.append(m.snippet);
								message.append("</i></div></html>");

								coordinates = populatePopupMessage(
										message.toString(),
										(Color) m.account.properties.get("color.object"));

								/* animate popup entry if this is the first mail shown */
								if (!shownFirstMail)
								{
									SlidingAnimator.animate(
											window,
											coordinates[0],
											coordinates[1],
											SlidingAnimator.Direction.UP_IN,
											10);

									shownFirstMail = true;
									interrupt = sleepTillInterrupted(FIRST_POPUP_DURATION_MILLISECONDS);
								}
								else
								{
									interrupt = sleepTillInterrupted(POPUP_DURATION_MILLISECONDS);
								}

								/* update last mail sequence number for this account */
								synchronized (lastMailSequenceNumbers)
								{
									final Integer last = lastMailSequenceNumbers.get(m.account);

									if ((last == null) || (m.sequenceNumber > last))
									{
										lastMailSequenceNumbers.put(m.account, m.sequenceNumber);
									}
								}

								/* check for interruption */
								if (interrupt == ActionType.ADVANCE)
								{
									manualAdvance = true;
									continue ShowNextMailIteration;
								}
								else if (interrupt == ActionType.CANCEL)
								{
									SwingManipulator.setVisibleWindow(window, false);
									continue NextPopupEventIteration;
								}

								if (manualAdvance)
								{
									/* wait for user to advance or dismiss popup */
									while (true)
									{
										interrupt = sleepTillInterrupted(POPUP_DURATION_MILLISECONDS);

										/* check for interruption */
										if (interrupt == ActionType.ADVANCE)
										{
											manualAdvance = true;
											continue ShowNextMailIteration;
										}
										else if (interrupt == ActionType.CANCEL)
										{
											SwingManipulator.setVisibleWindow(window, false);
											continue NextPopupEventIteration;
										}
									}
								}
							}
						}

						if (shownFirstMail)
						{
							if (sleepTillInterrupted(LAST_POPUP_DURATION_MILLISECONDS) == ActionType.CANCEL)
							{
								SwingManipulator.setVisibleWindow(window, false);
								continue NextPopupEventIteration;
							}

							/* at least one mail was shown; proceed to animate exit */
							SlidingAnimator.animate(
									window,
									coordinates[0],
									coordinates[1],
									SlidingAnimator.Direction.DOWN_OUT,
									10);
						}
					}
					else if (action == ActionType.TEST)
					{
						showSingleMessage(TEST_STRING, defaultBorderColor);
					}
					else if (action == ActionType.CANCEL)
					{
						SwingManipulator.setVisibleWindow(window, false);
					}
					else if (action == ActionType.TERMINATE)
					{
						break NextPopupEventIteration;
					}
				}
			}
		}).start();
	}


	/**
	 * Advance to the next mail that comes after the specified current mail.
	 *
	 * @param mail
	 *     current mail; null if the first mail among all accounts is to be returned
	 * @param loopback
	 *     should we loop back to the first account if there are no accounts left?
	 *     (loopback occurs once only)
	 * @return
	 *     the next mail after the specified current mail; null if there is no such mail
	 */
	private Mail advanceToNextMail(
			final Mail mail,
			final boolean loopback)
	{
		Account ac = null;
		Mail m = null;

		if (mail == null)
		{
			/* get the first account */
			ac = parent.getFirstAccount();

			if (ac == null)
			{
				/* there are zero accounts */
				return null;
			}
			else
			{
				m = ac.getFirstMail();
			}
		}
		else
		{
			/* get the next mail in the same account */
			ac = mail.account;
			m = ac.getNextMail(mail);
		}

		boolean seenLast = false;

		while (m == null)
		{
			/* advance to the next account */
			ac = parent.getNextAccount(ac);

			if (ac == null)
			{
				/* there are no more accounts */
				if (seenLast)
				{
					/* cannot find any mail */
					return null;
				}

				if (loopback)
				{
					/* loop back to the first account */
					ac = parent.getFirstAccount();

					if (ac == null)
					{
						/* there are zero accounts */
						return null;
					}

					seenLast = true;
				}
				else
				{
					/* cannot find any mail */
					return null;
				}
			}

			/* get the first mail of this account */
			m = ac.getFirstMail();
		}

		return m;
	}


	/**
	 * Show a single popup message.
	 * This method blocks until the animation has finished; it should NOT be called on the EDT.
	 *
	 * @param message
	 *     message text to be displayed on the popup message
	 * @param color
	 *     color of the popup message border
	 */
	private void showSingleMessage(
			final String message,
			final Color color)
	{
		/******************************
		 * (1) POPULATE POPUP MESSAGE *
		 ******************************/

		final int[] coordinates = populatePopupMessage(message, color);

		if (sleepTillInterrupted(0L) == ActionType.CANCEL)
		{
			SwingManipulator.setVisibleWindow(window, false);
			return;
		}

		/***************************
		 * (2) ANIMATE POPUP ENTRY *
		 ***************************/

		SlidingAnimator.animate(
				window,
				coordinates[0],
				coordinates[1],
				SlidingAnimator.Direction.UP_IN,
				10);

		/***********************************
		 * (3) DISPLAY POPUP FOR SOME TIME *
		 ***********************************/

		if (sleepTillInterrupted(FIRST_POPUP_DURATION_MILLISECONDS) == ActionType.CANCEL)
		{
			SwingManipulator.setVisibleWindow(window, false);
			return;
		}

		/**************************
		 * (4) ANIMATE POPUP EXIT *
		 **************************/

		SlidingAnimator.animate(
				window,
				coordinates[0],
				coordinates[1],
				SlidingAnimator.Direction.DOWN_OUT,
				10);

		SwingManipulator.setVisibleWindow(window, false);
	}


	/**
	 * Populate the popup message with the specified message text.
	 * This method blocks until the popup message is populated; it should NOT be called on the EDT.
	 *
	 * @param message
	 *     message text to be displayed on the popup message
	 * @param color
	 *     color of the popup message border
	 * @return
	 *     an int array containing the x and y coordinates of the window origin, in that order
	 */
	private int[] populatePopupMessage(
			final String message,
			final Color color)
	{
		final ValueCapsule<int[]> coordinates = new ValueCapsule<int[]>();

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				text.setText(message);
				setBorder(BorderFactory.createLineBorder(color, POPUP_BORDER_WIDTH));
				window.pack();

				final int width = window.getWidth();
				final int height = window.getHeight();
				final Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

				int x = r.x + r.width - width;
				int y = r.y + r.height - height;

				window.setLocation(x, y);
				coordinates.set(new int[] {x, y});
			}
		});

		return coordinates.get();
	}


	/**
	 * Update popup messages for the specified account.
	 *
	 * @param ac
	 *     account to be updated
	 * @param alertPopup
	 *     is the "Popup" alert active for the specified account?
	 */
	void updateMessages(
			final Account ac,
			final boolean alertPopup)
	{
		if (alertPopup)
		{
			showRecentMessages();
		}
		else
		{
			/* update last mail sequence number for this account */
			final Mail m = ac.getLastMail();

			if (m != null)
			{
				synchronized (lastMailSequenceNumbers)
				{
					final Integer last = lastMailSequenceNumbers.get(ac);

					if ((last == null) || (m.sequenceNumber > last))
					{
						lastMailSequenceNumbers.put(ac, m.sequenceNumber);
					}
				}
			}
		}
	}


	/**
	 * Advance to the next popup message.
	 */
	void advanceMessage()
	{
		synchronized (actions)
		{
			actions.addFirst(ActionType.ADVANCE);
		}
	}


	/**
	 * Dismiss the popup message.
	 */
	void dismissPopup()
	{
		synchronized (actions)
		{
			actions.addFirst(ActionType.CANCEL);
		}
	}


	/**
	 * Cancel display of all popup messages.
	 */
	void cancelAllMessages()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.addLast(ActionType.CANCEL);
		}
	}

	/**
	 * Show all popup messages.
	 */
	void showAllMessages()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.addLast(ActionType.CANCEL);
			actions.addLast(ActionType.ALL);
		}
	}


	/**
	 * Show recent popup messages.
	 */
	private void showRecentMessages()
	{
		synchronized (actions)
		{
			actions.addLast(ActionType.RECENT);
		}
	}


	/**
	 * Show a test popup message.
	 */
	void test()
	{
		synchronized (actions)
		{
			actions.addLast(ActionType.TEST);
		}
	}


	/**
	 * Terminate the popup thread.
	 */
	void terminate()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.addLast(ActionType.CANCEL);
			actions.addLast(ActionType.TERMINATE);
		}
	}


	/**
	 * Sleep for the specified duration, or until interrupted.
	 *
	 * @param duration
	 *      sleep duration in milliseconds
	 * @return
	 *      action that caused the interruption (CANCEL or ADVANCE) which would be dequeued
	 *      from the action queue; null if no interruption occurred
	 */
	private ActionType sleepTillInterrupted(
			final long duration)
	{
		if (duration <= 0L)
		{
			synchronized (actions)
			{
				final ActionType interrupt = actions.peekFirst();

				if ((interrupt == ActionType.CANCEL) ||
						(interrupt == ActionType.ADVANCE))
				{
					return actions.pollFirst();
				}
				else
				{
					return null;
				}
			}
		}
		else
		{
			final long start = System.currentTimeMillis();

			while (true)
			{
				if ((System.currentTimeMillis() - start) >= duration)
				{
					return null;
				}

				synchronized (actions)
				{
					final ActionType interrupt = actions.peekFirst();

					if ((interrupt == ActionType.CANCEL) ||
							(interrupt == ActionType.ADVANCE))
					{
						return actions.pollFirst();
					}
				}

				Debug.sleep(REFRESH_INTERVAL_MILLISECONDS / 10);
			}
		}
	}

	/******************
	 * NESTED CLASSES *
	 ******************/

	/**
	 * Types of action.
	 */
	private enum ActionType
	{
		ALL,
		RECENT,
		ADVANCE,
		CANCEL,
		TEST,
		TERMINATE
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

		panel = new javax.swing.JPanel();
		icon = new javax.swing.JLabel();
		text = new javax.swing.JLabel();

		setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 102, 0), 4));
		setOpaque(false);

		panel.setBackground(new java.awt.Color(255, 255, 255));
		panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));

		icon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/freeshell/zs/gmailassistant/resources/ga_logo_40.png"))); // NOI18N
		icon.setFocusable(false);

		text.setText("Text");
		text.setVerticalAlignment(javax.swing.SwingConstants.TOP);

		javax.swing.GroupLayout panelLayout = new javax.swing.GroupLayout(panel);
		panel.setLayout(panelLayout);
		panelLayout.setHorizontalGroup(
			panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(panelLayout.createSequentialGroup()
				.addComponent(icon, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(text, javax.swing.GroupLayout.PREFERRED_SIZE, 319, javax.swing.GroupLayout.PREFERRED_SIZE))
		);
		panelLayout.setVerticalGroup(
			panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addComponent(icon, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
			.addComponent(text, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
		);

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
		this.setLayout(layout);
		layout.setHorizontalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addComponent(panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
		);
		layout.setVerticalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
		);
	}// </editor-fold>//GEN-END:initComponents

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JLabel icon;
	private javax.swing.JPanel panel;
	private javax.swing.JLabel text;
	// End of variables declaration//GEN-END:variables
}
