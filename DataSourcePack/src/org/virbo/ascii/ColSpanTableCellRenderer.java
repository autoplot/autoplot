/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.ascii;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * This allows colspan in a JTable.
 * @author jbf
 */
class ColSpanTableCellRenderer extends DefaultTableCellRenderer {

    private int tableWidth;
    private int x;
    private Color background;

    private boolean isColSpan;

    public interface ColSpanTableModel {
        boolean isColSpan( int row, int col );
    }
    

    public ColSpanTableCellRenderer() {
        background= super.getBackground();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        if ( ! isColSpan ) {
            this.x= 0;
            super.setBounds( x, y, width, height );
        } else {
            this.x= x;
            super.setBounds( 0, y, tableWidth, height );
        }
    }

    @Override
    public void paint(Graphics g) {
        if ( background==null ) background= super.getBackground();
        g.translate( -x, 0);
        if ( ! isColSpan ) {
            setBackground(background.brighter());
        } else {
            setBackground(background.darker());
        }
        super.paint(g);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        this.tableWidth= table.getWidth();
        this.isColSpan= ( (ColSpanTableModel) table.getModel() ).isColSpan(row, column);
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }



}
