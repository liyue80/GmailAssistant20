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

import javax.swing.JLabel;


/**
 * Represent the content of a table cell.
 */
class TableCellContent
		implements Comparable<TableCellContent>
{
	/** corresponding Account object */
	final Account ac;

	/** text string for this cell */
	String text = "";

	/** value associated to this cell, for sorting purpose */
	int value = -1;

	/** horizontal alignment for this cell */
	int align = JLabel.LEFT;


	/**
	 * Constructor.
	 *
	 * @param ac
	 *      corresponding Account object
	 */
	TableCellContent(
			final Account ac)
	{
		this.ac = ac;
	}


	/**
	 * Return the tooltip text for this cell.
	 *
	 * @return
	 *      tooltip text
	 */
	String getToolTipText()
	{
		return ac.getToolTipText();
	}


	/**
	 * Compare this table cell content to the specified table cell content,
	 * by value first, and then by text.
	 */
	public int compareTo(
			TableCellContent o)
	{
		if (value == o.value)
		{
			return text.compareTo(o.text);
		}
		else if (value < o.value)
		{
			return -1;
		}
		else
		{
			return 1;
		}
	}


	/**
	 * Check for equality between this table cell content and the
	 * specified table cell content, using only the value and text fields.
	 */
	@Override
	public boolean equals(
			Object o)
	{
		if (o instanceof TableCellContent)
		{
			final TableCellContent c = (TableCellContent) o;
			return (value == c.value) && text.equals(c.text);
		}
		else
		{
			return false;
		}
	}


	/**
	 * Generate a hash code for this table cell content, using the value and text fields.
	 */
	@Override
	public
	int hashCode()
	{
		int hash = 7;
		hash = 83 * hash + (text != null ? text.hashCode() : 0);
		hash = 83 * hash + value;
		return hash;
	}
}
