
package org.autoplot.jythonsupport;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
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
import org.das2.qds.ops.Ops;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Contains operations that are only available to Jython code, and is dependent
 * on the jython libraries.
 *
 * @author jbf
 */
public class JythonOps {
    
    private static final Logger logger= Logger.getLogger("jython");
    
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
     * @param arg0
     * @param u unit context, which may be ignored for Datums, etc.
     * @return 
     * @see Ops#dataset(java.lang.Object, org.das2.datum.Units) 
     */
    public static QDataSet dataset( PyObject arg0, Units u ) {
        if ( arg0 instanceof PyQDataSet ) {
            return ((PyQDataSet)arg0).rods;
        } else if ( arg0 instanceof PyList ) {
            return Ops.putProperty( PyQDataSetAdapter.adaptList( (PyList)arg0 ), QDataSet.UNITS, u );
        } else if ( arg0 instanceof PyArray ) {
            return Ops.putProperty( PyQDataSetAdapter.adaptArray( (PyArray) arg0 ), QDataSet.UNITS, u );
        } else if ( arg0 instanceof PyInteger ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue(), u );
        } else if ( arg0 instanceof PyLong ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue(), u );
        } else if ( arg0 instanceof PyFloat ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue(), u );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(Datum.class) instanceof Datum ) ) {
            return DataSetUtil.asDataSet( (Datum)((PyJavaInstance)arg0).__tojava__(org.das2.datum.Datum.class) );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(DatumRange.class) instanceof DatumRange ) ) {
            return DataSetUtil.asDataSet( (DatumRange)((PyJavaInstance)arg0).__tojava__(org.das2.datum.DatumRange.class) );

        } else if ( arg0 instanceof PyString ) {
            try {
                return DataSetUtil.asDataSet( u.parse(arg0.toString()) );
            } catch ( ParseException ex ) {
                throw Py.SyntaxError( "unable to parse string: "+arg0 );
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
     * coerce python objects to DatumRange, when the units are known.
     * 
     * @param arg0 PyQDataSet, String, array or List.
     * @param context the units.
     * @return range with the same magnitude, but context units.
     */
    public static DatumRange datumRange( PyObject arg0, Units context ) {
        DatumRange newRange= JythonOps.datumRange(arg0);
        if ( ! context.isConvertibleTo(newRange.getUnits()) ) {
            newRange= DatumRange.newDatumRange( newRange.min().value(), newRange.max().value(), context );
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
        if (val.__tojava__(Color.class) != Py.NoConversion) {
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
     * download the resource, unpack it, and add it to the search path.  Note
     * such scripts will not work with Webstart releases!
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
     * @see https://sourceforge.net/p/autoplot/feature-requests/584/, which shows 
     * @throws IOException
     * @throws URISyntaxException 
     */
    public static String addToSearchPath( PyList syspath, String path, ProgressMonitor mon ) throws IOException, URISyntaxException {
        if ( System.getProperty("javawebstart.version")!=null ) {
            logger.warning("Jython addToSearchPath will probably fail because this is not supported with Webstart.");
        }
        if ( path.endsWith(".jar") ) {
            File jarFile= FileSystemUtil.doDownload( path, mon );
            File destDir= FileSystem.settings().getLocalCacheDir();
            destDir= new File( destDir, "jar" );
            String ss= path.replace("://", "/");
            destDir= new File( destDir, ss );
            org.das2.util.filesystem.FileSystemUtil.unzipFile( jarFile, destDir);
            syspath.append( new PyString(destDir.toString()) );
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
                for ( Object k: pd.keys() ) {
                    jargs.put( String.valueOf(k), String.valueOf( pd.get(  new PyString( String.valueOf(k) ) ) ) ); // TODO: surely there's an easier way
                }
                
            } else if ( args instanceof Map ) {
                Map m= (Map)args;
                for ( Object k: ((Map)args).keySet() ) {
                    jargs.put( String.valueOf(k), m.get( k ) );
                }
            } else {
                throw new IllegalArgumentException("args cannot be converted to Map");
            }
        }
        return URISplit.format( vapScheme, resourceUri, jargs );
    }
            
    /**
     * converts types often seen in Jython codes to the correct type.  For
     * example, ds= putProperty( ds, 'UNITS', 'seconds since 2012-01-01').
     * 
     * @param ds
     * @param name
     * @param value
     * @return the dataset, possibly converted to a mutable dataset.
     */
    public static MutablePropertyDataSet putProperty( QDataSet ds, String name, Object value ) {   
        return Ops.putProperty( ds, name, value );
    }
    
    /**
     * run the function on a different thread
     * @param func a jython callable.
     */
    public static void invokeSometime( final PyObject func ) {
        Runnable run= new Runnable() {
            public void run() {
                func.__call__();
            }
        };
        new Thread(run).start();
    }

    /**
     * run the function on a different thread
     * @param func a jython callable.
     * @param arg an object to pass to the callable as an argument
     */
    public static void invokeSometime( final PyObject func, final PyObject arg ) {
        Runnable run= new Runnable() {
            public void run() {
                func.__call__(arg);
            }
        };
        new Thread(run).start();
    }

}
