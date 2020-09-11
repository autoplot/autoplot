
package org.autoplot.dom;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
        
        if ( a.controller!=null ) {
            a.controller.dom.options.setAutolayout( false );
        }
        
        String trowid= a.getRowId();
        String tcolumnid= a.getColumnId();
        boolean txtv= a.getXaxis().isDrawTickLabels();
        boolean tytv= a.getYaxis().isDrawTickLabels();

        String ticksUriA= a.getTicksURI();
        String ticksUriB= b.getTicksURI();
        a.setTicksURI( ticksUriB );
        b.setTicksURI( ticksUriA );        
        a.setRowId(b.getRowId());
        a.setColumnId(b.getColumnId());
        a.getXaxis().setDrawTickLabels(b.getXaxis().isDrawTickLabels());
        a.getYaxis().setDrawTickLabels(b.getYaxis().isDrawTickLabels());
        b.setRowId(trowid);
        b.setColumnId(tcolumnid);
        b.getXaxis().setDrawTickLabels(txtv);
        b.getYaxis().setDrawTickLabels(tytv);

        if ( a.controller!=null ) {
            a.controller.dom.controller.waitUntilIdle();
            a.controller.dom.options.setAutolayout( true );
        }
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

        List<PlotElement> newElements = new ArrayList<>();
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
     * @param dom a dom
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
     * count the number of lines in the string, breaking on "!c"
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
        fixLayout( dom );
        
    }

    /**
     * New layout mechanism which fixes a number of shortcomings of the old layout mechanism, 
     * newCanvasLayout.  This one:<ul>
     * <li> Removes extra whitespace
     * <li> Preserves relative size weights.
     * <li> Preserves em heights, to support components which should not be rescaled. (Not yet supported.)
     * <li> Preserves space taken by strange objects, to support future canvas components.
     * <li> Renormalizes the margin row, so it is nice. (Not yet supported.  This should consider font size, where large fonts don't need so much space.)
     * </ul>
     * This should also be idempotent, where calling this a second time should have no effect.
     * @param dom an application state, with controller nodes. TODO: remove dependence on controller nodes.
     */
    public static void fixLayout( Application dom ) {
        Logger logger= LoggerManager.getLogger("autoplot.dom.layout");
                
        Canvas canvas= dom.getCanvases(0);

        double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();
        double pixelsToEm= 1/emToPixels;

        Row[] rows= canvas.getRows();
        int nrow= rows.length;

        //kludge: check for duplicate names of rows.  Use the first one found.
        Map<String,Row> rowsCheck= new HashMap();
        List<Row> rm= new ArrayList<>();
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
 
        // sort rows, which is a refactoring.
        Arrays.sort( rows, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                Row r1= (Row)o1;
                Row r2= (Row)o2;
                int d1= r1.getController().getDasRow().getDMinimum();
                int d2= r2.getController().getDasRow().getDMinimum();
                return d1-d2;
            }
        });
        
        double totalPlotHeightPixels= 0;
        for ( int i=0; i<nrow; i++ ) {           
           List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );

           if ( plots.size()>0 ) {
               DasRow dasRow= rows[i].getController().dasRow;
               totalPlotHeightPixels= totalPlotHeightPixels + dasRow.getHeight();
           }
        }
        
        double [] MaxUp= new double[ nrow ];
        double [] MaxDown= new double[ nrow ];

//        double[] emHeight= new double[ nrow ];
//        for ( int i=0; i<nrow; i++ ) {
//            DasRow dasRow= rows[i].getController().dasRow;
//            emHeight[i]= ( dasRow.getEmMaximum() - dasRow.getEmMinimum() );
//        }// I know there's some check we can do with this to preserve 1-em high plots.
        
        for ( int i=0; i<nrow; i++ ) {
            List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );
            double MaxUpJEm;
            double MaxDownPx;
            for ( Plot plotj : plots ) {
                String title= plotj.getTitle();
                MaxUpJEm= plotj.isDisplayTitle() ? Math.max( 2, lineCount(title) ) : 0.;
                //if (MaxUpJEm>0 ) MaxUpJEm= MaxUpJEm+1;
                MaxUp[i]= Math.max( MaxUp[i], MaxUpJEm*emToPixels );
                Rectangle plot= plotj.getController().getDasPlot().getBounds();
                Rectangle axis= plotj.getXaxis().getController().getDasAxis().getBounds();
                MaxDownPx= ( ( axis.getY() + axis.getHeight() ) - ( plot.getY() + plot.getHeight() ) + 1 * emToPixels );
                MaxDown[i]= Math.max( MaxDown[i], MaxDownPx );
            }
        }

        double [] relativePlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
            DasRow dasRow= rows[i].getController().dasRow;
            relativePlotHeight[i]= 1.0 * dasRow.getHeight() / totalPlotHeightPixels;
        }
        
        double newPlotTotalHeightPixels= canvas.height;
        for ( int i=0; i<nrow; i++ ) {
            newPlotTotalHeightPixels = newPlotTotalHeightPixels - MaxUp[i] - MaxDown[i];
        }

        double [] newPlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
            newPlotHeight[i]= newPlotTotalHeightPixels * relativePlotHeight[i];
        }

        double[] normalPlotHeight= new double[ nrow ];

        double height= dom.getCanvases(0).getMarginRow().getController().getDasRow().getHeight();
        
        double marginHeightPixels= 
                ( dom.getCanvases(0).getMarginRow().getController().getDasRow().getEmMinimum() -
                dom.getCanvases(0).getMarginRow().getController().getDasRow().getEmMaximum() ) * emToPixels ;
        
        if ( nrow==1 ) {
            normalPlotHeight[0]= ( newPlotHeight[0] + MaxUp[0] + MaxDown[0] ) / ( height + marginHeightPixels );
        } else {
            for ( int i=0; i<nrow; i++ ) {
                 normalPlotHeight[i]= ( newPlotHeight[i] + MaxUp[i] + MaxDown[i] ) / ( height + marginHeightPixels );
            }
        }

        double position=0;

        for ( int i=0; i<nrow; i++ ) {
            String newTop=  String.format( Locale.US, "%.2f%%%+.1fem", 100*position, MaxUp[i] * pixelsToEm );
            rows[i].setTop( newTop );
            position+= normalPlotHeight[i];
            String newBottom= String.format( Locale.US, "%.2f%%%+.1fem", 100*position, -1 * MaxDown[i] * pixelsToEm );
            rows[i].setBottom( newBottom );
            logger.log(Level.FINE, "row {0}: {1},{2}", new Object[]{i, newTop, newBottom});
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
