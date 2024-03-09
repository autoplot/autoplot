/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.jythonsupport;

import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.das2.qds.QDataSet;
import org.das2.datum.Units;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyDictionary;
import org.python.core.PyJavaInstance;

/**
 * new implementation of the dataset command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * dataset( [1,2,3,4,3], title='My Data' )
 *}</small></pre></blockquote>
 * @see http://autoplot.org/help.datasetCommand
 * @author jbf
 */
public class GetDataSetCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("jython.commands.getdataset");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>getDataSet(ds,timerange,monitor,[named parameters])</H2>"
            + " load the data specified by URI into Autoplot's internal data model.  This will\n" 
            + " block until the load is complete, and a ProgressMonitor object can be used to\n" 
            + " monitor the load..\n"
            + "<br><b>optional parameters:</b>\n"
            + "<table>\n"
            + "<tr><td>timerange</td><td>String or DatumRange</td></tr>\n"
            + "<tr><td>monitor</td><td>Progress Monitor</td></tr>\n"
            + "</table>\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>\n"
            + "<tr><td>trim=True </td><td>trim the data to the requested time range.</td></tr>\n"
            + "<tr><td>units=None </td><td>convert the data to the given units, or remove the unit if empty string or None\n"
            + "</table></html>");

    /**
     * implement the python call.
     * @param args the "rightmost" elements are the keyword values.
     * @param keywords the names for the keywords.
     * @return Py.None
     */
    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {

        FunctionSupport fs= new FunctionSupport( "getDataSet", 
            new String[] { "uri", 
                "timerange", "monitor",
                "trim", "units"
            },
            new PyObject[] { 
                Py.None, Py.None,
                Py.None, Py.None
            }
        );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        String uri=null;
        Units units= null;
        DatumRange trimRange= null;
        ProgressMonitor monitor= null;
        QDataSet result=null;
        
        for ( int i=nparm; i<args.length; i++ ) { 
            String kw= keywords[i-nparm];
            PyObject val= args[i];

            val.__str__().__tojava__(String.class);
            if ( kw.equals("units") ) {
                if ( val.__tojava__(Units.class)!= Py.NoConversion ) {
                    units= (Units)val.__tojava__(Units.class);
                } else {
                    String svalue= val.toString();
                    units= Units.lookupUnits(svalue);
                }
            } else if ( kw.equals("monitor") ) {
                Object v= val.__tojava__(ProgressMonitor.class);
                if ( v!=Py.NoConversion ) {
                    monitor= (ProgressMonitor)v;
                }
            } else if ( kw.equals("timerange") ) {
                Object v= val.__tojava__(DatumRange.class);
                if ( v!=Py.NoConversion ) {
                    trimRange= (DatumRange)v;
                } else {
                    v= val.__tojava__(String.class);
                    if ( v!=Py.NoConversion ) {
                        try {
                           trimRange= DatumRangeUtil.parseTimeRange((String)v);
                        } catch ( ParseException ex ) {
                            throw Py.JavaError(ex);
                        }
                    }
                }
            }
        }
        
        switch (nparm) {
            case 0:
                throw new IllegalArgumentException("dataset needs at least one argument");
            case 1:
                uri= args[0].__str__().toString();
                break;
            case 2: {
                PyString pyuri= args[0].__str__();
                uri= pyuri.toString();
                Object arg1;
                arg1= args[1].__tojava__(ProgressMonitor.class);
                if ( arg1!=Py.NoConversion ) {
                    monitor= (ProgressMonitor)arg1;
                }
                arg1= args[1].__tojava__(DatumRange.class);
                if ( arg1!=Py.NoConversion ) {
                    trimRange= (DatumRange)arg1;
                }
                arg1= args[1].__tojava__(String.class);
                if ( arg1!=Py.NoConversion ) {
                    try {
                        trimRange= DatumRangeUtil.parseTimeRange((String)arg1);
                    } catch ( ParseException ex ) {
                        throw Py.JavaError(ex);
                    }
                    break;
                }
                break;
            }
            case 3: {
                PyString pyuri= args[0].__str__();
                uri= pyuri.toString();
                Object arg1;
                arg1= args[1].__tojava__(DatumRange.class);
                if ( arg1!=Py.NoConversion ) {
                    trimRange= (DatumRange)arg1;
                }
                arg1= args[1].__tojava__(String.class);
                if ( arg1!=Py.NoConversion ) {
                    try {
                        trimRange= DatumRangeUtil.parseTimeRange((String)arg1);
                    } catch ( ParseException ex ) {
                        throw Py.JavaError(ex);
                    }
                    break;
                }
                Object arg2= args[2].__tojava__(ProgressMonitor.class);
                if ( arg2!=Py.NoConversion ) {
                    monitor= (ProgressMonitor)arg2;
                }
                break;                
            }
            default:
                throw new IllegalArgumentException("dataset needs between one and two parameters.");
        }
        
        try {
            result= Util.getDataSet( uri, trimRange, monitor );
        } catch ( Exception ex ) {
            throw Py.JavaError(ex);
        }
        if ( result==null ) return Py.None;
        
        for ( int i=nparm; i<args.length; i++ ) { //HERE nargs
            String kw= keywords[i-nparm];
            PyObject val= args[i];

            String sval= (String) val.__str__().__tojava__(String.class);
            switch ( kw ) {
                case "trim":
                    result= Ops.trim( result, trimRange );
                    break;
                case "units":
                    if ( val==Py.None ) {
                        result= Ops.putProperty( result, QDataSet.UNITS, null );
                    } else if ( val.__tojava__(Units.class)!= Py.NoConversion ) {
                        result= Ops.putProperty( result, QDataSet.UNITS, val.__tojava__(Units.class)  );
                    } else {
                        result= Ops.putProperty( result, QDataSet.UNITS, sval );
                    }
                    break;
                case "timerange":
                case "monitor":
                    // these were already handled
                    break;
                default:
                    throw new IllegalArgumentException("bad keyword");
            }
        }
            
        return new PyQDataSet(result);

    }

}
