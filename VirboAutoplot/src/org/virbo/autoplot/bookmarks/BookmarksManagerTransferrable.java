/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.bookmarks;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import org.xml.sax.SAXException;

/**
 * support the bookmarks manager by adding drag and drop
 * @author jbf
 */
public class BookmarksManagerTransferrable {

    private BookmarksManagerModel model;
    private JTree jTree1;

    BookmarksManagerTransferrable(BookmarksManagerModel model, JTree jTree1) {
        this.model = model;
        this.jTree1 = jTree1;
    }

    DropTargetListener createDropTargetListener() {
        return new DropTargetListener() {

            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(BOOKMARK_FLAVOR)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);

                } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }
            }

            public void dragOver(DropTargetDragEvent dtde) {
            }

            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            public void dragExit(DropTargetEvent dte) {
            }

            public void drop(DropTargetDropEvent dtde) {
                try {
                    Bookmark item = null;
                    List<Bookmark> items = null;
                    if (dtde.isDataFlavorSupported(BOOKMARK_FLAVOR)) {
                        item = (Bookmark) dtde.getTransferable().getTransferData(BOOKMARK_FLAVOR);
                    } else if (dtde.isDataFlavorSupported(BOOKMARK_LIST_FLAVOR)) {
                        items = (List<Bookmark>) dtde.getTransferable().getTransferData(BOOKMARK_LIST_FLAVOR);
                    } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String data = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        if (data.length() > 19 && data.startsWith("<bookmark-list")) {
                            items = Bookmark.parseBookmarks(data);
                        } else if (data.length() > 14 && data.startsWith("<bookmark")) {
                            item = Bookmark.parseBookmark(data);

                        } else {
                            item = new Bookmark.Item(data);
                        }
                    }

                    TreePath tp = jTree1.getPathForLocation((int) dtde.getLocation().getX(), (int) dtde.getLocation().getY());
                    Bookmark context = model.getSelectedBookmark(jTree1.getModel(), tp);

                    String remoteUrl= BookmarksManager.maybeGetRemoteBookmarkUrl( item, model, jTree1.getModel(), tp );
                    if ( remoteUrl.length()>0 ) {
                        JOptionPane.showMessageDialog( jTree1, "Drop target is within remote bookmarks\n"+remoteUrl, "Remote Bookmark Move Item",JOptionPane.OK_OPTION );
                        return;
                    }
                    //TODO: check that source is not remote bookmarks, and do not remote when it is.
                    if (item != null) {
                        if (item == context) return;
                        model.removeBookmark(item);
                        model.insertBookmark(item, context);
                    } else if (items != null) {
                        model.removeBookmarks(items);
                        model.addBookmarks(items, context, true);
                    }

                } catch (UnsupportedFlavorException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SAXException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        };
    }

    DragGestureListener createDragGestureListener() {
        return new DragGestureListener() {

            public void dragGestureRecognized(DragGestureEvent dge) {

                if ( jTree1.getSelectionCount()==1 ) {
                    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
                    //Toolkit tk= Toolkit.getDefaultToolkit();
                    if (b instanceof Bookmark.Item) {
                        //Cursor c= tk.createCustomCursor( b.getIcon().getImage(), new Point( 0,0), "bookmark");
                        dge.startDrag(null, createBookmarkTransferrable((Bookmark.Item) b));

                    } else if (b instanceof Bookmark.Folder) {
                        dge.startDrag(null, createBookmarkTransferrable((Bookmark.Folder) b));
                    }
                } else {
                    List<Bookmark> books= new ArrayList<Bookmark>();
                    TreePath[] tps= jTree1.getSelectionPaths();
                    if ( tps==null ) return;
                    for ( TreePath tp: tps ) {
                        Bookmark b = model.getSelectedBookmark( jTree1.getModel(), tp );
                        books.add(b);
                    }
                    dge.startDrag(null, createBookmarkListTransferrable(books) ) ;

                }

            }
        };
    };
    DataFlavor BOOKMARK_FLAVOR = new DataFlavor(Bookmark.class, "Bookmark");
    DataFlavor BOOKMARK_LIST_FLAVOR = new DataFlavor(List.class, "BookmarkList");

    Transferable createBookmarkTransferrable(final Bookmark.Item bookmark) {
        return new Transferable() {

            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{
                            DataFlavor.stringFlavor,
                            BOOKMARK_FLAVOR
                        };

            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == DataFlavor.stringFlavor || flavor == BOOKMARK_FLAVOR;
            }

            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                if (flavor == DataFlavor.stringFlavor) {
                    return bookmark.getUri();
                } else if (flavor == BOOKMARK_FLAVOR) {
                    return bookmark;
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            }
        };

    }

    Transferable createBookmarkTransferrable(final Bookmark.Folder bookmark) {
        return new Transferable() {

            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{
                            DataFlavor.stringFlavor,
                            BOOKMARK_FLAVOR
                        };

            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == DataFlavor.stringFlavor || flavor == BOOKMARK_FLAVOR;
            }

            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                if (flavor == BOOKMARK_FLAVOR) {
                    return bookmark;
                } else if (flavor == DataFlavor.stringFlavor) {
                    return Bookmark.formatBookmark(bookmark);
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            }
        };

    }

    Transferable createBookmarkListTransferrable(final List<Bookmark> bookmarks) {
        return new Transferable() {

            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{
                            DataFlavor.stringFlavor,
                            BOOKMARK_LIST_FLAVOR
                        };

            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == BOOKMARK_LIST_FLAVOR || flavor == DataFlavor.stringFlavor;
            }

            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                if (flavor == BOOKMARK_LIST_FLAVOR) {
                    return bookmarks;
                } else if (flavor == DataFlavor.stringFlavor) {
                    return Bookmark.formatBooks(bookmarks);
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            }
        };

    }
}
