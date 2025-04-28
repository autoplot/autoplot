/*
 * JDiskHogPanel.java
 *
 * Created on August 26, 2008, 4:35 PM
 */
package com.cottagesystems.jdiskhog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.das2.components.DasProgressLabel;
import org.das2.components.DasProgressPanel;
import org.das2.util.FileUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.AutoplotUI;
import org.autoplot.datasource.AutoplotSettings;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.filesystem.GitCommand;
import org.das2.util.filesystem.GitHubFileObject;
import org.das2.util.filesystem.GitHubFileSystem;
import org.das2.util.filesystem.WebFileSystem;

/**
 * Tool for cleaning up files in a local filesystem.  This has minor customizations 
 * for Autoplot, like ro_cache.txt file handling.
 * @author  jbf
 */
public final class JDiskHogPanel extends javax.swing.JPanel {

    AutoplotUI app;

    TreeModel def= new DefaultTreeModel( new DefaultMutableTreeNode("moment...") );
    MouseListener l= null;

    boolean goPressed= false;

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jdiskhog");

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
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                FSTreeModel model = (FSTreeModel) jtree.getModel();

                TreePath[] paths = jtree.getSelectionPaths();
                if ( paths==null ) return;

                boolean okay = true;
                IllegalArgumentException ex = null;

                for (TreePath path : paths) {
                    File f = model.getFile(path);
                    if (f.equals(model.root)) {
                        continue;
                    }
                    if (f.isFile()) {
                        if ( f.exists() ) {
                            okay = f.delete();
                        }
                    } else {
                        try {
                            Set<String> exclude= new HashSet();
                            exclude.add("ro_cache.txt");
                            exclude.add("keychain.txt");
                            okay = FileUtil.deleteFileTree(f,exclude);
                        } catch (IllegalArgumentException ex1) {
                            ex = ex1;
                            okay = false;
                        }
                    }
                }

