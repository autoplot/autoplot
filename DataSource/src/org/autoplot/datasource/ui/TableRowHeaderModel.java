/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource.ui;

/* * User: mkovalenko * Date: Oct 22, 2001 * Time: 6:33:56 PM * Describe file */ 
import javax.swing.*;

/*
 * Class JavaDoc
 */

public class TableRowHeaderModel extends AbstractListModel
{
	private JTable table;

	public TableRowHeaderModel(JTable table)
	{
		this.table = table;
	}

	public int getSize()
	{
		return table.getRowCount();
	}

	public Object getElementAt(int index)
	{
		return null;
	}
}
