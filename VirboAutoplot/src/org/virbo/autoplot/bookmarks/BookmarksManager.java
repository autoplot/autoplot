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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
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
import org.das2.system.RequestProcessor;
import org.virbo.autoplot.scriptconsole.GuiExceptionHandler;
import org.virbo.datasource.DataSetSelector;
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
                TreeModel mod = model.getTreeModel();
                jTree1.setModel(mod);
            }
        });

        BookmarksManagerTransferrable trans = new BookmarksManagerTransferrable(model, jTree1);

        DragSource dragSource = DragSource.getDefaultDragSource();
        DropTarget dropTarget = new DropTarget();
        try {
            dropTarget.addDropTargetListener(trans.createDropTargetListener());
        } catch (TooManyListenersException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        jTree1.setDropTarget(dropTarget);
        dragSource.createDefaultDragGestureRecognizer(jTree1, DnDConstants.ACTION_COPY_OR_MOVE, trans.createDragGestureListener());
    }
    
    BookmarksManagerModel model;
    Bookmark dirtyBookmark;

    JPopupMenu contextMenu= createContextMenu();

    public BookmarksManagerModel getModel() {
        return model;
    }

    /**
     * return the remoteUrl containing this bookmark, or an empty string.
     * @param b the target bookmark.
     * @param treeModel the model containing the bookmark
     * @param ppath the path to the bookmark.
     * @return the first remote URL containing the bookmark, or an empty string if it is local.
     */
    private String maybeGetRemoteBookmarkUrl(Bookmark b, TreeModel treeModel, TreePath ppath ) {
        // if it is a child of a remote bookmark, make sure it's not editable.
        String remoteUrl= "";
        if ( b instanceof Bookmark.Item ) ppath= ppath.getParentPath();
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
     * @param model
     * @param selectionPaths
     * @return the remaining remote bookmarks.
     */
    private List<Bookmark> removeRemoteBookmarks(List<Bookmark> bs, TreeModel model, TreePath[] selectionPaths) {
        assert selectionPaths.length==bs.size();
        List<Bookmark> result= new ArrayList();
        for ( int i=0; i<bs.size(); i++ ) {
            Bookmark bs1= bs.get(i);
            if ( (bs1 instanceof Bookmark.Folder && ((Bookmark.Folder)bs1).getRemoteUrl()!=null )
                    || "".equals( maybeGetRemoteBookmarkUrl( bs.get(i), model, selectionPaths[i] )) ) {
                result.add(bs.get(i));
            }
        }
        return result;
    }


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
                //TODO: check for remote bookmark
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
                    System.err.println( "WARNING: "+ title + ": " + message  );
                } else if ( messageType==JOptionPane.INFORMATION_MESSAGE ) {
                    System.err.println( "INFO: "+ title + ": " + message  );
                } else {
                    System.err.println( title + ": " + message  );
                }
            } else {
                JOptionPane.showMessageDialog( p, message, title, messageType );
            }
        } else {
            if ( messageType==JOptionPane.WARNING_MESSAGE ) {
                System.err.println( "WARNING: "+ title + ": " + message  );
            } else if ( messageType==JOptionPane.INFORMATION_MESSAGE ) {
                System.err.println( "INFO: "+ title + ": " + message  );
            } else {
                System.err.println( title + ": " + message  );
            }
        }
    }


    /*private void addIcon() {
        Runnable run = new Runnable() {

            public void run() {
                Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
                ImageIcon icon = AutoplotUtil.createIcon(null, ((Bookmark.Item) b).getUri());
                b.setIcon(icon);
                //iconButton.setIcon(icon);
                model.fireBookmarkChange(b);
            }
        };
        new Thread(run).start();
    }*/

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
        importButton = new javax.swing.JButton();
        importFromWebButton = new javax.swing.JButton();
        ExportButton = new javax.swing.JButton();
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
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        importMenuItem = new javax.swing.JMenuItem();
        importUrlMenuItem = new javax.swing.JMenuItem();
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

        importButton.setText("Import...");
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        importFromWebButton.setText("Import From Web...");
        importFromWebButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importFromWebButtonActionPerformed(evt);
            }
        });

        ExportButton.setText("Export...");
        ExportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ExportButtonActionPerformed(evt);
            }
        });

        dismissButton.setText("OK");
        dismissButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dismissButtonActionPerformed(evt);
            }
        });

        URILabel.setText("URI:");
        URILabel.setToolTipText("Location of the data (often the URL)");

        URLTextField.setToolTipText("Location of the data (often the URL)");
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

        jMenu1.setText("File");

        importMenuItem.setText("Import...");
        importMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(importMenuItem);

        importUrlMenuItem.setText("Import From Web...");
        importUrlMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importUrlMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(importUrlMenuItem);

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

        newFolderMenuItem.setText("New Folder");
        newFolderMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newFolderMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(newFolderMenuItem);

        addItemMenuItem.setText("New Bookmark");
        addItemMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addItemMenuItemActionPerformed(evt);
            }
        });
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
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(importButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(importFromWebButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(ExportButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 299, Short.MAX_VALUE)
                        .add(plotButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dismissButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 43, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(URILabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(URLTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 644, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(titleLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(titleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 639, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(jLabel4)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(descriptionTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 534, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(editDescriptionButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 53, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(titleLabel)
                    .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(7, 7, 7)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(descriptionTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(editDescriptionButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(URILabel)
                    .add(URLTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(16, 16, 16)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(dismissButton)
                    .add(importButton)
                    .add(importFromWebButton)
                    .add(ExportButton)
                    .add(plotButton))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {URLTextField, titleTextField}, org.jdesktop.layout.GroupLayout.VERTICAL);

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
    model.doImport(this);
}//GEN-LAST:event_importButtonActionPerformed

private void importFromWebButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importFromWebButtonActionPerformed
    model.doImportUrl(this);
}//GEN-LAST:event_importFromWebButtonActionPerformed

private void ExportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ExportButtonActionPerformed
    model.doExport(this);
}//GEN-LAST:event_ExportButtonActionPerformed

private void dismissButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dismissButtonActionPerformed
    doPlay= false;
    this.dispose();
}//GEN-LAST:event_dismissButtonActionPerformed

private void URLTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_URLTextFieldFocusLost
    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if ( b!=null && b instanceof Bookmark.Item) {
        ((Bookmark.Item) b).setUri(URLTextField.getText());
        jTree1.repaint();
        model.fireBookmarkChange(b);
    }
}//GEN-LAST:event_URLTextFieldFocusLost

private void titleTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_titleTextFieldFocusLost
    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if ( b!=null ) {
        b.setTitle(titleTextField.getText());
        jTree1.repaint();
        model.fireBookmarkChange(b);
    }
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
        URLTextField.setEditable( b instanceof Bookmark.Item );
        if (b instanceof Bookmark.Item) {
            URLTextField.setText(((Bookmark.Item) b).getUri());
            URILabel.setText("URI:");
        } else {
            if ( b instanceof Bookmark.Folder && ((Bookmark.Folder)b).getRemoteUrl()!=null ) {
                String url= ((Bookmark.Folder)b).getRemoteUrl();
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
            remoteUrl= maybeGetRemoteBookmarkUrl( b, jTree1.getModel(), ppath);
            URLTextField.setEditable(remoteUrl.length()==0);
        } else {
            URLTextField.setEditable(false);
        }
        if ( remoteUrl.length()==0 ) {
            titleLabel.setText("Title:");
            titleLabel.setToolTipText("Title for the URI");
        } else {
            titleLabel.setText("Title*:");
            titleLabel.setToolTipText("<html>Title for the URI.<br>This bookmark is part of a set of remote bookmarks from<br>"
                    + remoteUrl +
                    "<br> and cannot be edited.");
        }
        descriptionTextField.setEditable(remoteUrl.length()==0);
        editDescriptionButton.setEnabled(remoteUrl.length()==0);
        titleTextField.setEditable(remoteUrl.length()==0);

    } else {
        titleTextField.setText("");
        descriptionTextField.setText("");
        URLTextField.setText("");
        titleLabel.setText("Title:");
        descriptionTextField.setEditable(false);
        editDescriptionButton.setEnabled(false);
        titleTextField.setEditable(false);
    }
}//GEN-LAST:event_jTree1ValueChanged

private void importMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importMenuItemActionPerformed
    model.doImport(this);
}//GEN-LAST:event_importMenuItemActionPerformed

private void importUrlMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importUrlMenuItemActionPerformed
    model.doImportUrl(this);
}//GEN-LAST:event_importUrlMenuItemActionPerformed

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
        } catch (SAXException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            new GuiExceptionHandler().handle(ex);
        } catch (IOException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}//GEN-LAST:event_resetToDefaultMenuItemActionPerformed

private void exportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportMenuItemActionPerformed
    model.doExport(this);
}//GEN-LAST:event_exportMenuItemActionPerformed

