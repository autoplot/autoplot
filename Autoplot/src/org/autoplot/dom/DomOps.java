
package org.autoplot.dom;

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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSourceUtil;
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
    public static final String OPTION_FIX_LAYOUT_MOVE_LEGENDS_TO_OUTSIDE_NE = "moveLegendsToOutsideNE";
    public static final String OPTION_FIX_LAYOUT_VERTICAL_SPACING = "verticalSpacing";
    
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
        Logger logger= LoggerManager.getLogger("autoplot.dom.layout");
        logger.fine( "enter fixLayout" );
        
        boolean autoLayout= dom.options.isAutolayout();
        dom.options.setAutolayout(false);
        
        try {
                
            Canvas canvas= dom.getCanvases(0);
            Row marginRow= canvas.getMarginRow();
            
            double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();
            double pixelsToEm= 1/emToPixels;

            Row[] rows= canvas.getRows();
            int nrow= rows.length;
            boolean[] doAdjust= new boolean[nrow];

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

            // sort rows, which is a refactoring.
            Arrays.sort( rows, (Row r1, Row r2) -> {
                int d1= DomUtil.getRowPositionPixels( dom, r1, r1.getTop() );
                int d2= DomUtil.getRowPositionPixels( dom, r2, r2.getTop() );
                return d1-d2;
            });

            String topRowId= rows[0].getId();
            String bottomRowId= rows[rows.length-1].getId();

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

            if ( options.getOrDefault( OPTION_FIX_LAYOUT_MOVE_LEGENDS_TO_OUTSIDE_NE, "false" ).equals("true") ) {
                for ( Plot p: dom.plots ) {
                    if ( p.isDisplayLegend() ) {
                        if ( !p.getZaxis().isVisible() ) {
                            p.setLegendPosition(LegendPosition.OutsideNE);
                        }
                    }
                }
            }

            String verticalSpacing=  options.getOrDefault( OPTION_FIX_LAYOUT_VERTICAL_SPACING, "" );

            double [] MaxUp= new double[ nrow ];
            double [] MaxDown= new double[ nrow ];
            double [] MaxUpEm= new double[ nrow ];
            double [] MaxDownEm= new double[ nrow ];

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
                        logger.log(Level.FINE, "line552: {0},{1}", new Object[]{rows[i].top, rows[i].bottom});
                    }
                }
            }

            // reset marginRow.  define nup to be the number of lines above the top plot row.  define nbottom to be the number
            // of lines below the bottom row.
            double ntopEm=0, nbottomEm=0;
            for ( int i=0; i<dom.plots.size(); i++ ) {
                Plot p= dom.plots.get(i);
                if ( p.getRowId().equals(topRowId) ) {
                    ntopEm= Math.max( ntopEm, lineCount( p.getTitle() ) );
                }
                if ( p.getRowId().equals(bottomRowId) ) {
                    if ( p.getXaxis().isDrawTickLabels() ) {
                        if ( p.getEphemerisLineCount()>-1 ) {
                            nbottomEm= Math.max( nbottomEm, p.getEphemerisLineCount()+1 );  // +1 is for ticks
                        } else {
                            nbottomEm= Math.max( nbottomEm, 2 );
                        }
                    } else {
                        nbottomEm= Math.max( nbottomEm, 1 );
                    }
                }
            }
            nbottomEm= 0;
            marginRow.setTop( String.format( "0%%+%.1fem", ntopEm+2 ) );
            marginRow.setBottom( String.format( "100%%-%.1fem", nbottomEm ) );

            double[] resizablePixels= new double[nrow];
            boolean[] isEmRow= new boolean[nrow];
            double[] emsUpSize= new double[nrow];
            double[] emsDownSize= new double[nrow];

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
                            if ( i==0 ) {
                                MaxUpJEm= ( addLines ? lc : 0. ) - ntopEm;
                            } else {
                                MaxUpJEm= addLines ? lc : 0.;
                            }
                            
                            logger.log(Level.FINE, "{0} addLines: {1}  isDiplayTitle: {2}  lineCount(title): {3}", 
                                    new Object[]{plotj.getId(), addLines, plotj.isDisplayTitle(), lc});
                            MaxUp[i]= Math.max( MaxUp[i], MaxUpJEm*emToPixels );
                            MaxUpEm[i]= Math.max( MaxUpEm[i], MaxUpJEm );
                            if ( plotj.getXaxis().isDrawTickLabels() ) {
                                if ( plotj.getEphemerisLineCount()>-1 ) {
                                    nbottomEm= -(plotj.getEphemerisLineCount()+1);  // +1 is for ticks
                                } else {
                                    nbottomEm= -(2+1);
                                }
                            } else {
                                nbottomEm= -1;
                            }
                            MaxDownEm[i]= Math.min( MaxDownEm[i], nbottomEm );
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
            
            // assert the sum of relativePlotHeight is 1.0
            
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
                        totalPlotHeightPixels= totalPlotHeightPixels + resizablePixels[i] + MaxUp[i] - MaxDown[i];
                    }
                }
            }

            double [] relativePlotHeight= new double[ nrow ];
            for ( int i=0; i<nrow; i++ ) {
                if ( isEmRow[i] ) {
                    relativePlotHeight[i]= 0.0;
                } else {
                    relativePlotHeight[i]= (double)(resizablePixels[i]+MaxUp[i]-MaxDown[i]) / totalPlotHeightPixels;
                }
            }
            
            double canvasHeight= canvas.height;
            int d1= DomUtil.getRowPositionPixels( dom, canvas.marginRow, canvas.marginRow.top );
            int d2= DomUtil.getRowPositionPixels( dom, canvas.marginRow, canvas.marginRow.bottom );
            double marginHeight= d2-d1;

            double newPlotTotalHeightPixels= marginHeight; // this will be the pixels available to divide amungst the plots.
            for ( int i=0; i<nrow; i++ ) {
                if ( isEmRow[i] ) {
                    newPlotTotalHeightPixels = newPlotTotalHeightPixels - MaxUp[i] + MaxDown[i];
                }
            }

            // newPlotHeight is the height of each plot in pixels.
            double [] newPlotHeightPixels= new double[ nrow ];
            for ( int i=0; i<nrow; i++ ) {
                newPlotHeightPixels[i]= newPlotTotalHeightPixels * relativePlotHeight[i]; 
            }

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
                        normalPlotHeight[i]= newPlotHeightPixels[i] / ( marginHeight );
                    }
                }
            }

            double position= 0;
            double extraEms= 0;

            // guess the spacing expected between plots, for when this is not explicit.
            double nominalSpacingEms= -MaxDownEm[0]+MaxUpEm[0];

            for ( int i=0; i<nrow; i++ ) {
                if ( doAdjust[i] ) {
                    String newTop;
                    String newBottom;                
                    if ( !isEmRow[i] ) {
                        newTop=  String.format( Locale.US, "%.2f%%%+.2fem", 100*position, (MaxUpEm[i]+extraEms) );
                        position+= normalPlotHeight[i];
                        newBottom = String.format( Locale.US, "%.2f%%%+.2fem", 100*position, (MaxDownEm[i]+extraEms) );
                    } else {
                        newTop=  String.format( Locale.US, "%.2f%%%+.2fem", 100*position, (MaxUpEm[i]+extraEms) );
                        newBottom = String.format( Locale.US, "%.2f%%%+.2fem", 100*position, (MaxDownEm[i]+extraEms) );
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

            for ( int i=0; i<rows.length; i++ ) {
                canvas.getRows(i).syncTo(rows[i]);
            }

            fixHorizontalLayout( dom ); //comment while fixing jenkins tests

        } finally {
            dom.options.setAutolayout(autoLayout);
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
        Logger logger= LoggerManager.getLogger("autoplot.dom.layout");
        logger.fine( "enter fixHorizontalLayout" );
                
        Canvas canvas= dom.getCanvases(0);

        double emToPixels= java.awt.Font.decode(dom.getCanvases(0).font).getSize();
        double pixelsToEm= 1/emToPixels;

        Column[] columns= canvas.getColumns();
        
        int ncolumn= columns.length;

        //kludge: check for duplicate names of rows.  Use the first one found.
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
        ArrayList columnsList= new ArrayList(Arrays.asList(columns));
        rm.forEach((r) -> {
            columnsList.remove(r);
        });
        canvas.setColumns((Column[]) columnsList.toArray( new Column[columnsList.size()]));
        columns= canvas.getColumns();
        ncolumn= columns.length;
 
        // sort rows, which is a refactoring.
        Arrays.sort( columns, (Column c1, Column c2) -> {
            int d1= DomUtil.getColumnPositionPixels( dom, c1, c1.getLeft() );
            int d2= DomUtil.getColumnPositionPixels( dom, c2, c2.getLeft() );
            return d1-d2;        });
        
        double totalPlotSizePixels= 0;
        for ( int i=0; i<ncolumn; i++ ) {           
           List<Plot> plots= DomOps.getPlotsFor( dom, columns[i], true );

           if ( plots.size()>0 ) {
               int d1 = DomUtil.getColumnPositionPixels( dom, columns[i], columns[i].getLeft() );
               int d2 = DomUtil.getColumnPositionPixels( dom, columns[i], columns[i].getRight() );
               totalPlotSizePixels= totalPlotSizePixels + ( d2-d1 );
           }
        }
        
        double [] maxLeft= new double[ ncolumn ];
        double [] maxRight= new double[ ncolumn ];

//        double[] emHeight= new double[ nrow ];
//        for ( int i=0; i<nrow; i++ ) {
//            DasRow dasRow= rows[i].getController().dasRow;
//            emHeight[i]= ( dasRow.getEmMaximum() - dasRow.getEmMinimum() );
//        }// I know there's some check we can do with this to preserve 1-em high plots.
        
        for ( int i=0; i<ncolumn; i++ ) {
            List<Plot> plots= DomOps.getPlotsFor( dom, columns[i], true );
            double maxLeftPx;
            double maxRightPx;
            for ( Plot plotj : plots ) {
                String title= plotj.getTitle();
                String content= title; // title.replaceAll("(\\!c|\\!C|\\<br\\>)", " ");
                boolean addLines= true;
                int lc= lineCount(plotj.yaxis.label);
                if ( plotj.zaxis.isVisible() ) {
                    lc+=2+lineCount(plotj.zaxis.getLabel());
                }
                maxLeftPx= ( addLines ? Math.max( 2, lc ) : 0. ) * emToPixels;
                logger.log(Level.FINE, "{0} addLines: {1}  isDiplayTitle: {2}  lineCount(title): {3}", 
                        new Object[]{plotj.getId(), addLines, plotj.isDisplayTitle(), lc});
                //if (MaxUpJEm>0 ) MaxUpJEm= MaxUpJEm+1;
                maxLeft[i]= Math.max( maxLeft[i], maxLeftPx );
                
                if ( plotj.zaxis.isVisible() ) {
                    maxRightPx= 7 * emToPixels;
                } else {
                    maxRightPx= 2 * emToPixels;
                }
                maxRight[i]= Math.max( maxRight[i], maxRightPx );
            }
        }

        double [] relativePlotHeight= new double[ ncolumn ];
        for ( int i=0; i<ncolumn; i++ ) {
            DasColumn dasColumn= columns[i].getController().dasColumn;
            relativePlotHeight[i]= 1.0 * dasColumn.getWidth() / totalPlotSizePixels;
        }
        
        double newPlotTotalWidthPixels= canvas.width;
        for ( int i=0; i<ncolumn; i++ ) {
            newPlotTotalWidthPixels = newPlotTotalWidthPixels - maxLeft[i] - maxRight[i];
        }

        double [] newPlotWidth= new double[ ncolumn ];
        for ( int i=0; i<ncolumn; i++ ) {
            newPlotWidth[i]= newPlotTotalWidthPixels * relativePlotHeight[i];
        }

        double[] normalPlotSize= new double[ ncolumn ];

        Column row= dom.getCanvases(0).getMarginColumn();
        int c0= DomUtil.getColumnPositionPixels( dom, row, row.getLeft() );
        int c1= DomUtil.getColumnPositionPixels( dom, row, row.getRight() );        
        double width= c1-c0;
        
        double[] ppleft,ppright;
        try {
            ppleft= DasRow.parseLayoutStr(row.getLeft());
        } catch ( ParseException ex ) {
            ppleft= new double[] {0,0,0};
        }
        try {
            ppright= DasRow.parseLayoutStr(row.getRight());
        }catch ( ParseException ex ) {
            ppright= new double[] {0,0,0};
        }
        double marginHeightPixels= ( ppleft[1] - ppright[1] ) * emToPixels;
        
        if ( ncolumn==1 ) {
            normalPlotSize[0]= ( newPlotWidth[0] + maxLeft[0] + maxRight[0] ) / ( width + marginHeightPixels );
        } else {
            for ( int i=0; i<ncolumn; i++ ) {
                 normalPlotSize[i]= ( newPlotWidth[i] + maxLeft[i] + maxRight[i] ) / ( width + marginHeightPixels );
            }
        }

        double position=0;

        for ( int i=0; i<ncolumn; i++ ) {
            String newLeft=  String.format( Locale.US, "%.2f%%%+.2fem", 100*position, maxLeft[i] * pixelsToEm );
            columns[i].setLeft( newLeft );
            position+= normalPlotSize[i];
            String newRight= String.format( Locale.US, "%.2f%%%+.2fem", 100*position, -1 * maxRight[i] * pixelsToEm );
            columns[i].setRight( newRight );
            if ( logger.isLoggable( Level.FINE ) ) {
                c0= DomUtil.getColumnPositionPixels( dom,  columns[i],  columns[i].getLeft() );
                c1= DomUtil.getColumnPositionPixels( dom,  columns[i],  columns[i].getRight() );    
                logger.log(Level.FINE, "column {0}: {1},{2} ({3} pixels)", new Object[]{i, newLeft, newRight, c1-c0 });
            }
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
