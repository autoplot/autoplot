

package org.autoplot.jythonsupport;

import java.text.ParseException;
import java.util.List;
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
import org.python.core.PyList;

/**
 * new implementation of the dataset command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * dataset( [1,2,3,4,3], title='My Data' )
 *}</small></pre></blockquote>
 * @see http://autoplot.org/help.datasetCommand
 * @author jbf
 */
public class GetDataSetsCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("jython.commands.getdataset");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>getDataSets(list,timerange,monitor,[named parameters])</H2>"
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

    private List<String> toList( Object arg ) {
        return (List<String>)arg;
    }
    
    /**
     * implement the python call.
     * @param args the "rightmost" elements are the keyword values.
     * @param keywords the names for the keywords.
     * @return Py.None
     */
    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {

        FunctionSupport fs= new FunctionSupport( "getDataSets", 
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

        List<String> uris=null;
        Units units= null;
        DatumRange trimRange= null;
        ProgressMonitor monitor= null;
        List<QDataSet> result=null;
        
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
                uris= toList( args[0] );
                break;
            case 2: {
                uris= toList( args[0] );
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
                uris= toList( args[0] );
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
            result= Util.getDataSets( uris, monitor );
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
                    for ( int ids=0; ids<result.size(); ids++ ) {
                        result.set( ids, Ops.trim( result.get(ids), trimRange) );
                    }
                    break;
                case "units":
                    if ( val==Py.None ) {
                        for ( int ids=0; ids<result.size(); ids++ ) {
                            result.set( ids, Ops.putProperty( result.get(ids), QDataSet.UNITS, null ) );
                        }
                    } else if ( val.__tojava__(Units.class)!= Py.NoConversion ) {
                        Units u= (Units)val.__tojava__(Units.class);
                        for ( int ids=0; ids<result.size(); ids++ ) {
                            result.set( ids, Ops.putProperty( result.get(ids), QDataSet.UNITS, u ) );
                        }
                    } else {
                        for ( int ids=0; ids<result.size(); ids++ ) {
                            result.set( ids, Ops.putProperty( result.get(ids), QDataSet.UNITS, sval ) );
                        }
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
        
        PyList result2= new PyList();
        for ( int ids= 0; ids<result.size(); ids ++ ) {
            result2.__add__( new PyQDataSet(result.get(ids)) );
        }
        
        return result2;

    }

}
