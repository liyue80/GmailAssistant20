/**
 * SlidingAnimator.java
 * Copyright 2008 Zach Scrivena
 * zachscrivena@gmail.com
 * http://zs.freeshell.org/
 *
 * TERMS AND CONDITIONS:
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.freeshell.zs.common;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;


/**
 * Perform a sliding animation for a window.
 */
public final class SlidingAnimator
{
	/** interval between animation refreshes, in milliseconds */
	private static final int ANIMATION_REFRESH_INTERVAL_MILLISECONDS = 20;


	/**
	 * Private constructor that should never be called.
	 */
	private SlidingAnimator()
	{}


	/**
	 * Perform a sliding animation for the specified window.
	 * This method blocks until the animation has finished; it should NOT be called on the EDT.
	 *
	 * @param sourceWindow
	 *      source window to be animated
	 * @param refX
	 *      x-coordinate of the window origin at the start/end reference position
	 * @param refY
	 *      y-coordinate of the window origin at the start/end reference position
	 * @param dir
	 *      direction of animation
	 * @param stepSize
	 *      number of incremental pixels per animation step
	 */
	public static void animate(
			final JWindow sourceWindow,
			final int refX,
			final int refY,
			final Direction dir,
			final int stepSize)
	{
		/****************************************
		 * (1) CREATE SWING TIMER FOR ANIMATION *
		 ****************************************/

		final Timer swingTimer = new Timer(ANIMATION_REFRESH_INTERVAL_MILLISECONDS, null);
		swingTimer.start();

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				/**************************************
				 * (2) DETERMINE ANIMATION PARAMETERS *
				 **************************************/

				final int step = Math.max(stepSize, 1);
				final int sourceWidth = sourceWindow.getWidth();
				final int sourceHeight = sourceWindow.getHeight();
				final int initialX;
				final int initialY;
				final int finalX;
				final int finalY;
				final int deltaX;
				final int deltaY;

				switch (dir)
				{
					case UP_IN:
						initialX = refX;
						initialY = refY + sourceHeight;
						finalX = refX;
						finalY = refY;
						deltaX = 0;
						deltaY = -step;
						break;

					case DOWN_IN:
						initialX = refX;
						initialY = refY - sourceHeight;
						finalX = refX;
						finalY = refY;
						deltaX = 0;
						deltaY = step;
						break;

					case LEFT_IN:
						initialX = refX + sourceWidth;
						initialY = refY;
						finalX = refX;
						finalY = refY;
						deltaX = -step;
						deltaY = 0;
						break;

					case RIGHT_IN:
						initialX = refX - sourceWidth;
						initialY = refY;
						finalX = refX;
						finalY = refY;
						deltaX = step;
						deltaY = 0;
						break;

					case UP_OUT:
						initialX = refX;
						initialY = refY;
						finalX = refX;
						finalY = refY - sourceHeight;
						deltaX = 0;
						deltaY = -step;
						break;

					case DOWN_OUT:
						initialX = refX;
						initialY = refY;
						finalX = refX;
						finalY = refY + sourceHeight;
						deltaX = 0;
						deltaY = step;
						break;

					case LEFT_OUT:
						initialX = refX;
						initialY = refY;
						finalX = refX - sourceWidth;
						finalY = refY;
						deltaX = -step;
						deltaY = 0;
						break;

					case RIGHT_OUT:
						initialX = refX;
						initialY = refY;
						finalX = refX + sourceWidth;
						finalY = refY;
						deltaX = step;
						deltaY = 0;
						break;

					default:
						initialX = 0;
						initialY = 0;
						finalX = 0;
						finalY = 0;
						deltaX = 0;
						deltaY = 0;
				}

				/********************************************
				 * (3) CREATE AN IMAGE OF THE SOURCE WINDOW *
				 ********************************************/

				final BufferedImage image = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_RGB);
				final Graphics2D g = image.createGraphics();
				sourceWindow.setVisible(true);
				sourceWindow.paintAll(g);

				/* hide the source window if animation is "in-bound" */
				if ((dir == Direction.UP_IN) ||
						(dir == Direction.DOWN_IN) ||
						(dir == Direction.LEFT_IN) ||
						(dir == Direction.RIGHT_IN))
				{
					sourceWindow.setVisible(false);
				}

				/************************************************************
				 * (4) CREATE ACTUAL WINDOW USED IN RENDERING THE ANIMATION *
				 ************************************************************/

				final RenderedWindow window = new RenderedWindow(image, initialX, initialY);

