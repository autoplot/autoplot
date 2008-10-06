/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 * support the bookmarks manager by adding drag and drop
 * @author jbf
 */
public class BookmarksManagerTransferrable {
    private BookmarksManagerModel model;
    private JTree jTree1;
    
    BookmarksManagerTransferrable( BookmarksManagerModel model, JTree jTree1 ) {
        this.model= model;
        this.jTree1= jTree1;
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
                    Bookmark item=null;
                    if (dtde.isDataFlavorSupported(BOOKMARK_FLAVOR)) {
                        item = (Bookmark) dtde.getTransferable().getTransferData(BOOKMARK_FLAVOR);
                    } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String data= (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        if ( data.length()>14 && data.startsWith("<bookmark") ) {
                            try {
                                item = Bookmark.parseBookmark(data);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        } else {
                            item = new Bookmark.Item(data);
                        }
                    }
                    TreePath tp= jTree1.getPathForLocation( (int)dtde.getLocation().getX(), (int)dtde.getLocation().getY() );
                    Bookmark context= model.getSelectedBookmark( jTree1.getModel(), tp );
                    
                    if ( item==context ) return;
                    model.removeBookmark(item);
                    model.addBookmark(item, context);
                    
                } catch (UnsupportedFlavorException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(BookmarksManager.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        };
    }

    DropTarget createDropTarget() {
        return new DropTarget() {

            public void dragEnter(DragSourceDragEvent dsde) {
                System.err.println("dragEnter");
            }

            public void dragOver(DragSourceDragEvent dsde) {
                System.err.println("dragOver");
            }

            public void dropActionChanged(DragSourceDragEvent dsde) {
                System.err.println("dropActionChanged");
            }

            public void dragExit(DragSourceEvent dse) {
                System.err.println("dragExit");
            }

            public void dragDropEnd(DragSourceDropEvent dsde) {
                System.err.println("dragDropEnd");
            }
        };
    }

    DragGestureListener createDragGestureListener() {
        return new DragGestureListener() {

            public void dragGestureRecognized(DragGestureEvent dge) {

                Bookmark b = model.getSelectedBookmark(jTree1.getModel(), jTree1.getSelectionPath());
                //Toolkit tk= Toolkit.getDefaultToolkit();
                if (b instanceof Bookmark.Item) {
                    //Cursor c= tk.createCustomCursor( b.getIcon().getImage(), new Point( 0,0), "bookmark");
                    dge.startDrag( null, createBookmarkTransferrable((Bookmark.Item) b));
                    
                } else if ( b instanceof Bookmark.Folder ) {
                    dge.startDrag(null, createBookmarkTransferrable((Bookmark.Folder) b));
                }

            }
        };
    }
    DataFlavor BOOKMARK_FLAVOR = new DataFlavor(Bookmark.class, "Bookmark");

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
                    return bookmark.getUrl();
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
                } else if ( flavor==DataFlavor.stringFlavor ) {
                    return Bookmark.formatBookmark( bookmark );
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            }
        };

    }
    
}
