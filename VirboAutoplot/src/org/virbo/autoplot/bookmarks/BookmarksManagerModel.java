/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.bookmarks;

import org.virbo.autoplot.*;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class BookmarksManagerModel {

    protected void doImport(Component c) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".xml");
            }

            public String getDescription() {
                return "bookmarks files (*.xml)";
            }
        });
        int r = chooser.showOpenDialog(c);
        if (r == JFileChooser.APPROVE_OPTION) {
            try {
                List<Bookmark> recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new FileInputStream(chooser.getSelectedFile())));
                setList(recent);
            } catch (SAXException ex) {
                Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ParserConfigurationException ex) {
                Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    protected void doExport(Component c) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".xml");
            }

            public String getDescription() {
                return "bookmarks files (*.xml)";
            }
        });
        int r = chooser.showSaveDialog(c);
        if (r == JFileChooser.APPROVE_OPTION) {
            try {
                String format = Bookmark.formatBooks(getList());
                FileOutputStream out = new FileOutputStream(chooser.getSelectedFile());
                out.write(format.getBytes());
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
        protected List<Bookmark> list = null;
    public static final String PROP_LIST = "list";

    public List<Bookmark> getList() {
        return list;
    }

    public void setList(List<Bookmark> list) {
        List<Bookmark> oldList = this.list;
        this.list = list;
        propertyChangeSupport.firePropertyChange(PROP_LIST, oldList, list);
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }


    public TreeModel getTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Bookmarks");
        DefaultTreeModel model = new DefaultTreeModel(root);
        if ( this.list!=null ) addChildNodes(root, this.list);
        return model;
    }

    void addBookmark( Bookmark bookmark, Bookmark context ) {
        ArrayList<Bookmark> newList= new ArrayList<Bookmark>( this.list.size() );
        for ( Bookmark b: this.list ) newList.add( (Bookmark)b.copy() );
        if ( context==null || ( bookmark instanceof Bookmark.Folder && context instanceof Bookmark.Folder ) ) { // only allow folders in the root node
            newList.add( bookmark );
        } else if ( context instanceof Bookmark.Folder ) {
            Bookmark.Folder newFolder= (Bookmark.Folder) newList.get( newList.indexOf(context) );
            newFolder.getBookmarks().add(bookmark);
        } else {
            if ( newList.contains(context) ) {
                newList.add( newList.indexOf(context)+1, bookmark );
            } else {
                boolean isAdded= false;
                for ( Bookmark b: newList ) {
                    if ( b instanceof Bookmark.Folder ) {
                        List<Bookmark> bs= ( (Bookmark.Folder)b).getBookmarks();
                        if ( !isAdded && bs.contains(context) ) {
                            bs.add( bs.indexOf(context)+1, bookmark );
                            isAdded= true;
                        } 
                    }
                }
                if ( isAdded==false ) newList.add( bookmark );
            }
        }
        setList(newList);
    }

    void removeBookmark(Bookmark bookmark) {
        ArrayList<Bookmark> newList= new ArrayList<Bookmark>( this.list.size() );
        for ( Bookmark b: this.list ) newList.add( (Bookmark)b.copy() );
        if ( newList.contains(bookmark) ) {
            newList.remove( bookmark );
        } else {
            for ( Bookmark b: newList ) {
                if ( b instanceof Bookmark.Folder ) {
                    List<Bookmark> bs= ( (Bookmark.Folder)b).getBookmarks();
                    if ( bs.contains(bookmark) ) {
                        bs.remove( bookmark );
                    } 
                }
            }
        }
        setList(newList);
    }

    private void addChildNodes(MutableTreeNode parent, List<Bookmark> bookmarks) {
        for (Bookmark b : bookmarks) {
            MutableTreeNode child = new DefaultMutableTreeNode(b);
            parent.insert(child, parent.getChildCount());
            if (b instanceof Bookmark.Folder) {
                List<Bookmark> kids= ((Bookmark.Folder) b).getBookmarks();
                if ( kids.size()==0 ) {
                    child.insert( new DefaultMutableTreeNode("(empty)"),0 );
                } else {
                    addChildNodes(child, kids );
                }
            }
        }
    }

    protected Bookmark getSelectedBookmark(TreeModel model, TreePath path) {
        if ( path==null || path.getPathCount()==1 ) return null;
        Object sel= ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if ( sel.equals("(empty)" ) ) {
            return getSelectedBookmark( model, path.getParentPath() );
        } 
        return (Bookmark)sel; 
    }

    void doImportUrl( Component c ) {
        String ansr = null;  // it's likely they will mistype, preserve their work.
        URL url = null;
        boolean okay = false;
        while (okay == false) {
            String s;
            if (ansr == null) {
                s = JOptionPane.showInputDialog(c, "Enter the URL of a bookmarks file:", "");
            } else {
                s = JOptionPane.showInputDialog(c, "Whoops, Enter the URL of a bookmarks file:", ansr);
            }

            if (s == null) {
                return;
            } else {
                try {
                    url = new URL(s);
                    okay = true;
                } catch (MalformedURLException ex) {
                }
            }
        }
        try {
            Document doc = AutoplotUtil.readDoc(url.openStream());
            List<Bookmark> book = Bookmark.parseBookmarks(doc.getDocumentElement());
            this.setList(book);
        } catch (SAXException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
