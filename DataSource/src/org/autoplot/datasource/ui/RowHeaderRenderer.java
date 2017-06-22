/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import java.awt.*;

/*
 * Class JavaDoc
 */

public class RowHeaderRenderer extends JLabel implements ListCellRenderer
{
	private JTable table;
	private Border selectedBorder;
	private Border normalBorder;
	private Font selectedFont;
	private Font normalFont;

	RowHeaderRenderer(JTable table)
	{
		this.table = table;
		normalBorder = UIManager.getBorder("TableHeader.cellBorder");
		selectedBorder = BorderFactory.createRaisedBevelBorder();
		final JTableHeader header = table.getTableHeader();
		normalFont = header.getFont();
		selectedFont = normalFont.deriveFont(normalFont.getStyle() | Font.BOLD);

		setForeground(header.getForeground());
		setBackground(header.getBackground());
		setOpaque(true);
		setHorizontalAlignment(CENTER);
	}

	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
	{
		if (table.getSelectionModel().isSelectedIndex(index))
		{
			setFont(selectedFont);
			setBorder(selectedBorder);
		}
		else
		{
			setFont(normalFont);
			setBorder(normalBorder);
		}
		String label = String.valueOf(index + 1);
		setText(label);
		return this;
	}
}