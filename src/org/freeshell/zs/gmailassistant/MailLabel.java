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


/**
 * Represent a Gmail mail label.
 * Instances of this class are immutable.
 */
class MailLabel
{
	/** set of system labels */
	private static final String[] SYSTEM_LABELS =
	{
		"All Mail", "Drafts", "Sent Mail", "Spam", "Starred", "Trash"
	};

	/** mail label */
	final String label;

	/** IMAP folder name corresponding to the mail label */
	final String folder;


	/**
	 * Constructor.
	 *
	 * @param label
	 *      mail label given by the user
	 */
	MailLabel(
			final String s)
	{
		final String t = s.trim();

		if (t.isEmpty())
		{
			label = "";
			folder = "";
			return;
		}

		if ("INBOX".equalsIgnoreCase(t))
		{
			label = "INBOX";
			folder = "INBOX";
			return;
		}

		for (String l : SYSTEM_LABELS)
		{
			if (l.equalsIgnoreCase(t))
			{
				label = l;
				folder = "[Gmail]/" + l;
				return;
			}
		}

		/* otherwise, use the user-specified label */
		label = t;

		/* convert user-specified label to the IMAP folder name */
		final StringBuilder sb = new StringBuilder();

		/* handle first character */
		char c;

		c = t.charAt(0);

		if (c == '/')
		{
			sb.append('_');
		}
		else
		{
			sb.append(c);
		}

		/* handle second character up to the second-last character */
		for (int i = 1; i < (t.length() - 1); i++)
		{
			final char d = t.charAt(i);

			if ((c == '/') && (d == '/'))
			{
				sb.append('_');
			}
			else
			{
				sb.append(d);
			}

			c = d;
		}

		/* handle last character */
		c = t.charAt(t.length() - 1);

		if (c == '/')
		{
			sb.append('_');
		}
		else
		{
			sb.append(c);
		}

		folder = sb.toString();
	}
}
