/*
 * JDiskHogPanel.java
 *
 * Created on August 26, 2008, 4:35 PM
 */
package com.cottagesystems.jdiskhog;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.datasource.AutoplotSettings;

/**
 *
 * @author  jbf
 */
public class JDiskHogPanel extends javax.swing.JPanel {

    AutoplotUI app;

    TreeModel def= new DefaultTreeModel( new DefaultMutableTreeNode("moment...") );
    MouseListener l= null;

    boolean goPressed= false;

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    /** Creates new form JDiskHogPanel */
    public JDiskHogPanel(AutoplotUI model) {
        this.app = model;
        initComponents();
        jTree1.setModel( def );

    }

    private MouseListener createMouseListener(final JTree jtree) {
        return new MyMouseListener(jtree, this);
    }

    Action getDeleteAction(final JTree jtree) {
        return new AbstractAction("Delete") {

            public void actionPerformed(ActionEvent e) {
                FSTreeModel model = (FSTreeModel) jtree.getModel();

                TreePath[] paths = jtree.getSelectionPaths();
                if ( paths==null ) return;

                boolean okay = true;
                IllegalArgumentException ex = null;

                for (int i = 0; i < paths.length; i++) {
                    File f = model.getFile(paths[i]);

                    if (f.equals(model.root)) {
                        continue;
                    }
                    if (f.isFile()) {
                        if ( f.exists() ) {
                            okay = f.delete();
                        }
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
        };
    }

    Action getPlotAction(final JTree jtree) {
        return new AbstractAction("Plot") {

            public void actionPerformed(ActionEvent e) {

                if ( doPlotSelected() ) return;

                Component p= JDiskHogPanel.this.getTopLevelAncestor();
                if ( p instanceof JDialog ) {
                    p.setVisible(false); // it's confusing to have this modal dialog still going after this operation.
                }
            }

        };
    }

    /**
     * return null or the url of the folder.
     * @param sf
     * @return
     */
    private String outsideName( String sf ) {
        String cache = AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE);
        String outsideName = sf.substring(cache.length());
        boolean acceptOutside= false;
        if (sf.startsWith(cache)) {
            String[] protos = new String[]{"ftp", "http", "https"};
            for (int i = 0; i < protos.length; i++) {
                if (outsideName.startsWith("/" + protos[i] + "/")) {
                    outsideName = protos[i] + "://" + outsideName.substring(protos[i].length() + 2);
                    acceptOutside = true;
                }
            }
        }
        if ( acceptOutside ) {
            return outsideName;
        } else {
            return null;
        }
    }

    public boolean doPlotSelected() {
        FSTreeModel model = (FSTreeModel) jTree1.getModel();
        TreePath[] paths = jTree1.getSelectionPaths();
        if ( paths==null || paths.length == 0) {
            return true;
        }
        File f = model.getFile(paths[0]);
        String sf = f.toString();
        String outsideName= outsideName( sf );
        if ( outsideName!=null ) {
            app.plotUri(outsideName);
        } else {
            app.plotUri(sf);
        }
        return false;
    }

    Action getCopyToAction(final JTree jtree) {
        FSTreeModel model = (FSTreeModel) jtree.getModel();
        TreePath[] paths = jtree.getSelectionPaths();
        final File f;
        if ( paths!=null && paths.length==1 ) {
            f= model.getFile(paths[0]);
        } else {
            f= null;
        }
        return new AbstractAction("Copy To...") {

            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                File p=  new File("foo").getAbsoluteFile().getParentFile();
                chooser.setCurrentDirectory( p ); //http://www.rgagnon.com/javadetails/java-0370.html
                if ( f!=null ) chooser.setSelectedFile( new File(p,f.getName() ) );
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showSaveDialog(jtree) == JFileChooser.APPROVE_OPTION) {
                    File destdir = chooser.getSelectedFile();
                    
                    FSTreeModel model = (FSTreeModel) jtree.getModel();

                    TreePath[] paths = jtree.getSelectionPaths();
                    if ( paths==null ) return;

                    for (int i = 0; i < paths.length; i++) {
                        File f = model.getFile(paths[i]);
                        try {
                            Util.fileCopy(f, destdir);
                        } catch (FileNotFoundException ex1) {
                            logger.log(Level.SEVERE, null, ex1);
                            JOptionPane.showMessageDialog(jtree, "File Not Found:\n" + ex1.getLocalizedMessage());
                        } catch (IOException ex1) {
                            logger.log(Level.SEVERE, null, ex1);
                            JOptionPane.showMessageDialog(jtree, "Error Occurred:\n" + ex1.getLocalizedMessage());
                        }
                    }

                }

            }
        };
    }

