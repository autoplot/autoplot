/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 * Provide popup menu for JDiskHogPanel.
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

    private synchronized void showPopup(MouseEvent e) {
        JPopupMenu popup;
        JMenuItem mi;
        popup = new JPopupMenu();
        mi= new JMenuItem( panel.getDeleteAction(jtree) );
        mi.setToolTipText( "Delete files or folders from the local cache" );
        popup.add(mi);

        mi= new JMenuItem( panel.getPruneTreeAction(jtree) );
        mi.setToolTipText( "Delete empty folders from the local cache" );
        popup.add(mi);

        mi= new JMenuItem( panel.getCopyToAction(jtree) );
        mi.setToolTipText( "Make a copy of files or folders to location outside of file cache." );
        popup.add(mi);

        mi= new JMenuItem( panel.getCopyClipboardAction(jtree) );
        mi.setToolTipText( "Copy the name to the system clipboard." );
        popup.add(mi);

        mi= new JMenuItem( panel.getLocalROCacheAction(jtree) );
        mi.setToolTipText( "Specify a local copy of the remote files, and use files from here before downloading." );
        popup.add(mi);

        panel.maybeAddGitPullAction( popup );
        popup.show(jtree, e.getX(), e.getY());
    }
}
