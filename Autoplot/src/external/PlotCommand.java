/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package external;

import java.awt.Color;
import java.util.List;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.util.ClassMap;
import org.python.core.Py;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.autoplot.RenderType;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.autoplot.dom.CanvasUtil;
import org.autoplot.dom.Column;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.Row;
import org.das2.qds.QDataSet;
import org.autoplot.jythonsupport.JythonOps;

/**
 * new implementation of the plot command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * plot( 0, ripples(20) )
 * plot( 1, ripples(20), color=Color.BLUE )
 * plot( 2, ripples(20), renderType='series>color=blue' )
 *}</small></pre></blockquote>
 * @author jbf
 */
public class PlotCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>plot([index],x,y,z,[named parameters])</H2>"
            + "plot (or plotx) plots the data or URI for data on the canvas.\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>"
            + "<tr><td>xlog ylog zlog </td><td>explicitly set this axis to log (or linear when set equal to 0.).</td></tr>\n"
            + " <tr><td> xtitle ytitle ztitle  </td><td>set the label for the axis.</td></tr>\n"
            + " <tr><td> index       </td><td>plot index\n</td></tr>"
            + " <tr><td> title       </td><td>title for the plot\n</td></tr>"
            + " <tr><td> renderType  </td><td> explcitly set the render type, to scatter, series, nnSpectrogram, digital, etc\n</td></tr>"
            + " <tr><td> color      </td><td> the line colors.\n</td></tr>"
            + " <tr><td> fillColor   </td><td>the color when filling volumes.\n</td></tr>"
            + " <tr><td> colorTable  </td><td>the color table to use, like white_blue_black or black_red.\n</td></tr>"
            + " <tr><td> symbolSize     </td><td>set the point (pixel) size\n</td></tr>"
            + " <tr><td> lineWidth   </td><td>deprecated--the line thickness in points (pixels)\n</td></tr>"
            + " <tr><td> lineThick   </td><td>the line thickness in points (pixels)\n</td></tr>"
            + " <tr><td> lineStyle   </td><td>the line style, one of solid,none,dotfine,dashfine</td></tr>"
            + " <tr><td> symbol      </td><td>the symbol, e.g. dots triangles cross\n</td></tr>"
            + " <tr><td> isotropic   </td><td>constrain the ratio between the x and y axes.\n</td></tr>"
            + " <tr><td> legendLabel </td><td>add label to the legend</td></tr>"
            + " <tr><td> title   </td><td>title for the plot\n</td></tr>"
            + " <tr><td> xpos    </td><td>override horizontal position of plot, eg. '50%+1em,100%-2em'\n</td>"
            + " <tr><td> ypos    </td><td>override vertical position of plot, eg. '0%+1em,25%-2em', 0 is top\n</td>"
            + " <tr><td> xdrawTickLabels</td><td>False turns off the x tick labels for the plot\n</td>"
            + " <tr><td> ydrawTickLabels</td><td>False turns off the y tick labels for the plot\n</td>"
            + " <tr><td> xautoRangeHints</td><td>hints to the autorange, see http://autoplot.org/AxisAutoRangeHints\n</td>"
            + "</table></html>");

    private static QDataSet coerceIt( PyObject arg0 ) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            return JythonOps.dataset(arg0);
        } else {
            QDataSet ds = (QDataSet) o;
            if (ds.rank() == 0) {
                // QDataSet library handles coerce logic.
                return ds;
            } else {
                return ds;
            }
        }
    }
    
    /**
     * implement the python call.
     * @param args the "rightmost" elements are the keyword values.
     * @param keywords the names for the keywords.
     * @return Py.None
     */
    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {

        PyObject False= Py.newBoolean(false);

        FunctionSupport fs= new FunctionSupport( "plot", 
            new String[] { "pos", "x", "y", "z",
            "xtitle", "xrange",
            "ytitle", "yrange",
            "ztitle", "zrange",
            "xlog", "ylog", "zlog",
            "title",
            "renderType",
            "color", "fillColor", "colorTable",
            "symbolSize","lineWidth","lineThick","lineStyle",
            "symsize","linewidth","linethick","linestyle",
            "legendLabel",
            "symbol",
            "isotropic", "xpos", "ypos",
            "xdrawTickLabels", "ydrawTickLabels",
            "xautoRangeHints", "yautoRangeHints", "zautoRangeHints",
            "index"
        },
        new PyObject[] { Py.None, Py.None, Py.None, Py.None,
            Py.None, Py.None,
            Py.None, Py.None,
            Py.None, Py.None,
            False, False, False,
            Py.None,
            Py.None,
            Py.None,Py.None,Py.None,
            Py.None,Py.None,Py.None,Py.None,
            Py.None,Py.None,Py.None,Py.None,
            Py.None,
            Py.None,
            Py.None, Py.None, Py.None,
            Py.None, Py.None,
            Py.None, Py.None, Py.None,
            Py.None
        } );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        if ( nparm==0 ) {
            logger.warning("args.length=0");
            return Py.None;
        }

        int iplot=0;
        int nargs= nparm;

        // If the first (zeroth) argument is an int, than this is the data source where the value should be inserted.  Additional
        // data sources and plots will be added until there are enough.
        // this is an alias for the index argument.
        PyObject po0= args[0];
        if ( po0 instanceof PyInteger ) {
            iplot= ((PyInteger)po0).getValue();
            PyObject[] newArgs= new PyObject[args.length-1];
            for ( int i=0; i<args.length-1; i++ ) {
                newArgs[i]= args[i+1];
            }
            args= newArgs;
            nargs= nargs-1;
            nparm= args.length - keywords.length;
            po0= args[0];
        }
        
        Row row=null; // these will be set when xpos and ypos are used.
        Column column=null;
        
        Application dom= ScriptContext.getDocumentModel();
        String renderType=null;
        for ( int i=0; i<keywords.length; i++  ) {
            if ( keywords[i].equals("renderType" ) ) {
                renderType= args[i+nparm].toString();
            } else if ( keywords[i].equals("xpos" ) ) {
                String spec= args[i+nparm].toString();
                column= dom.getCanvases(0).getController().maybeAddColumn( spec );
                if ( row==null ) row=dom.getCanvases(0).getMarginRow();
            } else if ( keywords[i].equals("ypos")) {
                String spec= args[i+nparm].toString();
                row= dom.getCanvases(0).getController().maybeAddRow( spec );
                if ( column==null ) column=dom.getCanvases(0).getMarginColumn();
            } else if ( keywords[i].equals("index") ) {
                int sindex= Integer.parseInt( args[i+nparm].toString() );
                iplot= sindex;
            }
        }
        
        if ( row!=null ) {
            assert column!=null;
            Plot p= null;
            for ( Plot p1: dom.getPlots() ) {
                if ( p1.getRowId().equals(row.getId()) && p1.getColumnId().equals(column.getId()) ) {
                    p=p1;
                }
            }
            if ( p==null ) p= dom.getController().addPlot( row, column );
            while ( dom.getDataSourceFilters().length <= iplot ) {
                dom.getController().addDataSourceFilter();
            }
            PlotElement pe= dom.getController().addPlotElement( p, dom.getDataSourceFilters(iplot) );
            List<PlotElement> pes= dom.getController().getPlotElementsFor( dom.getDataSourceFilters(iplot) );
            pes.remove(pe);
            for ( PlotElement rm: pes ) {
                Plot prm= dom.getController().getPlotFor(rm);
                dom.getController().deletePlotElement(rm);
                if ( dom.getController().getPlotElementsFor(prm).isEmpty() ) {
                    dom.getController().deletePlot(prm);
                }
            }
        }

        QDataSet[] qargs= new QDataSet[nargs];

        if ( nargs==1 && po0 instanceof PyString ) {
            ScriptContext.plot( iplot, ((PyString) po0).toString());
        } else {
            for ( int i=0; i<nargs; i++ ) {
                QDataSet ds= coerceIt(args[i]);
                qargs[i]= ds;
            }

            if ( nargs==1 ) {  // x
                ScriptContext.plot( iplot, null, null, qargs[0], renderType );
            } else if ( nargs==2 ) {  // x, y
                ScriptContext.plot( iplot, null, qargs[0], qargs[1], renderType );
            } else if ( nargs==3 ) {  // x, y, z
                ScriptContext.plot( iplot, null, qargs[0], qargs[1], qargs[2], renderType );
            }

        }
        // we're done plotting, now for the arguments 

        // we can't use this up above because ScriptContext.plot hangs because we've locked the application and it can't wait until idle.
        // NOTE THERE'S A BUG HERE, for a moment the application is idle and a waiting process could proceed.
        dom.getController().registerPendingChange( this, this );  
        dom.getController().performingChange(this,this);

        try {
            int chNum= iplot;

            while ( dom.getDataSourceFilters().length <= chNum ) {
                Plot p= CanvasUtil.getMostBottomPlot(dom.getController().getCanvas());
                dom.getController().setPlot(p);
                dom.getController().addPlotElement( null, null  );
            }
            DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
            List<PlotElement> elements= dom.getController().getPlotElementsFor( dsf );

            Plot plot= dom.getController().getPlotFor(elements.get(0));
            plot.setIsotropic(false);

            for ( int i=nparm; i<args.length; i++ ) { //HERE nargs
                String kw= keywords[i-nparm];
                PyObject val= args[i];

                String sval= (String) val.__str__().__tojava__(String.class);
                if ( kw.equals("ytitle") ) {
                    plot.getYaxis().setLabel( sval);
                } else if ( kw.equals("yrange") ) {
                    DatumRange newRange= JythonOps.datumRange(val,plot.getYaxis().getRange().getUnits());
                    if ( plot.getYaxis().isLog() && newRange.min().doubleValue(newRange.getUnits())<0 ) {
                        plot.getYaxis().setLog(false);
                    }
                    plot.getYaxis().setRange( newRange );
                } else if ( kw.equals("ylog") ) {
                    plot.getYaxis().setLog( "1".equals(sval) );
                } else if ( kw.equals("xtitle") ) {
                    plot.getXaxis().setLabel( sval);
                } else if ( kw.equals("xrange") ) {
                    DatumRange newRange= JythonOps.datumRange( val,plot.getXaxis().getRange().getUnits() );
                    if ( plot.getXaxis().isLog() && newRange.min().doubleValue(newRange.getUnits())<0 ) {
                        plot.getXaxis().setLog(false);
                    }
                    plot.getXaxis().setRange( newRange );
                } else if ( kw.equals("xlog") ) {
                    plot.getXaxis().setLog( "1".equals(sval) );
                } else if ( kw.equals("ztitle") ) {
                    plot.getZaxis().setLabel( sval);
                } else if ( kw.equals("zrange") ) {
                    DatumRange newRange= JythonOps.datumRange(val,plot.getZaxis().getRange().getUnits());
                    if ( plot.getZaxis().isLog() && newRange.min().doubleValue(newRange.getUnits())<0 ) {
                        plot.getZaxis().setLog(false);
                    }
                    plot.getZaxis().setRange( newRange );
                } else if ( kw.equals("zlog") ) {
                    plot.getZaxis().setLog( "1".equals(sval) );
                } else if ( kw.equals("color" ) ) {
                    Color c= JythonOps.color(val);
                    elements.get(0).getStyle().setColor( c );
                } else if ( kw.equals("fillColor" ) ) { // because you can specify renderType=stairSteps, we need fillColor.
                    Color c;
                    c= JythonOps.color(val);
                    elements.get(0).getStyle().setFillColor( c );
                } else if ( kw.equals("colorTable" ) ) { 
                    DasColorBar.Type t= org.das2.graph.DasColorBar.Type.parse(sval);
                    elements.get(0).getStyle().setColortable(t);
                } else if ( kw.equals("title") ) {
                    plot.setTitle(sval);
                } else if ( kw.equals("symsize") || kw.equals("symbolSize") ) {
                    elements.get(0).getStyle().setSymbolSize( Double.valueOf(sval) );
                } else if ( kw.equals("linewidth" ) || kw.equals("lineWidth") ) {
                    elements.get(0).getStyle().setLineWidth( Double.valueOf(sval) );
                } else if ( kw.equals("linethick" ) || kw.equals("lineThick") ) {
                    elements.get(0).getStyle().setLineWidth( Double.valueOf(sval) );
                } else if ( kw.equals("linestyle") || kw.equals("lineStyle") ) {
                    PsymConnector p= (PsymConnector) ClassMap.getEnumElement( PsymConnector.class, sval );
                    elements.get(0).getStyle().setSymbolConnector( p );
                } else if ( kw.equals("symbol") ) {
                    PlotSymbol p= (PlotSymbol) ClassMap.getEnumElement( DefaultPlotSymbol.class, sval );
                    if ( p!=null ) {
                        elements.get(0).getStyle().setPlotSymbol( p );
                    } else {
                        throw new IllegalArgumentException("unable to identify symbol: "+sval);
                    }
                } else if ( kw.equals("renderType") ) {
                    String srenderType= sval;
                    String renderControl;
                    if ( srenderType!=null && srenderType.trim().length()>0 ) {
                        int ii= srenderType.indexOf('>');
                        if ( ii==-1 ) {
                            renderControl= "";
                        } else {
                            renderControl= srenderType.substring(ii+1);
                            srenderType= srenderType.substring(0,ii);
                        }                    
                        RenderType rt= RenderType.valueOf(srenderType);
                        elements.get(0).setRenderType(rt);
                        elements.get(0).setRenderControl(renderControl);
                    }
                } else if ( kw.equals("legendLabel" ) ) {
                    if ( !sval.equals("") ) {
                        elements.get(0).setLegendLabel(sval);
                        elements.get(0).setDisplayLegend(true);
                    }
                } else if ( kw.equals("isotropic" ) ) {
                    plot.setIsotropic(true);
                } else if ( kw.equals("xdrawTickLabels") ) {
                    plot.getXaxis().setDrawTickLabels( "1".equals(sval) );
                } else if ( kw.equals("ydrawTickLabels") ) {
                    plot.getYaxis().setDrawTickLabels( "1".equals(sval) );
                } else if ( kw.equals("xautoRangeHints") ) {
                    plot.getXaxis().setAutoRangeHints( sval );
                } else if ( kw.equals("yautoRangeHints") ) {
                    plot.getYaxis().setAutoRangeHints( sval );
                } else if ( kw.equals("zautoRangeHints") ) {
                    plot.getZaxis().setAutoRangeHints( sval );
                }
            }

        } finally {
            dom.getController().changePerformed(this,this);
        }

        return Py.None;
    }

}
