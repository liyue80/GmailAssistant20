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
import java.util.Date;
import java.util.Deque;
import java.util.regex.Pattern;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.NewsAddress;
import org.freeshell.zs.common.HtmlManipulator;


/**
 * <p>Represent an email message retrieved from a mail server.</p>
 *
 * <p>Instances of this class are <i>naturally ordered</i> by their <code>account</code>
 * and <code>sequenceNumber</code> fields, in that order.</p>
 *
 * <p>Instances of this class are immutable.</p>
 */
class Mail
		implements Comparable<Mail>
{
	/** maximum length of the email text snippet */
	private static final int SNIPPET_MAX_LENGTH = 500;

	/** account to which this mail belongs */
	final Account account;

	/** mail sequence number */
	final int sequenceNumber;

	/** "from" addresses (senders) */
	final String from;

	/** "to" addresses (recipients) */
	final String to;

	/** email sent date */
	final Date date;

	/** email subject */
	final String subject;

	/** email text snippet */
	final String snippet;


	/**
	 * Constructor.
	 *
	 * @param account
	 *     account to which this mail belongs
	 * @param msg
	 *      Message object corresponding to this mail
	 * @param sequenceNumber
	 *      sequence number of this mail
	 * @throws javax.mail.MessagingException
	 *      if thrown by the specified Message object when accessing it
	 */
	Mail(
			final Account account,
			final Message msg,
			final int sequenceNumber)
			throws MessagingException
	{
		final StringBuilder sb = new StringBuilder();

		/* account to which this mail belongs */
		this.account = account;

		/* mail sequence number */
		this.sequenceNumber = sequenceNumber;

		/* "from" addresses (senders) */
		final Address[] fromAddresses = msg.getFrom();
		sb.setLength(0);

		if (fromAddresses != null)
		{
			for (int i = 0; i < fromAddresses.length; i++)
			{
				if (i > 0)
				{
					sb.append("; ");
				}

				sb.append(addressToString(fromAddresses[i]));
			}
		}

		from = sb.toString();

		/* "to" addresses (recipients) */
		final Address[] toAddresses = msg.getAllRecipients();
		sb.setLength(0);

		if (toAddresses != null)
		{
			for (int i = 0; i < toAddresses.length; i++)
			{
				if (i > 0)
				{
					sb.append("; ");
				}

				sb.append(addressToString(toAddresses[i]));
			}
		}

		to = sb.toString();

		/* email sent date */
		final Date msgDate = msg.getSentDate();
		date = (msgDate == null) ? new Date() : msgDate;

		/* email subject */
		final String msgSubject = msg.getSubject();
		subject = (msgSubject == null) ? "(no subject)" : msgSubject;

		/* email text snippet */
		if (msg instanceof MimeMessage)
		{
			snippet = getEmailTextSnippet((MimeMessage) msg);
		}
		else
		{
			snippet = "";
		}
	}


	/**
	 * Compare this mail to the specified mail.
	 * This comparison uses the <code>account</code> and <code>sequenceNumber</code> fields,
	 * in that order.
	 */
	public int compareTo(
			final Mail m)
	{
		final int i = account.compareTo(m.account);
		if (i != 0) return i;

		if (sequenceNumber < m.sequenceNumber) return -1;
		if (sequenceNumber > m.sequenceNumber) return 1;

		return 0;
	}


	/**
	 * Is this mail equal to the specified mail?
	 * This comparison uses the <code>account</code> and <code>sequenceNumber</code> fields.
	 */
	@Override
	public boolean equals(
			final Object o)
	{
		if (o instanceof Mail)
		{
			final Mail m = (Mail) o;

			return (account.equals(m.account) &&
					(sequenceNumber == m.sequenceNumber));
		}

		return false;
	}


	/**
	 * Generate a hashcode for this mail.
	 * This method uses the <code>account</code> and <code>sequenceNumber</code> fields.
	 */
	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 31 * hash + (this.account != null ? this.account.hashCode() : 0);
		hash = 31 * hash + this.sequenceNumber;
		return hash;
	}


	/**
	 * Return a string representation of this mail item.
	 */
	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder();

		sb.append("[FROM] ");
		sb.append(from);

		sb.append(" [TO] ");
		sb.append(to);

		sb.append(" [SUBJECT] ");
		sb.append(subject);

		sb.append(" [DATE] ");
		sb.append(date);

		sb.append(" [SNIPPET] ");
		sb.append(snippet);

		return sb.toString();
	}


	/**
	 * Return an email text snippet for a MIME message.
	 *
	 * @param mm
	 *      MIME message
	 * @return
	 *      text snippet
	 */
	private static String getEmailTextSnippet(
			final MimeMessage mm)
	{
		/* text snippet */
		final StringBuilder sb = new StringBuilder();

		/* stack of message parts to be processed */
		final Deque<Part> stack = new ArrayDeque<Part>();

		stack.push(mm);

		while (!stack.isEmpty())
		{
			/* process a message part */
			final Part p = stack.pop();

			try
			{
				if (p.isMimeType("text/plain"))
				{
					final Object o = p.getContent();

					if (o instanceof String)
					{
						sb.append(' ');
						sb.append((String) o);
					}
				}
				else if (p.isMimeType("text/html"))
				{
					final Object o = p.getContent();

					if (o instanceof String)
					{
						sb.append(' ');
						sb.append(HtmlManipulator.replaceHtmlEntities((String) o));
					}
				}
				else if (p.isMimeType("multipart/*"))
				{
					final Object o = p.getContent();

					if (o instanceof Multipart)
					{
						final Multipart mp = (Multipart) o;
						final int n = mp.getCount();

						for (int i = n - 1; i >= 0; i--)
						{
							stack.push(mp.getBodyPart(i));
						}
					}
				}
				else if (p.isMimeType("message/rfc822"))
				{
					final Object o = p.getContent();

					if (o instanceof Part)
					{
						stack.push((Part) o);
					}
				}
			}
			catch (Exception e)
			{
				/* ignore */
			}

			if (sb.length() >= SNIPPET_MAX_LENGTH)
			{
				break;
			}
		}

		return sb.toString()
				.replaceAll("(?s)<head.*</head>", "")  /* ignore HTML header */
				.replaceAll("(?s)<[^>]+>", "")         /* ignore HTML tags */
				.replaceAll("[" + Pattern.quote("~`!@#$%^&*()_-+={[}]|\\:;\"'<,>.?/") + "]{2,}", " ") /* ignore "lines" */
				.replaceAll("[\\s\\xA0]++", " ")      /* replace consecutive whitespace */
				.trim();
	}


	/**
	 * Return a suitable string representation of an address.
	 *
	 * @param a
	 *      address
	 * @return
	 *      string representation
	 */
	private static String addressToString(
			final Address a)
	{
		String s;

		if (a instanceof InternetAddress)
		{
			final InternetAddress ia = (InternetAddress) a;
			s = ia.getPersonal();

			if ((s == null) || s.isEmpty())
			{
				s = ia.getAddress();

				if ((s == null) || s.isEmpty())
				{
					s = a.toString();
				}
			}
		}
		else if (a instanceof NewsAddress)
		{
			final NewsAddress na = (NewsAddress) a;
			s = na.getNewsgroup();

			if ((s == null) || s.isEmpty())
			{
				s = na.getHost();

				if ((s == null) || s.isEmpty())
				{
					s = a.toString();
				}
			}
		}
		else
		{
			s = a.toString();
		}

		return s;
	}
}
