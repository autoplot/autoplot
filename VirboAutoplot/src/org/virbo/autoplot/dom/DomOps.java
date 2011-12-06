/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.das2.graph.DasRow;

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
     * play with new canvas layout.  This started as a Jython Script, but it's faster to implement here.
     * See http://autoplot.org/developer.autolayout#Algorithm
     * @param dom
     */
    public static void newCanvasLayout( Application dom ) {

        Canvas canvas= dom.getCanvases(0);

        System.err.println( String.format( "Canvas Height is canvas.height=%d\n", canvas.height ) );

        double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();
        double pixelToNorm= 1./canvas.height;
        double pixelsToEm= 1/emToPixels;

        Row[] rows= canvas.getRows();
        int nrow= rows.length;

        double TotalPlotHeight= 0;
        for ( int i=0; i<nrow; i++ ) {
           Plot[] plots= DomOps.getPlotsFor( dom, rows[i], true ).toArray( new Plot[0] );

           if ( plots.length>0 ) {
               DasRow dasRow= rows[i].getController().dasRow;
               TotalPlotHeight= TotalPlotHeight + dasRow.getHeight();
            }
        }

        System.err.println( String.format( "Total Plot Height is TotalHeight=%f\n", TotalPlotHeight ) );

        double [] MaxUp= new double[ nrow ];
        double [] MaxDown= new double[ nrow ];

        for ( int i=0; i<nrow; i++ ) {
            Plot[] plots= DomOps.getPlotsFor( dom, rows[i], true ).toArray( new Plot[0] );
            double MaxUpJEm= 0.;
            double MaxDownJEm= 0.;
            for ( int j=0; j<plots.length; j++ ) {
                String title= plots[j].getTitle();
                MaxUpJEm= title.trim().length()==0 ? 0 : 1 * title.split("\n").length + 1;
                MaxUp[i]= Math.max( MaxUp[i], MaxUpJEm*emToPixels );
                String axisTitle= plots[j].getXaxis().getLabel();
                String axisTickFormat= ( plots[j].getXaxis().isVisible() && plots[j].getXaxis().isDrawTickLabels() ) ? "0.0" : "";  //TODO: do this
                double axisTickLength= plots[j].getXaxis().isVisible() ? 0 : 0.66;
                MaxDownJEm= axisTickLength + axisTickFormat.split("\n").length + axisTitle.split("\n").length;
                MaxDown[i]= Math.max( MaxDown[i], MaxDownJEm*emToPixels );
            }
        }

        double [] RelativePlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
            DasRow dasRow= rows[i].getController().dasRow;
            RelativePlotHeight[i]= 1.0 * dasRow.getHeight() / TotalPlotHeight;
        }

        double NewPlotTotalHeight= canvas.height;
        for ( int i=0; i<nrow; i++ ) {
            NewPlotTotalHeight = NewPlotTotalHeight - MaxUp[i] - MaxDown[i];
        }

        double [] PlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
            PlotHeight[i]= NewPlotTotalHeight * RelativePlotHeight[i];
        }

        System.err.println( String.format( "NewPlotTotalHeight=%f", NewPlotTotalHeight ));

        // NormalPlotHeight_i= ( PlotHeight_i + MaxUp_i + MaxDown_i ) / TotalPlotHeight
        double[] NormalPlotHeight= new double[ nrow ];
        for ( int i=0; i<nrow; i++ ) {
             NormalPlotHeight[i]= ( PlotHeight[i] + MaxUp[i] + MaxDown[i] ) / canvas.height;
        }

        double position=0;

        for ( int i=0; i<nrow; i++ ) {
            System.err.printf("Row %3d:  ",i);
            //rows[i].top= String.format( "%5.2f%+5.1fem", 100*position, MaxUp[i] / emToPixels );
            System.err.printf( String.format( "%.2f%+.1fem", 100*position, MaxUp[i] * pixelsToEm ) );
            position+= NormalPlotHeight[i];
            //rows[i].bottom= String.format( "%5.2f%+5.1fem", 100*position, MaxDown[i] / emToPixels );
            System.err.printf( String.format( "  %.2f%+.1fem", 100*position, -1 * MaxDown[i] * pixelsToEm ) );
            System.err.printf( String.format( "  plotHeight=%5.2f", PlotHeight[i] ) );
            System.err.printf( String.format( "  relHeight=%5.2f", RelativePlotHeight[i] ) );
            System.err.println();

        }


    }
}