private void closeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeMenuItemActionPerformed
    this.dispose();
}//GEN-LAST:event_closeMenuItemActionPerformed

private void newFolderMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newFolderMenuItemActionPerformed
    String s = JOptionPane.showInputDialog(this,"New Folder Name:");
    if (s != null && !s.equals("")) {
        if ( s.startsWith("http:") || s.startsWith("https:") || s.startsWith("ftp:") ) { // kludge for testing remote bookmarks
            try {
                 // kludge for testing remote bookmarks
                model.addRemoteBookmarks(s, model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath())); // null getSelectedBook is okay
                reload();
                
            } catch (IllegalArgumentException ex ) {
                if ( true ) { //ex.toString().contains("URLDecoder") ) {
                    showMessage( "Error in format of "+s+"\n"+ex.toString(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
                } //else {
                //    showMessage( "Expected XML at "+s, "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
                //}
            }
        } else {
            model.addBookmark(new Bookmark.Folder(s), model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath()));
        }
    }
}//GEN-LAST:event_newFolderMenuItemActionPerformed

private void addItemMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addItemMenuItemActionPerformed
        String s = JOptionPane.showInputDialog("Bookmark URL:");
        if (s != null && !s.equals("")) {
        model.addBookmark(new Bookmark.Item(s), model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath()));
    }
}//GEN-LAST:event_addItemMenuItemActionPerformed

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

        } catch (SAXException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        }
}//GEN-LAST:event_mergeInDefaultMenuItemActionPerformed

