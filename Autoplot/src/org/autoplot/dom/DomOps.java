/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSourceUtil;

/**
 * Many operations are defined within the DOM object controllers that needn't
 * be.  This class is a place for operations that are performed on the DOM
 * independent of the controllers.  For example, the operation to swap the
 * position of two plots is easily implemented by changing the rowid and columnid
 * properties of the two plots.
 *
 * @author jbf
 */
public class DomOps {
    
    private static final Logger logger = LoggerManager.getLogger("autoplot.dom");
    
    /**
     * swap the position of the two plots.  If one plot has its tick labels hidden,
     * then this is swapped as well.
     * @param a
     * @param b
     */
    public static void swapPosition( Plot a, Plot b ) {
        if ( a==b ) return;
        String trowid= a.getRowId();
        String tcolumnid= a.getColumnId();
        boolean txtv= a.getXaxis().isDrawTickLabels();
        boolean tytv= a.getYaxis().isDrawTickLabels();

        a.setRowId(b.getRowId());
        a.setColumnId(b.getColumnId());
        a.getXaxis().setDrawTickLabels(b.getXaxis().isDrawTickLabels());
        a.getYaxis().setDrawTickLabels(b.getYaxis().isDrawTickLabels());
        b.setRowId(trowid);
        b.setColumnId(tcolumnid);
        b.getXaxis().setDrawTickLabels(txtv);
        b.getYaxis().setDrawTickLabels(tytv);

    }

    /**
     * Copy the plot and its axis settings, optionally binding the axes. Whether
     * the axes are bound or not, the duplicate plot is initially synchronized to
     * the source plot.
     * See {@link org.autoplot.dom.ApplicationController#copyPlot(org.autoplot.dom.Plot, boolean, boolean, boolean) copyPlot}
     * @param srcPlot
     * @param bindx
     * @param bindy
     * @param direction
     * @return the new plot
     *
     */    
    public static Plot copyPlot(Plot srcPlot, boolean bindx, boolean bindy, Object direction ) {
        Application application= srcPlot.getController().getApplication();
        ApplicationController ac= application.getController();

        Plot that = ac.addPlot( direction );
        that.getController().setAutoBinding(false);

        that.syncTo( srcPlot, Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID ) );

        if (bindx) {
            BindingModel bb = ac.findBinding(application, Application.PROP_TIMERANGE, srcPlot.getXaxis(), Axis.PROP_RANGE);
            if (bb == null) {
                ac.bind(srcPlot.getXaxis(), Axis.PROP_RANGE, that.getXaxis(), Axis.PROP_RANGE);
            } else {
                ac.bind(application, Application.PROP_TIMERANGE, that.getXaxis(), Axis.PROP_RANGE);
            }

        }

        if (bindy) {
            ac.bind(srcPlot.getYaxis(), Axis.PROP_RANGE, that.getYaxis(), Axis.PROP_RANGE);
        }

