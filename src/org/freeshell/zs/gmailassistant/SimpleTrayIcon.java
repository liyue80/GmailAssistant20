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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.TrayIcon;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.freeshell.zs.common.Debug;


/**
 * Represent a simple system tray icon.
 */
class SimpleTrayIcon
		extends TrayIcon
{
	/** refresh interval in milliseconds */
	private static final long REFRESH_INTERVAL_MILLISECONDS = 200L;

	/** number of iterations to blink */
	private static final int BLINK_ITERATIONS = 5;

	/** blinking interval in milliseconds */
	private static final long BLINK_INTERVAL_MILLISECONDS = 200L;

	/** queued actions to be performed by the timer */
	private final Queue<ActionType> actions = new ArrayDeque<ActionType>();


	/**
	 * Constructor.
	 *
	 * @param parent
	 *     parent GmailAssistant object
	 * @throws java.io.IOException
	 *     if an I/O error occurs while reading the icon image resources
	 */
	SimpleTrayIcon(
			final GmailAssistant parent)
			throws IOException
	{
		super(ImageIO.read(SimpleTrayIcon.class.getResource("/org/freeshell/zs/gmailassistant/resources/blank.png")));
		setToolTip(parent.name);
		setImageAutoSize(true);

		final Dimension d = getSize();
		final int w = Math.max(d.height, d.width);
		final String suffix = (w <= 16) ? "16" : "24";

		final Image blankIcon = ImageIO.read(SimpleTrayIcon.class.getResource("/org/freeshell/zs/gmailassistant/resources/blank.png"));
		final Image normalIcon = ImageIO.read(SimpleTrayIcon.class.getResource(String.format("/org/freeshell/zs/gmailassistant/resources/ga_tray_normal_%s.png", suffix)));
		final Image hotIcon = ImageIO.read(SimpleTrayIcon.class.getResource(String.format("/org/freeshell/zs/gmailassistant/resources/ga_tray_hot_%s.png", suffix)));
		final Image normalErrorIcon = ImageIO.read(SimpleTrayIcon.class.getResource(String.format("/org/freeshell/zs/gmailassistant/resources/ga_tray_normal_error_%s.png", suffix)));
		final Image hotErrorIcon = ImageIO.read(SimpleTrayIcon.class.getResource(String.format("/org/freeshell/zs/gmailassistant/resources/ga_tray_hot_error_%s.png", suffix)));

		setNormalIcon();

		/**************************************************
		 * INITIALIZE THREAD FOR HANDLING TRAY ICON STATE *
		 **************************************************/

		new Thread(new Runnable()
		{
			/** temporary icon used for blinking effect */
			private Image tempIcon;

			/** "normal" state of icon */
			private boolean normalState = true;

			/** "error" state of icon */
			private boolean errorState = false;


			/**
			 * Return the icon corresponding to the specified "normal" and "error" states.
			 */
			private Image pickIcon(
					final boolean normal,
					final boolean error)
			{
				if (normal)
				{
					return (error) ? normalErrorIcon : normalIcon;
				}
				else
				{
					return (error) ? hotErrorIcon : hotIcon;
				}
			}


			@Override
			public void run()
			{
				while (true)
				{
					final ActionType action;

					synchronized (actions)
					{
						action = actions.poll();
					}

					if (action == ActionType.NORMAL)
					{
						normalState = true;
					}
					else if (action == ActionType.HOT)
					{
						normalState = false;
					}
					else if (action == ActionType.SET_ERROR)
					{
						errorState = true;
					}
					else if (action == ActionType.CLEAR_ERROR)
					{
						errorState = false;
					}
					else if (action == ActionType.BLINK)
					{
						try
						{
							SwingUtilities.invokeAndWait(new Runnable()
							{
								public void run()
								{
									tempIcon = getImage();
								}
							});

							for (int i = 0; i < BLINK_ITERATIONS; i++)
							{
								SwingUtilities.invokeAndWait(new Runnable()
								{
									public void run()
									{
										setImage(blankIcon);
									}
								});

								Thread.sleep(BLINK_INTERVAL_MILLISECONDS);

								SwingUtilities.invokeAndWait(new Runnable()
								{
									public void run()
									{
										setImage(tempIcon);
									}
								});

								Thread.sleep(BLINK_INTERVAL_MILLISECONDS);
							}
						}
						catch (Exception e)
						{
							/* ignore */
						}

						try
						{
							SwingUtilities.invokeAndWait(new Runnable()
							{
								public void run()
								{
									setImage(tempIcon);
								}
							});
						}
						catch (Exception e)
						{
							/* ignore */
						}
					}
					else if (action == ActionType.TERMINATE)
					{
						return;
					}

					if ((action == ActionType.NORMAL) ||
							(action == ActionType.HOT) ||
							(action == ActionType.SET_ERROR) ||
							(action == ActionType.CLEAR_ERROR))
					{
						try
						{
							SwingUtilities.invokeAndWait(new Runnable()
							{
								public void run()
								{
									setImage(pickIcon(normalState, errorState));
								}
							});
						}
						catch (Exception e)
						{
							/* ignore */
						}
					}

					Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);
				}
			}
		}).start();
	}


	/**
	 * Set the tray icon to the "normal" state.
	 */
	void setNormalIcon()
	{
		synchronized (actions)
		{
			actions.add(ActionType.NORMAL);
		}
	}


	/**
	 * Set the tray icon to the "hot" state.
	 */
	void setHotIcon()
	{
		synchronized (actions)
		{
			actions.add(ActionType.HOT);
		}
	}


	/**
	 * Set the tray icon to the "error" state.
	 */
	void setErrorIcon()
	{
		synchronized (actions)
		{
			actions.add(ActionType.SET_ERROR);
		}
	}


	/**
	 * Clear the "error" state of the tray icon.
	 */
	void clearErrorIcon()
	{
		synchronized (actions)
		{
			actions.add(ActionType.CLEAR_ERROR);
		}
	}


	/**
	 * Blink the tray icon.
	 */
	void blinkIcon()
	{
		synchronized (actions)
		{
			actions.add(ActionType.BLINK);
		}
	}


	/**
	 * Terminate the thread for handling tray icon state.
	 */
	void terminate()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.add(ActionType.TERMINATE);
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
		NORMAL,
		HOT,
		SET_ERROR,
		CLEAR_ERROR,
		BLINK,
		TERMINATE
	}
}
