/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.das2.datum.DatumVector;
import org.das2.graph.DasRow;
import org.das2.graph.TickVDescriptor;
import org.virbo.datasource.DataSourceUtil;

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
    /**
     * swap the position of the two plots.  If one plot has its tick labels hidden,
     * then this is swapped as well.
     * @param a
     * @param b
     */
    public static void swapPosition( Plot a, Plot b ) {
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


    public static List<PlotElement> copyPlotElements( Plot srcPlot, Plot dstPlot ) {

        DataSourceFilter dsf= null;

        ApplicationController ac=  srcPlot.getController().getApplication().getController();
        List<PlotElement> srcElements = ac.getPlotElementsFor(srcPlot);

        List<PlotElement> newElements = new ArrayList<PlotElement>();
        for (PlotElement srcElement : srcElements) {
            if (!srcElement.getComponent().equals("")) {
                if ( srcElement.getController().getParentPlotElement()==null ) {
                    PlotElement newp = ac.copyPlotElement(srcElement, dstPlot, dsf);
                    newElements.add(newp);
                }
            } else {
                PlotElement newp = ac.copyPlotElement(srcElement, dstPlot, dsf);
                newElements.add(newp);
                List<PlotElement> srcKids = srcElement.controller.getChildPlotElements();
                List<PlotElement> newKids = new ArrayList();
                DataSourceFilter dsf1 = ac.getDataSourceFilterFor(newp);
                for (PlotElement k : srcKids) {
                    if (srcElements.contains(k)) {
                        PlotElement kidp = ac.copyPlotElement(k, dstPlot, dsf1);
                        kidp.getController().setParentPlotElement(newp);
                        newElements.add(kidp);
                        newKids.add(kidp);
                    }
                }
            }
        }
        return newElements;

    }

    
    public static Plot copyPlotAndPlotElements( Plot srcPlot, boolean copyPlotElements, boolean bindx, boolean bindy, Object direction ) {
        Plot dstPlot= copyPlot( srcPlot, bindx, bindy, direction );
        if ( copyPlotElements ) copyPlotElements( srcPlot, dstPlot );
        return dstPlot;
    }

    public static Column getOrCreateSelectedColumn( Application dom, List<Plot> selectedPlots, boolean create ) {
        List<String> n= new ArrayList<String>();
        for ( Plot p: selectedPlots ) {
            n.add( p.getColumnId() );
        }
        if ( n.size()==1 ) {
            return (Column) DomUtil.getElementById(dom,n.get(0));
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

    public static Row getOrCreateSelectedRow( Application dom, List<Plot> selectedPlots, boolean create ) {
        List<String> n= new ArrayList<String>();
        for ( Plot p: selectedPlots ) {
            if ( !n.contains(p.getRowId()) ) n.add( p.getRowId() );
        }
        if ( n.size()==1 ) {
            return (Row) DomUtil.getElementById(dom,n.get(0));
        } else {
            if ( create ) {
                Row r= (Row) DomUtil.getElementById( dom.getCanvases(0), n.get(0) );
                Row rmax= r;
                Row rmin= r;
                for ( int i=1; i<n.size(); i++ ) {
                    r= (Row) DomUtil.getElementById( dom.getCanvases(0), n.get(i) );
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
            double MaxUpJEm= 0.;
            double MaxDownJ= 0.;
            for ( int j=0; j<plots.size(); j++ ) {
                Plot plotj= plots.get(j);
                String title= plotj.getTitle();
                MaxUpJEm= plotj.isDisplayTitle() ? lineCount(title) : 0;
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
            String newTop=  String.format( "%.2f%%%+.1fem", 100*position, MaxUp[i] * pixelsToEm );
            rows[i].setTop( newTop );
            position+= normalPlotHeight[i];
            String newBottom= String.format(   "%.2f%%%+.1fem", 100*position, -1 * MaxDown[i] * pixelsToEm );
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
                dsf.uri= agg;
            }
        }
        dom.setDataSourceFilters(dsfs);
        dom.syncTo( oldDom, Collections.singletonList( "dataSourceFilters" ) );

    }
}
