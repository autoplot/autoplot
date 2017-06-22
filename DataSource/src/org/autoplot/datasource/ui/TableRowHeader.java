/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package org.autoplot.datasource.ui;
        
/*
 * User: mkovalenko * Date: Oct 22, 2001 * Time: 5:17:14 PM * Describe file
 */
import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;

/*
 * Class JavaDoc
 */

public class TableRowHeader extends JList
{
	private JTable table;

	public TableRowHeader(JTable table)
	{
		super(new TableRowHeaderModel(table));
		this.table = table;
		setFixedCellHeight(table.getRowHeight());
		setFixedCellWidth(preferredHeaderWidth());
		setCellRenderer(new RowHeaderRenderer(table));
		setSelectionModel(table.getSelectionModel());
	}

	/**
	 * Returns the bounds of the specified range of items in <code>JList</code>
	 * coordinates. Returns <code>null</code> if index isn't valid.
	 *
	 * @param index0  the index of the first <code>JList</code> cell in the range
	 * @param index1  the index of the last <code>JList</code> cell in the range
	 * @return the bounds of the indexed cells in pixels
	 */
	public Rectangle getCellBounds(int index0, int index1)
	{
		Rectangle rect0 = table.getCellRect(index0, 0, true);
		Rectangle rect1 = table.getCellRect(index1, 0, true);
		int y, height;
		if (rect0.y < rect1.y)
		{
			y = rect0.y;
			height = rect1.y + rect1.height - y;
		}
		else
		{
			y = rect1.y;
			height = rect0.y + rect0.height - y;
		}
		return new Rectangle(0, y, getFixedCellWidth(), height);
	}

	// assume that row header width should be big enough to display row number Integer.MAX_VALUE completely
	private int preferredHeaderWidth()
	{
		JLabel longestRowLabel = new JLabel("65356");
		JTableHeader header = table.getTableHeader();
		longestRowLabel.setBorder(header.getBorder());//UIManager.getBorder("TableHeader.cellBorder"));
		longestRowLabel.setHorizontalAlignment(JLabel.CENTER);
		longestRowLabel.setFont(header.getFont());
		return longestRowLabel.getPreferredSize().width;
	}
}
