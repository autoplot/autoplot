/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.jythonsupport.commands;

import java.util.logging.Logger;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.das2.qds.QDataSet;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.PyQDataSet;
import org.das2.qds.ops.Ops;

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
            + "<tr><td>title </td><td>title for the dataset, which could be used above a plot.</td></tr>\n"
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

        FunctionSupport fs= new FunctionSupport( "dataset", 
            new String[] { "ds", 
            "title", "label", 
            "units", 
            "fillValue", "validMin", "validMax", "typicalMin", "typicalMax",
        },
        new PyObject[] { Py.None,
            Py.None, Py.None,
            Py.None, 
            Py.None, Py.None, Py.None, Py.None, Py.None,
        } );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        if ( nparm==0 ) {
            throw new IllegalArgumentException("dataset needs at least one argument");
        }

        QDataSet result= JythonOps.dataset( args[0] );
            
        for ( int i=nparm; i<args.length; i++ ) { //HERE nargs
            String kw= keywords[i-nparm];
            PyObject val= args[i];

            String sval= (String) val.__str__().__tojava__(String.class);
            switch ( kw ) {
                case "title":
                case "label":
                    result= Ops.putProperty( result, kw.toUpperCase(), sval );
                    break;
                case "units":
                    result= Ops.putProperty( result, kw.toUpperCase(), sval );
                    break;
                case "validMin":
                    result= Ops.putProperty( result, QDataSet.VALID_MIN, val );
                    break;
                case "validMax":
                    result= Ops.putProperty( result, QDataSet.VALID_MAX, val );
                    break;
                case "typicalMin":
                    result= Ops.putProperty( result, QDataSet.TYPICAL_MIN, val );
                    break;
                case "typicalMax":
                    result= Ops.putProperty( result, QDataSet.TYPICAL_MAX, val );
                    break;
                case "fillValue":
                    result= Ops.putProperty( result, QDataSet.FILL_VALUE, val );
                    break;
                default:
                    throw new IllegalArgumentException("bad keyword");
            }
        }
            
        return new PyQDataSet(result);

    }

}