        return that;

    }

    /**
     * copy the plot elements from srcPlot to dstPlot.  This does not appear
     * to be used.
     * See {@link org.autoplot.dom.ApplicationController#copyPlotElement(org.autoplot.dom.PlotElement, org.autoplot.dom.Plot, org.autoplot.dom.DataSourceFilter) copyPlotElement}
     * @param srcPlot plot containing zero or more plotElements.
     * @param dstPlot destination for the plotElements.
     * @return 
     */
    public static List<PlotElement> copyPlotElements( Plot srcPlot, Plot dstPlot ) {

        ApplicationController ac=  srcPlot.getController().getApplication().getController();
        List<PlotElement> srcElements = ac.getPlotElementsFor(srcPlot);

        List<PlotElement> newElements = new ArrayList<PlotElement>();
        for (PlotElement srcElement : srcElements) {
            if (!srcElement.getComponent().equals("")) {
                if ( srcElement.getController().getParentPlotElement()==null ) {
                    PlotElement newp = ac.copyPlotElement(srcElement, dstPlot, null);
                    newElements.add(newp);
                }
            } else {
                PlotElement newp = ac.copyPlotElement(srcElement, dstPlot, null);
                newElements.add(newp);
                List<PlotElement> srcKids = srcElement.controller.getChildPlotElements();
                DataSourceFilter dsf1 = ac.getDataSourceFilterFor(newp);
                for (PlotElement k : srcKids) {
                    if (srcElements.contains(k)) {
                        PlotElement kidp = ac.copyPlotElement(k, dstPlot, dsf1);
                        kidp.getController().setParentPlotElement(newp);
                        newElements.add(kidp);
                    }
                }
            }
        }
        return newElements;

    }

    /**
     * copyPlotAndPlotElements.  This does not appear to be used.
     * See {@link org.autoplot.dom.ApplicationController#copyPlotAndPlotElements(org.autoplot.dom.Plot, org.autoplot.dom.DataSourceFilter, boolean, boolean) copyPlotAndPlotElements}
     * @param srcPlot
     * @param copyPlotElements
     * @param bindx
     * @param bindy
     * @param direction
     * @return 
     */
    public static Plot copyPlotAndPlotElements( Plot srcPlot, boolean copyPlotElements, boolean bindx, boolean bindy, Object direction ) {
        Plot dstPlot= copyPlot( srcPlot, bindx, bindy, direction );
        if ( copyPlotElements ) copyPlotElements( srcPlot, dstPlot );
        return dstPlot;
    }

    /**
     * Used in the LayoutPanel's add hidden plot, get the column of 
     * the selected plot or create a new column if several plots are
     * selected.
     * @param dom the application.
     * @param selectedPlots the selected plots.
     * @param create allow a new column to be created.
     * @return 
     */
    public static Column getOrCreateSelectedColumn( Application dom, List<Plot> selectedPlots, boolean create ) {
        Set<String> n= new HashSet<>();
        for ( Plot p: selectedPlots ) {
            n.add( p.getColumnId() );
        }
        if ( n.size()==1 ) {
            return (Column) DomUtil.getElementById(dom,n.iterator().next());
        } else {
            if ( create ) {
                Canvas c= dom.getCanvases(0); //TODO: do this
                Column col= c.getController().addColumn();
                col.setLeft("0%");
                col.setRight("100%");
                return col;
            } else {
                return null;
            }
        }
    }

    /**
     * Used in the LayoutPanel's add hidden plot, get the row of 
     * the selected plot or create a new row if several plots are
     * selected.
     * @param dom the application.
     * @param selectedPlots the selected plots.
     * @param create allow a new column to be created.
     * @return 
     */    
    public static Row getOrCreateSelectedRow( Application dom, List<Plot> selectedPlots, boolean create ) {
        Set<String> n= new HashSet<>();
        for ( Plot p: selectedPlots ) {
            if ( !n.contains(p.getRowId()) ) n.add( p.getRowId() );
        }
        if ( n.size()==1 ) {
            return (Row) DomUtil.getElementById(dom,n.iterator().next());
        } else {
            if ( create ) {
                Iterator<String> iter= n.iterator();
                Row r= (Row) DomUtil.getElementById( dom.getCanvases(0), iter.next() );
                Row rmax= r;
                Row rmin= r;
                for ( int i=1; iter.hasNext(); i++ ) {
                    r= (Row) DomUtil.getElementById( dom.getCanvases(0), iter.next() );
                    if ( r.getController().getDasRow().getDMaximum()>rmax.getController().getDasRow().getDMaximum() ) {
                        rmax= r;
                    }
                    if ( r.getController().getDasRow().getDMinimum()<rmin.getController().getDasRow().getDMinimum() ) {
                        rmin= r;
                    }
                }
                Canvas c= dom.getCanvases(0);
                Row row= c.getController().addRow();
                row.setTop(rmin.getTop());
                row.setBottom(rmax.getBottom());
                return row;
            } else {
                return null;
            }
        }
    }

    /**
     * return the bottom and top most plot of a list of plots.  
     * This does use controllers.
     * @param dom
     * @param plots
     * @return
     */
    public static Plot[] bottomAndTopMostPlot( Application dom, List<Plot> plots ) {
        Plot pmax=plots.get(0);
        Plot pmin=plots.get(0);
        Row r= (Row) DomUtil.getElementById( dom.getCanvases(0), pmax.getRowId() );
        Row rmax= r;
        Row rmin= r;
        for ( Plot p: plots ) {
            r= (Row) DomUtil.getElementById( dom.getCanvases(0), p.getRowId() );
            if ( r.getController().getDasRow().getDMaximum()>rmax.getController().getDasRow().getDMaximum() ) {
                rmax= r;
                pmax= p;
            }
            if ( r.getController().getDasRow().getDMinimum()<rmin.getController().getDasRow().getDMinimum() ) {
                rmin= r;
                pmin= p;
            }
        }
        return new Plot[] { pmax, pmin };
    }

    /**
     * return a list of the plots using the given row.
     * This does not use controllers.
     * @param row the row to search for.
     * @param visible  if true, then the plot must also be visible.  (Note its colorbar visible is ignored.)
     * @return a list of plots.
     */
    public static List<Plot> getPlotsFor( Application dom, Row row, boolean visible ) {
        ArrayList<Plot> result= new ArrayList();
        for ( Plot p: dom.getPlots() ) {
            if ( p.getRowId().equals(row.getId()) ) {
                if ( visible ) {
                    if ( p.isVisible() ) result.add(p);
                } else {
                    result.add(p);
                }
            }
        }
        return result;
    }

    /**
     * count the number of lines in the string.
     * @param s
     * @return
     */
    private static int lineCount( String s ) {
        String[] ss= s.split("![cC]");
        int emptyLines=0;
        while ( emptyLines<ss.length && ss[emptyLines].trim().length()==0 ) {
            emptyLines++;
        }
        return ss.length - emptyLines;
    }

    /**
     * play with new canvas layout.  This started as a Jython Script, but it's faster to implement here.
     * See http://autoplot.org/developer.autolayout#Algorithm
     * @param dom
     */
    public static void newCanvasLayout( Application dom ) {
        
        Canvas canvas= dom.getCanvases(0);

        double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();
        double pixelsToEm= 1/emToPixels;

        Row[] rows= canvas.getRows();
        int nrow= rows.length;

        //kludge: check for duplicate names of rows.  Use the first one found.
        Map<String,Row> rowsCheck= new HashMap();
        List<Row> rm= new ArrayList<Row>();
        for ( int i=0; i<nrow; i++ ) {           
           List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );

           if ( plots.size()>0 ) {
               if ( rowsCheck.containsKey(rows[i].getId()) ) {
                   logger.log(Level.FINE, "duplicate row id: {0}", rows[i].getId());
                   rm.add( rows[i] );
               } else {
                   rowsCheck.put( rows[i].getId(), rows[i] );
               }
            } else {
               logger.log(Level.FINE, "unused row: {0}", rows[i]);
               rm.add( rows[i] );
           }
        }
        for ( Row r : rm ) {
            canvas.getController().deleteRow(r);
        }
        rows= canvas.getRows();
        nrow= rows.length;
 
        double totalPlotHeight= 0;
        for ( int i=0; i<nrow; i++ ) {           
           List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );

           if ( plots.size()>0 ) {
               DasRow dasRow= rows[i].getController().dasRow;
               totalPlotHeight= totalPlotHeight + dasRow.getHeight();
           }
        }
        
        double [] MaxUp= new double[ nrow ];
        double [] MaxDown= new double[ nrow ];

        for ( int i=0; i<nrow; i++ ) {
            List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );
            double MaxUpJEm;
            double MaxDownJ;
            for ( Plot plotj : plots ) {
                String title= plotj.getTitle();
                MaxUpJEm= plotj.isDisplayTitle() ? lineCount(title) : 0.;
                if (MaxUpJEm>0 ) MaxUpJEm= MaxUpJEm+1;
                MaxUp[i]= Math.max( MaxUp[i], MaxUpJEm*emToPixels );
                Rectangle plot= plotj.getController().getDasPlot().getBounds();
                Rectangle axis= plotj.getXaxis().getController().getDasAxis().getBounds();
                MaxDownJ= ( ( axis.getY() + axis.getHeight() ) - ( plot.getY() + plot.getHeight() ) + emToPixels );
                MaxDown[i]= Math.max( MaxDown[i], MaxDownJ );
            }
        }

        double [] relativePlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
            DasRow dasRow= rows[i].getController().dasRow;
            relativePlotHeight[i]= 1.0 * dasRow.getHeight() / totalPlotHeight;
        }

        double newPlotTotalHeight= canvas.height;
        for ( int i=0; i<nrow; i++ ) {
            newPlotTotalHeight = newPlotTotalHeight - MaxUp[i] - MaxDown[i];
        }

        double [] PlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
            PlotHeight[i]= newPlotTotalHeight * relativePlotHeight[i];
        }

        double[] normalPlotHeight= new double[ nrow ];

        double height= dom.getCanvases(0).getMarginRow().getController().getDasRow().getHeight();
        for ( int i=0; i<nrow; i++ ) {
             normalPlotHeight[i]= ( PlotHeight[i] + MaxUp[i] + MaxDown[i] ) / height;
        }

        double position=0;

        for ( int i=0; i<nrow; i++ ) {
            String newTop=  String.format( Locale.US, "%.2f%%%+.1fem", 100*position, MaxUp[i] * pixelsToEm );
            rows[i].setTop( newTop );
            position+= normalPlotHeight[i];
            String newBottom= String.format( Locale.US, "%.2f%%%+.1fem", 100*position, -1 * MaxDown[i] * pixelsToEm );
            rows[i].setBottom( newBottom );

        }


    }

    /**
     * aggregate all the URIs within the dom.
     * @param dom
     */
    public static void aggregateAll( Application dom ) {
        Application oldDom= (Application) dom.copy(); // axis settings, etc.
        DataSourceFilter[] dsfs= dom.getDataSourceFilters();
        for ( DataSourceFilter dsf: dsfs ) {
            if ( dsf.uri==null || dsf.uri.length()==0 ) continue;
            if ( dsf.uri.startsWith("vap+internal:") ) continue;
            String agg= DataSourceUtil.makeAggregation( dsf.uri );
            if ( agg!=null ) {
                dsf.setUri(agg);
            }
        }
        dom.setDataSourceFilters(dsfs);
        dom.syncTo( oldDom, Collections.singletonList( "dataSourceFilters" ) );

    }
}
