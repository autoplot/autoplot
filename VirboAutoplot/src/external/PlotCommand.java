/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package external;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PlotSymbol;
import org.python.core.Py;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.CanvasUtil;
import org.virbo.autoplot.dom.Column;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.Row;
import org.virbo.dataset.QDataSet;
import org.virbo.jythonsupport.JythonOps;

/**
 * new implementation of the plot command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * plotx( 0, ripples(20) )
 * plotx( 1, ripples(20), renderType='color:blue' )
 *}</small></pre></blockquote>
 * @author jbf
 */
public class PlotCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    
    public static PyString __doc__ =
        new PyString("<html>plotx is an experimental extension of the plot command that uses Python features like keywords.\n"
            + "<br>plotx(x,y,z,[keywords])\n"
            + "<br>keywords:\n"
            + "<table>"
            + "<tr><td>xlog ylog zlog </td><td>explicitly set this axis to log (or linear when set equal to 0.).</td></tr>\n"
            + " <tr><td> xtitle ytitle ztitle  </td><td>set the label for the axis.</td></tr>\n"
            + " <tr><td> title       </td><td>title for the plot\n</td></tr>"
            + " <tr><td> renderType  </td><td> explcitly set the render type, to scatter, series, nnSpectrogram, digital, etc\n</td></tr>"
            + " <tr><td> color      </td><td> the line colors.\n</td></tr>"
            + " <tr><td> fillColor   </td><td>the color when filling volumes.\n</td></tr>"
            + "  <tr><td>symsize     </td><td>set the point (pixel) size\n</td></tr>"
            + " <tr><td> linewidth   </td><td>the line thickness in points (pixels)\n</td></tr>"
            + " <tr><td> symbol      </td><td>the symbol, e.g. dots triangles cross\n</td></tr>"
            + " <tr><td> isotropic   </td><td>constrain the ratio between the x and y axes.\n</td></tr>"
            + " <tr><td> title   </td><td>title for the plot\n</td></tr>"
            + "</table>");

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
     * return the object or null for this string  "RED" -&gt; Color.RED
     * @param c the class defining the target type
     * @param ele the string representation to be interpreted for this type.
     * @return the instance of the type.
     */
    private Object getEnumElement( Class c, String ele ) {
        int PUBLIC_STATIC_FINAL = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
        List lvals;
        if (c.isEnum()) {
            Object[] vals = c.getEnumConstants();
            for (Object o : vals) {
                Enum e = (Enum) o;
                if ( e.toString().equalsIgnoreCase(ele) ) return e;
            }
            lvals= Arrays.asList(vals);
        } else {
            Field[] fields = c.getDeclaredFields();
            lvals= new ArrayList();
            for ( Field f: fields ) {
                try {
                    String name = f.getName();
                    if ( ( ( f.getModifiers() & PUBLIC_STATIC_FINAL) == PUBLIC_STATIC_FINAL ) ) {
                        Object value = f.get(null);
                        if ( value!=null && c.isInstance(value) ) {
                            lvals.add(value);
                            if ( name.equalsIgnoreCase(ele) || value.toString().equalsIgnoreCase(ele) ) {
                               return value;
                            }
                        }
                    }
                } catch (IllegalAccessException iae) {
                    IllegalAccessError err = new IllegalAccessError(iae.getMessage());
                    err.initCause(iae);
                    throw err;
                }
            }
        }
        logger.log( Level.INFO, "looking for {0}, found {1}\n", new Object[]{ele, lvals.toString()});
        return null;
    }

    /**
     * implement the python call.
     * @param args
     * @param keywords
     * @return 
     */
    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {

        PyObject False= Py.newBoolean(false);

        FunctionSupport fs= new FunctionSupport( "plotx", 
            new String[] { "x", "y", "z",
            "xtitle", "xrange",
            "ytitle", "yrange",
            "ztitle", "zrange",
            "xlog", "ylog", "zlog",
            "title",
            "renderType",
            "color", "fillColor",
            "symsize","linewidth",
            "symbol",
            "isotropic", "xpos", "ypos"
        },
        new PyObject[] { Py.None, Py.None,
            Py.None, Py.None,
            Py.None, Py.None,
            Py.None, Py.None,
            False, False, False,
            Py.None,
            Py.None,
            Py.None,Py.None,
            Py.None,Py.None,
            Py.None,
            Py.None, Py.None, Py.None
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
            try {
                ScriptContext.plot( iplot, ((PyString) po0).toString());
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } else {
            for ( int i=0; i<nargs; i++ ) {
                QDataSet ds= coerceIt(args[i]);
                qargs[i]= ds;
            }

            try {
                if ( nargs==1 ) {  // x
                    ScriptContext.plot( iplot, null, null, qargs[0], renderType );
                } else if ( nargs==2 ) {  // x, y
                    ScriptContext.plot( iplot, null, qargs[0], qargs[1], renderType );
                } else if ( nargs==3 ) {  // x, y, z
                    ScriptContext.plot( iplot, null, qargs[0], qargs[1], qargs[2], renderType );
                }

            } catch ( InterruptedException ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                throw new RuntimeException(ex);
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
                    DatumRange dr= plot.getYaxis().getRange();
                    Units u= dr.getUnits();
                    PyList plval= (PyList)val;
                    if ( ((Number)plval.get(0)).doubleValue()<=0 ) {
                        plot.getYaxis().setLog(false);
                    }
                    plot.getYaxis().setRange( DatumRange.newDatumRange( ((Number)plval.get(0)).doubleValue(),
                           ((Number)plval.get(1)).doubleValue(), u ) );
                } else if ( kw.equals("ylog") ) {
                    plot.getYaxis().setLog( "1".equals(sval) );
                } else if ( kw.equals("xtitle") ) {
                    plot.getXaxis().setLabel( sval);
                } else if ( kw.equals("xrange") ) {
                    DatumRange dr= plot.getXaxis().getRange();
                    Units u= dr.getUnits();
                    PyList plval= (PyList)val;
                    if ( ((Number)plval.get(0)).doubleValue()<=0 ) {
                        plot.getXaxis().setLog(false);
                    }
                    plot.getXaxis().setRange( DatumRange.newDatumRange( ((Number)plval.get(0)).doubleValue(),
                           ((Number)plval.get(1)).doubleValue(), u ) );
                } else if ( kw.equals("xlog") ) {
                    plot.getXaxis().setLog( "1".equals(sval) );
                } else if ( kw.equals("ztitle") ) {
                    plot.getZaxis().setLabel( sval);
                } else if ( kw.equals("zrange") ) {
                    DatumRange dr= plot.getZaxis().getRange();
                    Units u= dr.getUnits();
                    PyList plval= (PyList)val;
                    if ( ((Number)plval.get(0)).doubleValue()<=0 ) {
                        plot.getZaxis().setLog(false);
                    }
                    plot.getZaxis().setRange( DatumRange.newDatumRange( ((Number)plval.get(0)).doubleValue(),
                           ((Number)plval.get(1)).doubleValue(), u ) );
                } else if ( kw.equals("zlog") ) {
                    plot.getZaxis().setLog( "1".equals(sval) );
                } else if ( kw.equals("color" ) ) {
                    if ( sval!=null ) {
                       Color c;
                       try {
                           c= Color.decode( sval );
                       } catch ( NumberFormatException ex ) {
                           c= (Color)getEnumElement( Color.class, sval );
                       }
                       if ( c!=null ) {
                           elements.get(0).getStyle().setColor( c );
                       } else {
                           throw new IllegalArgumentException("unable to identify color: "+sval);
                       }
                    }
                } else if ( kw.equals("fillColor" ) ) { // because you can specify renderType=stairSteps, we need fillColor.
                    if ( sval!=null ) {
                       Color c;
                       try {
                           c= Color.decode( sval );
                       } catch ( NumberFormatException ex ) {
                           c= (Color)getEnumElement( Color.class, sval );
                       }
                       if ( c!=null ) {
                           elements.get(0).getStyle().setFillColor( c );
                       } else {
                           throw new IllegalArgumentException("unable to identify color: "+sval);
                       }
                    }
                } else if ( kw.equals("title") ) {
                    plot.setTitle(sval);
                } else if ( kw.equals("symsize") ) {
                    elements.get(0).getStyle().setSymbolSize( Double.valueOf(sval) );
                } else if ( kw.equals("linewidth") ) {
                    elements.get(0).getStyle().setLineWidth( Double.valueOf(sval) );
                } else if ( kw.equals("symbol") ) {
                    PlotSymbol p= (PlotSymbol) getEnumElement( DefaultPlotSymbol.class, sval );
                    if ( p!=null ) {
                        elements.get(0).getStyle().setPlotSymbol( p );
                    } else {
                        throw new IllegalArgumentException("unable to identify symbol: "+sval);
                    }
                } else if ( kw.equals("renderType") ) {
                    String srenderType= sval;
                    String renderControl;
                    if ( srenderType!=null && srenderType.trim().length()>0 ) {
                        int ii= srenderType.indexOf(">");
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
                } else if ( kw.equals("isotropic" ) ) {
                    plot.setIsotropic(true);
                }
            }

        } finally {
            dom.getController().changePerformed(this,this);
        }

        return Py.None;
    }

}