    private boolean writeROCacheLink( File src, File dest ) throws IOException {
        BufferedWriter write= new BufferedWriter( new FileWriter( new File( src, "ro_cache.txt" ) ) );
        write.append( dest.toString() );
        write.append( "\n" );
        write.close();
        return true;
    }
    
    Action getLocalROCacheAction(final JTree jtree) {
        return new AbstractAction("Link to Local Read-Only Cache...") {

            public void actionPerformed(ActionEvent e) {
                FSTreeModel model = (FSTreeModel) jtree.getModel();

                TreePath path = jtree.getSelectionPath();
                if ( path==null ) return;

                File f = model.getFile(path);


                if (f.isFile()) {
                    JOptionPane.showConfirmDialog(jtree, "Folder must be selected, not a file", "Link to Local R/O Cache",  JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE );
                } else {
// see http://stackoverflow.com/questions/1356273/jfilechooser-for-directories-on-the-mac-how-to-make-it-not-suck
//    JFrame frame = new JFrame();
//    System.setProperty("apple.awt.fileDialogForDirectories", "true");
//    FileDialog d = new FileDialog(frame);
//    d.setVisible(true);

                    String outsideName= outsideName(f.toString());
                    String[] nn= null;
                    if ( outsideName!=null ) {
                        try {
                            FileSystem fs = FileSystem.create(outsideName);
                            nn= fs.listDirectory("/");

                        } catch ( IOException ex ) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }


                    JFileChooser choose= new JFileChooser();
                    if ( nn!=null && nn.length>1 ) {
                        for ( int i=0; i<nn.length; i++ ) if ( nn[i]==null ) nn[i]= "";
                        StringBuilder s= new StringBuilder( nn[0] );
                        for ( int i=1; i<6; i++ ) {
                            if ( nn.length>i ) s.append( "<br>").append( nn[i] );
                        }
                        final JLabel label= new JLabel("<html>Target should contain the files:<br>"+ s.toString() );
                        choose.setAccessory( label );
                    }

                    choose.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
                    
                    if ( choose.showOpenDialog(jtree)==JFileChooser.APPROVE_OPTION ) {
                        try {
                            File ff= choose.getSelectedFile();
                            writeROCacheLink( f, ff );
                            JOptionPane.showConfirmDialog(jtree, "<html>Wrote link file in "+ f, "Link to Local R/O Cache",  JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE );
                        } catch ( IOException ex2 ) {
                            JOptionPane.showConfirmDialog(jtree, "<html>Unable to write link file in "+ f +"<br>"+ex2.toString(), "Link to Local R/O Cache",  JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE );
                        }
                    }

                }

            }
        };
    }


    public void scan(File root) {
        DiskUsageModel dumodel = new DiskUsageModel();
        dumodel.search(root, 0, new NullProgressMonitor());
        final FSTreeModel model = new FSTreeModel(dumodel, root);
        model.setHideListingFile(true);
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                jTree1.setModel(model);
                if ( l==null ) {
                    l= createMouseListener(jTree1);
                    jTree1.addMouseListener(l);
                }
            }
        } );
    }

    public boolean isGoPressed() {
        return goPressed;
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
        jButton1 = new javax.swing.JButton();
        goButton = new javax.swing.JButton();
        sortCB = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();

        jScrollPane2.setViewportView(jTree1);

        jButton1.setText("Ok");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        goButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/autoplot/go.png"))); // NOI18N
        goButton.setText("Plot");
        goButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goButtonActionPerformed(evt);
            }
        });

        sortCB.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "File Size", "Alpha" }));
        sortCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortCBActionPerformed(evt);
            }
        });

        jLabel1.setText("Sort By:");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 688, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(sortCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 374, Short.MAX_VALUE)
                .add(goButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 79, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 52, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 293, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jButton1)
                    .add(goButton)
                    .add(jLabel1)
                    .add(sortCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        SwingUtilities.getWindowAncestor(this).setVisible(false);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void goButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goButtonActionPerformed
        goPressed= true;
        SwingUtilities.getWindowAncestor(this).setVisible(false);
    }//GEN-LAST:event_goButtonActionPerformed

    private void sortCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortCBActionPerformed
        FSTreeModel model= ((FSTreeModel)jTree1.getModel());
        if ( sortCB.getSelectedIndex()==0 ) {
            model.setComparator(model.fileSizeComparator);
        } else if (  sortCB.getSelectedIndex()==1 ) {
            model.setComparator(model.alphaComparator );
        }

    }//GEN-LAST:event_sortCBActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JButton goButton;
    public javax.swing.JButton jButton1;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JScrollPane jScrollPane2;
    public javax.swing.JTree jTree1;
    public javax.swing.JComboBox sortCB;
    // End of variables declaration//GEN-END:variables
}
