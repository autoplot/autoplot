/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.ascii;

import javax.swing.*;
import java.awt.*;

/**
 * taken from http://www.jguru.com/faq/view.jsp?EID=87579 (see code towards the bottom, view source).
 * @author jbf
 */
public class Util
{
	protected Util()
	{
	}

	public static boolean isRowHeaderVisible(JTable table)
	{
		Container p = table.getParent();
		if (p instanceof JViewport)
		{
			Container gp = p.getParent();
			if (gp instanceof JScrollPane)
			{
				JScrollPane scrollPane = (JScrollPane) gp;
				JViewport rowHeaderViewPort = scrollPane.getRowHeader();
				if (rowHeaderViewPort != null)
					return rowHeaderViewPort.getView() != null;
			}
		}
		return false;
	}

	/**
	 * Creates row header for table with row number (starting with 1) displayed
	 */
	public static void removeRowHeader(JTable table)
	{
		Container p = table.getParent();
		if (p instanceof JViewport)
		{
			Container gp = p.getParent();
			if (gp instanceof JScrollPane)
			{
				JScrollPane scrollPane = (JScrollPane) gp;
				scrollPane.setRowHeader(null);
			}
		}
	}

	/**
	 * Creates row header for table with row number (starting with 1) displayed
	 */
	public static void setRowHeader(JTable table)
	{
		Container p = table.getParent();
		if (p instanceof JViewport)
		{
			Container gp = p.getParent();
			if (gp instanceof JScrollPane)
			{
				JScrollPane scrollPane = (JScrollPane) gp;
				scrollPane.setRowHeaderView(new TableRowHeader(table));
			}
		}
	}
}