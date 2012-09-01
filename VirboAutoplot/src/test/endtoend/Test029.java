/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.InputStream;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.util.InteractiveInterpreter;
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
        int status= testBuiltIns();
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
}
