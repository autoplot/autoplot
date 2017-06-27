
package org.autoplot.bookmarks;

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
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import org.xml.sax.SAXException;

/**
 * support the bookmarks manager by adding drag and drop
 * @author jbf
 */
public class BookmarksManagerTransferrable {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.bookmarks");

    private final BookmarksManagerModel model;
    private final JTree jTree1;

    BookmarksManagerTransferrable(BookmarksManagerModel model, JTree jTree1) {
        this.model = model;
        this.jTree1 = jTree1;
    }

    DropTargetListener createDropTargetListener() {
        return new DropTargetListener() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(BOOKMARK_FLAVOR)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);

                } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    boolean readonly= false; // only copy
                    Transferable transferable= dtde.getTransferable();
                    if ( transferable instanceof BookmarkTransferable ) {
                        readonly= ((BookmarkTransferable)transferable).readonly;
                    }
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

                    String remoteUrl= BookmarksManager.maybeGetRemoteBookmarkUrl( context, model, jTree1.getModel(), tp );
                    if ( remoteUrl.length()>0 ) {
                        JOptionPane.showMessageDialog( jTree1, "Drop target is within remote bookmarks\n"+remoteUrl, "Remote Bookmark Move Item",JOptionPane.OK_OPTION );
                        return;
                    }
                    if (item != null) {
                        if (item == context) return;
                        if ( !readonly ) model.removeBookmark(item);
                        model.insertBookmark(item, context);
                    } else if (items != null) {
                        if ( !readonly ) model.removeBookmarks(items);
                        model.addBookmarks(items, context, true);
                    }

                } catch (UnsupportedFlavorException | BookmarksException | IOException | SAXException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            }
        };
    }

    DragGestureListener createDragGestureListener() {
        return new DragGestureListener() {

            @Override
            public void dragGestureRecognized(DragGestureEvent dge) {

                if ( jTree1.getSelectionCount()==1 ) {
                    TreePath tp= jTree1.getSelectionPath();
                    Bookmark b = model.getSelectedBookmark(jTree1.getModel(), tp );
                    String remoteUrl= BookmarksManager.maybeGetRemoteBookmarkUrl( b, model, jTree1.getModel(), tp );
                    if ( remoteUrl.length()>0 ) {
                        System.err.println("Copy from remote bookmarks");
                    }
                    //Toolkit tk= Toolkit.getDefaultToolkit();
                    if (b instanceof Bookmark.Item) {
                        //Cursor c= tk.createCustomCursor( b.getIcon().getImage(), new Point( 0,0), "bookmark");
                        dge.startDrag(null, new BookmarkTransferable((Bookmark.Item) b, remoteUrl.length()==0 ));

                    } else if (b instanceof Bookmark.Folder) {
                        dge.startDrag(null, new BookmarkTransferable((Bookmark.Folder) b, remoteUrl.length()==0 ));
                    }
                } else {
                    List<Bookmark> books= new ArrayList<>();
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
    
    public static final DataFlavor BOOKMARK_FLAVOR = new DataFlavor(Bookmark.class, "Bookmark");
    public static final DataFlavor BOOKMARK_LIST_FLAVOR = new DataFlavor(List.class, "BookmarkList");

    public static class BookmarkTransferable implements Transferable {

        Bookmark bookmark;
        boolean readonly;

        /**
         * @param bookmark the bookmark
         * @param readOnly  don't attempt delete from the source location.
         */
        public BookmarkTransferable( final Bookmark bookmark, boolean readOnly ) {
            this.bookmark= bookmark;
            this.readonly= readOnly;
        }

        /**
         * return the flavors, either a string (the URI), or the internal BOOKMARK_FLAVOR
         * @return the flavors
         */
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{
                        DataFlavor.stringFlavor,
                        BOOKMARK_FLAVOR
                    };

        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor == DataFlavor.stringFlavor || flavor == BOOKMARK_FLAVOR;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if ( bookmark instanceof Bookmark.Item ) {
                if (flavor == DataFlavor.stringFlavor) {
                    return ((Bookmark.Item)bookmark).getUri();
                } else if (flavor == BOOKMARK_FLAVOR) {
                    return bookmark;
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            } else {
                if (flavor == BOOKMARK_FLAVOR) {
                    return bookmark;
                } else if (flavor == DataFlavor.stringFlavor) {
                    return Bookmark.formatBookmark(bookmark);
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            }
        }
    }


    Transferable createBookmarkListTransferrable(final List<Bookmark> bookmarks) {
        return new Transferable() {

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{
                            DataFlavor.stringFlavor,
                            BOOKMARK_LIST_FLAVOR
                        };

            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == BOOKMARK_LIST_FLAVOR || flavor == DataFlavor.stringFlavor;
            }

            @Override
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
