
package external;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.das2.datum.DatumRange;
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
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.dom.Application;
import org.autoplot.dom.CanvasUtil;
import org.autoplot.dom.Column;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.Row;
import org.das2.qds.QDataSet;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.PyQDataSetAdapter;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.FillStyle;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.qds.DataSetUtil;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyMethod;

/**
 * new implementation of the plot command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * plot( 0, ripples(20) )
 * plot( 1, ripples(20), color=Color.BLUE )
 * plot( 2, ripples(20), renderType='series>color=blue' )
 *}</small></pre></blockquote>
 * @see https://autoplot.org/help.plotCommand
 * @author jbf
 */
public class PlotCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>plot([index],x,y,z,[named parameters])</H2>"
            + "plot (or plotx) plots the data or URI for data on the canvas.\n"
            + "See https://autoplot.org/help.plotCommand<br>\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>"
            + "<tr><td>xlog ylog zlog </td><td>explicitly set this axis to log (or linear when set equal to 0.).</td></tr>\n"
            + " <tr><td> [xyz]title </td><td>set the label for the axis.</td></tr>\n"
            + " <tr><td> index       </td><td>plot index\n</td></tr>"
            + " <tr><td> title       </td><td>title for the plot\n</td></tr>"
            + " <tr><td> renderType  </td><td> explcitly set the render type, to scatter, series, nnSpectrogram, digital, etc\n</td></tr>"
            + " <tr><td> color      </td><td> the line colors.\n</td></tr>"
            + " <tr><td> fillColor   </td><td>the color when filling volumes.\n</td></tr>"
            + " <tr><td> colorTable  </td><td>the color table to use, like white_blue_black or black_red.\n</td></tr>"
            + " <tr><td> symbolSize     </td><td>set the point (pixel) size\n</td></tr>"
            + " <tr><td> symbolFill     </td><td>none, outline, or solid (solid is default)\n</td></tr>"
            + " <tr><td> lineWidth   </td><td>deprecated--the line thickness in points (pixels)\n</td></tr>"
            + " <tr><td> lineThick   </td><td>the line thickness in points (pixels)\n</td></tr>"
            + " <tr><td> lineStyle   </td><td>the line style, one of solid,none,dotfine,dashfine</td></tr>"
            + " <tr><td> symbol      </td><td>the symbol, e.g. dots triangles cross\n</td></tr>"
            + " <tr><td> isotropic   </td><td>constrain the ratio between the x and y axes.\n</td></tr>"
            + " <tr><td> legendLabel </td><td>add label to the legend</td></tr>"
            + " <tr><td> title   </td><td>title for the plot\n</td></tr>"
            + " <tr><td> xpos    </td><td>override horizontal position of plot, eg. '50%+1em,100%-2em'\n</td>"
            + " <tr><td> ypos    </td><td>override vertical position of plot, eg. '0%+1em,25%-2em', 0 is top\n</td>"
            + " <tr><td> [xy]drawTickLabels</td><td>False turns off the x or y tick labels for the plot\n</td>"
            + " <tr><td> [xy]tickValues</td><td>explicitly control the tick locations.</td>"
            + " <tr><td> [xyz]autoRangeHints</td><td>hints to the autorange, see https://autoplot.org/AxisAutoRangeHints\n</td>"
            + " <tr><td> renderer</td><td>add custom renderer, a class extending org.das2.graph.Renderer, see https://autoplot.org/CustomRenderers</td>"
            + " <tr><td> rightAxisOf</td><td>specify a plot where a new plot with a new yaxis.</td>"
            + " <tr><td> topAxisOf</td><td>specify a plot where a new plot with a new xaxis above.</td>"
            + " <tr><td> overplotOf</td><td>a plot or plot element with which this should share axes.  Note something should reset the plot!</td>"
            + " <tr><td> reset=F</td><td>suppress autoranging, default is True"
            + "</table></html>");

    public static final PyString __completions__;
    
    static {
        String text = new BufferedReader(
            new InputStreamReader( PlotCommand.class.getResourceAsStream("PlotCommand.json"), StandardCharsets.UTF_8) )
            .lines().collect(Collectors.joining("\n"));
        __completions__= new PyString( text );
    }
        
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
    
    private static boolean booleanValue( PyObject arg0 ) {
        if ( arg0.isNumberType() ) {
            return arg0.__nonzero__();
        } else {
            String s= String.valueOf(arg0);
            return s.equals("True") || s.equals("T") || s.equals("1");
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
        PyObject True= Py.newBoolean(true);

        FunctionSupport fs= new FunctionSupport( "plot", 
            new String[] { "pos", "x", "y", "z",
            "xtitle", "xrange",
            "ytitle", "yrange",
            "ztitle", "zrange",
            "xscale", "yscale", 
            "xlog", "ylog", "zlog",
            "title",
            "renderType",
            "color", "fillColor", "colorTable",
            "symbolSize","lineWidth","lineThick","lineStyle",
            "symsize","linewidth","linethick","linestyle",
            "legendLabel",
            "symbol", "symbolFill",
            "isotropic", "xpos", "ypos",
            "xdrawTickLabels", "ydrawTickLabels",
            "xautoRangeHints", "yautoRangeHints", "zautoRangeHints",
            "xtickValues", "ytickValues", "ztickValues",
            "renderer", "rightAxisOf", "topAxisOf", "overplotOf",
            "index", "reset"
        },
        new PyObject[] { Py.None, Py.None, Py.None, Py.None,
            Py.None, Py.None,
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
            Py.None, Py.None, 
            Py.None, Py.None, Py.None,
            Py.None, Py.None,
            Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None, Py.None,
            Py.None, True,
        } );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        if ( nparm==0 ) {
            logger.warning("args.length=0");
            return Py.None;
        }

        int iplot=0;
        int nargs= nparm;

        boolean reset=true; // reset axis settings
        
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
        Plot plot=null; // use this plot
        
        Application dom= ScriptContext.getDocumentModel();
        String renderType=null;
        for ( int i=0; i<keywords.length; i++  ) {
            if ( keywords[i].equals("renderType" ) ) {
                renderType= args[i+nparm].toString();
            } else if ( keywords[i].equals("column") || keywords[i].equals("xpos")) {
                String spec= args[i+nparm].toString();
                if ( Ops.isSafeName(spec) ) {
                    DomNode n= DomUtil.getElementById( dom, spec );
                    if ( n instanceof Column ) {
                        column= (Column)n;
                    } else {
                        throw new IllegalArgumentException("column named parameter is not the name of a column");
                    }
                } else if ( args[i+nparm] instanceof PyString ) {
                    column= dom.getCanvases(0).getController().maybeAddColumn( spec );
                } else {
                    try {
                        column= (Column)args[i+nparm].__tojava__(Column.class);
                    } catch (Exception e ) {
                        String columnId=((Plot)args[i+nparm].__tojava__(Plot.class)).getColumnId();
                        DomNode n= DomUtil.getElementById( dom, columnId );
                        column= (Column)n;
                    }
                }
                if ( row==null ) row=dom.getCanvases(0).getMarginRow();
            } else if ( keywords[i].equals("row") || keywords[i].equals("ypos")) {
                String spec= args[i+nparm].toString();
                if ( Ops.isSafeName(spec) ) {
                    DomNode n= DomUtil.getElementById( dom, spec );
                    if ( n instanceof Row ) {
                        row= (Row)n;                        
                    } else {
                        throw new IllegalArgumentException("row named parameter is not the name of a row");
                    }
                } else if ( args[i+nparm] instanceof PyString ) {
                    row= dom.getCanvases(0).getController().maybeAddRow( spec );                 
                } else {
                    try {
                        row= (Row)args[i+nparm].__tojava__(Row.class);
                    } catch (Exception e ) {
                        String rowId=((Plot)args[i+nparm].__tojava__(Plot.class)).getRowId();
                        DomNode n= DomUtil.getElementById( dom, rowId );
                        row= (Row)n;
                    }
                }
                if ( column==null ) column=dom.getCanvases(0).getMarginColumn();
            } else if ( keywords[i].equals("rightAxisOf") || keywords[i].equals("topAxisOf") || keywords[i].equals("overplotOf") ) {
                String spec= args[i+nparm].toString();
                Plot p=null;
                if ( Ops.isSafeName(spec) ) {
                    DomNode n= DomUtil.getElementById( dom, spec );
                    if ( n instanceof PlotElement ) {
                        n= DomUtil.getElementById( dom, ((PlotElement)n).getPlotId() );
                    }
                    p= (Plot)n;
                } else {
                    try {
                        p = (Plot)args[i+nparm].__tojava__(Plot.class);
                    } catch ( Exception e ) {
                        PlotElement pe= ((PlotElement)args[i+nparm].__tojava__(PlotElement.class));
                        if ( pe!=null ) {
                            p= (Plot) DomUtil.getElementById( dom, ((PlotElement)pe).getPlotId() );
                        }                        
                    }
                }
                if ( p==null ) {
                    throw new IllegalArgumentException("unable to identify plot");
                }
                Plot underPlot=null;
                row= (Row)DomUtil.getElementById(dom,p.getRowId());
                column= (Column)DomUtil.getElementById(dom,p.getColumnId());
                if ( keywords[i].equals("overplotOf") ) {
                    plot= p;
                    iplot= dom.getDataSourceFilters().length;
                } else {
                    for ( Plot p1: dom.getPlots() ) {
                        if ( p1.getRowId().equals(row.getId()) && p1.getColumnId().equals(column.getId()) ) {
                            if ( p1.getYaxis().isOpposite() ) {
                                plot= p1;
                            } else {
                                underPlot= p1;
                            }
                        }
                    }
                }
                
                if ( plot==null ) {
                    plot= dom.getController().addPlot( row, column );
                    if ( keywords[i].equals("rightAxisOf") ) {
                        plot.getYaxis().setOpposite(true);
                        dom.getController().bind( underPlot.getXaxis(), "range", plot.getXaxis(), "range"  );
                        plot.getXaxis().setVisible(false);
                    } else if ( keywords[i].equals("topAxisOf") ) {
                        plot.getXaxis().setOpposite(true);
                        dom.getController().bind( underPlot.getYaxis(), "range", plot.getYaxis(), "range"  );
                        plot.getYaxis().setVisible(false);
                    }
                }
                
            } else if ( keywords[i].equals("index") ) {
                int sindex= Integer.parseInt( args[i+nparm].toString() );
                iplot= sindex;
            } else if ( keywords[i].equals("reset") ) {
                reset= args[i+nparm].equals(True);
            } else if ( keywords[i].equals("renderer") ) {
                renderType="internal";
            }
        }
        
        if ( row!=null ) {
            assert column!=null;
            Plot p= plot;
            if ( p==null ) {
                for ( Plot p1: dom.getPlots() ) {
                    if ( p1.getRowId().equals(row.getId()) && p1.getColumnId().equals(column.getId()) ) {
                        p=p1;
                    }
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
        } else if ( nargs==2 && po0 instanceof PyString && args[1] instanceof PyString ) {
            DatumRange drtr= DatumRangeUtil.parseTimeRangeValid(((PyString)args[1]).toString() );
            try{
                String uri= DataSourceUtil.setTimeRange( ((PyString) po0).toString(), drtr, new NullProgressMonitor() );
                ScriptContext.plot( iplot, uri );          
            } catch ( IOException | URISyntaxException | ParseException ex ) {
                throw new RuntimeException(ex);
            }
            
        } else {
            for ( int i=0; i<nargs; i++ ) {
                QDataSet ds= coerceIt(args[i]);
                qargs[i]= ds;
            }

            if ( nargs==1 ) {  // x
                ScriptContext.plot( iplot, null, null, qargs[0], renderType, reset );
            } else if ( nargs==2 ) {  // x, y
                ScriptContext.plot( iplot, null, qargs[0], qargs[1], renderType, reset );
            } else if ( nargs==3 ) {  // x, y, z
                ScriptContext.plot( iplot, null, qargs[0], qargs[1], qargs[2], renderType, reset );
            }

        }
        // we're done plotting, now for the arguments 

        // we can't use this up above because ScriptContext.plot hangs because we've locked the application and it can't wait until idle.
        // NOTE THERE'S A BUG HERE, for a moment the application is idle and a waiting process could proceed.
        dom.getController().registerPendingChange( this, this );  
        dom.getController().performingChange(this,this);

        final Plot fplot;
        final PlotElement fplotElement;
        
        try {
            int chNum= iplot;

            while ( dom.getDataSourceFilters().length <= chNum ) {
                Plot p= CanvasUtil.getMostBottomPlot(dom.getController().getCanvas());
                dom.getController().setPlot(p);
                dom.getController().addPlotElement( null, null  );
            }
            DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
            List<PlotElement> elements= dom.getController().getPlotElementsFor( dsf );
            if ( elements.isEmpty() ) {
                logger.log(Level.WARNING, "no elements found for data at index={0}", iplot);
                return Py.None;
            }
            PlotElement element= elements.get(0);
            plot= dom.getController().getPlotFor(element);
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
                    plot.getYaxis().setLog( booleanValue( val ) );
                } else if ( kw.equals("yscale") ) {
                    plot.getYaxis().setScale(JythonOps.datum(val));
                } else if ( kw.equals("xtitle") ) {
                    plot.getXaxis().setLabel( sval);
                } else if ( kw.equals("xrange") ) {
                    DatumRange newRange= JythonOps.datumRange( val,plot.getXaxis().getRange().getUnits() );
                    if ( plot.getXaxis().isLog() && newRange.min().doubleValue(newRange.getUnits())<0 ) {
                        plot.getXaxis().setLog(false);
                    }
                    plot.getXaxis().setRange( newRange );
                } else if ( kw.equals("xlog") ) {
                    plot.getXaxis().setLog( booleanValue(val) );
                } else if ( kw.equals("xscale") ) {
                    plot.getXaxis().setScale(JythonOps.datum(val));
                } else if ( kw.equals("ztitle") ) {
                    plot.getZaxis().setLabel( sval);
                } else if ( kw.equals("zrange") ) {
                    DatumRange newRange= JythonOps.datumRange(val,plot.getZaxis().getRange().getUnits());
                    if ( plot.getZaxis().isLog() && newRange.min().doubleValue(newRange.getUnits())<0 ) {
                        plot.getZaxis().setLog(false);
                    }
                    plot.getZaxis().setRange( newRange );
                } else if ( kw.equals("zlog") ) {
                    plot.getZaxis().setLog( booleanValue(val) );
                } else if ( kw.equals("color" ) ) {
                    Color c= JythonOps.color(val);
                    element.getStyle().setColor( c );
                } else if ( kw.equals("fillColor" ) ) { // because you can specify renderType=stairSteps, we need fillColor.
                    Color c;
                    c= JythonOps.color(val);
                    element.getStyle().setFillColor( c );
                } else if ( kw.equals("colorTable" ) ) { 
                    if ( val.__tojava__(DasColorBar.Type.class) == Py.NoConversion) {
                        DasColorBar.Type t= org.das2.graph.DasColorBar.Type.parse(sval);
                        element.getStyle().setColortable(t);
                    } else {
                        DasColorBar.Type t = (DasColorBar.Type) val.__tojava__(DasColorBar.Type.class);
                        element.getStyle().setColortable( t );
                    }
                } else if ( kw.equals("title") ) {
                    plot.setTitle(sval);
                } else if ( kw.equals("symsize") || kw.equals("symbolSize") ) {
                    element.getStyle().setSymbolSize( Double.valueOf(sval) );
                } else if ( kw.equals("symbolFill") ) {
                    if ( element.getController().getRenderer() instanceof SeriesRenderer ) {
                        FillStyle sfs= (FillStyle) ClassMap.getEnumElement( FillStyle.class, sval ) ;
                        ((SeriesRenderer) element.getController().getRenderer() ).setFillStyle(sfs);
                    }
                } else if ( kw.equals("linewidth" ) || kw.equals("lineWidth") ) {
                    element.getStyle().setLineWidth( Double.valueOf(sval) );
                } else if ( kw.equals("linethick" ) || kw.equals("lineThick") ) {
                    element.getStyle().setLineWidth( Double.valueOf(sval) );
                } else if ( kw.equals("linestyle") || kw.equals("lineStyle") ) {
                    PsymConnector p= (PsymConnector) ClassMap.getEnumElement( PsymConnector.class, sval );
                    element.getStyle().setSymbolConnector( p );
                } else if ( kw.equals("symbol") ) {
                    PlotSymbol p= (PlotSymbol) ClassMap.getEnumElement( DefaultPlotSymbol.class, sval );
                    if ( p!=null ) {
                        element.getStyle().setPlotSymbol( p );
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
                        element.setRenderType(rt);
                        element.setRenderControl(renderControl);
                    }
                } else if ( kw.equals("renderer") ) {
                    Renderer r;
                    if (val.__tojava__(Renderer.class) != Py.NoConversion) {
                        Renderer oldRenderer= element.getController().getRenderer();
                        String control= oldRenderer.getControl();
                        r = (Renderer) val.__tojava__(Renderer.class);
                        QDataSet ds1=null;
                        switch (nargs) {
                            case 1:
                                ds1= qargs[0];
                                break;
                            case 2:
                                ds1= Ops.link( qargs[0], qargs[1] );
                                break;
                            case 3:
                                ds1= Ops.link( qargs[0], qargs[1], qargs[2] );
                                break;
                            default:
                                break;
                        }
                        PyObject doAuto= val.__findattr__( "doAutorange" );
                        if ( doAuto==null ) {
                            doAuto= val.__findattr__( "autorange" );
                        }
                        if ( doAuto!=null && doAuto!=Py.None && ds1!=null ) {
                            PyObject range= ((PyMethod)doAuto).__call__(new PyQDataSetAdapter().adapt(ds1));
                            QDataSet rangeds= (QDataSet) range.__tojava__(QDataSet.class);
                            plot.getXaxis().setRange( DataSetUtil.asDatumRange(rangeds.slice(0) ) );
                            if ( rangeds.length()>1 ) plot.getYaxis().setRange( DataSetUtil.asDatumRange(rangeds.slice(1) ) );
                            if ( rangeds.length()>2 ) plot.getZaxis().setRange( DataSetUtil.asDatumRange(rangeds.slice(2) ) );
                        }
                        plot.getController().getDasPlot().removeRenderer(oldRenderer);
                        plot.getController().getDasPlot().addRenderer(r);
                        r.setDataSet(ds1);
                        r.setColorBar((DasColorBar) plot.getZaxis().getController().getDasAxis());
                        element.getController().setRenderer(r);
                        element.setRenderType(RenderType.internal);
                        r.setControl(control);
                    } else {
                        logger.warning("no conversion for renderer");
                    }
                } else if ( kw.equals("legendLabel" ) ) {
                    if ( !sval.equals("") ) {
                        element.setLegendLabel(sval);
                        element.setDisplayLegend(true);
                    }
                } else if ( kw.equals("isotropic" ) ) {
                    plot.setIsotropic(true);
                } else if ( kw.equals("xdrawTickLabels") ) {
                    plot.getXaxis().setDrawTickLabels( booleanValue(val) );
                } else if ( kw.equals("ydrawTickLabels") ) {
                    plot.getYaxis().setDrawTickLabels( booleanValue(val) );
                } else if ( kw.equals("xtickValues") ) {
                    plot.getXaxis().setTickValues(sval);
                } else if ( kw.equals("ytickValues") ) {
                    plot.getYaxis().setTickValues(sval);
                } else if ( kw.equals("ztickValues") ) {
                    plot.getZaxis().setTickValues(sval);
                } else if ( kw.equals("xautoRangeHints") ) {
                    plot.getXaxis().setAutoRangeHints( sval );
                } else if ( kw.equals("yautoRangeHints") ) {
                    plot.getYaxis().setAutoRangeHints( sval );
                } else if ( kw.equals("zautoRangeHints") ) {
                    plot.getZaxis().setAutoRangeHints( sval );
                }
            }
            
            fplot= plot;
            fplotElement= element;

        } finally {
            dom.getController().changePerformed(this,this);
        }

        return new PyList( new PyObject[] { new PyJavaInstance(fplot), new PyJavaInstance(fplotElement)  } );
    }

}
