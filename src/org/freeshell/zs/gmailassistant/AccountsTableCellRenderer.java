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
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;


/**
 * Custom table cell renderer for the table of accounts.
 */
class AccountsTableCellRenderer
		extends JLabel
		implements TableCellRenderer
{
	/** foreground color */
	private final Color fg;

	/** background color */
	private final Color bg;

	/** foreground color for selected cells */
	private final Color fgSelected;

	/** background color for selected cells */
	private final Color bgSelected;

	/** Account object corresponding to the currently rendered cell */
	private volatile Account ac;


	/**
	 * Constructor.
	 *
	 * @param fg
	 *      foreground color
	 * @param bg
	 *      background color
	 * @param fgSelected
	 *      foreground color for selected cells
	 * @param bgSelected
	 *      background color for selected cells
	 */
	AccountsTableCellRenderer(
			final Color fg,
			final Color bg,
			final Color fgSelected,
			final Color bgSelected)
	{
		super();
		this.fg = fg;
		this.bg = bg;
		this.fgSelected = fgSelected;
		this.bgSelected = bgSelected;
	}


	/**
	 * Return the tooltip text for this label.
	 */
	@Override
	public String getToolTipText()
	{
		return ac.getToolTipText();
	}


	/**
	 * Return the JProgressBar component used for drawing the cell.
	 */
	@Override
	public Component getTableCellRendererComponent(
			final JTable table,
			final Object val,
			final boolean isSelected,
			final boolean hasFocus,
			final int row,
			final int col)
	{
		final TableCellContent c = (TableCellContent) val;
		ac = c.ac;

		setText(c.text);
		setHorizontalAlignment(c.align);

		if (isSelected)
		{
			setForeground(fgSelected);
			setBackground(bgSelected);
			setOpaque(true);
		}
		else
		{
			setForeground(fg);
			setBackground(bg);
			setOpaque(false);
		}

		return this;
	}
}
