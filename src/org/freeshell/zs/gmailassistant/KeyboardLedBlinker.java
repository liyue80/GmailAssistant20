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

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Queue;
import org.freeshell.zs.common.Debug;


/**
 * Blink the keyboard LED.
 */
class KeyboardLedBlinker
{
	/** interval between keyboard num-lock LED blinking, in milliseconds */
	private static final long BLINK_INTERVAL_MILLISECONDS = 200L;

	/** number of blinking iterations when performing a test */
	private static final int TEST_ITERATIONS = 2;

	/** queued actions to be performed by the timer */
	private final Queue<ActionType> actions = new ArrayDeque<ActionType>();

	/** has the thread for blinking keyboard LED been terminated? */
	private volatile boolean threadTerminated = false;


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      GmailAssistant parent object
	 */
	KeyboardLedBlinker(
			final GmailAssistant parent)
	{
		/***********************************************
		 * INITIALIZE THREAD FOR BLINKING KEYBOARD LED *
		 ***********************************************/

		new Thread(new Runnable()
		{
			/* toolkit for accessing LED state */
			private Toolkit toolkit = null;

			/* robot for pressing keys */
			private Robot robot = null;


			/**
			 * Set the LED to the specified state.
			 *
			 * @param state
			 *     new state of the LED
			 */
			private void setKeyboardLedState(
					final boolean state)
			{
				final String key = parent.properties.getString("alert.led.key");
				final int keyCode;

				if ("none".equals(key))
				{
					return;
				}

				if ("num".equals(key))
				{
					keyCode = KeyEvent.VK_NUM_LOCK;
				}
				else if ("caps".equals(key))
				{
					keyCode = KeyEvent.VK_CAPS_LOCK;
				}
				else if ("scroll".equals(key))
				{
					keyCode = KeyEvent.VK_SCROLL_LOCK;
				}
				else
				{
					throw new IllegalStateException("Illegal \"Alert LED Key\" selection state.");
				}

				try
				{
					toolkit.setLockingKeyState(keyCode, state);
				}
				catch (Exception e)
				{
					if (robot != null)
					{
						robot.keyPress(keyCode);
						robot.keyRelease(keyCode);
					}
				}
			}


			@Override
			public void run()
			{
				try
				{
					toolkit = Toolkit.getDefaultToolkit();
				}
				catch (Exception e)
				{
					/* ignore */
				}

				try
				{
					robot = new Robot();
				}
				catch (Exception e)
				{
					/* ignore */
				}

				/* current LED state */
				boolean state = false;

				/* saved LED states */
				boolean savedNumLockState = false;
				boolean savedCapsLockState = false;
				boolean savedScrollLockState = false;

				/* is LED currently blinking? */
				boolean blinking = false;

				while (true)
				{
					final ActionType action;

					synchronized (actions)
					{
						action = actions.poll();
					}

					if (action == ActionType.START)
					{
						if (!blinking)
						{
							savedNumLockState = getLockingKeyState(toolkit, KeyEvent.VK_NUM_LOCK);
							savedCapsLockState = getLockingKeyState(toolkit, KeyEvent.VK_CAPS_LOCK);
							savedScrollLockState = getLockingKeyState(toolkit, KeyEvent.VK_SCROLL_LOCK);
							state = false;
							blinking = true;
						}
					}
					else if (action == ActionType.STOP)
					{
						if (blinking)
						{
							setLockingKeyState(toolkit, KeyEvent.VK_NUM_LOCK, savedNumLockState);
							setLockingKeyState(toolkit, KeyEvent.VK_CAPS_LOCK, savedCapsLockState);
							setLockingKeyState(toolkit, KeyEvent.VK_SCROLL_LOCK, savedScrollLockState);
							blinking = false;
						}
					}
					else if (action == ActionType.TEST)
					{
						final boolean existingNumLockState = getLockingKeyState(toolkit, KeyEvent.VK_NUM_LOCK);
						final boolean existingCapsLockState = getLockingKeyState(toolkit, KeyEvent.VK_CAPS_LOCK);
						final boolean existingScrollLockState = getLockingKeyState(toolkit, KeyEvent.VK_SCROLL_LOCK);

						for (int i = 0; i < TEST_ITERATIONS; i++)
						{
							setKeyboardLedState(true);
							Debug.sleep(BLINK_INTERVAL_MILLISECONDS);
							setKeyboardLedState(false);
							Debug.sleep(BLINK_INTERVAL_MILLISECONDS);
						}

						setLockingKeyState(toolkit, KeyEvent.VK_NUM_LOCK, existingNumLockState);
						setLockingKeyState(toolkit, KeyEvent.VK_CAPS_LOCK, existingCapsLockState);
						setLockingKeyState(toolkit, KeyEvent.VK_SCROLL_LOCK, existingScrollLockState);
					}
					else if (action == ActionType.TERMINATE)
					{
						threadTerminated = true;
						return;
					}

					if (blinking)
					{
						state = !state;
						setKeyboardLedState(state);
					}

					Debug.sleep(BLINK_INTERVAL_MILLISECONDS);
				}
			}
		}).start();
	}


	/**
	 * Start the blinker, if it has not already been started.
	 */
	void start()
	{
		synchronized (actions)
		{
			actions.add(ActionType.START);
		}
	}


	/**
	 * Stop the blinker, if it is blinking.
	 */
	void stop()
	{
		synchronized (actions)
		{
			actions.add(ActionType.STOP);
		}
	}


	/**
	 * Test the blinker.
	 */
	void test()
	{
		synchronized (actions)
		{
			actions.add(ActionType.TEST);
		}
	}


	/**
	 * Stop the blinker now, if it is blinking.
	 */
	void cancelAll()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.add(ActionType.STOP);
		}
	}


	/**
	 * Terminate the thread for blinking keyboard LED.
	 * This method blocks until the thread is cancelled.
	 */
	void terminate()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.add(ActionType.STOP);
			actions.add(ActionType.TERMINATE);
		}

		while (!threadTerminated)
		{
			Debug.sleep(BLINK_INTERVAL_MILLISECONDS / 10);
		}
	}


	/**
	 * Get the state of the specified locking key, without throwing an exception.
	 *
	 * @param toolkit
	 *     <code>Toolkit</code> object to be used for accessing the state
	 * @param keyCode
	 *     key code of the locking key
	 * @return
	 *     state of the specified locking key; false if an exception occurs when accessing
	 *     the state
	 */
	private static boolean getLockingKeyState(
			final Toolkit toolkit,
			final int keyCode)
	{
		try
		{
			return toolkit.getLockingKeyState(keyCode);
		}
		catch (Exception e)
		{
			return false;
		}
	}


	/**
	 * Set the state of the specified locking key, without throwing an exception.
	 *
	 * @param toolkit
	 *     <code>Toolkit</code> object to be used for accessing the state
	 * @param keyCode
	 *     key code of the locking key
	 * @param state
	 *     new state of the specified locking key
	 */
	private static void setLockingKeyState(
			final Toolkit toolkit,
			final int keyCode,
			final boolean state)
	{
		try
		{
			toolkit.setLockingKeyState(keyCode, state);
		}
		catch (Exception e)
		{
			/* ignore */
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
		START,
		STOP,
		TEST,
		TERMINATE
	}
}
