
package org.autoplot.jythonsupport;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.FileSystemUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.python.core.Py;
import org.python.core.PyArray;
import org.python.core.PyDictionary;
import org.das2.qds.QubeDataSetIterator;
import org.python.core.PyFloat;
import org.python.core.PyFunction;
import org.python.core.PyInteger;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyLong;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.URISplit;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.jythoncompletion.JavadocLookup;
import org.das2.qds.LDataSet;
import org.das2.qds.LongWriteAccess;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.JsonUtil;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Contains operations that are only available to Jython code, and is dependent
 * on the jython libraries.
 *
 * @author jbf
 */
public class JythonOps {
    
    private static final Logger logger= Logger.getLogger("jython");
    
    /**
     * Apply the Python function, typically a lambda function, to each
     * element of the dataset.  For example: 
     * <blockquote><pre><small>{@code
     * xx= dindgen( 6 ) 
     * yy= applyLambda( xx, lambda x : x**2 )
     * plot( xx, yy )
     *}</small></pre></blockquote>
     * 
     * @param ds the dataset to which the function is applied
     * @param f the function
     * @return the dataset with the function applied
     */
    public static QDataSet applyLambda(QDataSet ds, PyFunction f ) {
        QubeDataSetIterator it = new QubeDataSetIterator(ds);
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));
        while (it.hasNext()) {
            it.next();
            double d = it.getValue(ds);
            PyFloat r = (PyFloat) f.__call__(new PyFloat(d));
            it.putValue( result, r.getValue() );
        }
        return result;
    }
      
    /**
     * Apply the Python function, typically a two-argument lambda function, to each
     * element of the dataset.  For example: 
     * <blockquote><pre><small>{@code
     * xx= dindgen( 6 ) 
     * yy= ones( 6 )
     * yy= applyLambda( xx, yy, lambda x,y : x+y )
     * plot( xx, yy )
     *}</small></pre></blockquote>
     * 
     * @param ds1 the dataset to which the function is applied
     * @param ds2 the dataset to which the function is applied
     * @param f the function
     * @return the dataset with the function applied
     */    
    public static QDataSet applyLambda( QDataSet ds1, QDataSet ds2, PyFunction f ) {
        QubeDataSetIterator it = new QubeDataSetIterator(ds1);
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));
        while (it.hasNext()) {
            it.next();
            double d1 = it.getValue(ds1);
            double d2 = it.getValue(ds2);
            PyFloat r = (PyFloat) f.__call__( new PyFloat(d1), new PyFloat(d2) );
            it.putValue( result, r.getValue() );
        }
        return result;
    }
    
    /**
     * Apply the Python function, typically a three-argument lambda function, to each
     * element of the dataset.  For example: 
     * <blockquote><pre><small>{@code
     * xx= dindgen( 6 ) 
     * yy= ones( 6 )
     * yy= applyLambda( xx, yy, lambda x,y : x+y )
     * plot( xx, yy )
     *}</small></pre></blockquote>
     * 
     * @param ds1 the dataset to which the function is applied
     * @param ds2 the dataset to which the function is applied
     * @param ds3 the dataset to which the function is applied
     * @param f the function
     * @return the dataset with the function applied
     */        
    public static QDataSet applyLambda( QDataSet ds1, QDataSet ds2, QDataSet ds3, PyFunction f ) {
        QubeDataSetIterator it = new QubeDataSetIterator(ds1);
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));
        while (it.hasNext()) {
            it.next();
            double d1 = it.getValue(ds1);
            double d2 = it.getValue(ds2);
            double d3 = it.getValue(ds3);
            PyFloat r = (PyFloat) f.__call__( new PyFloat(d1), new PyFloat(d2), new PyFloat(d3) );
            it.putValue( result, r.getValue() );
        }
        return result;
    }

