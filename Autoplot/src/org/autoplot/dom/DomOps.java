
package org.autoplot.dom;

import java.awt.Event;
import java.awt.Rectangle;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.graph.DasAxis;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.LegendPosition;

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
        String ticksUriALabels= a.getEphemerisLabels();
        String ticksUriBLabels= b.getEphemerisLabels();
        a.setTicksURI( ticksUriBLabels );
        b.setTicksURI( ticksUriALabels ); 
        
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
     * return the bottom-most and top-most plot of a list of plots.  
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
        return new Plot[] { pmax, pmin }; // note backwards because bottom is the max.
    }
    
    /**
     * return the bottom-most and top-most plot of a list of plots.  
     * This does use controllers.
     * @param dom
     * @param plots
     * @return
     */
    public static Plot[] bottomAndTopMostPlot( Application dom, Plot[] plots ) {
        return bottomAndTopMostPlot( dom, Arrays.asList(plots) );
    }
    
    /**
     * return the left-most and right-most plot of a list of plots.  
     * This does use controllers.
     * @param dom
     * @param plots
     * @return two-element array of leftmost and rightmost plot.
     */
    public static Plot[] leftAndRightMostPlot( Application dom, List<Plot> plots ) {
        Plot pmax=plots.get(0);
        Plot pmin=plots.get(0);
        Column r= (Column) DomUtil.getElementById( dom.getCanvases(0), pmax.getColumnId() );
        Column rmax= r;
        Column rmin= r;
        for ( Plot p: plots ) {
            r= (Column) DomUtil.getElementById( dom.getCanvases(0), p.getColumnId() );
            if ( r.getController().getDasColumn().getDMaximum()>rmax.getController().getDasColumn().getDMaximum() ) {
                rmax= r;
                pmax= p;
            }
            if ( r.getController().getDasColumn().getDMinimum()<rmin.getController().getDasColumn().getDMinimum() ) {
                rmin= r;
                pmin= p;
            }
        }
        return new Plot[] { pmin, pmax };
    }
    
    /**
     * return the left-most and right-most plot of a list of plots.  
     * This does use controllers.
     * @param dom
     * @param plots
     * @return two-element array of leftmost and rightmost plot.
     */
    public static Plot[] leftAndRightMostPlot( Application dom, Plot[] plots ) {
        return leftAndRightMostPlot( dom, Arrays.asList(plots) );
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
     * return a list of the plots using the given row.
     * This does not use controllers.
     * @param dom a dom
     * @param column the column to search for.
     * @param visible  if true, then the plot must also be visible.  (Note its colorbar visible is ignored.)
     * @return a list of plots.
     */
    public static List<Plot> getPlotsFor( Application dom, Column column, boolean visible ) {
        ArrayList<Plot> result= new ArrayList();
        for ( Plot p: dom.getPlots() ) {
            if ( p.getColumnId().equals(column.getId()) ) {
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
        String[] ss= s.split("(\\!c|\\!C|\\<br\\>)");
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
        fixLayout( dom, Collections.emptyMap() );
        
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
     * @param dom an application state. 
     */
    public static void fixLayout( Application dom) {
        fixLayout( dom, Collections.emptyMap() );
    }
        
    /**
     * See https://sourceforge.net/p/autoplot/feature-requests/811/
     */
    public static final String OPTION_FIX_LAYOUT_HIDE_TITLES = "hideTitles";
    public static final String OPTION_FIX_LAYOUT_HIDE_TIME_AXES = "hideTimeAxes";
    public static final String OPTION_FIX_LAYOUT_HIDE_Y_AXES = "hideYAxes"; 
    public static final String OPTION_FIX_LAYOUT_MOVE_LEGENDS_TO_OUTSIDE_NE = "moveLegendsToOutsideNE";
    public static final String OPTION_FIX_LAYOUT_VERTICAL_SPACING = "verticalSpacing";
    public static final String OPTION_FIX_LAYOUT_HORIZONTAL_SPACING = "horizontalSpacing";
    
    
    private static double[] parseLayoutStr( String s, double[] deflt ) {
        try {
            return DasDevicePosition.parseLayoutStr(s);
        } catch ( ParseException ex ) {
            return deflt;
        }
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
     * Additional options include:<ul>
     * <li>interPlotVerticalSpacing - 1em
     * </ul>
     * @param dom an application state.
     * @param options additional options, including interPlotVerticalSpacing.
     */
    public static void fixLayout( Application dom, Map<String,String> options  ) {
        Logger logger= LoggerManager.getLogger("autoplot.dom.layout.fixlayout");
        logger.fine( "enter fixLayout" );
        
        if ( !dom.controller.changesSupport.mutatorLock().isLocked() 
                && !SwingUtilities.isEventDispatchThread() ) {
            dom.getController().waitUntilIdle();
        }
        
        boolean autoLayout= dom.options.isAutolayout();
        dom.options.setAutolayout(false);
        
        try {
                
            Canvas canvas= dom.getCanvases(0);
            Column marginColumn= canvas.getMarginColumn();
            
            Row[] rows= canvas.getRows();
            int nrow= rows.length;

            Column[] columns= canvas.getColumns();
            
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

            List<Row> rowsList= new ArrayList<>(Arrays.asList(rows));
            rm.forEach((r) -> {
                rowsList.remove(r);
            });
            canvas.setRows( rowsList.toArray(new Row[rowsList.size()]));

            rows= new Row[ rowsList.size() ];
            nrow= rows.length;
            for ( int i=0; i<nrow; i++ ) {
                rows[i]= new Row();
                rows[i].syncTo( canvas.getRows(i) );
            }

            // sort rows, which is a refactoring.  TODO: I think there are still issues here
            Arrays.sort( rows, (Row r1, Row r2) -> {
                int d1= DomUtil.getRowPositionPixels( dom, r1, r1.getTop() );
                int d2= DomUtil.getRowPositionPixels( dom, r2, r2.getTop() );
                return d1-d2;
            });
            
            String topRowId= rows[0].getId();
            String bottomRowId= rows[rows.length-1].getId();
            
            String leftColumnId= columns.length>0 ? columns[0].getId() : "";
            
            if ( options.getOrDefault( OPTION_FIX_LAYOUT_HIDE_TITLES, "false" ).equals("true") ) {
                for ( Plot p: dom.plots ) {
                    if ( p.getRowId().equals(topRowId) ) {
                        logger.fine("not hiding top plot's title");
                    } else {
                        p.setDisplayTitle(false);
                    }
                }
            }

            if ( options.getOrDefault( OPTION_FIX_LAYOUT_HIDE_TIME_AXES, "false" ).equals("true") ) {
                for ( Plot p: dom.plots ) {
                    if ( p.getRowId().equals(bottomRowId) ) {
                        logger.fine("not hiding bottom plot's time axis"); 
                    } else {
                        // TODO: check bindings to see that this axis is bound to the timerange
                        p.xaxis.setDrawTickLabels(false);
                    }
                }
            }

            if ( options.getOrDefault( OPTION_FIX_LAYOUT_HIDE_Y_AXES, "false" ).equals("true") ) {
                if ( columns.length!=0 ) {
                    for ( Plot p: dom.plots ) {
                        if ( p.getColumnId().equals(leftColumnId) || p.getColumnId().equals(marginColumn.getId()) ) {
                            logger.fine("not hiding leftmost plot's Y axis"); 
                        } else {
                            // TODO: check bindings to see that this axis is bound to the timerange
                            p.yaxis.setDrawTickLabels(false);
                        }
                    }
                }
            }

            if ( options.getOrDefault( OPTION_FIX_LAYOUT_MOVE_LEGENDS_TO_OUTSIDE_NE, "false" ).equals("true") ) {
                for ( Plot p: dom.plots ) {
                    if ( p.isDisplayLegend() ) {
                        if ( !p.getZaxis().isVisible() ) {
                            p.setLegendPosition(LegendPosition.OutsideNE);
                        }
                    }
                }
            }

            fixVerticalLayout( dom, options );

            fixHorizontalLayout( dom, options ); 

        } finally {
            dom.options.setAutolayout(autoLayout);
        }
        
        if ( !dom.controller.changesSupport.mutatorLock().isLocked()
                && !SwingUtilities.isEventDispatchThread() ) {
            dom.getController().waitUntilIdle();
        }
    }

    /**
     * return the number of lines taken by the x-axis, including the ticks.
     * @param plotj
     * @return 
     */
    private static int getXAxisLines( Plot plotj ) {
        if ( !plotj.getXaxis().isDrawTickLabels() ) {
            return 1;
        }
        int lc= lineCount(plotj.getXaxis().getLabel());
        int ephemerisLineCount;
        if ( plotj.getEphemerisLineCount()>-1 ) {
            ephemerisLineCount= plotj.getEphemerisLineCount();
        } else {
            if ( plotj.getTicksURI().trim().length()>0 ) {
                if ( plotj.getXaxis().getController()!=null ) {
                    DasAxis a= plotj.getXaxis().getController().getDasAxis();
                    ephemerisLineCount= a.getTickLines();
                } else {
                    ephemerisLineCount= 5; // complete guess
                }
            } else {
                if ( lc==0 ) { // without the label used to label the range, midnights will be indicated with the date, so add one em.
                    lc=1;
                }
                ephemerisLineCount= 0;                
            }
        }
        ephemerisLineCount+= Math.ceil(ephemerisLineCount/4.); // there's an extra 25% added, see DasAxis.getLineSpacing!
        return ephemerisLineCount+2+1+lc;  // +1 is for ticks        
    }
    
    /**
     * This is the new layout mechanism (fixLayout), correcting the layout in the vertical direction.  This one:<ul>
     * <li> Removes extra whitespace
     * <li> Preserves relative size weights.
     * <li> Preserves em heights, to support components which should not be rescaled. (Not yet supported.)
     * <li> Preserves space taken by strange objects, to support future canvas components.
     * <li> Renormalizes the margin row, so it is nice. (Not yet supported.  This should consider font size, where large fonts don't need so much space.)
     * </ul>
     * This should also be idempotent, where calling this a second time should have no effect.
     * @param dom an application state, with controller nodes. 
     * @see #fixLayout(org.autoplot.dom.Application) 
     */
    public static void fixVerticalLayout( Application dom ) {
        fixVerticalLayout( dom, Collections.emptyMap() );
    } 
    
    /**
     * This is the new layout mechanism (fixLayout), correcting the layout in the vertical direction.  This one:<ul>
     * <li> Renormalizes the margin row, so it is nice. 
     * <li> Removes extra whitespace
     * <li> Preserves relative size heights.
     * <li> Preserves em heights, to support components which should not be rescaled. 
     * <li> Try to make each row's em offsets similar, using the marginRow, so that fonts can be scaled.
     * </ul>
     * This should also be idempotent, where calling this a second time should have no effect.
     * @param dom an application state, with controller nodes. 
     * @param options 
     * @see #fixLayout(org.autoplot.dom.Application, java.util.Map) 
     */    
    public static void fixVerticalLayout( Application dom, Map<String,String> options ) {

        Canvas canvas= dom.getCanvases(0);
        Row marginRow= (Row)canvas.getMarginRow().copy();
        
        double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();
        
        Row[] rows= canvas.getRows(); // note this is a shallow copy
        int nrow= rows.length;
        for ( int i=0; i<rows.length; i++ ) {
            rows[i]= (Row)rows[i].copy(); // deep copy
        }
        
        
        boolean[] doAdjust= new boolean[nrow];

        String topRowId= rows[0].getId();
        String bottomRowId= rows[rows.length-1].getId();
        
        try {
            double [] MaxUp= new double[ nrow ];
            double [] MaxDown= new double[ nrow ];
            double [] MaxUpEm= new double[ nrow ];
            double [] MaxDownEm= new double[ nrow ];

            String verticalSpacing=  options.getOrDefault( OPTION_FIX_LAYOUT_VERTICAL_SPACING, "" );
            
            if ( verticalSpacing.trim().length()>0 ) {
                Pattern p= Pattern.compile("([0-9\\.]*)em");
                if ( p.matcher(verticalSpacing).matches() ) {
                    Double d= Double.parseDouble(verticalSpacing.substring(0,verticalSpacing.length()-2));
                    double extraEms=0;
                    for ( int i=0; i<MaxDown.length; i++ ) {
                        MaxUp[i]= 0;
                        MaxDown[i]= -d*emToPixels;
                        double[] dd1,dd2; 
                        dd1= parseLayoutStr( rows[i].top, new double[] { 0, 0, 0 } );
                        dd2= parseLayoutStr( rows[i].bottom, new double[] { 0, 0, 0 } );
                        if ( dd1[0]==dd2[0] ) {
                            double h=(dd2[1]-dd1[1]);
                            dd1[1]= extraEms;
                            dd2[1]= extraEms+h;
                            extraEms+= h+d;
                        } else {
                            dd1[1]= extraEms;
                            dd2[1]= extraEms-d;
                        }
                        rows[i].top= DasDevicePosition.formatLayoutStr(dd1);
                        rows[i].bottom= DasDevicePosition.formatLayoutStr(dd2);
                    }
                }
            }

            // 1. Reset marginRow.  define nup to be the number of lines above the top plot row.  define nbottom to be the number
            // of lines below the bottom row.
            double ntopEm=0, nbottomEm=0;
            for ( int i=0; i<dom.plots.size(); i++ ) {
                Plot p= dom.plots.get(i);
                if ( p.getRowId().equals(topRowId) ) {
                    ntopEm= Math.max( ntopEm, lineCount( p.getTitle() ) );
                }
                if ( p.getRowId().equals(bottomRowId) ) {
                    nbottomEm= Math.max( nbottomEm, getXAxisLines(p) );
                }
            }
            marginRow.setTop( DasDevicePosition.formatLayoutStr( new double[] { 0, ntopEm+2, 0 } ) );
            marginRow.setBottom( DasDevicePosition.formatLayoutStr( new double[] { 1.0, -(nbottomEm+2), 0 } ) );

            double[] resizablePixels= new double[nrow];
            boolean[] isEmRow= new boolean[nrow];
            double[] emsUpSize= new double[nrow];
            double[] emsDownSize= new double[nrow];

            logger.log(Level.FINER, "1. new settings for the margin row:{0} {1}", new Object[]{marginRow.getTop(), marginRow.getBottom()});
                  
            // 2. For each row, identify the number of lines above and below each plot in MaxUp and MaxDown (MaxUpEm is just expressed in ems).
            for ( int i=0; i<nrow; i++ ) {
                double[] rr1= parseLayoutStr(rows[i].getTop(),new double[3]); // whoo hoo let's parse this too many times!
                double[] rr2= parseLayoutStr(rows[i].getBottom(),new double[3]);
                isEmRow[i]= Math.abs( rr1[0]-rr2[0] )<0.001;
                emsUpSize[i]= rr1[1];
                emsDownSize[i]= rr2[1];
                
                if ( isEmRow[i] ) {
                    MaxDownEm[i]= emsDownSize[i];
                    MaxUpEm[i]= emsUpSize[i];
                    MaxDown[i]= emsDownSize[i]*emToPixels;
                    MaxUp[i]= emsUpSize[i]*emToPixels;
                    doAdjust[i]= true;
                    
                } else {
                    List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );
                    double MaxUpJEm;
                    double MaxDownPx;
                    for ( Plot plotj : plots ) {
                        if ( rows[i].parent.equals(marginRow.id) ) { 
                            String title= plotj.getTitle();
                            String content= title; // title.replaceAll("(\\!c|\\!C|\\<br\\>)", " ");
                            boolean addLines= plotj.isDisplayTitle() && content.trim().length()>0;
                            int lc= lineCount(title);
                            if ( rows[i].id.equals(topRowId) ) {
                                MaxUpJEm= ( addLines ? lc : 0. ) - ntopEm;
                            } else {
                                MaxUpJEm= addLines ? lc : 0.;
                            }
                            
                            MaxUp[i]= Math.max( MaxUp[i], MaxUpJEm*emToPixels );
                            MaxUpEm[i]= Math.max( MaxUpEm[i], MaxUpJEm );
                            
                            if ( rows[i].id.equals(bottomRowId) ) {
                                MaxDownEm[i]= Math.min( MaxDownEm[i], 0 );
                            } else {
                                MaxDownEm[i]= Math.min( MaxDownEm[i], -getXAxisLines(plotj) );
                            }
                            MaxDown[i]= MaxDownEm[i]*emToPixels;

                            doAdjust[i]= true;
                        } else {
                            doAdjust[i]= false;
                        }
                    }
                    if ( verticalSpacing.trim().length()>0 ) {
                        MaxDownEm[i]= emsDownSize[i]-emsUpSize[i];
                        MaxUpEm[i]= 0.;
                    }
                
                }

            }
            if ( logger.isLoggable(Level.FINER) ) {
                logger.log(Level.FINER, "2. space needed to the top and bottom of each plot:" );
                for ( int i=0; i<nrow; i++ ) {
                    logger.log(Level.FINER, "  {0}em {1}em", new Object[]{MaxUpEm[i], MaxDownEm[i]});
                }
            }            
            
            // 2.5 see if we can tweak the marginRow to make the row em offsets more similar.  Note for two rows this can
            // always be done.
            if ( rows.length>1 ) {
                // when all but the top have the equal ems, moving ems to the marginRow if will make things equal
                boolean adjust=true;
                double em= MaxUpEm[1];
                for ( int i=2; i<rows.length; i++ ) {
                    if ( em!=MaxUpEm[i] ) {
                        adjust= false;
                    }
                }
                if ( adjust ) {
                    if ( MaxUpEm[0]!=em ) {
                        double toMarginEms= MaxUpEm[0]-em;
                        MaxUpEm[0]=em;
                        MaxUp[0]=em*emToPixels;
                        double[] dd1= parseLayoutStr( marginRow.top, new double[] { 0, 0, 0 } );
                        dd1[1]+=toMarginEms;
                        marginRow.top= DasDevicePosition.formatLayoutStr(dd1);
                    }
                }
                // now do the same thing but with the bottom, moving ems to the marginRow when it will make things equal
                adjust=true;
                em= MaxDownEm[0];
                for ( int i=0; i<rows.length-1; i++ ) {
                    if ( em!=MaxDownEm[i] ) {
                        adjust= false;
                    }
                }
                if ( adjust ) {
                    int last= MaxDownEm.length-1;
                    if ( MaxDownEm[last]!=em ) {
                        double toMarginEms= MaxUpEm[0]-em;
                        MaxDownEm[last]=em;
                        MaxDown[last]=em*emToPixels;
                        double[] dd1= parseLayoutStr( marginRow.bottom, new double[] { 0, 0, 0 } );
                        dd1[1]=-toMarginEms;
                        marginRow.bottom= DasDevicePosition.formatLayoutStr(dd1);
                    }
                }                
            }
            
            // 3. identify the number of pixels in each of the rows which are resizable.
            double totalPlotHeightPixels= 0;
            for ( int i=0; i<nrow; i++ ) {           
                List<Plot> plots= DomOps.getPlotsFor( dom, rows[i], true );

                if ( plots.size()>0 ) {
                    int d1 = DomUtil.getRowPositionPixels( dom, rows[i], rows[i].getTop() );
                    int d2 = DomUtil.getRowPositionPixels( dom, rows[i], rows[i].getBottom() );
                    resizablePixels[i]= ( d2-d1 );
                    if ( isEmRow[i] ) {
                        logger.fine("here's an events bar row!");
                    } else {
                        totalPlotHeightPixels= totalPlotHeightPixels + resizablePixels[i];
                    }
                }
            }
            logger.log(Level.FINER, "3. number of pixels used by plots which are resizable: {0}", totalPlotHeightPixels);        

            // 4. express this as a fraction of all the pixels which could be resized.
            double [] relativePlotHeight= new double[ nrow ];
            for ( int i=0; i<nrow; i++ ) {
                if ( isEmRow[i] ) {
                    relativePlotHeight[i]= 0.0;
                } else {
                    relativePlotHeight[i]= (double)(resizablePixels[i]) / totalPlotHeightPixels;
                }
            }
            if ( logger.isLoggable(Level.FINER) ) {
                logger.finer("4. relative sizes of the rows: ");
                for ( int i=0; i<nrow; i++ ) {
                    logger.log(Level.FINER, "  {0}", relativePlotHeight[i]);
                }
            }            
            
            // 5. Calculate the number of pixels available for resized plots on the canvas.
            int d1= DomUtil.getRowPositionPixels( dom, marginRow, marginRow.top );
            int d2= DomUtil.getRowPositionPixels( dom, marginRow, marginRow.bottom );
            double marginHeight= d2-d1;

            double newPlotTotalHeightPixels= marginHeight; // this will be the pixels available to divide amungst the plots.
            for ( int i=0; i<nrow; i++ ) {
                newPlotTotalHeightPixels = newPlotTotalHeightPixels - MaxUp[i] + MaxDown[i];
            }
            logger.log(Level.FINER, "5. number of pixels available to the plots which can resize: {0}", newPlotTotalHeightPixels);
            

            // 6. newPlotHeight is the height of each plot in pixels.
            double [] newPlotHeightPixels= new double[ nrow ];
            for ( int i=0; i<nrow; i++ ) {
                newPlotHeightPixels[i]= newPlotTotalHeightPixels * relativePlotHeight[i]; 
            }
            if ( logger.isLoggable(Level.FINER) ) {
                logger.finer("6. new resizable plot heights in pixels: ");
                for ( int i=0; i<nrow; i++ ) {
                    logger.log(Level.FINER, "  {0}", newPlotHeightPixels[i]);
                }
            }            

            // 7. Now calculate the layout string (e.g. 50%+1em,100%-3em) for each row.
            // normalPlotHeight will be the normalized size of each plot, which includes the em offsets.
            double[] normalPlotHeight= new double[ nrow ];

            if ( nrow==1 ) {
                normalPlotHeight[0]= ( newPlotHeightPixels[0] ) / ( marginHeight );
            } else {
                for ( int i=0; i<nrow; i++ ) {
                    if ( relativePlotHeight[i]==0 ) {
                        normalPlotHeight[i]= 0.0;
                    } else {
                        //normalPlotHeight[i]= ( newPlotHeight[i] - MaxUp[i] - MaxDown[i] ) / ( marginHeight );
                        normalPlotHeight[i]= ( newPlotHeightPixels[i] + MaxUp[i] - MaxDown[i] ) / ( marginHeight );
                    }
                }
            }
            if ( logger.isLoggable(Level.FINER) ) {
                logger.finer("7. new plot heights, which also include the em offsets: ");
                for ( int i=0; i<nrow; i++ ) {
                    logger.log(Level.FINER, "  {0}", normalPlotHeight[i]);
                }
            }            

            
            // 8. calculate each row's new layout string, possibly adding additional ems for rows which are not resized.
            double position= 0;
            double extraEms= 0;

            // guess the spacing expected between plots, for when this is not explicit.
            double nominalSpacingEms= -MaxDownEm[0]+MaxUpEm[0];

            for ( int i=0; i<nrow; i++ ) {
                if ( doAdjust[i] ) {
                    String newTop;
                    String newBottom;                
                    if ( !isEmRow[i] ) {
                        newTop=  DasDevicePosition.formatLayoutStr( new double[] { position, MaxUpEm[i]+extraEms, 0 } );
                        position+= normalPlotHeight[i];
                        newBottom = DasDevicePosition.formatLayoutStr( new double[] { position, MaxDownEm[i]+extraEms, 0 } );
                    } else {
                        newTop=  DasDevicePosition.formatLayoutStr( new double[] { position, MaxUpEm[i]+extraEms, 0 } );
                        newBottom = DasDevicePosition.formatLayoutStr( new double[] { position, MaxDownEm[i]+extraEms, 0 } );
                        if ( verticalSpacing.trim().length()>0 ) {
                            logger.finer("we already accounted for this.");
                        } else {
                            extraEms+= nominalSpacingEms + ( MaxDownEm[i] + MaxUpEm[i] );
                        }
                    }
                    rows[i].setTop( newTop );                
                    rows[i].setBottom( newBottom );
                    if ( logger.isLoggable( Level.FINE ) ) {
                        int r0= (int)DomUtil.getRowPositionPixels( dom, rows[i], rows[i].getTop() );
                        int r1= (int)DomUtil.getRowPositionPixels( dom, rows[i], rows[i].getBottom() );
                        logger.log(Level.FINE, "row {0}: {1},{2} ({3} pixels)", new Object[]{i, newTop, newBottom, r1-r0 });
                    }
                } else {
                    logger.log(Level.FINE, "row {0} is not adjusted", i );
                }
            }
            if ( logger.isLoggable(Level.FINER) ) {
                logger.finer("8. new layout strings: ");
                for ( int i=0; i<nrow; i++ ) {
                    logger.log(Level.FINER, "  {0} {1}", new Object[]{ rows[i].getTop(), rows[i].getBottom() } );
                }
            }
        
            // 9. reset the rows to this new location.
            for ( int i=0; i<rows.length; i++ ) {
                canvas.getRows(i).syncTo(rows[i]);
            }
            dom.getCanvases(0).getMarginRow().syncTo(marginRow);
            
        } finally {
            
        }
    }
    
    /**
     * This is the new layout mechanism (fixLayout), but changed from vertical layout to horizontal.  This one:<ul>
     * <li> Removes extra whitespace
     * <li> Preserves relative size weights.
     * <li> Preserves em heights, to support components which should not be rescaled. (Not yet supported.)
     * <li> Preserves space taken by strange objects, to support future canvas components.
     * <li> Renormalizes the margin row, so it is nice. (Not yet supported.  This should consider font size, where large fonts don't need so much space.)
     * </ul>
     * This should also be idempotent, where calling this a second time should have no effect.
     * @param dom an application state, with controller nodes. 
     * @see #fixLayout(org.autoplot.dom.Application) 
     */
    public static void fixHorizontalLayout( Application dom ) {
        fixHorizontalLayout( dom, Collections.emptyMap() );
    } 
    
    /**
     * This is the new layout mechanism (fixLayout), but changed from vertical layout to horizontal.  This one:<ul>
     * <li> Renormalizes the margin column, so it is nice. 
     * <li> Removes extra whitespace
     * <li> Preserves relative size weights.
     * <li> Preserves em widths, to support components which should not be rescaled. 
     * <li> Try to make each column's em offsets similar, using the marginColumn, so that fonts can be scaled.
     * </ul>
     * This should also be idempotent, where calling this a second time should have no effect.
     * @param dom an application state, with controller nodes. 
     * @param options 
     * @see #fixLayout(org.autoplot.dom.Application) 
     */    
    public static void fixHorizontalLayout( Application dom, Map<String,String> options ) {
        Logger logger= LoggerManager.getLogger("autoplot.dom.layout.fixlayout");
        logger.fine( "enter fixHorizontalLayout" );
                
        Canvas canvas= dom.getCanvases(0);
        Column marginColumn= (Column)canvas.getMarginColumn().copy();

        double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();

        Column[] columns= canvas.getColumns();
                
        int ncolumn= columns.length;
        boolean[] doAdjust= new boolean[ncolumn];

        //kludge: check for duplicate names of columns.  Use the first one found.
        Map<String,Column> columnCheck= new HashMap();
        List<Column> rm= new ArrayList<>();
        for ( int i=0; i<ncolumn; i++ ) {           
           List<Plot> plots= DomOps.getPlotsFor( dom, columns[i], true );

           if ( plots.size()>0 ) {
               if ( columnCheck.containsKey(columns[i].getId()) ) {
                   logger.log(Level.FINE, "duplicate row id: {0}", columns[i].getId());
                   rm.add( columns[i] );
               } else {
                   columnCheck.put( columns[i].getId(), columns[i] );
               }
            } else {
               logger.log(Level.FINE, "unused row: {0}", columns[i]);
               rm.add( columns[i] );
           }
        }
        ArrayList<Column> columnsList= new ArrayList(Arrays.asList(columns));
        rm.forEach((r) -> {
            columnsList.remove(r);
        });

        // see if we can remove redundant columns.
        Map<Column,Column> replace= new HashMap<>();
        ncolumn= columnsList.size();
        for ( int i=0; i<ncolumn; i++ ) {
            Column c= columnsList.get(i);
            for ( int j=i+1; j<ncolumn; j++ ) {
                Column nj= columnsList.get(j);
                if ( nj==c ) continue;
                if ( c.left.equals(nj.left) && c.right.equals(nj.right) && c.parent.equals(nj.parent) ) {
                    replace.put(nj,c);
                }
            }
        }
        for ( Entry<Column,Column> rm1 : replace.entrySet() ) {
            for ( Plot p: dom.plots ) {
                if ( p.getColumnId().equals(rm1.getKey().id ) ) {
                    p.setColumnId(rm1.getValue().id);
                }
            }
            for ( Annotation ann: dom.annotations ) {
                if ( ann.getColumnId().equals(rm1.getKey().id ) ) {
                    ann.setColumnId(rm1.getValue().id);
                }
            }
            columnsList.remove(rm1.getKey());
        }
        
        canvas.setColumns((Column[]) columnsList.toArray( new Column[columnsList.size()]));
        
        columns= new Column[ columnsList.size() ];
        ncolumn= columns.length;
        for ( int i=0; i<ncolumn; i++ ) {
            columns[i]= new Column();
            columns[i].syncTo( canvas.getColumns(i) );
        }
 
        // sort rows, which is a refactoring.
        Arrays.sort( columns, (Column c1, Column c2) -> {
            int d1= DomUtil.getColumnPositionPixels( dom, c1, c1.getLeft() );
            int d2= DomUtil.getColumnPositionPixels( dom, c2, c2.getLeft() );
            return d1-d2;
        });
        
        String leftColumnId= ncolumn>0 ? columns[0].id : "";
        String rightColumnId= ncolumn>0 ? columns[columns.length-1].id : "";
        
        String horizontalSpacing=  options.getOrDefault( OPTION_FIX_LAYOUT_HORIZONTAL_SPACING, "" );

        double [] MaxLeft= new double[ ncolumn ];
        double [] MaxRight= new double[ ncolumn ];
        double [] MaxLeftEm= new double[ ncolumn ];
        double [] MaxRightEm= new double[ ncolumn ];
        
        if ( horizontalSpacing.trim().length()>0 ) {
            Pattern p= Pattern.compile("([0-9\\.]*)em");
            if ( p.matcher(horizontalSpacing).matches() ) {
                Double d= Double.parseDouble(horizontalSpacing.substring(0,horizontalSpacing.length()-2));
                double extraEms=0;
                for ( int i=0; i<MaxRight.length; i++ ) {
                    MaxLeft[i]= 0;
                    MaxRight[i]= -d*emToPixels;
                    double[] dd1,dd2; 
                    dd1= parseLayoutStr( columns[i].left, new double[] { 0, 0, 0 } );
                    dd2= parseLayoutStr( columns[i].right, new double[] { 0, 0, 0 } );
                    if ( dd1[0]==dd2[0] ) {
                        double h=(dd2[1]-dd1[1]);
                        dd1[1]= extraEms;
                        dd2[1]= extraEms+h;
                        extraEms+= h+d;
                    } else {
                        dd1[1]= extraEms;
                        dd2[1]= extraEms-d;
                    }
                    columns[i].left= DasDevicePosition.formatLayoutStr(dd1);
                    columns[i].right= DasDevicePosition.formatLayoutStr(dd2);
                    logger.log(Level.FINE, "line986: {0},{1}", new Object[]{columns[i].left, columns[i].right});
                }
            }
        }
        
        // 1. reset marginColumn.  define nleftEm to be the number of lines 
        // to the left of the leftmost plot column.  define nrightEm to be the number
        // of lines to the right of the rightmost column.
        double nleftEm=0, nrightEm=0;
        for ( int i=0; i<dom.plots.size(); i++ ) {
            Plot p= dom.plots.get(i);
            if ( p.getColumnId().equals(leftColumnId) || p.getColumnId().equals(marginColumn.id) ) {
                nleftEm= Math.max( nleftEm, lineCount( p.getYaxis().getLabel() ) + 5 );
            }
            if ( p.getColumnId().equals(rightColumnId) || p.getColumnId().equals(marginColumn.id) ) {
                if ( p.getZaxis().isVisible() ) {
                    nrightEm= Math.max( nrightEm, 14 ); //TODO: label is showing, etc
                    //nrightEm= Math.max( nrightEm, 6 ); //TODO: label is showing, etc
                } else {
                    nrightEm= Math.max( nrightEm, 8 );
                    //nrightEm= Math.max( nrightEm, 2 );                    
                }
                if ( p.isDisplayLegend() ) {
                    if ( p.getLegendPosition()==LegendPosition.OutsideNE || 
                            p.getLegendPosition()==LegendPosition.OutsideSE ) {
                        double arbitaryRightEms= 13;
                        nrightEm= Math.max( nrightEm, arbitaryRightEms ); 
                    }
                }
            }
        }
        if ( ncolumn>0 ) nrightEm= 0; 
        
        marginColumn.setLeft( DasDevicePosition.formatLayoutStr( new double[] { 0, nleftEm+2, 0 } ) );
        marginColumn.setRight( DasDevicePosition.formatLayoutStr( new double[] { 1, -nrightEm, 0 } ) );
        
        if ( ncolumn==0 ) {
            logger.finer("0. No adjustable columns, returning!");
            return;
        }
        
        double[] resizablePixels= new double[ncolumn];
        boolean[] isEmColumn= new boolean[ncolumn];
        double[] emsLeftSize= new double[ncolumn];
        double[] emsRightSize= new double[ncolumn];
        
        logger.log(Level.FINER, "1. new settings for the margin column:{0} {1}", new Object[]{marginColumn.getLeft(), marginColumn.getRight()});
        
        // 2. For each column, identify the space to the left and right of each plot.      
        for ( int i=0; i<ncolumn; i++ ) {
            double[] rr1= parseLayoutStr(columns[i].getLeft(),new double[3]); // whoo hoo let's parse this too many times!
            double[] rr2= parseLayoutStr(columns[i].getRight(),new double[3]);
            isEmColumn[i]= Math.abs( rr1[0]-rr2[0] )<0.001;
            emsLeftSize[i]= rr1[1];
            emsRightSize[i]= rr2[1];

            if ( isEmColumn[i] ) {
                MaxRightEm[i]= emsRightSize[i];
                MaxLeftEm[i]= emsLeftSize[i];
                MaxRight[i]= emsRightSize[i]*emToPixels;
                MaxLeft[i]= emsLeftSize[i]*emToPixels;
                doAdjust[i]= true;

            } else {
                List<Plot> plots= DomOps.getPlotsFor( dom, columns[i], true );
                double MaxLeftJEm;
                double MaxRightPx;
                for ( Plot plotj : plots ) {
                    if ( columns[i].parent.equals(marginColumn.id) ) { 
                        String label= plotj.getYaxis().getLabel();
                        boolean addLines= plotj.getYaxis().isDrawTickLabels();
                        int lc= lineCount(label);
                        int lcPlusTicks= lc + 4;
                        MaxLeftJEm= addLines ? lcPlusTicks : 0.;
                        MaxLeft[i]= Math.max( MaxLeft[i], MaxLeftJEm*emToPixels );
                        MaxLeftEm[i]= Math.max( MaxLeftEm[i], MaxLeftJEm );
                        double nnrightEm;
                        if ( plotj.getZaxis().isVisible() ) {
                            nnrightEm= -4;
                        } else {
                            nnrightEm= -1;
                        }
                        if ( plotj.getLegendPosition()==LegendPosition.OutsideNE ||
                                plotj.getLegendPosition()==LegendPosition.OutsideSE ) {
                            if ( plotj.isDisplayLegend() ) {
                                double legendWidthEms= -8;
                                nnrightEm= Math.min( nnrightEm, legendWidthEms );
                            }
                        }
                        MaxRightEm[i]= Math.min( MaxRightEm[i], nnrightEm );
                        MaxRight[i]= MaxRightEm[i]*emToPixels;

                        doAdjust[i]= true;
                    } else {
                        doAdjust[i]= false;
                    }
                }
                if ( horizontalSpacing.trim().length()>0 ) {
                    MaxRightEm[i]= emsRightSize[i]-emsLeftSize[i];
                    MaxLeftEm[i]= 0.;
                }

            }

        }
        if ( logger.isLoggable(Level.FINER) ) {
            logger.log(Level.FINER, "2. space needed to the right and left of each plot:" );
            for ( int i=0; i<ncolumn; i++ ) {
                logger.log(Level.FINER, "  {0}em {1}em", new Object[]{MaxLeftEm[i], MaxRightEm[i]});
            }
        }

            // 2.5 see if we can tweak the marginRow to make the row em offsets more similar.  Note for two rows this can
            // always be done.
            if ( columns.length>1 ) {
                // when all but the top have the equal ems, moving ems to the marginColumn if will make things equal
                boolean adjust=true;
                double em= MaxLeftEm[1];
                for ( int i=2; i<columns.length; i++ ) {
                    if ( em!=MaxLeftEm[i] ) {
                        adjust= false;
                    }
                }
                if ( adjust ) {
                    if ( MaxLeftEm[0]!=em ) {
                        double toMarginEms= MaxLeftEm[0]-em;
                        MaxLeftEm[0]=em;
                        MaxLeft[0]=em*emToPixels;
                        double[] dd1= parseLayoutStr( marginColumn.left, new double[] { 0, 0, 0 } );
                        dd1[1]= toMarginEms;
                        marginColumn.left= DasDevicePosition.formatLayoutStr(dd1);
                    }
                }
                // now do the same thing but with the right, moving ems to the marginColumn when it will make things equal
                adjust=true;
                em= MaxRightEm[0];
                for ( int i=0; i<columns.length-1; i++ ) {
                    if ( em!=MaxRightEm[i] ) {
                        adjust= false;
                    }
                }
                if ( adjust ) {
                    int last= MaxRightEm.length-1;
                    if ( MaxRightEm[last]!=em ) {
                        double toMarginEms= MaxRightEm[0]-em;
                        MaxRightEm[last]=em;
                        MaxRight[last]=em*emToPixels;
                        double[] dd1= parseLayoutStr( marginColumn.right, new double[] { 0, 0, 0 } );
                        dd1[1]+=toMarginEms;
                        marginColumn.right= DasDevicePosition.formatLayoutStr(dd1);
                    }
                }                
            }
            
            
        // 3. identify the number of pixels in each of the columns which are resizable.
        double totalPlotWidthPixels= 0;
        for ( int i=0; i<ncolumn; i++ ) {           
            List<Plot> plots= DomOps.getPlotsFor( dom, columns[i], true );

            if ( plots.size()>0 ) {
                int d1 = DomUtil.getColumnPositionPixels( dom, columns[i], columns[i].getLeft() );
                int d2 = DomUtil.getColumnPositionPixels( dom, columns[i], columns[i].getRight() );
                resizablePixels[i]= ( d2-d1 );
                if ( isEmColumn[i] ) {
                    logger.fine("here's a fixed-width column!");
                } else {
                    totalPlotWidthPixels= totalPlotWidthPixels + resizablePixels[i];
                }
            }
        }
        logger.log(Level.FINER, "3. number of pixels used by plots which are resizable: {0}", totalPlotWidthPixels);
        
        // 4. express this as a fraction of all the pixels which could be resized.
        double [] relativePlotWidth= new double[ ncolumn ];
        for ( int i=0; i<ncolumn; i++ ) {
            if ( isEmColumn[i] ) {
                relativePlotWidth[i]= 0.0;
            } else {
                relativePlotWidth[i]= (double)(resizablePixels[i]) / totalPlotWidthPixels;
            }
        }
        if ( logger.isLoggable(Level.FINER) ) {
            logger.finer("4. relative sizes of the rows: ");
            for ( int i=0; i<ncolumn; i++ ) {
                logger.log(Level.FINER, "  {0}", relativePlotWidth[i]);
            }
        }
         
        // 5. Calculate the number of pixels available for resized plots on the canvas.
        double canvasWidth= canvas.width;
        int d1= DomUtil.getColumnPositionPixels( dom, marginColumn, marginColumn.left );
        int d2= DomUtil.getColumnPositionPixels( dom, marginColumn, marginColumn.right );
        double marginWidth= d2-d1;
            
        double newPlotTotalWidthPixels= marginWidth;
        for ( int i=0; i<ncolumn; i++ ) {
            newPlotTotalWidthPixels = newPlotTotalWidthPixels - MaxLeft[i] + MaxRight[i];
        }
        logger.log(Level.FINER, "5. number of pixels available to the plots which can resize: {0}", newPlotTotalWidthPixels);

        // 6. newPlotWidth is the width of each plot in pixels.
        double [] newPlotWidthPixels= new double[ ncolumn ];
        for ( int i=0; i<ncolumn; i++ ) {
            newPlotWidthPixels[i]= newPlotTotalWidthPixels * relativePlotWidth[i];
        }
        if ( logger.isLoggable(Level.FINER) ) {
            logger.finer("6. new resizable plot widths in pixels: ");
            for ( int i=0; i<ncolumn; i++ ) {
                logger.log(Level.FINER, "  {0}", newPlotWidthPixels[i]);
            }
        }

        // 7. Now calculate the total width in normalized widths of each plot.
        // normalPlotWidth will be the normalized size of each plot, which includes the em offsets.
        double[] normalPlotWidth= new double[ ncolumn ];

        if ( ncolumn==1 ) {
            normalPlotWidth[0]= ( newPlotWidthPixels[0] ) / ( marginWidth );
        } else {
            for ( int i=0; i<ncolumn; i++ ) {
                if ( relativePlotWidth[i]==0 ) {
                    normalPlotWidth[i]= 0.0;
                } else {
                    normalPlotWidth[i]= ( newPlotWidthPixels[i] + MaxLeft[i] - MaxRight[i] ) / ( marginWidth );
                }
            }
        }
        if ( logger.isLoggable(Level.FINER) ) {
            logger.finer("7. new plot widths, which also include the em offsets: ");
            for ( int i=0; i<ncolumn; i++ ) {
                logger.log(Level.FINER, "  {0}", normalPlotWidth[i]);
            }
        }
        
        // 8. calculate each columns's new layout string, possibly adding additional ems for columns which are not resized.
        double position= 0;
        double extraEms= 0;

        // guess the spacing expected between plots, for when this is not explicit.
        double nominalSpacingEms= -MaxRightEm[0]+MaxLeftEm[0];

        for ( int i=0; i<ncolumn; i++ ) {
            if ( doAdjust[i] ) {
                String newLeft;
                String newRight;                
                if ( !isEmColumn[i] ) {
                    newLeft =  DasDevicePosition.formatLayoutStr( new double [] { position, (MaxLeftEm[i]+extraEms), 0 } );
                    position+= normalPlotWidth[i];
                    newRight = DasDevicePosition.formatLayoutStr( new double [] { position, (MaxRightEm[i]+extraEms), 0 } );
                } else {
                    newLeft = DasDevicePosition.formatLayoutStr( new double [] { position, 100*position, (MaxLeftEm[i]+extraEms), 0 } );
                    newRight = DasDevicePosition.formatLayoutStr( new double [] { position, 100*position, (MaxRightEm[i]+extraEms), 0 } );
                    if ( horizontalSpacing.trim().length()>0 ) {
                        logger.finest("we already accounted for this.");
                    } else {
                        extraEms+= nominalSpacingEms + ( MaxRightEm[i] + MaxLeftEm[i] );
                    }
                }
                columns[i].setLeft( newLeft );                
                columns[i].setRight( newRight );
                if ( logger.isLoggable( Level.FINE ) ) {
                    int r0= (int)DomUtil.getColumnPositionPixels( dom, columns[i], columns[i].getLeft() );
                    int r1= (int)DomUtil.getColumnPositionPixels( dom, columns[i], columns[i].getRight() );
                }
            } else {
                logger.log(Level.FINEST, "row {0} is not adjusted", i );
            }
        }
        if ( logger.isLoggable(Level.FINER) ) {
            logger.finer("8. new layout strings: ");
            for ( int i=0; i<ncolumn; i++ ) {
                logger.log(Level.FINER, "  {0} {1}", new Object[]{ columns[i].getLeft(), columns[1].getRight() } );
            }
        }
        
        // 9. reset the columns to this new location.
        for ( int i=0; i<columns.length; i++ ) {
            canvas.getColumns(i).syncTo(columns[i]);
        }
        canvas.getMarginColumn().syncTo(marginColumn);
        
        logger.log(Level.FINEST, "done" );
        
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
