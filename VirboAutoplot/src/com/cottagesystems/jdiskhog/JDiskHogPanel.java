/*
 * JDiskHogPanel.java
 *
 * Created on August 26, 2008, 4:35 PM
 */
package com.cottagesystems.jdiskhog;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 *
 * @author  jbf
 */
public class JDiskHogPanel extends javax.swing.JPanel {

    /** Creates new form JDiskHogPanel */
    public JDiskHogPanel() {
        initComponents();
        jTree1.addMouseListener(createMouseListener(jTree1));

    }

    private MouseListener createMouseListener(final JTree jtree) {
        return new MouseAdapter() {

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
            JPopupMenu popup;

            private void showPopup(MouseEvent e) {
                if (popup == null) {
                    popup = new JPopupMenu();
                    popup.add(new JMenuItem(new AbstractAction("Delete") {

                        public void actionPerformed(ActionEvent e) {
                            FSTreeModel model = (FSTreeModel) jtree.getModel();

                            TreePath[] paths = jtree.getSelectionPaths();

                            boolean okay = true;
                            IllegalArgumentException ex = null;

                            for (int i = 0; i < paths.length; i++) {
                                File f = model.getFile(paths[i]);

                                if (f.equals(model.root)) {
                                    continue;
                                }
                                if (f.isFile()) {
                                    okay = f.delete();
                                } else {
                                    try {
                                        okay = Util.deleteFileTree(f);
                                    } catch (IllegalArgumentException ex1) {
                                        ex = ex1;
                                        okay = false;
                                    }
                                }
                            }

                            if (!okay) {
                                JOptionPane.showConfirmDialog(jtree, ex.getLocalizedMessage(), "unable to delete", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
                            }

                            scan(model.root);

                        }
                    }));
                    
                    //popup.add(new JSeparator());
                    popup.add(new JMenuItem( new AbstractAction("Copy To...") {
                        public void actionPerformed(ActionEvent e) {
                            JFileChooser chooser = new JFileChooser();
                            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                            if (chooser.showOpenDialog(JDiskHogPanel.this) == JFileChooser.APPROVE_OPTION) {
                                File destdir= chooser.getSelectedFile();
                                
                                FSTreeModel model = (FSTreeModel) jtree.getModel();

                                TreePath[] paths = jtree.getSelectionPaths();

                                boolean okay = true;
                                IllegalArgumentException ex = null;

                                for (int i = 0; i < paths.length; i++) {
                                    File f = model.getFile(paths[i]);
                                    try {
                                        Util.fileCopy(f, destdir);
                                    } catch (FileNotFoundException ex1) {
                                        Logger.getLogger(JDiskHogPanel.class.getName()).log(Level.SEVERE, null, ex1);
                                    } catch (IOException ex1) {
                                        Logger.getLogger(JDiskHogPanel.class.getName()).log(Level.SEVERE, null, ex1);
                                    }
                                }

                            }

                        }
                    }));
                }
                popup.show(jtree, e.getX(), e.getY());
            }
        };
    }

    public void scan(File root) {
        DiskUsageModel dumodel = new DiskUsageModel();
        dumodel.search(root, 0, this.progressMonitorComponent1);
        FSTreeModel model = new FSTreeModel(dumodel, root);
        jTree1.setModel(model);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane2 = new javax.swing.JScrollPane();
        jTree1 = new javax.swing.JTree();
        progressMonitorComponent1 = new com.cottagesystems.jdiskhog.ProgressMonitorComponent();

        jScrollPane2.setViewportView(jTree1);

        progressMonitorComponent1.setToolTipText("progress monitor"); // NOI18N

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 402, Short.MAX_VALUE)
            .add(progressMonitorComponent1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 402, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 251, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(progressMonitorComponent1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 31, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JScrollPane jScrollPane2;
    public javax.swing.JTree jTree1;
    public com.cottagesystems.jdiskhog.ProgressMonitorComponent progressMonitorComponent1;
    // End of variables declaration//GEN-END:variables
}