//    public static QDataSet coerce( PyObject arg0 ) {
//        System.err.println("======================================================");
//        System.err.println("coerce( PyObject ) command that makes a QDataSet is deprecated--use coerceToDs( PyObject ) instead.");
//        System.err.println("native python coerce command will be available soon.  Contact faden @ cottagesystems.com if you need assistance.");
//        System.err.println("  sleeping for 3 seconds.");
//        System.err.println("======================================================");
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return coerceToDs( arg0 );
//    }

    /**
     * @deprecated use dataset command.
     * @param arg0
     * @return 
     */
    public static QDataSet coerceToDs( PyObject arg0 ) {
        return dataset( arg0 );
    }
    
    /**
     * coerce a python array or list into a QDataSet.
     * @param arg0 Python object or Datum
     * @return QDataSet
     * @see org.das2.qds.ops.Ops#dataset(java.lang.Object) 
     */
    public static QDataSet dataset( PyObject arg0 ) {
        if ( arg0 instanceof PyQDataSet ) {
            return ((PyQDataSet)arg0).rods;
        } else if ( arg0 instanceof PyDatum ) {
            Datum d= ((PyDatum)arg0).datum;
            Units u= d.getUnits();
            if ( u==Units.cdfTT2000 ) {
                if ( d instanceof Datum.Long ) {
                    LDataSet result= LDataSet.wrap( new long[] { ((Datum.Long)d).longValue(u) }, new int[0] );
                    result.putProperty( QDataSet.UNITS,u );
                    return result;
                }
            }
            return DataSetUtil.asDataSet( ((PyDatum)arg0).datum );
        } else if ( arg0 instanceof PyList ) {
            return PyQDataSetAdapter.adaptList( (PyList)arg0 ) ;
        } else if ( arg0 instanceof PyArray ) {
            return PyQDataSetAdapter.adaptArray( (PyArray) arg0 );
        } else if ( arg0 instanceof PyTuple ) {
            return PyQDataSetAdapter.adaptTuple( (PyTuple) arg0 );            
        } else if ( arg0 instanceof PyInteger ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else if ( arg0 instanceof PyLong ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else if ( arg0 instanceof PyFloat ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(Datum.class) instanceof Datum ) ) {
            return DataSetUtil.asDataSet( (Datum)((PyJavaInstance)arg0).__tojava__(org.das2.datum.Datum.class) );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(DatumRange.class) instanceof DatumRange ) ) {
            return DataSetUtil.asDataSet( (DatumRange)((PyJavaInstance)arg0).__tojava__(org.das2.datum.DatumRange.class) );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(QDataSet.class) instanceof QDataSet ) ) {
            return DataSetUtil.asDataSet( ((PyJavaInstance)arg0).__tojava__(QDataSet.class) );
        } else if ( arg0 instanceof PyString ) {
            try {
               return Ops.dataset(arg0.toString());
            } catch (IllegalArgumentException ex) {
               throw Py.SyntaxError( "unable to parse string: "+arg0 );
            }
        } else if ( arg0 instanceof PyNone ) {
            // In python code, support test like "ds!=None"
            return null;
        } else {
            throw Py.TypeError("JythonOps is unable to coerce "+arg0+" to QDataSet");
        }
        
    }
    
    /**
     * coerce Python objects like arrays Lists and Arrays into a QDataSet.
     * @param arg0 a PyQDataSet, PyList, PyArray, PyTuple, PyInteger, PyLong, PyFloat, Datum, DatumRange, or String.
     * @param u unit context
     * @return the dataset
     * @see Ops#dataset(java.lang.Object, org.das2.datum.Units) 
     */
    public static QDataSet dataset( PyObject arg0, Units u ) {
        if ( arg0 instanceof PyQDataSet ) {
            QDataSet result= ((PyQDataSet)arg0).rods;
            return Ops.dataset( result, u );
        } else if ( arg0 instanceof PyList ) {
            PyList pl= (PyList)arg0;
            DataSetBuilder builder= new DataSetBuilder( 1, pl.__len__() );
            for ( int i=0; i<pl.__len__(); i++ ) {
                builder.nextRecord( Ops.dataset( pl.get(i), u ) );
            }
            return builder.getDataSet();
        } else if ( arg0 instanceof PyArray ) {
            return Ops.putProperty( PyQDataSetAdapter.adaptArray( (PyArray) arg0 ), QDataSet.UNITS, u );
        } else if ( arg0 instanceof PyTuple ) {
            PyTuple pl= (PyTuple)arg0;
            DataSetBuilder builder= new DataSetBuilder( 1, pl.__len__() );
            for ( int i=0; i<pl.__len__(); i++ ) {
                builder.nextRecord( Ops.dataset( pl.get(i), u ) );
            }
            return builder.getDataSet();
        } else if ( arg0 instanceof PyInteger ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue(), u );
        } else if ( arg0 instanceof PyLong ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue(), u );
        } else if ( arg0 instanceof PyFloat ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue(), u );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(Datum.class) instanceof Datum ) ) {
            Datum d= (Datum)((PyJavaInstance)arg0).__tojava__(org.das2.datum.Datum.class);
            return Ops.dataset( d, u );

        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(DatumRange.class) instanceof DatumRange ) ) {
            DatumRange dr= (DatumRange)((PyJavaInstance)arg0).__tojava__(org.das2.datum.DatumRange.class);
            return Ops.dataset( dr, u );

        } else if ( arg0 instanceof PyString ) {
            try {
                return DataSetUtil.asDataSet( u.parse(arg0.toString()) );
            } catch ( ParseException | IllegalArgumentException ex ) {
                throw Py.SyntaxError( "unable to parse string: "+arg0 );
            }
        } else if ( arg0 instanceof PyNone ) {
            // In python code, support test like "ds!=None"
            return null;
        } else {
            throw Py.TypeError("JythonOps is unable to coerce "+arg0+" to QDataSet");
        }
        
    }

    /**
     * coerce python objects to Datum
     * @param arg0 Python object, one of rank 0 dataset, int, float, or String.
     * @return Datum 
     * @see org.das2.qds.ops.Ops#datum(java.lang.Object) 
     */
    public static Datum datum( PyObject arg0 ) {
        if ( arg0 instanceof PyQDataSet ) {
            QDataSet ds= ((PyQDataSet)arg0).rods;
            if ( ds.rank()>0 ) {
                throw new IllegalArgumentException("QDataSet is not rank zero and cannot be converted to datum: "+ds);
            } else {
                return DataSetUtil.asDatum(ds);
            }
        } else if ( arg0 instanceof PyDatum ) {
            return ((PyDatum)arg0).datum;
        } else if ( arg0 instanceof PyInteger ) {
            return Units.dimensionless.createDatum(((PyInteger)arg0).getValue());
        } else if ( arg0 instanceof PyFloat ) {
            return Units.dimensionless.createDatum(((PyFloat)arg0).getValue());
        } else if ( arg0 instanceof PyJavaInstance ) {
            return Ops.datum( ((PyJavaInstance)arg0).__tojava__(java.lang.Object.class) );            
        } else if ( arg0 instanceof PyString ) {
            return Ops.datum(arg0.toString());
        } else {
            throw Py.TypeError("unable to coerce "+arg0+" to Datum");
        }
    }
    
    /**
     * coerce python objects to DatumRange
     * See http://jfaden.net:8080/hudson/job/autoplot-test029/
     * This supports:<ul>
     *   <li>2-element rank 1 QDataSet
     *   <li>Strings like ("5 to 15 s" or "2014-01-01")
     *   <li>2-element arrays and lists
     * </ul>
     * @param arg0 PyQDataSet, String, array or List.
     * @throws IllegalArgumentException if the argument cannot be parsed or converted.
     * @return DatumRange
     */    
    public static DatumRange datumRange( PyObject arg0 ) {
        if ( arg0 instanceof PyQDataSet ) {
            QDataSet ds= ((PyQDataSet)arg0).rods;
            if ( ds.rank()>1 ) {
                throw new IllegalArgumentException("QDataSet is not rank one and cannot be converted to datumRange: "+ds);
            } else {
                return DataSetUtil.asDatumRange(ds);
            }
        } else if ( arg0 instanceof PyList ) {
            PyObject p1= ((PyList)arg0).__getitem__(0);
            PyObject p2= ((PyList)arg0).__getitem__(1);
            return new DatumRange( datum( p1 ), datum( p2 ) );
        } else if ( arg0 instanceof PyArray ) {
            PyObject p1= ((PyArray)arg0).__getitem__(0);
            PyObject p2= ((PyArray)arg0).__getitem__(1);
            return new DatumRange( datum( p1 ), datum( p2 ) );
        } else if ( arg0 instanceof PyJavaInstance ) {
            return Ops.datumRange( ((PyJavaInstance)arg0).__tojava__(java.lang.Object.class) );
        } else if ( arg0 instanceof PyString ) {
            return Ops.datumRange(arg0.toString());
            
        } else {
            throw Py.TypeError("unable to coerce "+arg0+" to DatumRange");
        }
        
    }
    
    /**
     * coerce two python objects to DatumRange
     * @param arg0 Python object, one of rank 0 dataset, int, float, or String.
     * @param arg1 Python object, one of rank 0 dataset, int, float, or String.
     * @throws IllegalArgumentException if the argument cannot be parsed or converted.
     * @return DatumRange
     */    
    public static DatumRange datumRange( PyObject arg0, PyObject arg1 ) {
        if ( arg1 instanceof PyJavaInstance ) {
            Units u= (Units) ((PyJavaInstance)arg1).__tojava__(Units.class);
            if ( u!=null ) {
                return datumRange( arg0, u );
            }
        }
        Datum d1= datum( arg0 );
        Datum d2= datum( arg1 );
        
        return new DatumRange( d1, d2 );
    }
    
    /**
     * coerce python objects to DatumRange, when the units are known.
     * 
     * @param arg0 PyQDataSet, String, array or List.
     * @param context the units.
     * @return range with the same magnitude, but context units.
     */
    public static DatumRange datumRange( PyObject arg0, Units context ) {
        DatumRange newRange= JythonOps.datumRange(arg0);
        if ( ! context.isConvertibleTo(newRange.getUnits()) ) {
            if ( newRange.min().getUnits()==Units.dimensionless ) {
                newRange= DatumRange.newDatumRange( newRange.min().value(), newRange.max().value(), context );
            }
        } else if ( context!=newRange.getUnits() ) {
            newRange= new DatumRange( newRange.min().convertTo(context), newRange.max().convertTo(context) );
        }
        return newRange;
    }
    
    /**
     * get the color from the python object, for example:
     * <ul>
     * <li>Color.RED
     * <li>16711680   (int for red)
     * <li>16711680.  (float from QDataSet)
     * <li>(255,0,0)
     * <li>(1.0,0,0)
     * </ul>
     * @param val the value
     * @return java.awt.Color
     */
    public static Color color( PyObject val ) {
        Color c=null;
        if (val==Py.None) {
            c= new Color( 0, 0, 0, 0 );
        } else if (val.__tojava__(Color.class) != Py.NoConversion) {
            c = (Color) val.__tojava__(Color.class);
        } else if (val instanceof PyFloat) {
            c = new Color((int) ((PyFloat) val).getValue());
        } else if (val instanceof PyInteger) {
            c = new Color(((PyInteger) val).getValue());
        } else if (val instanceof PyQDataSet) {
            c = new Color((int) ((PyQDataSet) val).getQDataSet().value());
        } else if (val instanceof PyTuple) {
            String sval= val.toString();
            sval= sval.substring(1,sval.length()-1);
            if (sval != null) {
                c = Ops.colorFromString(sval);
            } else {
                throw new IllegalArgumentException("can't identify color");
            }
        } else {
            String sval = (String) val.__str__().__tojava__(String.class);
            if (sval != null) {
                c = Ops.colorFromString(sval);
            } else {
                throw new IllegalArgumentException("can't identify color");
            }
        }
        return c;
    }
    
    /**
     * validate the parameter value against the constraint.  This will
     * raise an exception when the constraint is not met, or returns a modified
     * value conforming (with format).  See https://github.com/autoplot/dev/blob/master/demos/2025/20250108/getParamsValidation.jy
     */
    public static Object validateParam( String name, Object v, List constraint ) {
        if ( !constraint.contains(v) ) {
            throw new IllegalArgumentException(String.format("value is not one of allowed values: %s %s",name,v));
        }
        return v;
    }
    
    /**
     * validate the parameter, possibly modifying it to match constraints.  For example,
     * a double less than the minimum would throw an IllegalArgumentException.  However a 
     * time range is reformatted to match the format, and a double can be formatted to
     * limit resolution.
     * 
     * Constraints include:
     * <ul>
     * <li>regex
     * <li>min
     * <li>max
     * <li>format
     * </ul>
     * @param name the parameter name, where "timerange" is special.
     * @param v the value
     * @param constraint the constraint map.
     * @return the parameter, possibly modified to match constraints.
     * @throws IllegalArgumentException if the constraint is not met
     * @see  https://github.com/autoplot/dev/blob/master/demos/2025/20250108/getParamsValidation.jy
     */
    public static Object validateParam( String name, Object v, Map<String,Object> constraint ) {
        
        if ( constraint.containsKey("regex") ) {
            if ( !Pattern.matches( (String)constraint.get("regex"), v.toString() ) ) {
                throw new IllegalArgumentException(String.format("value does not match regular expression: %s %s",name,v));
            }
        }
        if ( name.equals("timerange") ) {
            if ( constraint.containsKey("min") ) {
                if ( Ops.datumRange(v).min().lt( Ops.datumRange(constraint.get("min")).min() ) ) {
                    throw new IllegalArgumentException(String.format("value is less than minimum: %s %s",name,v));
                }
            }
            if ( constraint.containsKey("max") ) {
                if ( Ops.datumRange(v).max().gt( Ops.datumRange(constraint.get("max")).max() ) ) {
                    throw new IllegalArgumentException(String.format("value is greater than maximum: %s %s",name,v));
                }
            }
        } else {
            if ( constraint.containsKey("min") && Ops.datum(v).lt( Ops.datum(constraint.get("min"))) ) {
                throw new IllegalArgumentException(String.format("value is less than minimum: %s %s",name,v));
            }
            if ( constraint.containsKey("max") && Ops.datum(v).gt( Ops.datum(constraint.get("max"))) ) {
                throw new IllegalArgumentException(String.format("value is greater than maximum: %s %s",name,v));
            }
        }
        if ( constraint.containsKey("format") ) {
            String spec = (String)constraint.get("format");
            if ( spec.length()==0 ) throw new IllegalArgumentException("format cannot be empty string");
            if ( spec.charAt(0)=='$' ) {
                v = TimeParser.create(spec).format( Ops.datumRange(v).min() );
            } else if ( spec.charAt(0)=='%' ) {
                if ( v instanceof Double ) { // allow format to limit resolution
                    String s= String.format( spec, v );
                    v = Double.parseDouble( s );
                }
            }
        }
        return v;
    }
    
    /**
     * validate the parameter, possibly modifying it to match constraints.  For example,
     * a double less than the minimum would throw an IllegalArgumentException.  However a 
     * time range is reformatted to match the format, and a double can be formatted to
     * limit resolution.
     * 
     * Constraints include:
     * <ul>
     * <li>regex
     * <li>min
     * <li>max
     * <li>format
     * </ul>
     * @param name the parameter name, where "timerange" is special.
     * @param v the value
     * @param constraint the constraint map.
     * @return the parameter, possibly modified to match constraints.
     * @throws IllegalArgumentException if the constraint is not met
     * @see  https://github.com/autoplot/dev/blob/master/demos/2025/20250108/getParamsValidation.jy
     */
    public static Object validateParam( String name, Object v, PyDictionary constraint ) {
        return validateParam( name, v, JythonUtil.pyDictionaryToMap(constraint) );
    }
    
    /**
     * download the jar file resource, unpack it, and add it to the search path.  Note
     * such scripts will not work with Webstart releases!  The code is only
     * loaded once per session, so Autoplot must be restarted if the library is updated.
     *
     * Here is an example use:
     * <blockquote><pre><small>{@code
     *import sys
     *addToSearchPath( sys.path, 'http://www-us.apache.org/dist//commons/math/binaries/commons-math3-3.6.1-bin.zip/commons-math3-3.6.1/commons-math3-3.6.1.jar', monitor )
     *from org.apache.commons.math3.distribution import BetaDistribution
     *beta= BetaDistribution(2,5)
     *
     *xx= linspace(0,1.0,100)
     *yy= zeros(100)
     *for i in indgen(100):
     *    yy[i]= beta.density(xx[i].value())
     *#yy= map( xx, beta.density )
     *plot( xx, yy )
     *}</small></pre></blockquote>
     * @param syspath the list of folders to search, should be sys.path.
     * @param path the path to add, which should be a jar file, possibly contained within a zip on an http site.
     * @param mon monitor for the download.
     * @return the name of the folder or jar file added.
     * @see https://sourceforge.net/p/autoplot/feature-requests/584/, which shows example use.
     * @see #findJavaPathRoots(org.das2.util.filesystem.FileSystem) 
     * @throws IOException
     * @throws URISyntaxException 
     */    
    public static String addToSearchPath( PyList syspath, String path, ProgressMonitor mon ) throws IOException, URISyntaxException {
        return addToSearchPath( syspath, path, null, mon );
    }
    
    /**
     * download the jar file resource, unpack it, and add it to the search path.  Note
     * such scripts will not work with Webstart releases!  The code is only
     * loaded once per session, so Autoplot must be restarted if the library is updated.
     *
     * Here is an example use:
     * <blockquote><pre><small>{@code
     *import sys
     *addToSearchPath( sys.path, 'http://www-us.apache.org/dist//commons/math/binaries/commons-math3-3.6.1-bin.zip/commons-math3-3.6.1/commons-math3-3.6.1.jar', monitor )
     *from org.apache.commons.math3.distribution import BetaDistribution
     *beta= BetaDistribution(2,5)
     *
     *xx= linspace(0,1.0,100)
     *yy= zeros(100)
     *for i in indgen(100):
     *    yy[i]= beta.density(xx[i].value())
     *#yy= map( xx, beta.density )
     *plot( xx, yy )
     *}</small></pre></blockquote>
     * @param syspath the list of folders to search, should be sys.path.
     * @param path the path to add, which should be a jar file, possibly contained within a zip on an http site.
     * @param docPath the path containing javadocs, useful programmatically for completions.
     * @param mon monitor for the download.
     * @return the name of the folder or jar file added.
     * @see https://sourceforge.net/p/autoplot/feature-requests/584/ which shows example use.
     * @see #findJavaPathRoots(org.das2.util.filesystem.FileSystem) 
     * @throws IOException
     * @throws URISyntaxException 
     */
    public static String addToSearchPath( PyList syspath, String path, String docPath, ProgressMonitor mon ) throws IOException, URISyntaxException {
        if ( System.getProperty("javawebstart.version")!=null ) {
            logger.warning("Jython addToSearchPath will probably fail because this is not supported with Webstart.");
        }
        if ( path.endsWith(".jar") ) {
            File jarFile;
            try {
                jarFile= FileSystemUtil.doDownload( path, mon );
            } catch ( IOException e ) {
                jarFile= DataSetURI.downloadResourceAsTempFile( DataSetURI.getURL(path),mon);
            }
            File destDir= FileSystem.settings().getLocalCacheDir();
            destDir= new File( destDir, "jar" );
            String ss= path.replace("://", "/");
            destDir= new File( destDir, ss );
            org.das2.util.filesystem.FileSystemUtil.unzipFile( jarFile, destDir);
            syspath.insert( 0, new PyString(destDir.toString()) );
            if ( docPath!=null ) {
                List<String> paths= findJavaPathRoots( FileSystem.create(destDir.toURI()) );
                paths.forEach((p) -> {
                    JavadocLookup.getInstance().setLinkForJavaSignature(p,docPath);
                });
            }
            return destDir.toString();
        } else {
            throw new IllegalArgumentException("only jar files can be added.");
        }
    }
    
    /**
     * convenience method for creating URIs.  
     * @param vapScheme null or the data source scheme, such as "vap+das2server" or "vap+cdaweb"
     * @param resourceUri null or the resource uri, such as "http://www-pw.physics.uiowa.edu/das/das2Server"
     * @param args null or a map/dictionary of arguments, including "arg_0" for a positional argument.  
     * @return the URI.  If vapScheme is null, then the URI will be implicit.
     */
    public static String formUri( String vapScheme, String resourceUri, Object args ) {
        
        Map<String,Object> jargs= new LinkedHashMap();
        if ( args!=null ) {
            if ( args instanceof PyDictionary ) {
                PyDictionary pd= (PyDictionary)args;
                pd.keys().forEach((k) -> {
                    jargs.put( String.valueOf(k), String.valueOf( pd.get(  new PyString( String.valueOf(k) ) ) ) ); // TODO: surely there's an easier way
                });
                
            } else if ( args instanceof Map ) {
                Map m= (Map)args;
                ((Map)args).keySet().forEach((k) -> {
                    jargs.put( String.valueOf(k), m.get( k ) );
                });
            } else {
                throw new IllegalArgumentException("args cannot be converted to Map");
            }
        }
        return URISplit.format( vapScheme, resourceUri, jargs );
    }
            
    /**
     * converts types often seen in Jython codes to the correct type.  For
     * example, ds= putProperty( ds, 'UNITS', 'seconds since 2012-01-01').
     * Note USER_PROPERTIES can be a Python dictionary and it will be converted
     * to a Java Map.
     * 
     * @param ds the dataset
     * @param name the name of the property, such as UNITS or USER_PROPERTIES
     * @param value the value of the property
     * @return the dataset, possibly converted to a mutable dataset.
     */
    public static MutablePropertyDataSet putProperty( QDataSet ds, String name, Object value ) {
        String type= DataSetUtil.getPropertyType(name);
        if ( type!=null && type.equals(DataSetUtil.PROPERTY_TYPE_MAP) ) {
            if ( !( value instanceof Map ) ) {
                try {
                    String json= value.toString(); // Python Dictionary
                    JSONObject obj= new JSONObject(json);
                    value= JsonUtil.jsonToMap(obj);
                } catch (JSONException ex) {
                    logger.log(Level.SEVERE, "type is not supported for PROPERTY TYPE MAP: "+value, ex);
                }
            }
        }
        return Ops.putProperty( ds, name, value );
    }
    
    /**
     * run the function on a different thread
     * @param func a jython callable.
     */
    public static void invokeSometime( final PyObject func ) {
        Runnable run= () -> {
            func.__call__();
        };
        new Thread(run).start();
    }

    /**
     * run the function on a different thread
     * @param func a jython callable.
     * @param arg an object to pass to the callable as an argument
     */
    public static void invokeSometime( final PyObject func, final PyObject arg ) {
        Runnable run= () -> {
            func.__call__(arg);
        };
        new Thread(run).start();
    }

    /**
     * return the current line in the Jython script as &lt;filename&gt;:&lt;linenum&gt;
     * or ??? if this cannot be done.  Note calls to this will collect a stack
     * trace and will affect performance.
     * 
     * @return the current line or ???
     * @see QubeDataSetIterator#currentJythonLine() 
     */
    public static String currentLine() {
        StackTraceElement[] sts= new Exception().getStackTrace();
        int i= 0;
        while ( i<sts.length ) {
            if ( sts[i].getClassName().startsWith("org.python.pycode") ) {
                return sts[i].getFileName()+":"+ sts[i].getLineNumber();
            }
            i=i+1;
        }
        return "???";
    }

    /**
     * search the folder for the names of packages.  This could trivially
     * return "org", but instead navigate to find a more precise name, or names
     * like "org.autoplot" and "org.das2".  Note this is a bit like a recursive
     * find command, but note that some Java assumptions like classnames being
     * capitalized and packages being lower case are encoded.
     * @param destDir root to start the search.
     * @return list of packages.
     * @see #addToSearchPath(org.python.core.PyList, java.lang.String, org.das2.util.monitor.ProgressMonitor) 
     */
    public static List<String> findJavaPathRoots(FileSystem destDir) {
        return findJavaPathRoots(destDir,"/",new ArrayList<>() );
    }
    
    private static List<String> findJavaPathRoots( FileSystem destDir, String prefix, List<String> result) {
        try {
            String[] roots= destDir.listDirectory("/");
            for ( String r: roots ) {
                if ( r.length()==0 || Character.isUpperCase( r.charAt(0) ) ) {
                    logger.log(Level.FINER, "skipping {0}", r); //META-INF, Class names...
                } else {
                    if ( destDir.getFileObject(r).isFolder() ) {
                        try {
                            FileSystem child= destDir.createFileSystem(r);
                            findJavaPathRoots( child, prefix + r, result);
                        } catch (URISyntaxException ex) {
                            Logger.getLogger(JythonOps.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            if ( prefix.length()>1 ) {
                boolean haveIt= false;
                for ( String r: result ) {
                    if ( r.startsWith(prefix) ) {
                        haveIt= true;
                        break;
                    }
                }
                if ( !haveIt ) {
                    result.add( prefix );
                }
            }
            return result;
        } catch (IOException ex) {
            Logger.getLogger(JythonOps.class.getName()).log(Level.SEVERE, null, ex);
            return result;
        }
    }
}