private void descriptionTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_descriptionTextFieldFocusLost
    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if ( b!=null ) {
        b.setDescription(descriptionTextField.getText());
        jTree1.repaint();
        model.fireBookmarkChange(b);
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

    int ok= JOptionPane.showConfirmDialog( this, jScrollPane2, titleTextField.getText(), JOptionPane.OK_CANCEL_OPTION );
    if ( ok==JOptionPane.OK_OPTION ) {
        txt= edit.getText();
        txt= txt.replaceAll("\n", "<br>");
        descriptionTextField.setText(txt);
    }

    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
    if ( b!=null ) {
        b.setDescription(descriptionTextField.getText());
        jTree1.repaint();
        model.fireBookmarkChange(b);
    }
}//GEN-LAST:event_editDescriptionButtonActionPerformed

    private boolean maybePlot() {
        Bookmark sel= (Bookmark) model.getSelectedBookmark( jTree1.getModel(), jTree1.getSelectionPath() );
        if ( sel instanceof Bookmark.Item ) {
            if ( getParent() instanceof AutoplotUI ) {
                AutoplotUI p= (AutoplotUI)getParent();
                Bookmark.Item bisel= (Bookmark.Item) sel;
                p.plotUri(bisel.getUri());
            }
            dispose();
            return true;
        }
        return false;
    }

