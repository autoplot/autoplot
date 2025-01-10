
package org.autoplot.inline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyTuple;
import org.python.util.PythonInterpreter;
import org.das2.qds.BundleDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.Util;
import org.das2.datum.Datum;
import org.das2.datum.DatumUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.qds.examples.Schemes;
import org.python.core.Py;
import org.python.core.PyException;

/**
 * Data source used mostly for demonstrations and quick modifications
 * of data.  It also implements the "Mash Up Tool" which is a special
 * case of vap+inline, where N datasets are loaded an a combination of these
 * datasets is returned.
 * @author jbf
 */
public class InlineDataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("jython.inline");
    
    PythonInterpreter interp;
    TimeSeriesBrowse tsb=null;
    
    public InlineDataSource(URI uri) {
        super(uri);
        List<String> script= new ArrayList<>();
        String timerange= InlineDataSourceFactory.getScript( uri.toString(), script );
        if ( timerange!=null ) {
            try {
                tsb= InlineTimeSeriesBrowse.create( uri.toString(),timerange );
                addCapability( TimeSeriesBrowse.class, tsb );
            } catch (ParseException ex) {
                logger.warning(ex.toString());
            }
        }
    }
    
    /**
     * execute the expression.  This can be a command, presumably, or an variable
     * name or an expression.
     * @param c expression or variable name.
     * @return the dataset resolved.
     * @throws Exception 
     */
    private MutablePropertyDataSet handleJythonExpression( String c ) throws Exception {
        logger.finest(c);
        
        PyObject result= evalCommand( interp,c );

        QDataSet res;

        if (result instanceof PyList) {
            res = JythonOps.dataset((PyList) result);
        } else if ( result instanceof PyTuple && ((PyTuple)result).size()<3 ) {
            //JythonOps.coerce(result);  too bad coerce doesn't do this already.
            PyTuple tres= (PyTuple)result;
            switch (tres.size()) {
                case 2:
                    res= Ops.link( (QDataSet)tres.get(0), (QDataSet)tres.get(1) );
                    break;
                case 3:
                    res= Ops.link( (QDataSet)tres.get(0), (QDataSet)tres.get(1), (QDataSet)tres.get(2) );
                    break;
                case 1:
                    res= (QDataSet)tres.get(0); // how did this happen?  Might as well support it
                    break;
                default:
                    throw new ParseException( "unable to parse command: "+c, 0 );
            }
        } else {
            res = (QDataSet) result.__tojava__(QDataSet.class);
            if ( res==null ) {
                throw new IllegalArgumentException("expression is not a QDataSet: "+c);
            }
        }
        return DataSetOps.makePropertiesMutable(res);
    }

    /**
     * parse the string which contains a comma-delineated sequence of 
     * data.  If all elements are parseable as a
     * double, the result is a dimensionless array.  If parseable as
     * times, the result is a time array.  Otherwise the result is a
     * result has enumeration units for the ordinal values.
     * @param s formatted ds with only commas delineating datums
     * @return rank 1 dataset, or rank 0 if there are no commas.
     */
    private MutablePropertyDataSet parseInlineDsSimple( String s ) {
        logger.log(Level.FINEST, "parseInlineDsSimple {0}", s);
        
        Units u= Units.dimensionless;
        Units tu= Units.us2000;
        EnumerationUnits eu= EnumerationUnits.create("default");
        String[] ss2= s.split(",");
        DDataSet result= DDataSet.createRank1(ss2.length);

        boolean isTime= false;
        boolean isEnum= false;
        for (String ss21 : ss2) {
            try {
                if (!isTime && !isEnum) {
                    try {
                        u.parse(ss21);
                    } catch ( InconvertibleUnitsException ex3 ) {
                        Datum d= DatumUtil.lookupDatum(ss21);
                        u= d.getUnits();
                    }
                }
            } catch (ParseException e) {
                isTime= true;
                if (!isEnum) {
                    try {
                        tu.parse(ss21);
                    }catch (ParseException ex) {
                        try {
                            Datum d= DatumUtil.lookupDatum(ss21);
                            u= d.getUnits();
                            isTime= false;
                        } catch ( ParseException ex2 ) {
                            isEnum= true;
                        }
                    }
                }
            }
        }

        try {
            for ( int j=0; j<ss2.length; j++ ) {
                String ss= ss2[j];
                if ( isEnum ) {
                    if ( ss.startsWith("'") && ss.endsWith("'") ) ss= ss.substring(1,ss.length()-1);
                    result.putValue( j, eu.createDatum(ss).doubleValue(eu) );
                    if ( j==0 ) result.putProperty( QDataSet.UNITS, eu );
                } else if ( isTime ) {
                    result.putValue( j, tu.parse(ss).doubleValue(tu) );
                    if ( j==0 ) result.putProperty( QDataSet.UNITS, tu );
                } else {
                    result.putValue(j, u.parse(ss).value() );
                    if ( j==0 && u!=Units.dimensionless ) result.putProperty( QDataSet.UNITS, u );
                }
            }
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex);
        }

        if ( ss2.length==1 ) {
            return Ops.copy(result.slice(0));
        } else {
            return result;
        }
    }

    /**
     * handle line of inline ds which can take one of three forms:<ul>
     * <li>a list of variables like "aa,bb,cc" which means link the three together to make a dataset.
     * <li>an expression like "aa+bb" or "pow(aa,10)"
     * <li>a literal like "1,2,3,2,3,2,1" or "2000-02-02T02:02,2000-02-02T02:03,2000-02-02T02:04"
     * </ul>
     * @param s the line of code
     * @return the QDataSet
     * @throws Exception when the line cannot be interpreted.
     */
    private MutablePropertyDataSet parseInlineDs( String s ) throws Exception {
        logger.log(Level.FINEST, "parseInlineDs {0}", s);

        if ( s.equals("None") || s.equals("null") || s.equals("") ) return null;
            
        String linkCommand=null;
        
        try {    
            PyObject result;
            if ( Ops.isSafeName(s) ) {
                result= evalCommand( interp, s ); 
            } else {
                linkCommand= "link( "+s + ")";        
                result= evalCommand( interp, linkCommand ); //wha?
            }
            
            QDataSet res = (QDataSet) result.__tojava__(QDataSet.class);
            return DataSetOps.makePropertiesMutable(res);
        } catch ( RuntimeException ex ) {
            // since we couldn't run the command, we know that wasn't it.
            // TODO: it would be nicer to try to parse the string a little instead.
            logger.log(Level.FINE, "failed to execute: {0}", (linkCommand!=null ? linkCommand : s ) );
            logger.log( Level.FINE, ex.getMessage(), ex );
        }

        try {
            return handleJythonExpression(s);
        } catch ( Exception ex ) {
            boolean isNotList= s.length()>0 && ( ( s.charAt(0)>='a' && s.charAt(0)<='z' ) || s.charAt(0)=='(' );
            if ( isNotList ) {
                throw new IllegalArgumentException( "inline jython code raises exception: "+ex, ex );
            }
        }

        String[] ss= s.split(";",-2);
        if ( ss.length>1 ) { // rank 2
            BundleDataSet bds= BundleDataSet.createRank1Bundle();
            String[] ss2= ss[0].split(",");
            int nds= ss2.length; // number per record

            // do each column of the rank two table.
            int nrec=ss.length;
            for ( int j=0; j<nds; j++ ) {
                DataSetBuilder b= new DataSetBuilder(1,ss.length);
                for ( int i=0; i<nrec; i++ ) {
                    ss2= ss[i].split(",");
                    if ( j==0 && ss2[j].trim().length()==0 && i<nrec ) {
                        nrec=i;
                        continue;
                    } // check for empty record after semi: "1.23,134;"
                    QDataSet result= parseInlineDsSimple(ss2[j]);
                    b.putValue(-1,result.value());
                    b.putProperty( QDataSet.UNITS, result.property(QDataSet.UNITS) );
                    b.nextRecord();
                }
                bds.bundle(b.getDataSet());
            }

            //bds.putProperty( QDataSet.BUNDLE_1, null );
            return bds;
        } else {
            MutablePropertyDataSet result= parseInlineDsSimple(ss[0]);
            return result;
        }
    }

    /**
     * only split on the delimiter when we are not within the exclude delimiters.  For example,
     * <code>
     * x=getDataSet("http://autoplot.org/data/autoplot.cdf?Magnitude&noDep=T")&y=getDataSet('http://autoplot.org/data/autoplot.cdf?BGSEc&slice1=2')&sqrt(x)
     * </code>
     * @param s the string to split.
     * @param delim the delimiter to split on, for example the ampersand (&).
     * @param exclude1 for example the single quote (')
     * @param exclude2 for example the double quote (")  Note URIs don't support these anyway.
     * @return the split.
     */
    protected static String[] guardedSplit( String s, char delim, char exclude1, char exclude2 ) {    
        return Util.guardedSplit(s, delim, exclude1, exclude2);
    }
    
    /**
     * return true if the name is a property name like DEPEND_0 or DELTA_PLUS.
     * @param n the name
     * @return true if the name is a property name
     */
    private boolean isPropName( String n ) {
        return DataSetUtil.getPropertyType(n)!=null;
    }
    
    // http://autoplot.org/developer.inlineData
    //vap+inline:3,4;3,6;5,6
    //vap+inline:2000-001T00:00,23.5;2000-002T00:00,23.5;2000-003T00:00,23.5
    //vap+inline:1,2,3&DEPEND_0=1,2,3&DEPEND_0.UNITS=hours since 2000-001T00:00
    //vap+inline:exp(findgen(20))&UNITS=eV&SCALE_TYPE=log&LABEL=Energy
    //vap+inline:ripples(1440)&DEPEND_0=timegen('2003-05-01','1 min',1440) 
    //vap+inline:t=linspace(0,2*PI,200)&cos(2*t),sin(3*t),t
    @Override
    public QDataSet getDataSet( ProgressMonitor mon ) throws Exception {

        String s= tsb==null ? getURI() : tsb.getURI();

        logger.log(Level.FINE, "getDataSet {0}", s );

        logger.log( Level.FINER, "create interpreter");
        interp= JythonUtil.createInterpreter(true);
        if ( ! org.autoplot.jythonsupport.Util.isLegacyImports() ) { // we need to always bring this in to support legacy URIs.
            logger.log( Level.FINER, "import the stuff we don't import automatically anymore");
            try (InputStream in = org.autoplot.jythonsupport.Util.class.getResource("imports2023.py").openStream()) {
                interp.execfile( in, "imports2023.py");
            }
        }
                        
        List<String> script= new ArrayList<>();
        String timerange= InlineDataSourceFactory.getScript( s, script );
        
        String[] ss= script.toArray(new String[script.size()]);
        
        // make sure timerange is set before any other calls.
        if ( timerange!=null ) {
            interp.set("timerange",timerange);
        }
        
        MutablePropertyDataSet ds= null;
        MutablePropertyDataSet bundle1= null;
        MutablePropertyDataSet[] depn= new MutablePropertyDataSet[4];
        Map<String,String>[] deppropn= new Map[4];

        Map<String,String> p= new LinkedHashMap<>();

        if ( ss.length==1 && ss[0].equals("None") ) {
            logger.info("vap+inline:None is useful for testing");
            return null;
        }
        
        mon.setTaskSize(ss.length);
        mon.started();
        
        try {
            Pattern depPat= Pattern.compile("DEPEND_(\\d+)(\\.([A-Z]+))?");
            Matcher m;
            for ( int i=0; i<ss.length; i++ ) {
                mon.setTaskProgress(i);
                mon.setProgressMessage(ss[i]);
                String arg= ss[i];
                if ( arg.length()==0 ) continue;
                String propName= null;
                int ieq= arg.indexOf('=');
                if ( ieq>-1 && isPropName(arg.substring(0,ieq).trim()) ) {
                    propName= arg.substring(0,ieq).trim();
                }
                if ( propName!=null ) { // it's a directive
                    String propValue= arg.substring(ieq+1).trim();
                    if ( (m=depPat.matcher(propName)).matches() ) {
                        int idep= Integer.parseInt( m.group(1) );
                        if ( m.group(3)!=null ) {
                            Map map= deppropn[idep];
                            if ( map==null ) {
                                map= new HashMap();
                                deppropn[idep]= map;
                            }
                            map.put( m.group(3), propValue );
                        } else {
                            depn[idep]= parseInlineDs(propValue);
                        }
                    } else if ( propName.startsWith("BUNDLE_1") ) {
                        if ( propValue.equals("") || propValue.equals("None") || propValue.equals("null") ) {
                            p.put(propName,propValue);
                        } else {
                            bundle1= parseInlineDs(propValue);
                        }
                    } else {
                        if ( DataSetUtil.isDimensionProperty(propName) || propName.equals(QDataSet.RENDER_TYPE) || propName.equals(QDataSet.DELTA_PLUS) || propName.equals(QDataSet.DELTA_MINUS) ) {
                            p.put(propName,propValue);
                        } else {
                            try {
                                interp.set( "monitor", mon.getSubtaskMonitor(arg));
                                execCommand( interp,arg );
                            } catch ( Exception ex ) {
                                throw ex; // https://sourceforge.net/p/autoplot/bugs/1376/
                            }
                        }
                    }
                } else if ( isAssignment(arg) ) {
                    logger.log( Level.FINER, "assignment {0}", arg);
                    interp.set( "monitor", mon.getSubtaskMonitor(arg));
                    arg= URISplit.uriDecode(arg);
                    if ( arg.startsWith("timerange=") ) continue;
                    try {
                        execCommand( interp, arg );
                    } catch ( Exception ex ) {
                        if ( ex instanceof PyException ) {
                            Object o= ((PyException)ex).value.__tojava__(Exception.class);
                            if ( o==Py.NoConversion ) {
                                throw ex;
                            } else {
                                Exception ex2= (Exception)o;
                                throw ex2; 
                            }
                        } else {
                            throw ex;
                        }
                    }
                } else { 
                    ds= parseInlineDs(arg);

                }
            }

        } finally {
            mon.finished();
        }
    
        if ( ds==null ) {
            throw new IllegalArgumentException("URI don't contain anything to plot");
        }
        
        for ( int idep=0; idep<QDataSet.MAX_RANK; idep++ ) {
            Map<String,String> depp= deppropn[idep];
            if ( depp==null ) continue;
            for ( Entry<String,String> ent: depp.entrySet() ) {
                String prop= ent.getKey();
                MutablePropertyDataSet dep0= depn[idep];
                if ( dep0==null ) {
                    throw new IllegalArgumentException( "DEPEND_"+idep+"."+prop+" specified, but no DEPEND_"+idep+" ds");
                }
                String propValue= ent.getValue();
                switch (prop) {
                    case "UNITS":
                        dep0.putProperty( prop,Units.lookupUnits(propValue));
                        break;
                    case "FILL_VALUE":
                    case "VALID_MIN":
                    case "VALID_MAX":
                    case "TYPICAL_MIN":
                    case "TYPICAL_MAX":
                        dep0.putProperty( prop,Double.parseDouble(propValue));
                        break;
                    case "MONOTONIC":
                        dep0.putProperty( prop, Boolean.parseBoolean(propValue) ); // True or TRUE
                        break;
                    default:
                        // it would be nice if the same code handled DEPEND_0 and the dataset.
                        dep0.putProperty(prop,propValue);
                        break;
                }
            }
        }

        for ( Entry<String,String> e: p.entrySet() ) {
            String prop= e.getKey();
            String propValue= e.getValue();
            ds= Ops.putProperty( ds, prop, propValue );
        }

        if ( depn[0]==null ) {
            if ( bundle1==null && ds.rank()==2 && ds.length(1)==2 ) { //TODO: we should be able to use bundle dataset... kludge
                if ( Ops.isBundle(ds) ) {
                    MutablePropertyDataSet xx= (MutablePropertyDataSet)DataSetOps.unbundle(ds,0) ;
                    MutablePropertyDataSet zz= (MutablePropertyDataSet)DataSetOps.unbundle(ds,1);
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;
                } else if ( ds instanceof BundleDataSet ) { // use unbundle to support TimeLocation and EnumerationUnits types
                    BundleDataSet bds= (BundleDataSet)ds;
                    MutablePropertyDataSet xx= (MutablePropertyDataSet) bds.unbundle(0);
                    MutablePropertyDataSet zz= (MutablePropertyDataSet) bds.unbundle(ds.length(0)-1);
                    if ( ds.property(QDataSet.RENDER_TYPE)!=null ) zz.putProperty(QDataSet.RENDER_TYPE,ds.property(QDataSet.RENDER_TYPE)); // vap+inline:0,0,100,100,0,0; 0,0,0,100,100,0&RENDER_TYPE=scatter
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;
                } else if ( Schemes.isBoundingBox(ds) ) {
                    // do nothing
                } else {
                    MutablePropertyDataSet xx= DDataSet.copy(DataSetOps.slice1(ds,0));
                    MutablePropertyDataSet zz= DDataSet.copy(DataSetOps.slice1(ds,ds.length(0)-1));
                    DataSetUtil.copyDimensionProperties( ds, zz ); // we put these in the wrong place, fix this...
                    if ( ds.property(QDataSet.RENDER_TYPE)!=null ) zz.putProperty(QDataSet.RENDER_TYPE,ds.property(QDataSet.RENDER_TYPE)); // vap+inline:0,0,100,100,0,0; 0,0,0,100,100,0&RENDER_TYPE=scatter
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;
                }
            }
        } else {
            for ( int idep=0; idep<4; idep++ ) {
                ds.putProperty( "DEPEND_"+idep, depn[idep] );
            }
        }

        if ( bundle1!=null ) {
            ds.putProperty( QDataSet.BUNDLE_1, bundle1 );
        }
        return ds;
    }
    
    /**
     * execute the command line the interpreter, with a hook for security concerns.
     * @param interp the interpreter
     * @param arg the command to execute.
     */
    private static void execCommand( PythonInterpreter interp, String arg ) {
        if ( arg.contains("execfile") ) {
            throw new IllegalArgumentException("inline commands cannot contain execfile");
        } else if ( arg.contains("__import__") ) {
            throw new IllegalArgumentException("inline commands cannot contain __import__");
        }
        interp.exec(arg);
    }

    private static PyObject evalCommand( PythonInterpreter interp, String arg ) {
        if ( arg.contains("execfile") ) {
            throw new IllegalArgumentException("inline commands cannot contain execfile");
        } else if ( arg.contains("__import__") ) {
            throw new IllegalArgumentException("inline commands cannot contain __import__");
        }
        return interp.eval(arg);
    }
    
    private boolean isAssignment(String arg) {
        int i= arg.indexOf("=");
        if ( i==-1 ) return false;
        String varNames= arg.substring(0,i);
        Pattern p= Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*\\s*");
        if ( p.matcher(varNames).matches() ) {
            return true;
        } 
        Pattern p2= Pattern.compile("\\(([a-zA-Z_][a-zA-Z_0-9]*)(\\,[a-zA-Z_][a-zA-Z_0-9]*)*\\)");
        return p2.matcher(varNames).matches();
    }

}
