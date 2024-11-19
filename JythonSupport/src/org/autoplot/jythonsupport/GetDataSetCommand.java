
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.das2.qds.QDataSet;
import org.das2.datum.Units;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.ProgressMonitor;

/**
 * new implementation of the dataset command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * Tp=getDataSet( 'vap+cdaweb:ds=STA_L2_MAGPLASMA_1M&id=Tp',trim='2022-12-24' )
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
            + "<tr><td>sortTime=True</td><td>sort the data by its timetags</td></tr>\n"
            + "</table></html>");

    public static final PyString __completions__;
    
    static {
        String text = new BufferedReader(
            new InputStreamReader( GetDataSetCommand.class.getResourceAsStream("GetDataSetCommand.json"), StandardCharsets.UTF_8) )
            .lines().collect(Collectors.joining("\n"));
        __completions__= new PyString( text );
    }
    
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
                "trim", "units",
                "sortTime"
            },
            new PyObject[] { 
                Py.None, Py.None,
                Py.None, Py.None,
                Py.None
            }
        );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        String uri=null;
        Units units= null;
        DatumRange trimRange= null;
        ProgressMonitor monitor= null;
        QDataSet result=null;
        boolean doTrim= false;
        
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
            } else if ( kw.equals("timerange") || kw.equals("trim") ) {
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
                    } else {
                        v= val.__tojava__(int.class);
                        if ( v!=Py.NoConversion ) {
                            if ( v.equals(1) ) {
                                doTrim= true;
                            }
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
                    break;
                }
                arg1= args[1].__tojava__(DatumRange.class);
                if ( arg1!=Py.NoConversion ) {
                    trimRange= (DatumRange)arg1;
                    break;
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
                arg1= args[1].__tojava__(QDataSet.class);
                if ( arg1!=Py.NoConversion ) {
                    trimRange= Ops.datumRange(arg1);
                    break;
                }
                throw new IllegalArgumentException("unable to use second argument: "+args[1].__str__() );
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
                } else {
                    throw new IllegalArgumentException("unable to use third argument: "+args[2].__str__() );
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
        
        if ( doTrim ) {
            try {
                if ( trimRange==null ) {
                    DataSource dss= org.autoplot.datasource.DataSetURI.getDataSource(uri);
                    TimeSeriesBrowse tsb= DataSourceUtil.getTimeSeriesBrowse(dss);
                    tsb.setURI(uri);
                    trimRange= tsb.getTimeRange();
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            
        }
        
        for ( int i=nparm; i<args.length; i++ ) { //HERE nargs
            String kw= keywords[i-nparm];
            PyObject val= args[i];

            String sval= (String) val.__str__().__tojava__(String.class);
            switch ( kw ) {
                case "trim":
                    if ( trimRange==null ) {
                        if ( val.equals(Py.None) ) {
                            continue;
                        } else {
                            try {
                                trimRange= DatumRangeUtil.parseTimeRange(sval);
                            } catch (ParseException ex) {
                                Logger.getLogger(GetDataSetsCommand.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
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
                case "sortTime":
                    if ( val.__nonzero__() ) {
                        long t0= System.currentTimeMillis();
                        QDataSet tt= Ops.xtags( result );
                        QDataSet s= Ops.sort(tt);
                        if ( Boolean.TRUE.equals(s.property(QDataSet.MONOTONIC)) && s.length()==tt.length() ) {
                            continue;
                        }
                        long t1=  System.currentTimeMillis();
                        result= Ops.applyIndex( result, s);
                        long t2=  System.currentTimeMillis();
                        logger.log(Level.INFO, "sort in millis: {0}", t1-t0 );
                        logger.log(Level.INFO, "applyIndex in millis: {0}", t2-t1 );
                    }
                default:
                    throw new IllegalArgumentException("bad keyword: "+kw);
            }
        }
            
        return new PyQDataSet(result);

    }

}
