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
 * Identifier for a mail.
 * Instances of this class are immutable.
 */
class MailIdentifier
{
	/** name of the IMAP folder containing this mail message */
	final String imapFolderName;

	/** IMAP UID for this mail message (unique within the folder) */
	final long imapMessageUid;


	/**
	 * Constructor.
	 *
	 * @param imapFolderName
	 *      name of the IMAP folder containing this mail message
	 * @param imapMessageUid
	 *      IMAP UID for this mail message (unique within the folder)
	 */
	MailIdentifier(
			final String imapFolderName,
			final long imapMessageUid)
	{
		this.imapFolderName = imapFolderName;
		this.imapMessageUid = imapMessageUid;
	}


	/**
	 * Check for equality between this mail ID and the specified mail ID.
	 */
	@Override
	public boolean equals(
			Object obj)
	{
		if (obj instanceof MailIdentifier)
		{
			final MailIdentifier o = (MailIdentifier) obj;

			return (imapFolderName.equals(o.imapFolderName) &&
					(imapMessageUid == o.imapMessageUid));
		}
		else
		{
			return false;
		}
	}


	/**
	 * Generate a hash code for this mail ID.
	 */
	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 61 * hash + (imapFolderName != null ? imapFolderName.hashCode() : 0);
		hash = 61 * hash + (int) (imapMessageUid ^ (imapMessageUid >>> 32));
		return hash;
	}
}
