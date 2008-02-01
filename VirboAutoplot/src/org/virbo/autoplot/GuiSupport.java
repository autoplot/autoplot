/*
 * GuiSupport.java
 *
 * Created on November 30, 2007, 5:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.dataset.TableDataSet;
import edu.uiowa.physics.pw.das.dataset.TableUtil;
import edu.uiowa.physics.pw.das.dataset.VectorDataSet;
import edu.uiowa.physics.pw.das.dataset.VectorUtil;
import edu.uiowa.physics.pw.das.graph.DasCanvas;
import edu.uiowa.physics.pw.das.graph.PsymConnector;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.virbo.autoplot.transferrable.ImageSelection;
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.VectorDataSetAdapter;

/**
 *
 * @author jbf
 */
public class GuiSupport {
    
    AutoPlotMatisse parent;
    public GuiSupport( AutoPlotMatisse parent ) {
        this.parent= parent;
    }
    
    public void doPasteDataSetURL() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);
        boolean hasTransferableText =
                (contents != null) &&
                contents.isDataFlavorSupported(DataFlavor.stringFlavor)
                ;
        String result=null;
        if ( hasTransferableText ) {
            try {
                result = (String)contents.getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException ex){
                //highly unlikely since we are using a standard DataFlavor
                System.out.println(ex);
                ex.printStackTrace();
            } catch (IOException ex) {
                System.out.println(ex);
                ex.printStackTrace();
            }
        }
        if ( result!=null ) {
            parent.dataSetSelector.setValue(result);
        }
    }
    
    public void doCopyDataSetURL() {
        StringSelection stringSelection = new StringSelection( parent.dataSetSelector.getValue() );
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents( stringSelection, new ClipboardOwner() {
            public void lostOwnership(Clipboard clipboard, Transferable contents) {
            }
        } );
    }
    
    public void doCopyDataSetImage() {
        Runnable run= new Runnable() {
            public void run() {
                ImageSelection imageSelection = new ImageSelection();
                DasCanvas c= parent.applicationModel.canvas;
                Image i= c.getImage( c.getWidth(), c.getHeight() );
                imageSelection.setImage( i );
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents( imageSelection, new ClipboardOwner() {
                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                    }
                } );
            }
        };
        new Thread(run).start();
    }
    
    Action getDumpDataAction() {
        return new AbstractAction( "Export Data To ASCII" ) {
            public void actionPerformed( ActionEvent e ) {
                JFileChooser chooser= new JFileChooser();
                int r= chooser.showSaveDialog( parent );
                if ( r==JFileChooser.APPROVE_OPTION ) {
                    try {
                        if ( parent.applicationModel.fillDataset!=null ) {
                            if ( parent.applicationModel.fillDataset.rank()==2 ) {
                                TableDataSet tds= TableDataSetAdapter.create( parent.applicationModel.fillDataset );
                                FileOutputStream fo=  new FileOutputStream( chooser.getSelectedFile() );
                                TableUtil.dumpToAsciiStream( tds, fo );
                                fo.close();
                            } else if (  parent.applicationModel.fillDataset.rank()==1 ) {
                                VectorDataSet vds= VectorDataSetAdapter.create( parent.applicationModel.fillDataset );
                                FileOutputStream fo=  new FileOutputStream( chooser.getSelectedFile() );
                                VectorUtil.dumpToAsciiStream( vds, fo );
                                fo.close();
                            }
                        }
                    } catch ( IOException ex ) {
                        parent.applicationModel.application.getExceptionHandler().handle(ex);
                    }
                }
            }
        };
    }
    
    JMenu createEZAccessMenu() {
        final ApplicationModel model= parent.applicationModel;
        JMenu result = new JMenu("plot style");
        result.add(new JMenuItem(new AbstractAction("scatter") {
            public void actionPerformed(ActionEvent e) {
                model.seriesRend.setPsymConnector(PsymConnector.NONE);
                model.seriesRend.setHistogram(false);
                model.seriesRend.setFillToReference(false);
                model.setRenderer(model.seriesRend,model.overSeriesRend);
            }
        }));
        
        result.add(new JMenuItem(new AbstractAction("series") {
            
            public void actionPerformed(ActionEvent e) {
                model.seriesRend.setPsymConnector(PsymConnector.SOLID);
                model.seriesRend.setHistogram(false);
                model.seriesRend.setFillToReference(false);
                model.setRenderer(model.seriesRend,model.overSeriesRend);
            }
        }));
        
        result.add(new JMenuItem(new AbstractAction("histogram") {
            
            public void actionPerformed(ActionEvent e) {
                model.seriesRend.setPsymConnector(PsymConnector.SOLID);
                model.seriesRend.setHistogram(true);
                model.seriesRend.setFillToReference(false);
                model.setRenderer(model.seriesRend,model.overSeriesRend);
            }
        }));
        
        result.add(new JMenuItem(new AbstractAction("fill below") {
            
            public void actionPerformed(ActionEvent e) {
                model.seriesRend.setPsymConnector(PsymConnector.SOLID);
                model.seriesRend.setHistogram(true);
                
                model.seriesRend.setFillToReference(true);
                model.setRenderer(model.seriesRend,model.overSeriesRend);
            }
        }));
        
        result.add(new JMenuItem(new AbstractAction("spectrogram") {
            
            public void actionPerformed(ActionEvent e) {
                model.setRenderer(model.spectrogramRend,model.overSpectrogramRend);
            }
        }));
        
        return result;
    }
    
}
