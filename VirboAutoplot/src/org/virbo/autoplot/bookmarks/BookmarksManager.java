/*
 * BookmarksManager.java
 *
 * Created on October 1, 2008, 7:53 AM
 */
package org.virbo.autoplot.bookmarks;

import java.awt.Component;
import java.awt.Container;
import java.awt.HeadlessException;
import org.virbo.autoplot.bookmarks.Bookmark.Folder;
import org.virbo.datasource.AutoplotSettings;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import org.virbo.autoplot.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.components.DasProgressPanel;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.scriptconsole.GuiExceptionHandler;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Object for managing the user's bookmarks.  This object sits quietly beside the Autoplot
 * UI, becoming visible when the user asks to manage bookmarks.  This also populates the Bookmarks
 * submenu.
 * @author  jbf
 */
public class BookmarksManager extends javax.swing.JDialog {

    private final static Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.bookmarks");

    /**
     * return the index of the node in the tree, by comparing at toString of each node.
     * @param tree
     * @param root
     * @param find
     * @return
     */
    private int indexOfChild( TreeModel tree, Object root, Object find ) {
        for ( int i=0; i<tree.getChildCount(root); i++ ) {
            Object tt= tree.getChild(root, i);
            if ( tt.toString().equals(find.toString()) ) return i;
        }
        return -1;
    }

    /**
     * returns the TreePath in the new tree, or null if it cannot be identified.
     * @param mod
     * @param foriegn
     * @return
     */
    private TreePath moveTreePath( TreeModel mod, TreePath foriegn ) {
        Object parent= mod.getRoot();
        Object[] path= new Object[foriegn.getPathCount()];
        path[0]= parent;
        for ( int i=1; i<foriegn.getPathCount(); i++ ) {
            int j= indexOfChild( mod, parent, foriegn.getPathComponent(i) );
            if ( j>-1 ) {
                parent= mod.getChild(parent, j);
                path[i]= parent;
            } else {
                Object[] parentPath= new Object[i];
                System.arraycopy(path, 0, parentPath, 0, i);
                return new TreePath(parentPath);
            }
        }
        return new TreePath(path);
    }

    /** Creates new form BookmarksManager */
    public BookmarksManager(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        this.model = new BookmarksManagerModel();
        this.jTree1.setModel(model.getTreeModel());
        this.jTree1.addMouseListener( createContextMenuMouseListener() );
        /*this.jTree1.setCellRenderer( new TreeCellRenderer() {
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Object o= ((DefaultMutableTreeNode)value).getUserObject();
        Icon icon;
        if ( o instanceof Bookmark.Folder ) {
        icon=  expanded ?  UIManager.getIcon( "Tree.expandedIcon") : UIManager.getIcon( "Tree.closedIcon");
        } else if ( o instanceof Bookmark.Item ) {
        icon= null;
        }
        return new JLabel( String.valueOf(value), icon, JLabel.LEFT );
        }
        });*/

        model.addPropertyChangeListener(BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Runnable run= new Runnable() {
                    public void run() {
                        TreeModel mod = model.getTreeModel();
                        TreePath tp= jTree1.getSelectionPath();
                        jTree1.setModel(mod);
                        if ( tp!=null ) {
                            tp= moveTreePath( mod, tp );
                            if ( tp!=null ) {
                                jTree1.setSelectionPath(tp);
                                if ( jTree1.getModel().isLeaf(tp.getLastPathComponent()) ) tp= tp.getParentPath();
                                jTree1.expandPath(tp);
                                jTree1.scrollPathToVisible(tp);
                            }
                        }
                    }
                };
                if ( SwingUtilities.isEventDispatchThread() ) {
                    run.run();
                } else {
                    SwingUtilities.invokeLater(run);
                }
            }
        });

        BookmarksManagerTransferrable trans = new BookmarksManagerTransferrable(model, jTree1);

        DragSource dragSource = DragSource.getDefaultDragSource();
        DropTarget dropTarget = new DropTarget();
        try {
            dropTarget.addDropTargetListener(trans.createDropTargetListener());
        } catch (TooManyListenersException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        jTree1.setDropTarget(dropTarget);
        dragSource.createDefaultDragGestureRecognizer(jTree1, DnDConstants.ACTION_COPY_OR_MOVE, trans.createDragGestureListener());
    }
    
    BookmarksManagerModel model;
    Bookmark dirtyBookmark;

    JPopupMenu contextMenu= createContextMenu();

    /**
     * true indicates the menu managed by the bookmarks manager is dirty and needs to be updated.
     */
    private boolean menuIsDirty= false;
    private JMenu dirtyMenu=null;
    private DataSetSelector dirtySelector=null;

    public BookmarksManagerModel getModel() {
        return model;
    }

    /**
     * DataSetSelector to send URIs to.
     */
    DataSetSelector sel;

    /**
     * return the remoteUrl containing this bookmark, or an empty string.
     * @param b the target bookmark.  This can be null.
     * @param treeModel the model containing the bookmark
     * @param ppath the path to the bookmark.
     * @return the first remote URL containing the bookmark, or an empty string if it is local.
     */
    protected static String maybeGetRemoteBookmarkUrl(Bookmark b, BookmarksManagerModel model, TreeModel treeModel, TreePath ppath ) {
        // if it is a child of a remote bookmark, make sure it's not editable.
        String remoteUrl= "";
        if ( ppath==null ) return remoteUrl;
        if ( b!=null && b instanceof Bookmark.Item ) ppath= ppath.getParentPath();
        while ( ppath.getPathCount()>1 ) {
            Bookmark.Folder f= (Folder) model.getSelectedBookmark(treeModel,ppath);
            if ( f.remoteUrl!=null && !f.remoteUrl.equals("") ) {
                remoteUrl= f.remoteUrl;
                break;
            }
            ppath= ppath.getParentPath();
        }
        return remoteUrl;
    }

    /**
     * remove the bookmarks from the list that come from a remote bookmarks file.  For example, these cannot be individually deleted.
     * The root node of a bookmarks file is left in there, so that it may be used to delete the folder.
     * @param bs
     * @param tmodel
     * @param selectionPaths selection, or null if there was no selection.
     * @return the remaining remote bookmarks.
     */
    private List<Bookmark> removeRemoteBookmarks(List<Bookmark> bs, TreeModel tmodel, TreePath[] selectionPaths) {
        List<Bookmark> result= new ArrayList();
        if ( selectionPaths==null ) return result;
        for ( int i=0; i<bs.size(); i++ ) {
            Bookmark bs1= bs.get(i);
            Bookmark parent= bs1.getParent();
            TreePath parentPath= selectionPaths[i].getParentPath();
            //boolean isaRemoteBookmark= (bs1 instanceof Bookmark.Folder && ((Bookmark.Folder)bs1).getRemoteUrl()!=null );
            boolean isNotPartOfRemoteBookmark= "".equals( maybeGetRemoteBookmarkUrl( parent, model, tmodel, parentPath ) );
            if ( isNotPartOfRemoteBookmark ) {
                result.add(bs.get(i));
            }
        }
        return result;
    }