                if (!okay) {
                    assert ex!=null;
                    JOptionPane.showConfirmDialog(jtree, ex.getLocalizedMessage(), "unable to delete", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
                }

                scan(model.root);

            }
        };
    }

    Action getCopyClipboardAction(final JTree jtree) {
        return new AbstractAction("Copy to Filename to Clipboard") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                
                File f = getSelectedFile(jTree1);
                if ( f==null ) return;
                String sf = f.toString();
                String outsideName= outsideName( sf );     
                
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(  new StringSelection( sf ), new ClipboardOwner() {
                    @Override
                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                    }
                } );
            }

        };
    }

    Action getPlotAction(final JTree jtree) {
        return new AbstractAction("Plot") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
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
            for (String proto : protos) {
                if (outsideName.startsWith("/" + proto + "/")) {
                    outsideName = proto + "://" + outsideName.substring(proto.length() + 2);
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

    /**
     * attempt to plot the URI selected.
     * @return 
     */
    public boolean doPlotSelected() {
        File f = getSelectedFile(jTree1);
        if ( f==null ) return true;
        String sf = f.toString();
        String outsideName= outsideName( sf );
        if ( app!=null ) {
            if ( outsideName!=null ) {
                app.plotUri(outsideName);
            } else {
                app.plotUri(sf);
            }
        }
        return false;
    }
 
    /**
     * navigate through the cache looking for empty folders and removing them.
     * @param n the root folder.
     * @return true if successful.
     */
    Action getPruneTreeAction(final JTree jtree) {
        return new AbstractAction("Prune empty branches") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
            
                File local = getSelectedFile(jtree);
                if ( local==null ) {
                    return;
                }
        
                ProgressMonitor mon1= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(jtree), "Pruning Cache..." );
                mon1.started();
        
                List<String> problems= new ArrayList();
                boolean y= org.autoplot.Util.pruneFileTree( local, problems );
                mon1.finished();
                if ( y ) {
                    JOptionPane.showMessageDialog( jtree, "<html>Successful", "Prune fscache", JOptionPane.PLAIN_MESSAGE );
                } else {
                    StringBuilder msg= new StringBuilder( "Some problems occured while pruning cache:" );
                    for ( String s: problems ) {
                        msg.append( "<br>" ).append(s);
                    }
                    JOptionPane.showMessageDialog( jtree, "<html>"+msg+"</html>" );
                }
                FSTreeModel model = (FSTreeModel) jtree.getModel();
                scan( model.root );
            }
        };
    }
    
    /**
     * return the first selected file or null if nothing is selected.
     * @param jtree
     * @return 
     */
    private File getSelectedFile( JTree jtree ) {
        FSTreeModel model = (FSTreeModel) jtree.getModel();
        TreePath[] paths = jtree.getSelectionPaths();
        final File f;
        if ( paths!=null && paths.length==1 ) {
            f= model.getFile(paths[0]);
        } else {
            f= null;
        }
        return f;
    }
    
    static class CopyToAction extends AbstractAction {
        private final File f;
        private final JTree jtree;
        public CopyToAction( File f, JTree jtree ) {
            super( "Copy to..");
            this.f= f;
            this.jtree= jtree;
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            LoggerManager.logGuiEvent(e);
            if ( f==null ) {
                JOptionPane.showMessageDialog( jtree, "<html>Wait for scanning to complete.</html>" );
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            File p=  new File("foo").getAbsoluteFile().getParentFile();
            chooser.setCurrentDirectory( p ); //http://www.rgagnon.com/javadetails/java-0370.html
            chooser.setSelectedFile( new File(p,f.getName() ) );
            chooser.setAcceptAllFileFilterUsed(false);
            if (chooser.showSaveDialog(jtree) == JFileChooser.APPROVE_OPTION) {
                File destdir = chooser.getSelectedFile();

                FSTreeModel model = (FSTreeModel) jtree.getModel();

                TreePath[] paths = jtree.getSelectionPaths();
                if ( paths==null ) return;

                for (TreePath path : paths) {
                    File f1 = model.getFile(path);
                    try {
                        FileUtil.fileCopy(f1, destdir);
                    } catch (FileNotFoundException ex1) {
                        logger.log(Level.SEVERE, ex1.getMessage(), ex1);
                        JOptionPane.showMessageDialog(jtree, "File Not Found:\n" + ex1.getLocalizedMessage());
                    } catch (IOException ex1) {
                        logger.log(Level.SEVERE, ex1.getMessage(), ex1);
                        JOptionPane.showMessageDialog(jtree, "Error Occurred:\n" + ex1.getLocalizedMessage());
                    }
                }

            }

        }
    }

    Action getCopyToAction( final JTree jtree ) {
        CopyToAction a= new CopyToAction(getSelectedFile(jtree),jtree);
        return a;
    }
    
    private boolean writeROCacheLink( File src, File dest ) throws IOException {
        try (BufferedWriter write = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( new File( src, "ro_cache.txt" ) ), "UTF-8" ) )) {
            write.append( dest.toString() );
            write.append( "\n" );
        }
        return true;
    }
    
    Action getLocalPullAction( final File path) {
        return new AbstractAction("Pull Remote Changes") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                
                try {
                    GitCommand.GitResponse response= new GitCommand(path).pull();
                    if ( response.getExitCode()==0 ) {
                        JOptionPane.showMessageDialog( app, "git pull was successful.");
                    } else {
                        JOptionPane.showMessageDialog( app, "git pull was unsuccessful with exit code "+response.getExitCode() + ":"
                                + response.getErrorResponse() );
                        
                    }
                } catch (IOException | InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                
            }
        };
    }
    
    protected void maybeAddGitPullAction( JPopupMenu popup ) {
        FSTreeModel model = (FSTreeModel) jTree1.getModel();

        TreePath path = jTree1.getSelectionPath();
        if ( path==null ) return;

        File f = model.getFile(path);
        String outsideName= outsideName(f.toString());
        String[] nn= null;
        File localCache=null; // the local root if set already

        if ( outsideName!=null ) {
            try {
                FileSystem fs = FileSystem.create(outsideName);
                if ( fs instanceof GitHubFileSystem ) {
                    File localROCache= ((GitHubFileSystem)fs).getReadOnlyCache();
                    if ( localROCache!=null ) {
                        JMenuItem mi= new JMenuItem( getLocalPullAction(localROCache) );
                        mi.setToolTipText( "Pull changes from the remote repository to the local RO cache." );
                        popup.add(mi);       
                        return;
                    }
                }
            } catch ( FileSystemOfflineException | UnknownHostException | FileNotFoundException ex ) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        JMenuItem mi= new JMenuItem( getLocalPullAction(null) );
        mi.setEnabled(false);
        popup.add(mi);  // always add the option, even if it is disabled.
    }
                        
    Action getLocalROCacheAction(final JTree jtree) {
        return new AbstractAction("Link to Local Read-Only Cache...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
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
                    File localCache=null; // the local root if set already
                    
                    if ( outsideName!=null ) {
                        try {
                            FileSystem fs = FileSystem.create(outsideName);
                            nn= fs.listDirectory("/");
                            if ( fs instanceof WebFileSystem ) {
                                localCache= ((WebFileSystem)fs).getReadOnlyCache();
                            }
                        } catch ( IOException ex ) {
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                        }
                    }


                    JFileChooser choose= new JFileChooser();
                    if ( nn!=null && nn.length>1 ) {
                        for ( int i=0; i<nn.length; i++ ) if ( nn[i]==null ) nn[i]= "";
                        StringBuilder s= new StringBuilder( nn[0] );
                        for ( int i=1; i<6; i++ ) {
                            if ( nn.length>i ) s.append( "<br>").append( nn[i] );
                        }
                        final JLabel label= new JLabel("<html><u>Target should contain:</u><br>"+ s.toString() );
                        JPanel p= new JPanel();
                        p.setLayout( new BorderLayout() );
                        p.add( label, BorderLayout.NORTH );
                        choose.setAccessory( p );
                    }

                    choose.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
                    if ( localCache!=null ) {
                        choose.setSelectedFile( localCache );
                        choose.setCurrentDirectory( localCache );
                    }
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

    /**
     * scan the root recursively looking to get file usage.
     * @param root 
     */
    public synchronized void scan( final File root) {
        final DiskUsageModel dumodel = new DiskUsageModel();
        final DasProgressLabel monitor= new DasProgressLabel("Scanning disk usage");
        monitor.setLabelComponent(progressLabel);
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                dumodel.search(root, 0, monitor );
            }
        };
        new Thread( run, "diskUsage" ).start();
        
        final FSTreeModel model = new FSTreeModel(dumodel, root);
        if ( model.getComparator()==model.alphaComparator ) {
            sortCB.setSelectedIndex(1);
        }
        model.setHideListingFile(true);
        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run() {
                jTree1.setModel(model);
                if ( l==null ) {
                    l= createMouseListener(jTree1);
                    jTree1.addMouseListener(l);
                }
                progressLabel.setText("");
            }
        } );
    }

    public boolean isGoPressed() {
        return goPressed;
    }
    