private void plotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotButtonActionPerformed
    maybePlot();
}//GEN-LAST:event_plotButtonActionPerformed

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
    private javax.swing.JButton ExportButton;
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
    private javax.swing.JButton importButton;
    private javax.swing.JButton importFromWebButton;
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
    private javax.swing.JButton plotButton;
    private javax.swing.JMenuItem resetToDefaultMenuItem;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JTextField titleTextField;
    // End of variables declaration//GEN-END:variables

    private MouseListener createContextMenuMouseListener() {
        return new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.getClickCount()==2 ) {
                    maybePlot();
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
        menu.add( createExportFolderAction() );
        menu.add( createDeleteAction() );
        return menu;
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

    private Runnable loadBooksRunnable( final String start, final int depthf ) {
        Runnable run= new Runnable() {
            public void run() {
                try {
                    try {
                        Thread.sleep(5000); // sleep 5 seconds before making second pass
                    } catch (InterruptedException ex) {
                        Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    List<Bookmark> book = Bookmark.parseBookmarks(start, depthf);
                    model.setList(book);
                    if ( checkUnresolved(book) && depthf<2 ) {
                        Runnable run= loadBooksRunnable( start, depthf+1 );
                        RequestProcessor.invokeLater(run);
                    }
                    //System.err.println("\n\n=====");
                    //for ( Bookmark b: book ) {
                    //    System.err.println(b);
                    //}
                } catch (SAXException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        return run;
    }

    /**
     * reload the bookmarks from disk.  Remote bookmarks will be reloaded slightly later.
     */
    public void reload() {
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

                int depth=0;
                List<Bookmark> book= Bookmark.parseBookmarks(buff.toString(),depth);
                model.setList(book);

                boolean unresolved;
                unresolved= checkUnresolved(book);
                if ( unresolved ) {
                    final String start= buff.toString();
                    Runnable run= loadBooksRunnable( start, depth+1 );
                    RequestProcessor.invokeLater(run);
                }

            }

            model.addPropertyChangeListener( new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    formatToFile(bookmarksFile);
                }
            } );
            
        } catch (SAXException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
            showMessage( "XML error while parsing " + bookmarksFile +"\n" +ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
        } catch (IOException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
            showMessage( "IO Error while parsing. " + bookmarksFile +"\n" + ex.getMessage(), "Error while parsing bookmarks", JOptionPane.WARNING_MESSAGE );
        } finally {
            try {
                if ( read!=null ) read.close();
            } catch (IOException ex) {
                Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
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
        System.err.println("formatting "+f);
        OutputStream out = null;
        try {
            out= new FileOutputStream(f);
            Bookmark.formatBooks(out,model.getList());
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if ( out!=null ) out.close();
            } catch ( IOException ex ) {
                ex.printStackTrace();
            }
        }
    }


    public void updateBookmarks( JMenu bookmarksMenu, final DataSetSelector dataSetSelector ) {

        List<Bookmark> bookmarks= model.getList();

        bookmarksMenu.removeAll();

        bookmarksMenu.add(new AbstractAction("Add Bookmark...") {

            public void actionPerformed(ActionEvent e) {
                Bookmark bookmark = addBookmark(dataSetSelector.getEditor().getText());
                setAddBookmark(bookmark);
                setVisible(true);
            }
        });

        bookmarksMenu.add(new AbstractAction("Manage Bookmarks") {

            public void actionPerformed(ActionEvent e) {
                Container parent= BookmarksManager.this.getParent();
                BookmarksManager.this.setLocationRelativeTo( parent );
                setVisible(true);
            }
        });

        bookmarksMenu.add(new JSeparator());

        if ( bookmarks==null ) {
            bookmarks= Collections.emptyList();
        }

        addBookmarks( bookmarksMenu, bookmarks, dataSetSelector );

    }

    private void addBookmarks( JMenu bookmarksMenu, List<Bookmark> bookmarks, final DataSetSelector sel ) {

        //TODO: tooltips are sometimes underneath submenus.
        //ToolTipManager.sharedInstance().getReshowDelay();

        int MAX_TITLE_LEN=50;
        for (int i = 0; i < bookmarks.size(); i++) {
            final Bookmark book = bookmarks.get(i);

            if ( bookmarksMenu.isPopupMenuVisible() ) {
                sel.setMessage("Bookmarks updated");
            }

            if (book instanceof Bookmark.Item) {
                String title= book.getTitle();
                if ( title.length()>MAX_TITLE_LEN ) title= title.substring(0,MAX_TITLE_LEN)+"...";
                JMenuItem mi = new JMenuItem(new AbstractAction(title) {
                    public void actionPerformed(ActionEvent e) {
                        sel.setValue(((Bookmark.Item) book).getUri());
                        sel.maybePlot(e.getModifiers());
                    }
                });

                String uri=  ((Bookmark.Item)book).getUri();
                if ( uri.length()>MAX_TITLE_LEN ) {
                    uri= uri.substring(0,MAX_TITLE_LEN)+"...";
                }
                if ( book.getDescription()!=null && book.getDescription().length()>0 ) {
                    String ttext=  "<html><em>"+ title + "<br>" + book.getDescription()+ "<br>" + uri +"</em></html>";
                    mi.setToolTipText( ttext );
                } else {
                    String ttext=  "<html><em>"+ title + "<br>" + uri+"</em></html>";
                    mi.setToolTipText( ttext );
                }
                if (book.getIcon() != null) {
                    mi.setIcon(AutoplotUtil.scaleIcon(book.getIcon(), -1, 16));
                }
                bookmarksMenu.add(mi);
            } else {
                Bookmark.Folder folder = (Bookmark.Folder) book;
                String title= book.getTitle();
                if ( title.length()>MAX_TITLE_LEN ) title= title.substring(0,MAX_TITLE_LEN)+"...";

                String tooltip;
                if ( folder.getRemoteUrl()!=null ) {
                    if ( folder.getRemoteStatus()== Bookmark.Folder.REMOTE_STATUS_SUCCESSFUL ) {
                        title= title + " " + Bookmark.MSG_REMOTE;
                        tooltip= Bookmark.TOOLTIP_REMOTE;
                    } else if ( folder.getRemoteStatus()== Bookmark.Folder.REMOTE_STATUS_NOT_LOADED  ) {
                        title= title + " " + Bookmark.MSG_NOT_LOADED; // we use this now that we add bookmarks in stages
                        tooltip= Bookmark.TOOLTIP_NOT_LOADED;
                    } else {
                        title= title + " " + Bookmark.MSG_NO_REMOTE;
                        tooltip= Bookmark.TOOLTIP_NO_REMOTE;
                    }
                } else {
                    tooltip= "";
                }

                final JMenu subMenu = new JMenu(title);

                if ( tooltip.contains("%{URL}") ) {
                    tooltip= tooltip.replace("%{URL}",folder.getRemoteUrl());
                }
                
                if ( book.getDescription()!=null && book.getDescription().length()>0 ) {
                    String ttext=  "<html><em>"+ title + "<br>" + book.getDescription()+"</em>";
                    subMenu.setToolTipText( ttext + "<br>" + tooltip );
                } else {
                    if ( tooltip.length()>0 ) subMenu.setToolTipText( "<html>"+tooltip );
                }
                addBookmarks(subMenu, folder.getBookmarks(),sel);
                bookmarksMenu.add(subMenu);
            }
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
                ex.printStackTrace();
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
}
