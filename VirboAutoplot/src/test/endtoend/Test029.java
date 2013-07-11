/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.InputStream;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.util.InteractiveInterpreter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.JythonUtil;

/**
 * Test that built-in functions of python aren't accidentally stepped on.
 * This was motivated by the unfortunate use of list and coerce.
 *
 * Note the abs and pow functions are overridden, but we make sure that
 * they work for ints and floats.
 *
 * Note we also compare against python 2.7 built-ins, even though we use Jython 2.2.
 * 
 * @author jbf
 */
public class Test029 {
    public static void main(String[] args) throws IOException  {
        
        int status;
        
        status= testStringToDataSet();
        if ( status!=0 )             System.exit(status);
            
        status= testBuiltIns();
        if ( status!=0 )             System.exit(status);

        status= testJythonLib();
        if ( status!=0 )             System.exit(status);

    }

    /**
     * ensure that Python 2.2.1 Lib directory is available, with python libraries
     * (http://sourceforge.net/tracker/index.php?func=detail&aid=3134982&group_id=199733&atid=970682)
     * @throws IOException
     */
    private static int testJythonLib() throws IOException {
        // see if "/glob.py" is on path.  We used to put it in /Lib/glob.py, but
        //this caused problems and we need to put it in the root.
        java.net.URL s= Test029.class.getResource("/glob.py");
        InputStream in= s.openStream();

        int c;
        int count=0;
        while ( (c=in.read())!=-1 ) {
            if ( c=='\n' ) count++;
        }
        in.close();
        System.err.printf("glob.py is approx %d lines long.\n",count);

        InteractiveInterpreter interp = JythonUtil.createInterpreter(false);
        interp.exec("import glob\n");
        PyObject res= interp.eval("glob.glob('*')\n");
        System.err.println(res);

        return 0;
    }

    private static int testBuiltIns() throws IOException {
        // Python2.7 built-ins.  This comes from http://docs.python.org/library/functions.html.  The
        // params are removed and we evaluate each one.
        //
        // note abs() is redefined but behaves like the built-in.
        String builtIns = "abs() 	divmod() 	input() 	open() 	staticmethod() " + "all() 	enumerate() 	int() 	ord() 	str() " + "any() 	eval() 	isinstance() 	pow() 	sum() " + "basestring() 	execfile() 	issubclass() 	print() 	super() " + "bin() 	file() 	iter() 	property() 	tuple() " + "bool() 	filter() 	len() 	range() 	type() " + "bytearray() 	float() 	list() 	raw_input() 	unichr() " + "callable() 	format() 	locals() 	reduce() 	unicode()" + "chr() 	frozenset() 	long() 	reload() 	vars()" + "classmethod() 	getattr() 	map() 	repr() 	xrange()" + "cmp() 	globals() 	max() 	reversed() 	zip()" + "compile() 	hasattr() 	memoryview() 	round() 	__import__()" + "complex() 	hash() 	min() 	set() 	apply()" + "delattr() 	help() 	next() 	setattr() 	buffer()" + "dict() 	hex() 	object() 	slice() 	coerce()" + "dir() 	id() 	oct() 	sorted() 	intern()";
        String supported= "abs() pow() round()";
        String notYetSupported= "all() any()  print()  bin()  bytearray()   format()  frozenset()   reversed()   memoryview()   set()   help()   next()   buffer()   sorted() ";

        String[] bb = builtIns.replaceAll("\\(\\)", " ").split("\\s+");
        InteractiveInterpreter interp = JythonUtil.createInterpreter(false); // this is with Autoplot add-ons.
        //InteractiveInterpreter interp = new InteractiveInterpreter();  // this is our goal.
        for (int i = 0; i < bb.length; i++) {
            String b = bb[i];
            try {
                PyObject result = interp.eval(b);
                if (result instanceof PyReflectedFunction) {
                    System.err.println("!!!" + b + ": " + result);
                    if ( !supported.contains(b) ) {
                        return -3;
                    }
                } else {
                    System.err.println("   " + b + ": " + result);
                }
            } catch (PyException ex) {
                System.err.println("Error finding symbol \"" + b + "\"");
                if ( !notYetSupported.contains(b) ) {
                    return -2;
                }
            }
        }
        return 0;
    }
    
    private static int equiv( QDataSet norm, QDataSet test ) {
        return Ops.equivalent( norm, test ) ? 0 : 1;
    }
    
    /**
     * Test the codes that convert strings into DataSets, to serve as documentation for this
     * central feature.  This is JSON, but taken dangerously further.  In JSON, 
     * 0 is an integer, 0.0 is a float, '0.0' is a string, and ['0.0'] is an array.  Here we
     * tread out into deep water where '2014-01-01T00:00' is a time datum and 
     * '2014-01-01T00:00/2014-01-04T00:00' is a datum range.  We have a
     * system that supports ordinal data, so when is the string now datum from this
     * ordinal set?
     *   int, float, double, etc to Rank 0 datasets
     *   List&lt;Number&gt; to Rank 1 datasets.
     *   Java arrays of Number to Rank 1-4 qubes datasets
     *   Strings to rank 0 datasets with units ("5 s" "2014-01-01T00:00")
     *   Strings to rank 1 bins ('2014-01-01T00:00/2014-01-04T00:00')
     *   Datums to rank 0 datasets
     *   DatumRanges to rank 1 bins
     * Other things to watch out for:
     *   ':'  Python slice 
     *   '1996:1998'  Python slice
     *   '2014'  Is this a year long time range, integer, or ISO8601 date?  (It's an integer.)
     *   '2014/2016'  Is this an ISO8601 time range, or the ratio of two numbers?  (There was a das2 code that allowed simple expressions).
     *   '5 to 40 kg' is not supported (presently), because this is completely new water...
     * @return 
     */
    private static int testStringToDataSet() {
        QDataSet r0= DataSetUtil.asDataSet( 0, Units.dimensionless );
        QDataSet r1= Ops.indgen(4);
        QDataSet t1= DataSetUtil.asDataSet( TimeUtil.toDatum( new int[] { 2014,1,1,0,0,0,0 } ) );
        QDataSet r0_2014= DataSetUtil.asDataSet( 2014 );
        QDataSet r0_2014_s= DataSetUtil.asDataSet( 2014, Units.seconds );
        QDataSet tr1= DataSetUtil.asDataSet( DatumRangeUtil.parseISO8601Range("2014-01-01T00:00/2014-01-04T00:00") );
        
        int r;
        
        r= equiv( r0, Ops.dataset("0") );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: 0");

        r= equiv( t1, Ops.dataset("2014-01-01T00:00" ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: time");

        r= equiv( tr1, Ops.dataset("2014-01-01T00:00/2014-01-04T00:00" ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: time range");

        r= equiv( tr1, Ops.dataset("2014-01-01T00:00/P3D" ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: time range");

        r= equiv( tr1, Ops.dataset("P3D/2014-01-04T00:00" ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: time range");

        r= equiv( r0_2014, Ops.dataset("2014" ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: datum");

        r= equiv( r0_2014, Ops.dataset("2014." ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: datum with decimal");

        r= equiv( r0_2014_s, Ops.dataset("2014s" ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: datum with units");
        
        r= equiv( r1, Ops.dataset( new int[] { 0,1,2,3 } ) );
        if ( r!=0 ) throw new IllegalStateException("failed to make dataset: array");
        
        
        return 0;
        
    }
}
