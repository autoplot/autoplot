/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 *
 * @author jbf
 */
public class MyMouseListener extends MouseAdapter {

    JTree jtree;
    JDiskHogPanel panel;

    MyMouseListener(JTree jtree,JDiskHogPanel panel) {
        this.jtree = jtree;
        this.panel = panel;
    }

    TreePath context;

    @Override
    public void mousePressed(MouseEvent e) {

        if (e.isPopupTrigger()) {
            context = jtree.getPathForLocation(e.getX(), e.getY());
            jtree.getSelectionModel().addSelectionPath(context);
            if (context != null) {
                showPopup(e);
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            context = jtree.getPathForLocation(e.getX(), e.getY());
            jtree.getSelectionModel().addSelectionPath(context);
            if (context != null) {
                showPopup(e);
            }
        }
    }
    JPopupMenu popup;

    private synchronized void showPopup(MouseEvent e) {
        if (popup == null) {
            popup = new JPopupMenu();
            popup.add(new JMenuItem( panel.getDeleteAction(jtree) ));

            //popup.add(new JSeparator());
            popup.add(new JMenuItem( panel.getPlotAction(jtree) ));

            popup.add(new JMenuItem( panel.getCopyToAction(jtree) ));
        }
        popup.show(jtree, e.getX(), e.getY());
    }
}