//
//    /**
//     * see also maybeGetRemoteBookmarkUrl( b, null, null)
//     * @param b
//     * @return
//     */
//    private boolean isBookmarkRemote( Bookmark b ) {
//        while ( b!=null ) {
//            if ( b instanceof Bookmark.Folder ) {
//                Bookmark.Folder bf= (Bookmark.Folder)b;
//                if ( bf.remoteUrl!=null && bf.remoteUrl.length()>0 ) {
//                    return true;
//                } else {
//                    b= b.getParent();
//                }
//            } else {
//                b= b.getParent();
//            }
//        }
//        return false;
//    }

    /**
     * present a GUI offering to delete the set of bookmarks.
     * @param bs
     * @throws HeadlessException
     */
    private void maybeDeleteBookmarks(List<Bookmark> bs) throws HeadlessException {
        boolean confirm= false;
        if (bs.size() > 1 && JOptionPane.showConfirmDialog(this, "Delete " + bs.size() + " bookmarks?", "Delete Bookmarks", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            confirm = true;
        }
        if (confirm) {
            model.removeBookmarks(bs);
        } else {
            for (Bookmark b : bs) {
//                if ( isBookmarkRemote(b) ) {
//                    int i= JOptionPane.showConfirmDialog(this, "Bookmark is part of a remote bookmark folder", "Delete Bookmarks Folder", JOptionPane.OK_OPTION );
//                    return;
//                }
                if (b instanceof Bookmark.Folder) {
                    if (confirm || JOptionPane.showConfirmDialog(this, "Delete all bookmarks and folder?", "Delete Bookmarks Folder", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                        model.removeBookmark(b);
                    }
                } else {
                    model.removeBookmark(b);
                }
            }
        }
    }

    /**
     * show a message to the user.  Copied from ApplicationModel.
     * @param message
     * @param title
     * @param messageType JOptionPane.WARNING_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.PLAIN_MESSAGE,
     */
    private void showMessage( String message, String title, int messageType ) {
        if (  ! "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false") ) ) {
            Component p= SwingUtilities.getRoot(this);
            if ( p==null ) {
                if ( messageType==JOptionPane.WARNING_MESSAGE ) {
                    logger.log( Level.WARNING, "{0}: {1}", new Object[]{title, message});
                } else if ( messageType==JOptionPane.INFORMATION_MESSAGE ) {
                    logger.log( Level.INFO, "{0}: {1}", new Object[]{title, message});
                } else {
                    logger.log( Level.FINE, "{0}: {1}", new Object[]{title, message});
                }
            } else {
                JOptionPane.showMessageDialog( p, message, title, messageType );
            }
        } else {
            if ( messageType==JOptionPane.WARNING_MESSAGE ) {
                logger.log( Level.WARNING, "{0}: {1}", new Object[]{title, message});
            } else if ( messageType==JOptionPane.INFORMATION_MESSAGE ) {
                logger.log( Level.INFO, "{0}: {1}", new Object[]{title, message});
            } else {
                logger.log( Level.FINE, "{0}: {1}", new Object[]{title, message});
            }
        }
    }


