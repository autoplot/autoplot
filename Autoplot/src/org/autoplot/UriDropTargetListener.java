/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import org.autoplot.ApplicationModel;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasPlot;
import org.das2.util.LoggerManager;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.layout.LayoutConstants;
import org.autoplot.datasource.DataSetSelector;
import org.xml.sax.SAXException;

/**
 * DropTarget allows URIs to be dropped onto plots of the page.
 * @author jbf
 */
public class UriDropTargetListener implements DropTargetListener {
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.gui.droptarget");
    
    DataSetSelector dss;
    ApplicationModel model;

    public UriDropTargetListener(DataSetSelector dss, ApplicationModel model) {
        this.model = model;
        this.dss = dss;
    }

    private String getURILinux( DropTargetDropEvent dtde )  {
        try {
            DataFlavor nixFileDataFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
            if ( dtde.isDataFlavorSupported(nixFileDataFlavor) ) {
                // assume that we have already accepted the drop. dtde.acceptDrop(DnDConstants.ACTION_NONE);
                String data = (String)dtde.getTransferable().getTransferData(nixFileDataFlavor);
                return data;
            } else {
                return null;
            }
        } catch (UnsupportedFlavorException ex ) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }
    /**
     * I was hoping we could peek to see if it really was a URI...
     * TODO: rewrite this.
     * @param dtde
     * @return
     */
    private String getURI( DropTargetDropEvent dtde ) {
        try {
            boolean haveAcceptedDrop= false;
            Bookmark item = null;
            List<Bookmark> items = null;
            if ( logger.isLoggable(Level.FINE) ) {
                for ( DataFlavor df: dtde.getCurrentDataFlavors() ) {
                    logger.log(Level.FINE, "drop data flavor: {0} {1}", new Object[]{df.getMimeType(), df.getHumanPresentableName()});
                }
            }
            if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                if ( !haveAcceptedDrop ) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    haveAcceptedDrop= true;
                }
                String data = ((String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim();
                if (data.length() > 19 && data.startsWith("<bookmark-list")) {
                    items = Bookmark.parseBookmarks(data);
                } else if (data.length() > 14 && data.startsWith("<bookmark")) {
                    item = Bookmark.parseBookmark(data);
                } else {
                    item = new Bookmark.Item(data);
                }
            }
            if ( item==null ) { // how to do the drop on a Mac???     
                dtde.getCurrentDataFlavorsAsList();
                DataFlavor df;
                try {
                    df = new DataFlavor("application/x-java-url;class=java.net.URL");
                    if ( dtde.isDataFlavorSupported( df ) ) {
                        if ( !haveAcceptedDrop ) {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY);
                            haveAcceptedDrop= true;
                        }
                        String data = String.valueOf( dtde.getTransferable().getTransferData(df) );
                        if (data.startsWith("file://localhost/")) {
                            data= data.substring(16); // mac at least does this...
                        }
                        item= new Bookmark.Item( data );
                    } else {
                        DataFlavor nixFileDataFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
                        if ( dtde.isDataFlavorSupported(nixFileDataFlavor) ) {
                            if ( !haveAcceptedDrop ) {
                               dtde.acceptDrop(DnDConstants.ACTION_COPY);
                                haveAcceptedDrop= true;
                            }
                            String data = (String)dtde.getTransferable().getTransferData(nixFileDataFlavor);
                            if ( data!=null ) {
                                item= new Bookmark.Item( data );
                            }
                        }
                    }
                } catch (ClassNotFoundException ex) {
                    logger.log(Level.FINE, "class not found for flavor, wrong platform");
                }
                try {
                    df = new DataFlavor("application/x-java-file-list;class=java.util.List");
                    if ( dtde.isDataFlavorSupported( df ) ) {
                        if ( !haveAcceptedDrop ) {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY);
                            haveAcceptedDrop= true;
                        }
                        List list= (List)dtde.getTransferable().getTransferData(df);
                        if ( list.size()==1 ) {
                            String data = list.get(0).toString();
                            if (data.startsWith("file://localhost/")) {
                                data= data.substring(16); // mac at least does this...
                            }
                            item= new Bookmark.Item( data );
                        }
                    } 
                } catch (ClassNotFoundException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            
            String uri=null;
            if ( item != null ) {
                if ( item instanceof Bookmark.Item ) {
                    uri= ((Bookmark.Item)item).getUri();
                } else {
                    model.showMessage( "only one URI can be dropped", "only one URI", JOptionPane.WARNING_MESSAGE);
                }
            } else if ( items!=null ) {
                model.showMessage( "only one URI can be dropped", "only one URI", JOptionPane.WARNING_MESSAGE);
            } else {
                model.showMessage("couldn't find URI in drop target", "no URI", JOptionPane.WARNING_MESSAGE);
            }
            return uri;

        } catch (UnsupportedFlavorException ex) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );

        } catch (IOException ex) {
            logger.log( Level.SEVERE, null, ex );

        } catch (SAXException ex) {
            logger.log( Level.SEVERE, null, ex );

        } catch (BookmarksException ex) {
            logger.log( Level.SEVERE, null, ex );
        }
        return null;

    }

    public void dragEnter(DropTargetDragEvent dtde) {

        Object s= dtde.getSource();

        DasCanvasComponent cc=null;
        if ( s instanceof DropTarget ) {
            Component c= ((DropTarget)s).getComponent();
            if ( c instanceof DasCanvasComponent ) cc= (DasCanvasComponent)c;
        }
        if ( cc instanceof DasPlot ) {
            if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }
        }

    }

    public void dragOver(DropTargetDragEvent dtde) {
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void dragExit(DropTargetEvent dte) {
    }

    public void drop(DropTargetDropEvent dtde) {
        
        String uri= getURI( dtde );

        if (uri == null) {
            return;
            
        } else {

            DasCanvasComponent cc=null;

            Object s= dtde.getSource();

            if ( s instanceof DropTarget ) {
                Component c= ((DropTarget)s).getComponent();
                if ( c instanceof DasCanvasComponent ) cc= (DasCanvasComponent)c;
            }

            Plot plot;
            if ( cc instanceof DasPlot ) {
                plot= model.dom.getController().getPlotFor(cc);
                if ( plot==null ) plot= null;
            } else {
                plot= null;
            }

            if ( plot==null ) {
                DasCanvasComponent dcc=  model.getCanvas().getCanvasComponentAt( dtde.getLocation().x, dtde.getLocation().y-50  );
                if ( dcc!=null ) {
                    plot= model.dom.getController().getPlotFor(dcc);
                    plot= model.dom.getController().addPlot( plot, LayoutConstants.BELOW );
                } else {
                    dcc=  model.getCanvas().getCanvasComponentAt( dtde.getLocation().x, dtde.getLocation().y+50  );
                    if ( dcc!=null ) {
                        plot= model.dom.getController().getPlotFor(dcc);
                        plot= model.dom.getController().addPlot( plot, LayoutConstants.ABOVE );
                    }
                }

                if ( plot==null ) {
                    model.showMessage("URIs may only be dropped on plots, just above, or just below", "not plot target", JOptionPane.WARNING_MESSAGE);
                } else {
                    PlotElement pe= model.dom.getController().addPlotElement( plot, null );
                    model.dom.getController().setPlotElement(pe);
                    dss.setValue(uri);
                    dss.maybePlot(true);
                }
                
                return;
            } else {

                final List<PlotElement> pe = model.dom.getController().getPlotElementsFor(plot);
                if (pe.size() == 0) {
                    model.showMessage("no plot elements here", "no plot elements", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                model.dom.getController().setPlotElement(pe.get(0)); // set the focus

                dss.setValue(uri);
                dss.maybePlot(true);
            }

        }
    }

}
