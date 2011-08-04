/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package external;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
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

    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {
        System.err.println( Arrays.asList(args) );
        System.err.println( Arrays.asList(keywords) );

        PyObject False= Py.newBoolean(false);

        FunctionSupport fs= new FunctionSupport( "plotx", 
                new String[] { "x", "y", "z",
                "xtitle", "xrange",
                "ytitle", "yrange",
                "ztitle", "zrange",
                "xlog", "ylog", "zlog",
                "title",
                "renderType",
                "color", },
                new PyObject[] { Py.None, Py.None,
                        Py.None, Py.None,
                        Py.None, Py.None,
                        Py.None, Py.None,
                        False, False, False,
                        Py.None,
                        Py.None,
                        Py.None, } );
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

        PyObject po0= args[0];
        if ( po0 instanceof PyInteger ) {
            iplot= ((PyInteger)po0).getValue();
            for ( int i=0; i<args.length-1; i++ ) {
                args[i]= args[i+1];
            }
            nargs= nargs-1;
        }

        if ( args[nargs-1] instanceof PyInteger ) {  // NEW! last positional argument can be plot position
            iplot= ((PyInteger)args[nargs-1]).getValue();
            nargs= nargs-1;
        }

        QDataSet[] qargs= new QDataSet[nargs];

        Application dom= ScriptContext.getDocumentModel();

//        dom.getController().registerPendingChange( this, this );
//        dom.getController().performingChange(this,this);

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
        
        int chNum= iplot;

        while ( dom.getDataSourceFilters().length <= chNum ) {
            Plot p= CanvasUtil.getMostBottomPlot(dom.getController().getCanvas());
            dom.getController().setPlot(p);
            dom.getController().addPlotElement( null, null  );
        }
        DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
        List<PlotElement> elements= dom.getController().getPlotElementsFor( dsf );

        Plot plot= dom.getController().getPlotFor(elements.get(0));

        for ( int i=nparm; i<args.length; i++ ) {
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
                Color c= Color.decode( sval );
                if ( sval!=null ) elements.get(0).getStyle().setColor( c );
            } else if ( kw.equals("title") ) {
                plot.setTitle(sval);
            } else if ( kw.equals("renderType") ) {
                RenderType rt= RenderType.valueOf(sval);
                elements.get(0).setRenderType(rt);
            }
        }

//        dom.getController().changePerformed(plot, plot);

        return Py.None;
    }

}