				/*******************************************************************
				 * (5) ADD ACTION LISTENER TO SWING TIMER FOR ANIMATING THE WINDOW *
				 *******************************************************************/

				swingTimer.addActionListener(new ActionListener()
				{
					/** is this the first step in the animation? */
					private boolean firstStep = true;

					/**
					 * Perform a single step in the animation.
					 */
					public void actionPerformed(ActionEvent e)
					{
						/* update position of the virtual window */
						final int nextX = window.virtualX + deltaX;
						final int nextY = window.virtualY + deltaY;

						/* check if the animation should be terminated */
						boolean terminate = false;

						switch (dir)
						{
							case UP_IN:
							case UP_OUT:
								if (nextY < finalY)
								{
									terminate = true;
								}
								break;

							case DOWN_IN:
							case DOWN_OUT:
								if (nextY > finalY)
								{
									terminate = true;
								}
								break;

							case LEFT_IN:
							case LEFT_OUT:
								if (nextX < finalX)
								{
									terminate = true;
								}
								break;

							case RIGHT_IN:
							case RIGHT_OUT:
								if (nextX > finalX)
								{
									terminate = true;
								}
								break;
						}

						if (terminate)
						{
							/* terminate the animation */
							swingTimer.stop();

							if ((dir == Direction.UP_IN) ||
									(dir == Direction.DOWN_IN) ||
									(dir == Direction.LEFT_IN) ||
									(dir == Direction.RIGHT_IN))
							{
								sourceWindow.setVisible(true);
							}
							else
							{
								sourceWindow.setVisible(false);
							}

							window.setVisible(false);
							window.dispose();
							return;
						}
						else
						{
							/* register updated position of the virtual window */
							window.virtualX = nextX;
							window.virtualY = nextY;
						}

						/* determine position and size of the rendered window */
						final int windowX;
						final int windowY;
						final int windowWidth;
						final int windowHeight;

						if (window.virtualX >= refX)
						{
							windowX = window.virtualX;
							windowWidth = sourceWidth - window.virtualX + refX;
						}
						else
						{
							windowX = refX;
							windowWidth = window.virtualX + sourceWidth - refX;
						}

						if (window.virtualY >= refY)
						{
							windowY = window.virtualY;
							windowHeight = sourceHeight - window.virtualY + refY;
						}
						else
						{
							windowY = refY;
							windowHeight = window.virtualY + sourceHeight - refY;
						}

						window.setSize(windowWidth, windowHeight);
						window.setLocation(windowX, windowY);

						/* check if this is the first step in the animation */
						if (firstStep)
						{
							window.setVisible(true);
							firstStep = false;

							if ((dir == Direction.UP_OUT) ||
								(dir == Direction.DOWN_OUT) ||
								(dir == Direction.LEFT_OUT) ||
								(dir == Direction.RIGHT_OUT))
							{
								sourceWindow.setVisible(false);
							}
						}
					}
				});
			}
		});

		/* wait for animation to finish */
		while (swingTimer.isRunning())
		{
			Debug.sleep(ANIMATION_REFRESH_INTERVAL_MILLISECONDS);
		}
	}

	/******************
	 * NESTED CLASSES *
	 ******************/

	/**
	 * Represent the actual window used in rendering an animation.
	 */
	private static class RenderedWindow
			extends JWindow
	{
		/** image to be drawn on this window */
		private final BufferedImage image;

		/** current x-coordinate of the virtual window origin */
		int virtualX;

		/** current y-coordinate of the virtual window origin */
		int virtualY;


		/**
		 * Constructor.
		 *
		 * @param image
		 *     image to be drawn on this window
		 * @param virtualX
		 *     initial x-coordinate of the virtual window origin
		 * @param virtualY
		 *     initial y-coordinate of the virtual window origin
		 */
		RenderedWindow(
				final BufferedImage image,
				final int virtualX,
				final int virtualY)
		{
			super();
			setAlwaysOnTop(true);
			this.image = image;
			this.virtualX = virtualX;
			this.virtualY = virtualY;
		}


		@Override
		public void update(Graphics g)
		{
			paint(g);
		}


		@Override
		public void paint(Graphics g)
		{
			g.drawImage(image.getSubimage(
					getX() - virtualX,
					getY() - virtualY,
					getWidth(),
					getHeight()),
					0,
					0,
					this);
		}
	}


	/**
	 * Sliding animation direction.
	 */
	public static enum Direction
	{
		UP_IN,
		UP_OUT,
		DOWN_IN,
		DOWN_OUT,
		LEFT_IN,
		LEFT_OUT,
		RIGHT_IN,
		RIGHT_OUT
	}
}