//    public static void main( String[] args ) {
//        JDiskHogPanel p = new JDiskHogPanel(null);
//        p.scan( new File("/home/jbf/" ) );
//        JOptionPane.showMessageDialog( null, p );
//    }
    
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
        progressLabel = new javax.swing.JLabel();

        jScrollPane2.setViewportView(jTree1);

        jButton1.setText("Close");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        goButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/go.png"))); // NOI18N
        goButton.setText("Plot");
        goButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goButtonActionPerformed(evt);
            }
        });

        sortCB.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "File Size", "Alphabetical" }));
        sortCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortCBActionPerformed(evt);
            }
        });

        jLabel1.setText("Sort By:");

        progressLabel.setText(" ");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 688, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(sortCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(progressLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 295, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(4, 4, 4)
                .add(goButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 87, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 94, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
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
                    .add(sortCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(progressLabel)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        LoggerManager.logGuiEvent(evt);
        SwingUtilities.getWindowAncestor(this).setVisible(false);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void goButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        goPressed= true;
        SwingUtilities.getWindowAncestor(this).setVisible(false);
    }//GEN-LAST:event_goButtonActionPerformed

    private void sortCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortCBActionPerformed
        LoggerManager.logGuiEvent(evt);
        if ( !( jTree1.getModel() instanceof FSTreeModel ) ) return;
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
    public javax.swing.JLabel progressLabel;
    public javax.swing.JComboBox sortCB;
    // End of variables declaration//GEN-END:variables
}
