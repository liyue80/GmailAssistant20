/**
 * ResourceManipulator.java
 * Copyright 2007 - 2008 Zach Scrivena
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


/**
 * Perform resource-related operations.
 */
public final class ResourceManipulator
{
	/**
	 * Private constructor that should never be called.
	 */
	private ResourceManipulator()
	{}


	/**
	 * Return the contents of the specified resource as a string.
	 *
	 * @param name
	 *      name of the resource
	 * @return
	 *      contents of the resource as a string
	 * @throws java.io.IOException
	 *      if an I/O error occurs while reading the resource
	 */
	public static String resourceAsString(
			final String name)
			throws IOException
	{
		final BufferedReader br = new BufferedReader(new InputStreamReader(
				ResourceManipulator.class.getResourceAsStream(name)));

		final StringBuilder sb = new StringBuilder();

		while (true)
		{
			final String s = br.readLine();

			if (s == null)
			{
				break;
			}
			else
			{
				if (sb.length() > 0)
				{
					sb.append('\n');
				}

				sb.append(s);
			}
		}

		br.close();

		return sb.toString();
	}
}
