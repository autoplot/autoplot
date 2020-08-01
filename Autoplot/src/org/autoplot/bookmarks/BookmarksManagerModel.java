
package org.autoplot.bookmarks;

import org.autoplot.AutoplotUtil;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Internal model for managing a set of bookmarks.
 * @author jbf
 */
public class BookmarksManagerModel {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.bookmarks");
    private static final String EMPTY_FOLDER = "(empty)";
    
    protected void doImport(Component c) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "bookmarks files (*.xml)", "xml" ) );
        int r = chooser.showOpenDialog(c);
        if (r == JFileChooser.APPROVE_OPTION) {
            try {
                List<Bookmark> importBook = Bookmark.parseBookmarks( AutoplotUtil.readDoc(new FileInputStream(chooser.getSelectedFile())).getDocumentElement() );
                List<Bookmark> newList= new ArrayList(this.list.size());
                for ( int i=0; i<this.list.size(); i++ ) {
                    newList.add(i,this.list.get(i).copy());
                }
                mergeList(importBook,newList);
                setList(newList);

            } catch (SAXException ex) {
                JOptionPane.showMessageDialog( c, ex.getMessage(), "Error when reading bookmarks", JOptionPane.ERROR_MESSAGE );
                logger.log(Level.SEVERE, ex.getMessage(), ex);

            } catch (ParserConfigurationException | BookmarksException ex) {
                JOptionPane.showMessageDialog( c, ex.getMessage(), "Error when reading bookmarks", JOptionPane.ERROR_MESSAGE );
                logger.log(Level.SEVERE, ex.getMessage(), ex);

            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }


    protected void doExport(Component c) {
        doExport( c, getList() );
    }

    protected void doExport(Component c, List<Bookmark> list) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "bookmarks files (*.xml)", "xml" ) );
        int r = chooser.showSaveDialog(c);
        if (r == JFileChooser.APPROVE_OPTION) {
            FileOutputStream out=null;
            try {
                File f= chooser.getSelectedFile();
                if ( !f.toString().endsWith(".xml") ) f= new File( f.toString()+".xml" );
                out = new FileOutputStream(f);
                Bookmark.formatBooks( out, list );
                
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            } finally {
                if ( out!=null ) try {
                    out.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }
    }
    protected List<Bookmark> list = null;
    public static final String PROP_LIST = "list";
    
    /**
     * the contents of a bookmark changed, like the title or URL.
     */
    public static final String PROP_BOOKMARK = "bookmark";
    
    /**
     * get the bookmarks as a list.  This is a mutable copy of the internal list.
     * @return the list of bookmarks.
     */
    public List<Bookmark> getList() {
        return list;
    }

    /**
     * set the bookmarks list.  This is used as the internal list, without making a copy.
     * @param list list of bookmarks.
     */
    public void setList(List<Bookmark> list) {
        logger.log(Level.FINE, "setting list to {0}", list);
        this.list = list;
        propertyChangeSupport.firePropertyChange(PROP_LIST, null, list);  //always fire event, since the objects within are mutable.
    }
    
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * get a TreeModel of the internal model, so GUIs can show the state.
     * @return a TreeModel.
     */
    public TreeModel getTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(name);
        DefaultTreeModel model = new DefaultTreeModel(root);
        if (this.list != null) addChildNodes(root, this.list);
        return model;
    }

    /**
     * return the equal bookmark from the list, traverse tree
     * @param newList
     * @param context
     * @return
     */
    Bookmark.Folder getFolder( List<Bookmark>newList, Bookmark context ) {
        for (Bookmark item : newList) {
            if ( item.equals( context ) ) {
                return (Bookmark.Folder) item; // old logic.
            } else if ( item instanceof Bookmark.Folder ) {
                Bookmark.Folder sub= getFolder( ((Bookmark.Folder)item).getBookmarks(), context );
                if ( sub!=null ) return sub;
            } else {
                // do nothing.
            }
        }
        return null;
        
    }

    /**
     * there's a goofy rule that we can't have two folders with the same name, so enforce this.
     * @param bookmarks 
     */
    void checkUniqueFolderNames( List<Bookmark> bookmarks ) {
        List<String> folders= new ArrayList();
        for (Bookmark b : bookmarks) {
            if ( b instanceof Bookmark.Folder ) {
                if ( folders.contains(b.getTitle()) ) {
                    throw new IllegalArgumentException("two bookmark folders cannot have the same title");
                }
                folders.add(b.getTitle());
            }
        }
    }
    
    void addBookmarks(List<Bookmark> bookmarks, Bookmark context, boolean insert) {
        ArrayList<Bookmark> newList = new ArrayList<>(this.list.size());
        for (Bookmark b : this.list) {
            newList.add((Bookmark) b.copy());
        }
        boolean containsFolder = false;
        for (Bookmark b : bookmarks) {
            containsFolder = containsFolder || b instanceof Bookmark.Folder;
        }
        if (context == null ) { 
            if (newList.contains(null)) { //TODO: verify this code.  findbugs pointed out the error, and this code seems strange.
                newList.addAll(newList.indexOf(null) + ( insert ? 0 : 1 ), bookmarks);
            } else {
                newList.addAll(bookmarks);
            }
        } else if (context instanceof Bookmark.Folder) {
            Bookmark.Folder newFolder = getFolder( newList, context );
            newFolder.getBookmarks().addAll(bookmarks);
        } else {
            if (newList.contains(context)) {
                newList.addAll(newList.indexOf(context) + ( insert ? 0 : 1 ), bookmarks);
            } else {
                boolean isAdded = false;
                for (Bookmark b : newList) {
                    if (b instanceof Bookmark.Folder) {
                        List<Bookmark> bs = ((Bookmark.Folder) b).getBookmarks();
                        if (!isAdded && bs.contains(context)) {
                            bs.addAll(bs.indexOf(context) + ( insert ? 0 : 1 ), bookmarks);
                            isAdded = true;
                        }
                    }
                }
                if (isAdded == false) newList.addAll(bookmarks);
            }
        }
        checkUniqueFolderNames( newList );
        setList(newList);

    }

    /**
     * 
     * @param bookmark
     * @param context 
     * @throws IllegalArgumentException when names are not unique
     */
    void addBookmark(Bookmark bookmark, Bookmark context) {
        addBookmarks(Collections.singletonList(bookmark), context, false);
    }

    void insertBookmark( Bookmark bookmark, Bookmark context) {
        addBookmarks(Collections.singletonList(bookmark), context, true);
    }

    /**
     * return the first item with this name and type, or null.  This does
     * not recurse through the folders.
     * @param oldList
     * @param title
     * @return
     */
    private Bookmark findItem( List<Bookmark> oldList, String title, boolean findFolder ) {
        for (Bookmark item : oldList) {
            boolean isFolder=  item instanceof Bookmark.Folder;
            if (( findFolder == isFolder ) && item.getTitle().equals(title)) {
                return item;
            }
        }
        return null;
    }

    /**
     * merge in the bookmarks.  Items with the same title are repeated, and
     * folders with the same name are merged.  When merging in a remote folder,
     * the entire folder is replaced.
     * @param src the items to merge in
     * @param dest the list to update.
     */
     public void mergeList( List<Bookmark> src, List<Bookmark> dest ) {
        if ( src.isEmpty() ) return;

        for (Bookmark item : src) {
            if ( item instanceof Bookmark.Folder ) {
                String folderName= item.getTitle();
                Bookmark.Folder old= (Bookmark.Folder)findItem( dest, folderName, true );
                Bookmark.Folder itemBook= (Bookmark.Folder)item;
                if ( old!=null ) {
                    int indx;
                    boolean replace;
                    indx= dest.indexOf(old);
                    replace= old.remoteUrl!=null;
                    if ( itemBook.remoteUrl==null ) {
                        mergeList( ((Bookmark.Folder)item).getBookmarks(), old.getBookmarks() );
                    } else {
                        Bookmark.Folder parent= old.getParent();
                        if ( parent==null ) {
                            dest.add(indx+1,itemBook);
                            if ( replace ) dest.remove(indx);
                        } else {
                            List<Bookmark> llist= parent.getBookmarks();
                            indx= llist.indexOf(old);
                            llist.add(indx+1,itemBook);
                            if ( replace ) llist.remove(indx);
                        }
                    }
                } else {
                    dest.add(item);
                }
            } else {
                String id= item.getTitle();
                Bookmark.Item old= (Bookmark.Item) findItem( dest, id, false );
                if ( old!=null ) {
                    if ( old.equals(item) ) continue;
                } else {
                    dest.add(item);
                }
            }
        }
    }


    /**
     * kludge to trigger list change when a title is changed.
     */
    void fireBookmarkChange(Bookmark book) {
        propertyChangeSupport.firePropertyChange(PROP_BOOKMARK,null,book);
    }

    TreePath getPathFor(Bookmark b, TreeModel model, TreePath root ) {
        if ( root==null ) return null;
        final Object parent = root.getLastPathComponent();
        final int childCount = model.getChildCount(parent);
        for ( int ii=0; ii<childCount; ii++ ) {
            final Object child = model.getChild(parent, ii);
            if ( b.equals(( (DefaultMutableTreeNode)child).getUserObject()  ) ) {
                //b.equals(( (DefaultMutableTreeNode)child).getUserObject()  );
                return root.pathByAddingChild(child);
            }
            if ( model.getChildCount(child)>0 ) {
                TreePath childResult= getPathFor( b, model, root.pathByAddingChild(child) );
                if ( childResult!=null ) return childResult;
            }
        }
        return null;
    }

    Bookmark.Folder removeBookmarks( Bookmark.Folder folder, Bookmark book ) {
        ArrayList<Bookmark> newList = new ArrayList<>( folder.getBookmarks().size() );
        for (Bookmark b : folder.getBookmarks() ) {
            newList.add( (Bookmark) b.copy() );
        }
        for ( int i=0; i<newList.size(); i++  ) {
            Bookmark bookmark= newList.get(i);
            if ( bookmark instanceof Bookmark.Folder ) {
                if ( bookmark.equals(book) ) {
                    newList.set( i, null );
                } else {
//                    if ( book.getParent()==folder ) {
                        bookmark= removeBookmarks( (Bookmark.Folder)bookmark, book );
                        newList.set( i, bookmark );
//                    }
                }
            } else {
                if ( bookmark.equals(book) ) {
                    newList.set( i, null );
                }
            }
        }
        newList.removeAll( Collections.singleton(null) );
        Bookmark.Folder result= new Bookmark.Folder(folder.getTitle());
        result.bookmarks= newList;

        return result;
    }

    /**
     * remove the listed bookmarks.
     * @param bookmarks
     */
    void removeBookmarks(List<Bookmark> bookmarks) {
        ArrayList<Bookmark> newList = new ArrayList<>(this.list.size());
        for (Bookmark b : this.list) {
            newList.add((Bookmark) b.copy());
        }
        for (Bookmark bookmark : bookmarks) {
            if (newList.contains(bookmark)) {
                newList.remove(bookmark);
            } else {
                int i=0;
                for (Bookmark b2 : newList) {
                    if (b2 instanceof Bookmark.Folder) {
                        String remote= BookmarksManager.maybeGetRemoteBookmarkUrl( b2 );
                        if ( remote.length()==0 ) {
                            b2= removeBookmarks( (Bookmark.Folder) b2, bookmark );
                            newList.set( i, b2 );
                        }
                    } else {
                        newList.set( i, null );
                    }
                    i++;
                }
            }
        }
        newList.removeAll( Collections.singleton(null) );
        setList(newList);
    }

    void removeBookmark(Bookmark bookmark) {
        removeBookmarks( Collections.singletonList(bookmark) );
    }

    private void addChildNodes(MutableTreeNode parent, List<Bookmark> bookmarks) {
        for (Bookmark b : bookmarks) {
            String node= b.toString();
            if (b instanceof Bookmark.Folder) {
                if ( ((Bookmark.Folder)b).remoteUrl!=null && ((Bookmark.Folder)b).remoteUrl.length()>0 ) {
                    node= node + String.format( " [remoteUrl=%s]", ((Bookmark.Folder)b).remoteUrl );
                }
            }
            final String fnode= node;
            MutableTreeNode child = new DefaultMutableTreeNode(b) {
                @Override
                public String toString() {
                    return fnode;
                }
            };
            parent.insert(child, parent.getChildCount());
            if (b instanceof Bookmark.Folder) {
                List<Bookmark> kids = ((Bookmark.Folder) b).getBookmarks();
                if (kids.isEmpty()) {
                    child.insert(new DefaultMutableTreeNode(EMPTY_FOLDER), 0);
                } else {
                    addChildNodes(child, kids);
                }
            }
        }
    }

    /**
     * return the bookmark selected in the tree.
     * @param model
     * @param path the selected path, or null indicating no selection.
     * @return
     */
    protected Bookmark getSelectedBookmark(TreeModel model, TreePath path) {
        if (path == null || path.getPathCount() == 1) return null;
        Object sel = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (sel.equals(EMPTY_FOLDER)) {
            return getSelectedBookmark(model, path.getParentPath());
        }
        return (Bookmark) sel;
    }

    /**
     * return the bookmarks selected in the tree.
     * @param model
     * @param paths the selected paths. This may be null, indicating no selection.
     * @return
     */
    protected List<Bookmark> getSelectedBookmarks(TreeModel model, TreePath[] paths) {
        List<Bookmark> result= new ArrayList<>();
        if ( paths==null ) return result;
        for ( TreePath path: paths ) {
            if (path == null ) return null;
            if (path.getPathCount() == 1) return list;
            Object sel = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            Bookmark b;
            if (sel.equals(EMPTY_FOLDER)) {
                b= getSelectedBookmark(model, path.getParentPath());
            } else {
                b= (Bookmark)sel;
            }
            if ( b!=null ) result.add( b );
        }
        return result;
    }


    /**
     * merge the given list into the list.
     * @param books 
     */
    public void importList(List<Bookmark> books) {
        List<Bookmark> newList= new ArrayList(this.list.size());
        for ( int i=0; i<this.list.size(); i++ ) {
            newList.add(i,this.list.get(i).copy());
        }
        mergeList(books,newList);
        setList(newList);
    }

    /**
     * add the bookmarks in the remote URL to the list.
     * @param surl
     * @throws MalformedRemoteBookmarksException 
     */
    public void addRemoteBookmarks(String surl ) throws MalformedRemoteBookmarksException {
        addRemoteBookmarks(surl,null);
    }

    /**
     * add the remote bookmarks.  An ordinary bookmarks file downloaded from
     * a website can be tacked on to a user's existing bookmarks, and updates
     * to the file will be visible on the client's bookmark list.
     * 
     * @param surl
     * @param selectedBookmark location to add the bookmark, can be null.
     * @throws MalformedRemoteBookmarksException
     */
    public void addRemoteBookmarks(String surl, Bookmark selectedBookmark) throws MalformedRemoteBookmarksException {
        List<Bookmark> importBook= new ArrayList(100);
        RemoteStatus remote= Bookmark.getRemoteBookmarks(surl,Bookmark.REMOTE_BOOKMARK_DEPTH_LIMIT,true,importBook);
        if ( importBook.size()!=1 ) {
            throw new MalformedRemoteBookmarksException( "Remote bookmarks file contains more than one root folder: "+surl );
        }

        if ( remote.remoteRemote==true ) {
            logger.fine("remote bookmarks found in remote bookmarks...");
        }
        List<Bookmark> newList= new ArrayList(this.list.size());
        for ( int i=0; i<this.list.size(); i++ ) {
            newList.add(i,this.list.get(i).copy());
        }

        List<Bookmark> copy= new ArrayList();
        for (Bookmark m : importBook) {
            if ( m instanceof Bookmark.Folder ) {
                Bookmark.Folder bf= (Bookmark.Folder)m;
                if ( bf.getRemoteUrl()==null ) {
                    bf.setRemoteUrl( surl );
                }
                copy.add( m );
            } else if ( m.getTitle().equals(Bookmark.TITLE_ERROR_OCCURRED) ) {
                copy.add( m );
            }
        }
        mergeList(copy,newList);
        setList(newList);

    }

    private String name;
    
    /**
     * set the name for the bookmarks, such as "Bookmarks" or "Tools".  This is only used to label the root node.
     * @param name 
     */
    protected void setName(String name) {
        this.name= name;
    }
}
