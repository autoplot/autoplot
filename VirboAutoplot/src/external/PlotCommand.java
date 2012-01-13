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
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.jythonsupport.PyQDataSetAdapter;
import org.virbo.jythonsupport.Util;

/**
 * new implementation of the plot command allows for keywords.
 * @author jbf
 */
public class PlotCommand extends PyObject {

    private static QDataSet coerceIt( PyObject arg0 ) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            if (arg0.isNumberType()) {
                double d = (Double) arg0.__tojava__(Double.class);
                return DataSetUtil.asDataSet(d);
            } else if (arg0 instanceof PyString ) {
                try {
                    return Util.getDataSet( (String)arg0.__tojava__(String.class) );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                    return null;
                }
            } else if (arg0.isSequenceType()) {
                return PyQDataSetAdapter.adaptList((PyList) arg0);
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
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
     * return the object or null for this string  "RED" -> Color.RED
     * @param c
     * @param ele
     * @return
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
        System.err.printf( "looking for %s, found %s\n", ele, lvals.toString() );
        return null;
    }

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
                "color",
                "symsize","linewidth",
                "symbol",
        },
                new PyObject[] { Py.None, Py.None,
                        Py.None, Py.None,
                        Py.None, Py.None,
                        Py.None, Py.None,
                        False, False, False,
                        Py.None,
                        Py.None,
                        Py.None,
                        Py.None,Py.None,
                        Py.None,
        } );
        //Map<String,PyObject> foo= fs.args( args, keywords );
        //TODO: check on this with Ed.
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        if ( nparm==0 ) {
            System.err.println("args.length=0");
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
        }

        //if ( args[nargs-1] instanceof PyInteger ) {  // NEW! last positional argument can be plot position.  Where is this used?  I think it should go away.
        //    iplot= ((PyInteger)args[nargs-1]).getValue();
        //    nargs= nargs-1;
        //}

        QDataSet[] qargs= new QDataSet[nargs];

        Application dom= ScriptContext.getDocumentModel();

        if ( nargs==1 && po0 instanceof PyString ) {
            try {
                ScriptContext.plot(((PyString) po0).toString());
            } catch (InterruptedException ex) {
                Logger.getLogger(PlotCommand.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            for ( int i=0; i<nargs; i++ ) {
                QDataSet ds= coerceIt(args[i]);
                qargs[i]= ds;
            }

            try {
                if ( nargs==1 ) {  // x
                    ScriptContext.plot( iplot, qargs[0] );
                } else if ( nargs==2 ) {  // x, y
                    ScriptContext.plot( iplot, qargs[0], qargs[1] );
                } else if ( nargs==3 ) {  // x, y, z
                    ScriptContext.plot( iplot, qargs[0], qargs[1], qargs[2] );
                }

            } catch ( InterruptedException ex ) {
                ex.printStackTrace();
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
                    RenderType rt= RenderType.valueOf(sval);
                    elements.get(0).setRenderType(rt);
                }
            }

        } finally {
            dom.getController().changePerformed(this,this);
        }

        return Py.None;
    }

}