//    /*private void addIcon() {
//        Runnable run = new Runnable() {
//
//            public void run() {
//                Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
//                ImageIcon icon = AutoplotUtil.createIcon(null, ((Bookmark.Item) b).getUri());
//                b.setIcon(icon);
//                //iconButton.setIcon(icon);
//                model.fireBookmarkChange(b);
//            }
//        };
//        new Thread(run).start();
//    }*/

    public void setAddBookmark( Bookmark b ) {
        TreePath tp= model.getPathFor( b, jTree1.getModel(), new TreePath(jTree1.getModel().getRoot()) );
        jTree1.setSelectionPath(tp);
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                titleTextField.requestFocusInWindow();
                titleTextField.selectAll();
            }
        } );
    }
    
    /**
     * set if the play button was used to excuse the GUI.  I was thinking it would be useful to allow the manager to be used to select
     * data sources, since it shows the descriptions, but I need to focus on getting this cleaned up first.  I'm leaving this as
     * a reminder.  
     */
    private boolean doPlay= false;

    public boolean isPlay() {
        return doPlay;
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTree1 = new javax.swing.JTree();
        dismissButton = new javax.swing.JButton();
        URILabel = new javax.swing.JLabel();
        URLTextField = new javax.swing.JTextField();
        titleLabel = new javax.swing.JLabel();
        titleTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        descriptionTextField = new javax.swing.JTextField();
        editDescriptionButton = new javax.swing.JButton();
        plotButton = new javax.swing.JButton();
        plotBelowButton = new javax.swing.JButton();
        overplotButton = new javax.swing.JButton();
        viewDetailsButton = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        importMenuItem = new javax.swing.JMenuItem();
        importUrlMenuItem = new javax.swing.JMenuItem();
        reloadMenuItem = new javax.swing.JMenuItem();
        resetToDefaultMenuItem = new javax.swing.JMenuItem();
        mergeInDefaultMenuItem = new javax.swing.JMenuItem();
        exportMenuItem = new javax.swing.JMenuItem();
        closeMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        newFolderMenuItem = new javax.swing.JMenuItem();
        addItemMenuItem = new javax.swing.JMenuItem();
        deleteMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jTree1.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
                jTree1ValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(jTree1);

        dismissButton.setText("OK");
        dismissButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dismissButtonActionPerformed(evt);
            }
        });

        URILabel.setText("URI:");
        URILabel.setToolTipText("Location of the data (often the URL), or remote folder location");

        URLTextField.setToolTipText("");
        URLTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                URLTextFieldFocusLost(evt);
            }
        });
        URLTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                URLTextFieldKeyTyped(evt);
            }
        });

        titleLabel.setText("Title:");
        titleLabel.setToolTipText("Title for the URI");

        titleTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                titleTextFieldActionPerformed(evt);
            }
        });
        titleTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                titleTextFieldFocusLost(evt);
            }
        });
        titleTextField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                titleTextFieldPropertyChange(evt);
            }
        });
        titleTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                titleTextFieldKeyTyped(evt);
            }
        });

        jLabel1.setText("Bookmarks Manager");

        jLabel4.setText("Description:");
        jLabel4.setToolTipText("Up to a short paragraph describing the data");

        descriptionTextField.setToolTipText("Up to a short paragraph describing the data");
        descriptionTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                descriptionTextFieldFocusLost(evt);
            }
        });
        descriptionTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                descriptionTextFieldKeyTyped(evt);
            }
        });

        editDescriptionButton.setText("Edit");
        editDescriptionButton.setToolTipText("Edit/View description");
        editDescriptionButton.setEnabled(false);
        editDescriptionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editDescriptionButtonActionPerformed(evt);
            }
        });

        plotButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/autoplot/go.png"))); // NOI18N
        plotButton.setText("Plot");
        plotButton.setToolTipText("Plot the URI in the current focus position");
        plotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotButtonActionPerformed(evt);
            }
        });

        plotBelowButton.setText("Plot Below");
        plotBelowButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotBelowButtonActionPerformed(evt);
            }
        });

        overplotButton.setText("Overplot");
        overplotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overplotButtonActionPerformed(evt);
            }
        });

        viewDetailsButton.setText("Detailed Description");
        viewDetailsButton.setToolTipText("View description URL in browser");
        viewDetailsButton.setEnabled(false);
        viewDetailsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewDetailsButtonActionPerformed(evt);
            }
        });

        jMenu1.setText("File");

        importMenuItem.setText("Import...");
        importMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(importMenuItem);

        importUrlMenuItem.setText("Import From Web...");
        importUrlMenuItem.setToolTipText("");
        importUrlMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importUrlMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(importUrlMenuItem);

        reloadMenuItem.setText("Reload Bookmarks");
        reloadMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(reloadMenuItem);

        resetToDefaultMenuItem.setText("Reset to Default");
        resetToDefaultMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetToDefaultMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(resetToDefaultMenuItem);

        mergeInDefaultMenuItem.setText("Merge in Default");
        mergeInDefaultMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mergeInDefaultMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(mergeInDefaultMenuItem);

        exportMenuItem.setText("Export...");
        exportMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(exportMenuItem);

        closeMenuItem.setText("Close");
        closeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(closeMenuItem);

        jMenuBar1.add(jMenu1);

        editMenu.setText("Edit");

        newFolderMenuItem.setAction(newFolderAction());
        newFolderMenuItem.setText("New Folder...");
        editMenu.add(newFolderMenuItem);

        addItemMenuItem.setAction(addItemAction());
        addItemMenuItem.setText("New Bookmark...");
        editMenu.add(addItemMenuItem);

        deleteMenuItem.setText("Delete Bookmark");
        deleteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(deleteMenuItem);

        jMenuBar1.add(editMenu);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 681, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jLabel1)
                    .add(layout.createSequentialGroup()
                        .add(overplotButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 83, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(plotBelowButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 88, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(plotButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 75, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dismissButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 56, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(URILabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(URLTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 644, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .add(jLabel4)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(descriptionTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(editDescriptionButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 71, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(titleLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 475, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(viewDetailsButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 158, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {overplotButton, plotBelowButton, plotButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 295, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(titleLabel)
                    .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(viewDetailsButton))
                .add(7, 7, 7)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(descriptionTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(editDescriptionButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(URILabel)
                    .add(URLTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(dismissButton)
                    .add(plotButton)
                    .add(plotBelowButton)
                    .add(overplotButton))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {URLTextField, titleTextField}, org.jdesktop.layout.GroupLayout.VERTICAL);

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void dismissButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dismissButtonActionPerformed
    doPlay= false;
    if ( dirtyBookmark!=null ) {
        dirtyBookmark.setTitle(titleTextField.getText());
        if ( dirtyBookmark instanceof Bookmark.Item ) {
            ((Bookmark.Item)dirtyBookmark).setUri(URLTextField.getText());
        }
        dirtyBookmark.setDescription( descriptionTextField.getText() );
        model.fireBookmarkChange(dirtyBookmark);
        dirtyBookmark=null;
    }
    this.dispose();
    if ( menuIsDirty ) {
        updateBookmarks( dirtyMenu, dirtySelector );
    }
}//GEN-LAST:event_dismissButtonActionPerformed

private void URLTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_URLTextFieldFocusLost
    // it's been marked as dirty, so we don't need to do anything
}//GEN-LAST:event_URLTextFieldFocusLost

private void titleTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_titleTextFieldFocusLost
    // it's been marked as dirty, so we don't need to do anything
}//GEN-LAST:event_titleTextFieldFocusLost

private void jTree1ValueChanged(javax.swing.event.TreeSelectionEvent evt) {//GEN-FIRST:event_jTree1ValueChanged
    if ( dirtyBookmark!=null ) {
        dirtyBookmark.setTitle(titleTextField.getText());
        if ( dirtyBookmark instanceof Bookmark.Item ) {
            ((Bookmark.Item)dirtyBookmark).setUri(URLTextField.getText());
        }
        dirtyBookmark.setDescription( descriptionTextField.getText() );
        model.fireBookmarkChange(dirtyBookmark);
        dirtyBookmark=null;
    }
    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if (b != null) {
        titleTextField.setText(b.getTitle());
        /*if (iconButton != null) {
            iconButton.setText("");
            iconButton.setIcon(b.getIcon());
        } else {
            iconButton.setIcon(null);
            iconButton.setText("(no icon)");
        }*/
        descriptionTextField.setText( b.getDescription() );
        URL descriptionUrl= getDescriptionUrl(b);
        viewDetailsButton.setEnabled( descriptionUrl!=null );
        if ( descriptionUrl==null ) {
            viewDetailsButton.setToolTipText("");
        } else {
            viewDetailsButton.setToolTipText("View "+descriptionUrl);
        }

        URLTextField.setEditable( b instanceof Bookmark.Item );
        int status=0;
        String err="";
        if (b instanceof Bookmark.Item) {
            URLTextField.setText(((Bookmark.Item) b).getUri());
            URILabel.setText("URI:");
        } else {
            if ( b instanceof Bookmark.Folder && ((Bookmark.Folder)b).getRemoteUrl()!=null ) {
                String url= ((Bookmark.Folder)b).getRemoteUrl();
                status= ((Bookmark.Folder)b).getRemoteStatus();
                if ( status==Bookmark.Folder.REMOTE_STATUS_UNSUCCESSFUL ) err= "<br>** Unable to connect to remote URL **";
                URLTextField.setText(url);
                URILabel.setText("URL:");
            } else {
                URLTextField.setText("");
                URILabel.setText("URI:");
            }
        }
        // if it is a child of a remote bookmark, make sure it's not editable.
        String remoteUrl= "";
        TreePath ppath= jTree1.getSelectionPath();
        if ( ppath!=null ) {
            remoteUrl= maybeGetRemoteBookmarkUrl( b, model, jTree1.getModel(), ppath);
            URLTextField.setEditable(remoteUrl.length()==0);
            if ( remoteUrl.length()==0 ) {
                URLTextField.setToolTipText("Location of the remote folder"+err);
            } else {
                URLTextField.setToolTipText("Location of the data (often the URL)");
            }
        } else {
            URLTextField.setEditable(false);
        }

        if ( remoteUrl.length()==0 ) {
            titleLabel.setText("Title:");
            titleLabel.setToolTipText("Title for the URI");
        } else {
            titleLabel.setText("[Title]:");
            titleLabel.setToolTipText("<html>Title for the URI.<br>This bookmark is part of a set of remote bookmarks from<br>"
                    + remoteUrl +
                    "<br> and cannot be edited." + err );
        }
        descriptionTextField.setEditable(remoteUrl.length()==0);
        editDescriptionButton.setEnabled( true );
        editDescriptionButton.setText( remoteUrl.length()==0 ? "Edit" : "View" );
        titleTextField.setEditable(remoteUrl.length()==0);

    } else {
        titleTextField.setText("");
        descriptionTextField.setText("");
        URLTextField.setText("");
        titleLabel.setText("Title:");
        editDescriptionButton.setEnabled(false);
        viewDetailsButton.setEnabled(false);
        titleTextField.setEditable(false);
    }
}//GEN-LAST:event_jTree1ValueChanged

private void importMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importMenuItemActionPerformed
    model.doImport(this);
}//GEN-LAST:event_importMenuItemActionPerformed

private void importUrlMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importUrlMenuItemActionPerformed
    String ansr = null;  // possibly invalid entry.
    String url = null;
    boolean okay = false;
    while (okay == false) {
        String s;
        if (ansr == null) {
            s = JOptionPane.showInputDialog(this, "Enter the URL of a bookmarks file:", "");
        } else {
            s = JOptionPane.showInputDialog(this, "Whoops. Enter the URL of a bookmarks file:", ansr);
        }

        if (s == null) {
            return;
        } else {
            ansr= s; // it's likely they will mistype, preserve their work.
            url = s;
            okay = true;
        }
    }

    doImportUrl(url);
}//GEN-LAST:event_importUrlMenuItemActionPerformed

    /**
     * remove remote references in the bookmarks to make a static local copy.
     * @param books
     */
    void makeLocal( List<Bookmark> books ) throws MalformedRemoteBookmarksException {
        for ( Bookmark b: books ) {
            if ( b instanceof Bookmark.Folder ) {
                Bookmark.Folder bf= (Bookmark.Folder) b;
                if ( bf.remoteUrl!=null && bf.remoteStatus!=0 ) {
                    List<Bookmark> importBook= new ArrayList<Bookmark>();
                    RemoteStatus remote= Bookmark.getRemoteBookmarks( bf.remoteUrl,Bookmark.REMOTE_BOOKMARK_DEPTH_LIMIT,true,importBook);
                    if ( remote.status==0 ) {
                        bf.bookmarks= importBook;
                        bf.remoteUrl= null;
                        bf.remoteStatus= 0;
                    } else {
                        throw new IllegalArgumentException("couldn't resolve "+bf.remoteUrl);
                    }
                }
                makeLocal(bf.getBookmarks());
            }
        }
    }

    /**
     * import a URL, possibly making it a remote folder.  Note this contains a copy of code
     * @param url import this bookmarks file
     */
    void doImportUrl( String url ) {

        String bookmarksFile= url.toString();

        ImportBookmarksGui gui= new ImportBookmarksGui();
        gui.getBookmarksFilename().setText(bookmarksFile+" ?");
        gui.getRemote().setSelected(true);
        int r = JOptionPane.showConfirmDialog( this, gui, "Import bookmarks file", JOptionPane.OK_CANCEL_OPTION );
        if (r == JOptionPane.OK_OPTION) {
            InputStream in = null;
            try {
                ProgressMonitor mon = DasProgressPanel.createFramed("importing bookmarks");
                if ( gui.getRemote().isSelected() ) {
                    try {
                        this.getModel().addRemoteBookmarks(bookmarksFile);
                    } catch ( MalformedRemoteBookmarksException ex ) {
                        JOptionPane.showMessageDialog( this, "Malformed "+bookmarksFile+ "\n"+ex.getMessage(), "Error in remote bookmarks", JOptionPane.WARNING_MESSAGE );
                        return;
                    }
                    this.reload();
                } else {
                    in = DataSetURI.getInputStream(DataSetURI.getURIValid(bookmarksFile), mon);
                    ByteArrayOutputStream boas=new ByteArrayOutputStream();
                    WritableByteChannel dest = Channels.newChannel(boas);
                    ReadableByteChannel src = Channels.newChannel(in);
                    DataSourceUtil.transfer(src, dest);
                    String sin= new String( boas.toByteArray() );
                    List<Bookmark> books= Bookmark.parseBookmarks(sin);
                    makeLocal(books);
                    this.getModel().importList( books );
                }
                JOptionPane.showMessageDialog( this, "imported bookmarks file "+bookmarksFile );
            } catch (BookmarksException ex) {
                logger.log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog( this, "Error parsing "+bookmarksFile+ "\n"+ex.getMessage(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (SAXException ex) {
                logger.log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog( this, "XML error parsing "+bookmarksFile+ "\n"+ex.getMessage(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog( this, "Error parsing "+bookmarksFile+ "\n"+ex.getMessage(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (FileNotFoundException ex ) {
                JOptionPane.showMessageDialog( this, "File not found: "+bookmarksFile, "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (MalformedURLException ex) {
                logger.log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog( this, "Error parsing "+bookmarksFile+ "\n"+ex.getMessage(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog( this, "I/O Error with "+bookmarksFile, "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } finally {
                try {
                    if ( in!=null ) in.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }

    }

private void resetToDefaultMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetToDefaultMenuItemActionPerformed
    String surl = AutoplotUtil.getProperty("autoplot.default.bookmarks", "http://www.autoplot.org/data/demos.xml");
    int r = JOptionPane.showConfirmDialog(this, "Reset your bookmarks to " + surl + "?", "Reset Bookmarks", JOptionPane.OK_CANCEL_OPTION );
    if (r == JOptionPane.OK_OPTION) {
        try {
            URL url = new URL(surl);
            Document doc = AutoplotUtil.readDoc(url.openStream());
            List<Bookmark> book = Bookmark.parseBookmarks(doc.getDocumentElement() );
            model.setList(book);
            formatToFile( bookmarksFile );
        } catch (SAXException ex ) {
            throw new RuntimeException( "Default bookmarks are mis-formatted!  Please let the Autoplot developers know!", ex );

        } catch (BookmarksException ex) {
            new GuiExceptionHandler().handle(ex);

        } catch (FileNotFoundException ex) {
            new GuiExceptionHandler().handle(ex);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(rootPane, "<html>Unable to read in default bookmarks:<br>"+ex.getMessage());
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }
}//GEN-LAST:event_resetToDefaultMenuItemActionPerformed

private void exportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportMenuItemActionPerformed
    model.doExport(this);
}//GEN-LAST:event_exportMenuItemActionPerformed

private void closeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeMenuItemActionPerformed
    this.dispose();
    if ( menuIsDirty ) {
        updateBookmarks( dirtyMenu, dirtySelector );
    }
}//GEN-LAST:event_closeMenuItemActionPerformed

private void deleteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMenuItemActionPerformed
    List<Bookmark> bs = model.getSelectedBookmarks(jTree1.getModel(), jTree1.getSelectionPaths());
    if ( bs.size()>0 ) {
        bs= removeRemoteBookmarks( bs, jTree1.getModel(), jTree1.getSelectionPaths() );
        if ( bs.size()==0 ) {
            JOptionPane.showMessageDialog(rootPane, "Part of remote bookmarks tree cannot be deleted","Remote Bookmark Delete",JOptionPane.OK_OPTION);
        } else {
            maybeDeleteBookmarks(bs);
        }
    }
}//GEN-LAST:event_deleteMenuItemActionPerformed

private void titleTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_titleTextFieldActionPerformed
    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if ( b==null ) {
        JOptionPane.showMessageDialog(rootPane, "No bookmark is selected","No Bookmark Selected",JOptionPane.OK_OPTION);
        return;
    }
    b.setTitle(titleTextField.getText());
    jTree1.repaint();
    model.fireBookmarkChange(b);
}//GEN-LAST:event_titleTextFieldActionPerformed

private void titleTextFieldPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_titleTextFieldPropertyChange

}//GEN-LAST:event_titleTextFieldPropertyChange

private void titleTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_titleTextFieldKeyTyped
    dirtyBookmark= model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
}//GEN-LAST:event_titleTextFieldKeyTyped

private void URLTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_URLTextFieldKeyTyped
    dirtyBookmark= model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
}//GEN-LAST:event_URLTextFieldKeyTyped

private void mergeInDefaultMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mergeInDefaultMenuItemActionPerformed
        try {
            String surl = AutoplotUtil.getProperty("autoplot.default.bookmarks", "http://www.autoplot.org/data/demos.xml");
            URL url = new URL(surl);
            Document doc = AutoplotUtil.readDoc(url.openStream());
            List<Bookmark> importBook = Bookmark.parseBookmarks(doc.getDocumentElement());
            List<Bookmark> newList = new ArrayList(model.list.size());
            for (int i = 0; i < model.list.size(); i++) {
                newList.add(i, model.list.get(i).copy());
            }
            model.mergeList(importBook, newList);
            model.setList(newList);
            formatToFile( bookmarksFile );
        } catch (SAXException ex ) {
            throw new RuntimeException( "Default bookmarks are mis-formatted!  Please let the Autoplot developers know!", ex );

        } catch (BookmarksException ex) {
            new GuiExceptionHandler().handle(ex);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(rootPane, "<html>Unable to read in default bookmarks:<br>"+ex.getMessage());

        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
}//GEN-LAST:event_mergeInDefaultMenuItemActionPerformed

private void descriptionTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_descriptionTextFieldFocusLost
    // we have to do more here because of the View/Edit button
    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if ( b!=null ) {
        if ( b.getDescription().equals(descriptionTextField.getText() ) ) {
            dirtyBookmark= b;
        }
    }
}//GEN-LAST:event_descriptionTextFieldFocusLost

private void descriptionTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_descriptionTextFieldKeyTyped
    dirtyBookmark= model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
}//GEN-LAST:event_descriptionTextFieldKeyTyped

private void editDescriptionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editDescriptionButtonActionPerformed
    String txt= descriptionTextField.getText();
    if ( txt.contains("<br>") ) {
        txt=  txt.replaceAll("<br>", "\n");
    }
    JTextArea edit= new JTextArea(txt);
    edit.setRows(5);
    edit.setColumns(80);
    JScrollPane jScrollPane2= new JScrollPane(edit);

    edit.setEditable(descriptionTextField.isEditable());
    
    int ok= JOptionPane.showConfirmDialog( this, jScrollPane2, titleTextField.getText(), edit.isEditable() ? JOptionPane.OK_CANCEL_OPTION : JOptionPane.OK_CANCEL_OPTION );
    if ( edit.isEditable() && ok==JOptionPane.OK_OPTION ) {
        String ntxt= edit.getText();
        if ( ! ntxt.equals(txt) ) {
            ntxt= ntxt.replaceAll("\n", "<br>");
            descriptionTextField.setText(ntxt);
            Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
            if ( b!=null ) {
                dirtyBookmark= b;
            }
        }

    }

}//GEN-LAST:event_editDescriptionButtonActionPerformed

    private boolean maybePlot( int modifiers ) {
        Bookmark book= (Bookmark) model.getSelectedBookmark( jTree1.getModel(), jTree1.getSelectionPath() );
        if ( book instanceof Bookmark.Item ) {
            if ( getParent() instanceof AutoplotUI ) {
                sel.setValue(((Bookmark.Item) book).getUri());
                sel.maybePlot(modifiers);

            }
            dispose();
            if ( menuIsDirty ) {
               updateBookmarks( dirtyMenu, dirtySelector );
            }
            return true;
        }
        return false;
    }

private void plotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotButtonActionPerformed
    maybePlot(evt.getModifiers());
}//GEN-LAST:event_plotButtonActionPerformed

private void plotBelowButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotBelowButtonActionPerformed
    maybePlot(KeyEvent.CTRL_MASK);
}//GEN-LAST:event_plotBelowButtonActionPerformed

private void overplotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overplotButtonActionPerformed
    maybePlot(KeyEvent.SHIFT_MASK);
}//GEN-LAST:event_overplotButtonActionPerformed

private void viewDetailsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewDetailsButtonActionPerformed
    Bookmark book= model.getSelectedBookmark( jTree1.getModel(), jTree1.getSelectionPath()  );
    URL url= getDescriptionUrl( book );
    if ( url!=null ) {
        AutoplotUtil.openBrowser(url.toString());
    }
}//GEN-LAST:event_viewDetailsButtonActionPerformed

private void reloadMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadMenuItemActionPerformed
    reload();
}//GEN-LAST:event_reloadMenuItemActionPerformed

//    /**
//    * @param args the command line arguments
//    */
//    public static void main(String args[]) {
//        java.awt.EventQueue.invokeLater(new Runnable() {
//            public void run() {
//                BookmarksManager dialog = new BookmarksManager(new javax.swing.JFrame(), true);
//                dialog.setPrefNode("test");
//                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
//                    @Override
//                    public void windowClosing(java.awt.event.WindowEvent e) {
//                        System.exit(0);
//                    }
//                });
//                dialog.setVisible(true);
//            }
//        });
//    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel URILabel;
    private javax.swing.JTextField URLTextField;
    private javax.swing.JMenuItem addItemMenuItem;
    private javax.swing.JMenuItem closeMenuItem;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JTextField descriptionTextField;
    private javax.swing.JButton dismissButton;
    private javax.swing.JButton editDescriptionButton;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem exportMenuItem;
    private javax.swing.JMenuItem importMenuItem;
    private javax.swing.JMenuItem importUrlMenuItem;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTree jTree1;
    private javax.swing.JMenuItem mergeInDefaultMenuItem;
    private javax.swing.JMenuItem newFolderMenuItem;
    private javax.swing.JButton overplotButton;
    private javax.swing.JButton plotBelowButton;
    private javax.swing.JButton plotButton;
    private javax.swing.JMenuItem reloadMenuItem;
    private javax.swing.JMenuItem resetToDefaultMenuItem;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JTextField titleTextField;
    private javax.swing.JButton viewDetailsButton;
    // End of variables declaration//GEN-END:variables

    private MouseListener createContextMenuMouseListener() {
        return new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.getClickCount()==2 ) {
                    maybePlot(e.getModifiers());
                }
                if ( e.isPopupTrigger() ) {
                    contextMenu.show( jTree1, e.getX(), e.getY() );
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path= jTree1.getPathForLocation( e.getX(), e.getY() );
                if ( !jTree1.getSelectionModel().isPathSelected(path) ) {
                    jTree1.getSelectionModel().setSelectionPath(path);
                }
                if ( e.isPopupTrigger() ) {
                    contextMenu.show( jTree1, e.getX(), e.getY() );
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    contextMenu.show( jTree1, e.getX(), e.getY() );
                }
            }

        };
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu= new JPopupMenu();
        menu.add( newFolderAction() );
        menu.add( addItemAction() );
        menu.add( createExportFolderAction() );
        menu.add( createDeleteAction() );
        return menu;
    }

    private Action addItemAction() throws HeadlessException {
        return new AbstractAction( "New Bookmark..." ) {
            public void actionPerformed( ActionEvent e ) {
                String s = JOptionPane.showInputDialog( BookmarksManager.this, "Bookmark URL:");
                if (s != null && !s.equals("")) {
                    String x= maybeGetRemoteBookmarkUrl( null, model, jTree1.getModel(), jTree1.getSelectionPath() );
                    if ( x.length()==0 ) {
                        model.addBookmark(new Bookmark.Item(s), model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath()));
                    } else {
                        JOptionPane.showMessageDialog( rootPane, "Cannot add item to remote bookmarks\n"+x, "Remote Bookmark Add Item",JOptionPane.OK_OPTION );
                    }
                }
            }
        };
    }


    private Action newFolderAction() throws HeadlessException {
        return new AbstractAction( "New Folder..." ) {
            public void actionPerformed( ActionEvent e ) {
                Bookmark context= model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
                String x= maybeGetRemoteBookmarkUrl( context, model, jTree1.getModel(), jTree1.getSelectionPath() );
                if ( x.length()>0 ) {
                    JOptionPane.showMessageDialog( rootPane, "Cannot add folder to remote bookmarks\n"+x, "Remote Bookmark Add Folder",JOptionPane.OK_OPTION );
                    return;
                }
                String s = JOptionPane.showInputDialog( BookmarksManager.this, "New Folder Name:");
                if (s != null && !s.equals("")) {
                    if (s.startsWith("http:") || s.startsWith("https:") || s.startsWith("ftp:")) {
                        // kludge for testing remote bookmarks
                        try {
                            // kludge for testing remote bookmarks
                            model.addRemoteBookmarks(s, model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath())); // null getSelectedBook is okay
                            reload();
                        } catch ( MalformedRemoteBookmarksException ex ) {
                            showMessage("Error in format of remote " + s + "\n" + ex.toString(), "Error in remote bookmarks", JOptionPane.WARNING_MESSAGE);
                        } 
                    } else {
                        model.addBookmark(new Bookmark.Folder(s), context );
                    }
                }

            }
        };
    }



    Action createDeleteAction() {
        return new AbstractAction("Delete") {
            public void actionPerformed(ActionEvent e) {
                List<Bookmark> bs = model.getSelectedBookmarks(jTree1.getModel(), jTree1.getSelectionPaths());
                if ( bs.size()>0 ) {
                    bs= removeRemoteBookmarks( bs, jTree1.getModel(), jTree1.getSelectionPaths() );
                    if ( bs.size()==0 ) {
                        JOptionPane.showMessageDialog(rootPane, "Part of remote bookmarks tree cannot be deleted","Remote Bookmark Delete",JOptionPane.OK_OPTION);
                    } else {
                        maybeDeleteBookmarks(bs);
                    }
                }
            }
        };
    }

    Action createExportFolderAction() {
        return new AbstractAction("Export Items...") {
            public void actionPerformed(ActionEvent e) {
                List<Bookmark> bs = model.getSelectedBookmarks(jTree1.getModel(), jTree1.getSelectionPaths());
                model.doExport(BookmarksManager.this,bs);
            }
        };
    }

    /**
     * returns true if the preference node exists.
     * @param nodeName
     * @return
     */
    public boolean hasPrefNode( String nodeName ) {
        final File f = new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks/" + nodeName + ".xml");
        return f.exists();
    }
    
    String prefNode= null;

    /**
     * keep track of the bookmarks file.
     */
    File bookmarksFile= null;

    
    /**
     * true if we have resolved the bookmarks recently.  This is used to suppress reading remote bookmarks files.
     */
    boolean haveReadRemote= false;

    /**
     * check to see if there are remote bookmarks that need loading.
     * @param book
     * @return true if there are unresolved bookmarks.
     */
    private boolean checkUnresolved( List<Bookmark> book ) {
        boolean unresolved= false;
        for ( Bookmark b: book ) {
            if ( b instanceof Bookmark.Folder ) {
                Bookmark.Folder bf= (Bookmark.Folder)b;
                if ( bf.remoteStatus== Bookmark.Folder.REMOTE_STATUS_NOT_LOADED ) {
                    unresolved= true;
                }
            }
        }
        return unresolved;
    }

    public static void printBooks( List<Bookmark> book, String indent ) {
        for ( Bookmark b: book ) {
            printBooks(b,indent);
        }
    }
    
    public static void printBooks( Bookmark book, String indent ) {
        System.err.println( indent + book.getTitle() ); // logger okay
        if ( book instanceof Bookmark.Folder ) {
            Bookmark.Folder bf= (Bookmark.Folder)book;
            for ( Bookmark b: bf.getBookmarks() ) {
                printBooks( b, indent+"  " );
            }
        }

    }

    private Runnable loadBooksRunnable( final String start, final int depthf ) {
        Runnable run= new Runnable() {
            public void run() {
                try {
                    try {
                        Thread.sleep(1000); // sleep 1000 milliseconds before making second pass
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    List<Bookmark> book = Bookmark.parseBookmarks(start, depthf);
                    model.setList(book);
                    int depthLimit= Bookmark.REMOTE_BOOKMARK_DEPTH_LIMIT;
                    if ( checkUnresolved(book) && depthf<depthLimit ) {
                        Runnable run= loadBooksRunnable( start, depthLimit );
                        logger.finer( String.format( " invokeLater( loadBooksRunnable( start, %d )\n", depthf+1 ) );
                        RequestProcessor.invokeLater(run);
                    } else {
                        if ( checkUnresolved(book) && depthf>=depthLimit ) {
                            logger.fine("remote bookmarks depth limit met");
                        }
                    }
                    if ( checkUnresolved(book)==false ) {
                        haveReadRemote= true;
                    }
                    
                    //System.err.println("\n\n=====");
                    //for ( Bookmark b: book ) {
                    //    System.err.println(b);
                    //}
                } catch (BookmarksException ex ) {
                    logger.log(Level.SEVERE, null, ex);
                    showMessage( "Semantic error while parsing " + bookmarksFile +"\n" +ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
                } catch (SAXException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    showMessage( "XML error while parsing " + bookmarksFile +"\n" +ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    showMessage( "IO error while parsing " + bookmarksFile +"\n" +ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
                }
            }
        };
        return run;
    }

    /**
     * reload the bookmarks from disk.  Remote bookmarks will be reloaded slightly later.
     */
    public void reload() {
        haveReadRemote= false;
        setPrefNode( prefNode );
    }

    /**
     * setting this makes the manager the authority on bookmarks.
     * @param nodeName
     */
    public void setPrefNode( String nodeName ) {
        prefNode= nodeName;
        
        BufferedReader read = null;
        try {

            File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
            if ( !f2.exists() ) {
                boolean ok= f2.mkdirs();
                if ( !ok ) {
                    throw new RuntimeException("unable to create folder "+ f2 );
                }
            }
            
            final File f = new File( f2, nodeName + ".xml");
            bookmarksFile= f;

            if ( !f.exists() )  {
                model.setList( new ArrayList<Bookmark>() );
            } else {
                read = new BufferedReader(new FileReader(f));
                StringBuilder buff= new StringBuilder();
                String s= null;
                do {
                    if ( s!=null ) buff.append(s).append("\n");
                    s= read.readLine();
                } while ( s!=null );

                int depth= 0;
                if ( haveReadRemote ) {
                    depth= -1;
                }
                
                List<Bookmark> book= Bookmark.parseBookmarks(buff.toString(),depth);
                model.setList(book);

                boolean unresolved;
                unresolved= checkUnresolved(book);
                if ( unresolved ) {
                    final String start= buff.toString();
                    Runnable run= loadBooksRunnable( start, Bookmark.REMOTE_BOOKMARK_DEPTH_LIMIT );
                    logger.finer( String.format( "invokeLater( loadBooksRunnable( start, %d )\n", depth+1 ) );
                    RequestProcessor.invokeLater(run);
                }

            }

            model.addPropertyChangeListener( new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    formatToFile(bookmarksFile);
                }
            } );
            
        } catch (BookmarksException ex) {
            showMessage( "Semantic error while parsing " + bookmarksFile +"\n" +ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
            model.setList(new ArrayList());
        } catch (SAXException ex) {
            showMessage( "XML error while parsing " + bookmarksFile +"\n" +ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
            model.setList(new ArrayList());
        } catch (IOException ex) {
            showMessage( "IO Error while parsing. " + bookmarksFile +"\n" + ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
            model.setList(new ArrayList());
        } finally {
            try {
                if ( read!=null ) read.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * rename the pref node, to aid with version changes.  E.g. convert autoplot.xml to bookmarks.xml
     * @param nodeName
     */
    public void resetPrefNode( String nodeName ) {
        File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
        if ( !f2.exists() ) {
            boolean ok= f2.mkdirs();
            if ( !ok ) {
                throw new RuntimeException("unable to create folder "+ f2 );
            }
        }

        final File f = new File( f2, nodeName + ".xml");
        if ( f.exists() )  {
            throw new IllegalArgumentException("bookmarks pref node already exists: "+f);
        } else {
            formatToFile( f );
        }
        bookmarksFile= f; // setPrefNode added a listener.
        prefNode= nodeName;
        
    }

    private void formatToFile( File f ) {
        logger.log(Level.FINE, "formatting {0}", f);
        OutputStream out = null;
        try {
            out= new FileOutputStream(f);
            Bookmark.formatBooks(out,model.getList());
            out.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            try {
                if ( out!=null ) out.close();
            } catch ( IOException ex ) {
                logger.log( Level.SEVERE, null, ex );
            }
        }
    }


    public void updateBookmarks( JMenu bookmarksMenu, final DataSetSelector dataSetSelector ) {

        if ( this.isVisible() ) {
            menuIsDirty= true;
            dirtyMenu= bookmarksMenu;
            dirtySelector= dataSetSelector;
            return;
        }

        List<Bookmark> bookmarks= model.getList();

        bookmarksMenu.removeAll();

        JMenuItem mi;

        mi= new JMenuItem( new AbstractAction("Manage and Browse...") {
            public void actionPerformed(ActionEvent e) {
                Container parent= BookmarksManager.this.getParent();
                BookmarksManager.this.setLocationRelativeTo( parent );
                setVisible(true);
            }
        } );
        mi.setToolTipText("Manage bookmarks, or select bookmark to plot");
        bookmarksMenu.add(mi);

        mi= new JMenuItem(new AbstractAction("Add Bookmark...") {

            public void actionPerformed(ActionEvent e) {
                Bookmark bookmark = addBookmark(dataSetSelector.getEditor().getText());
                setAddBookmark(bookmark);
                setVisible(true);
            }
        } );
        mi.setToolTipText("Add the current URI to the bookmarks");
        bookmarksMenu.add(mi);

        bookmarksMenu.add(new JSeparator());

        if ( bookmarks==null ) {
            bookmarks= Collections.emptyList();
        }

        addBookmarks( bookmarksMenu, bookmarks, 0, dataSetSelector );

        menuIsDirty= false;
    }

    private void addBookmarks( JMenu bookmarksMenu, List<Bookmark> bookmarks, int treeDepth, final DataSetSelector select ) {

        this.sel= select;

        DelayMenu.calculateMenu( bookmarksMenu, bookmarks, treeDepth, select );
        if ( bookmarksMenu.isPopupMenuVisible() ) {
            select.setMessage("Bookmarks updated");
        }

    }

    public Bookmark addBookmark(final String surl) {

        Bookmark.Item item = new Bookmark.Item(surl);
        URISplit split = URISplit.parse(surl);
        String autoTitle = split.file==null ? surl : split.file.substring(split.path.length());
        if (autoTitle.length() == 0) autoTitle = surl;
        item.setTitle(autoTitle);

        List<Bookmark> bookmarks= model.getList();

        if ( bookmarks==null ) bookmarks= new ArrayList<Bookmark>();
        List<Bookmark> newValue = new ArrayList<Bookmark>(bookmarks);

        if (newValue.contains(item)) { // move it to the front of the list
            Bookmark.Item old = (Bookmark.Item) newValue.get(newValue.indexOf(item));
            item = old;  // preserve titles and other future metadata.
            newValue.remove(old);
        }

        newValue.add(item);

        if ( prefNode==null ) { //TODO: I suspect this is old code that can be removed.
            Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
            prefs.put("bookmarks", Bookmark.formatBooks(newValue));
            try {
                prefs.flush();
            } catch (BackingStoreException ex) {
                logger.log( Level.SEVERE, null, ex );
            }
        } else {
            // should already have a listener
        }

        model.setList(newValue);
        formatToFile( bookmarksFile );

        return item;
    }

    public static void main( String[] args ) {
        new BookmarksManager(null, false).setPrefNode("bookmarks");
    }

    /**
     * return true if we are already using the remote bookmark, marked as a remote bookmark,
     * at the root level.
     * @param bookmarksFile
     * @return true if we already have the bookmark.
     */
    public boolean haveRemoteBookmark(String bookmarksFile) {
        List<Bookmark> list= model.getList();
        for ( Bookmark book: list ) {
            if ( book instanceof Bookmark.Folder ) {
                String rurl= ((Bookmark.Folder)book).getRemoteUrl();
                if ( rurl!=null && rurl.equals(bookmarksFile) ) {
                    return true;
                }
            }
        }
        return false;
    }

    /** allow bookmarks to inherit description from parents.
     * @param book
     * @return URL describing bookmark.
     */
    private URL getDescriptionUrl(Bookmark book) {
        URL result= book.getDescriptionUrl();
        if ( result==null ) {
            Bookmark p= book.getParent();
            if ( p==null ) {
                return null;
            } else {
                return getDescriptionUrl( p );
            }
        } else {
            return result;
        }
        
    }
}
