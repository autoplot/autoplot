/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.jythonsupport;

import java.util.Map;
import java.util.logging.Logger;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.das2.qds.QDataSet;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.PyQDataSet;
import org.das2.datum.Units;
import org.das2.qds.ops.Ops;
import org.python.core.PyDictionary;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyJavaInstance;
import org.python.core.PySingleton;

/**
 * new implementation of the dataset command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * dataset( [1,2,3,4,3], title='My Data' )
 *}</small></pre></blockquote>
 * @see http://autoplot.org/help.datasetCommand
 * @author jbf
 */
public class DatasetCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("jython.commands.datset");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>dataset(ds,[named parameters])</H2>"
            + "dataset creates datasets from arrays and adds metadata.\n"
            + "See http://autoplot.org/help.datasetCommand<br>\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>"
            + "<tr><td>title </td><td>title for the data, which could be used above a plot.</td></tr>\n"
            + "<tr><td>label </td><td>label for the data, which could be used as an axis label.</td></tr>\n"
            + "<tr><td>name </td><td>name for the data, which should be a legal Jython variable name.</td></tr>\n"
            + "<tr><td>units </td><td>units for the data, which string representing the units of the data.</td></tr>\n"
            + "<tr><td>validMin validMax</td><td>range of valid values for the data.</td></tr>\n"
            + "<tr><td>typicalMin typicalMax</td><td>typical range dataset, used for suggesting axis ranges.</td></tr>\n"
            + "<tr><td>scaleType</td><td>'log' or 'linear'</td></tr>\n"
            + "<tr><td>format</td><td>format specifier, like %d or %.2f</td></tr>\n"
            + "<tr><td>cadence</td><td>nominal cadence, like 60s or 100Hz.  Note this goes with the independent parameter (timetags).</td></tr>\n"
            + "</table></html>");

    private static QDataSet datasetValue( PyObject arg0 ) {
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
    
    private static Number numberValue( PyObject arg0 ) {
        if ( arg0 instanceof PyInteger ) {
            return ((PyInteger)arg0).getValue();
        } else if ( arg0 instanceof PyFloat ) {
            return ((PyFloat)arg0).getValue();
        } else if ( arg0 instanceof PyString ) {
            return Double.parseDouble( String.valueOf(arg0) );
        } else {
            return arg0.__float__().getValue();
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

        FunctionSupport fs= new FunctionSupport( "dataset", 
            new String[] { "ds", "ds1", "ds2", "ds3", 
            "title", "label", "name",
            "units", "format", "cadence", 
            "fillValue", "validMin", "validMax", "typicalMin", "typicalMax",
            "scaleType",
            "renderType", "bins1", "bins0", "cacheTag", "userProperties",
            "deltaPlus", "deltaMinus", "binPlus", "binMinus", "binMin", "binMax",
        },
        new PyObject[] { Py.None, Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None, Py.None, Py.None,
            Py.None,
            Py.None, Py.None, Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None, Py.None, Py.None, Py.None,
        } );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        QDataSet result;
        
        switch (nparm) {
            case 0:
                throw new IllegalArgumentException("dataset needs at least one argument");
            case 1:
                result= JythonOps.dataset( args[0] );
                break;
            case 2: {
                if ( args[1] instanceof PyJavaInstance ) { // legacy use allowed the second argument to be a units object.
                    PyJavaInstance pji= (PyJavaInstance)args[1];
                    Object o= pji.__tojava__( Units.class );
                    if ( o!=Py.NoConversion ) {
                        logger.info("legacy script uses second argument for units, use units=... instead");
                        result= JythonOps.dataset( args[0] );
                        result= Ops.putProperty( result, QDataSet.UNITS, o );
                        break;
                    }
                }
                result= JythonOps.dataset( args[1] );
                QDataSet xds= JythonOps.dataset( args[0] );
                result= Ops.link( xds, result );
                break;
            }
            case 3: {
                result= JythonOps.dataset( args[2] );
                QDataSet xds= JythonOps.dataset( args[0] );
                QDataSet yds= JythonOps.dataset( args[1] );
                result= Ops.link( xds, yds, result );
                break;
            }
            case 4:        
                result= JythonOps.dataset( args[3] );
                QDataSet ds0= JythonOps.dataset( args[0] );
                QDataSet ds1= JythonOps.dataset( args[1] );
                QDataSet ds2= JythonOps.dataset( args[2] );
                result= Ops.link( ds0, ds1, ds2, result );
                break;
            default:
                throw new IllegalArgumentException("dataset needs between one and four parameters.");
        }
        
        for ( int i=nparm; i<args.length; i++ ) { //HERE nargs
            String kw= keywords[i-nparm];
            PyObject val= args[i];

            String sval= (String) val.__str__().__tojava__(String.class);
            switch ( kw ) {
                case "description":
                case "title":
                case "label":
                case "name":
                case "format":
                    result= Ops.putProperty( result, kw.toUpperCase(), sval );
                    break;
                case "units":
                    if ( val.__tojava__(Units.class)!= Py.NoConversion ) {
                        result= Ops.putProperty( result, QDataSet.UNITS, val.__tojava__(Units.class)  );
                    } else {
                        result= Ops.putProperty( result, QDataSet.UNITS, sval );
                    }
                    break;
                case "validMin":
                    result= Ops.putProperty( result, QDataSet.VALID_MIN, numberValue(val) );
                    break;
                case "validMax":
                    result= Ops.putProperty( result, QDataSet.VALID_MAX, numberValue(val) );
                    break;
                case "typicalMin":
                    result= Ops.putProperty( result, QDataSet.TYPICAL_MIN, numberValue(val) );
                    break;
                case "typicalMax":
                    result= Ops.putProperty( result, QDataSet.TYPICAL_MAX, numberValue(val) );
                    break;
                case "fillValue":
                    result= Ops.putProperty( result, QDataSet.FILL_VALUE, numberValue(val) );
                    break;
                case "scaleType":
                    result= Ops.putProperty( result, QDataSet.SCALE_TYPE, sval );
                    break;
                case "cadence":
                    result= Ops.putProperty( result, QDataSet.CADENCE, sval );
                    break;
                case "renderType":
                    result= Ops.putProperty( result, QDataSet.RENDER_TYPE, sval );
                    break;
                case "bins1":
                    result= Ops.putProperty( result, QDataSet.BINS_1, sval );
                    break;
                case "bins0":
                    result= Ops.putProperty( result, QDataSet.BINS_0, sval );
                    break;
                case "cacheTag": // 2019-02-03 @ 1s
                    result= Ops.putProperty( result, QDataSet.CACHE_TAG, sval );
                    break;
                case "userProperties": 
                    if ( val instanceof PyDictionary ) {
                        Map m= JythonUtil.pyDictionaryToMap((PyDictionary)val);
                        result= Ops.putProperty( result, QDataSet.USER_PROPERTIES, m );
                    } else {
                        result= Ops.putProperty( result, QDataSet.USER_PROPERTIES, val );
                    }
                    break;
                case "deltaPlus":
                    result= Ops.putProperty( result, QDataSet.DELTA_PLUS, JythonOps.dataset( val ) );
                    break;
                case "deltaMinus":
                    result= Ops.putProperty( result, QDataSet.DELTA_MINUS, JythonOps.dataset( val ) );
                    break;  
                case "binPlus":
                    result= Ops.putProperty( result, QDataSet.BIN_PLUS, JythonOps.dataset( val ) );
                    break;
                case "binMinus":
                    result= Ops.putProperty( result, QDataSet.BIN_MINUS, JythonOps.dataset( val ) );
                    break;  
                case "binMin":
                    result= Ops.putProperty( result, QDataSet.BIN_MIN, JythonOps.dataset( val ) );
                    break;
                case "binMax":
                    result= Ops.putProperty( result, QDataSet.BIN_MAX, JythonOps.dataset( val ) );
                    break;  
                default:
                    throw new IllegalArgumentException("bad keyword");
            }
        }
            
        return new PyQDataSet(result);

    }

}
