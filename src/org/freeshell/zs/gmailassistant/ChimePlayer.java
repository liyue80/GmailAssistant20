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

import java.util.ArrayDeque;
import java.util.Queue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import org.freeshell.zs.common.Debug;
import org.freeshell.zs.common.SwingManipulator;


/**
 * Play an audible chime and periodic bell.
 */
class ChimePlayer
{
	/** refresh interval in milliseconds */
	private static final long REFRESH_INTERVAL_MILLISECONDS = 200L;

	/** chime audio clip */
	private Clip chime = null;

	/** periodic bell audio clip */
	private Clip periodicBell = null;

	/** should the periodic bell be played? */
	private volatile boolean periodicBellPlay = false;

	/** time at which the periodic bell was last played */
	private volatile long periodicBellLastPlayed = 0L;

	/** queued actions to be performed by the timer */
	private final Queue<ActionType> actions = new ArrayDeque<ActionType>();


	/**
	 * Constructor.
	 *
	 * @param parent
	 *      parent GmailAssistant object
	 */
	ChimePlayer(
			final GmailAssistant parent)
	{
		/* load chime audio clip */
		try
		{
			final AudioInputStream stream = AudioSystem.getAudioInputStream(
					ChimePlayer.class.getResource(parent.properties.getString("alert.chime.audio.clip")));

			final AudioFormat format = stream.getFormat();

			chime = (Clip) AudioSystem.getLine(new DataLine.Info(
					Clip.class,
					format,
					(int) (stream.getFrameLength() * format.getFrameSize())));

			chime.open(stream);
		}
		catch (Exception e)
		{
			chime = null;

			SwingManipulator.showErrorDialog(
					parent,
					String.format("Initialization Error - %s", parent.name),
					String.format("Failed to load chime audio clip (%s).\n" +
					"%s will proceed to run without playing the chime audio clip.",
					e.toString(), parent.name));
		}

		/* load periodic bell audio clip */
		try
		{
			final AudioInputStream stream = AudioSystem.getAudioInputStream(
					ChimePlayer.class.getResource(parent.properties.getString("alert.periodic.bell.audio.clip")));

			final AudioFormat format = stream.getFormat();

			periodicBell = (Clip) AudioSystem.getLine(new DataLine.Info(
					Clip.class,
					format,
					(int) (stream.getFrameLength() * format.getFrameSize())));

			periodicBell.open(stream);
		}
		catch (Exception e)
		{
			periodicBell = null;

			SwingManipulator.showErrorDialog(
					parent,
					String.format("Initialization Error - %s", parent.name),
					String.format("Failed to load periodic bell audio clip (%s).\n" +
					"%s will proceed to run without playing the periodic bell audio clip.",
					e.toString(), parent.name));
		}

		/************************************
		 * INITIALIZE THREAD TO PLAY CHIMES *
		 ************************************/

		new Thread(new Runnable()
		{
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

					if (action == ActionType.CHIME)
					{
						playClipAndWait(chime);
						periodicBellLastPlayed = System.currentTimeMillis();
					}
					else if (action == ActionType.TEST_CHIME)
					{
						playClipAndWait(chime);
					}
					else if (action == ActionType.TEST_PERIODIC_BELL)
					{
						playClipAndWait(periodicBell);
					}
					else if (action == ActionType.TERMINATE)
					{
						return;
					}

					/* play periodic bell, if necessary */
					if (periodicBellPlay &&
							((System.currentTimeMillis() - periodicBellLastPlayed) >= parent.properties.getLong("alert.periodic.bell.interval.milliseconds")))
					{
						playClipAndWait(periodicBell);
						periodicBellLastPlayed = System.currentTimeMillis();
					}

					Debug.sleep(REFRESH_INTERVAL_MILLISECONDS);
				}
			}
		}).start();
	}


	/**
	 * Play the chime.
	 */
	void playChime()
	{
		synchronized (actions)
		{
			actions.add(ActionType.CHIME);
		}
	}


	/**
	 * Test the chime.
	 */
	void testChime()
	{
		synchronized (actions)
		{
			actions.add(ActionType.TEST_CHIME);
		}
	}


	/**
	 * Test the periodic bell.
	 */
	void testPeriodicBell()
	{
		synchronized (actions)
		{
			actions.add(ActionType.TEST_PERIODIC_BELL);
		}
	}


	/**
	 * Cancel all pending chimes.
	 */
	void cancelAll()
	{
		synchronized (actions)
		{
			actions.clear();
		}
	}


	/**
	 * Start the periodic bell.
	 */
	void startPeriodicBell()
	{
		periodicBellPlay = true;
	}


	/**
	 * Stop the periodic bell.
	 */
	void stopPeriodicBell()
	{
		periodicBellPlay = false;
	}


	/**
	 * Terminate the thread that plays chimes.
	 */
	void terminate()
	{
		synchronized (actions)
		{
			actions.clear();
			actions.add(ActionType.TERMINATE);
		}
	}


	/**
	 * Play the specified audio clip, and wait for it to finish.
	 *
	 * @param c
	 *      audio clip to be played
	 */
	private static void playClipAndWait(
			final Clip c)
	{
		if (c == null)
		{
			return;
		}

		/* rewind the clip and start playing */
		c.setFramePosition(0);
		c.start();

		while (c.isActive())
		{
			Debug.sleep(REFRESH_INTERVAL_MILLISECONDS / 10);
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
		CHIME,
		TEST_CHIME,
		TEST_PERIODIC_BELL,
		TERMINATE
	}
}
