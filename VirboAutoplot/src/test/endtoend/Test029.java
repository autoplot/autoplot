/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.util.InteractiveInterpreter;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
 * @author jbf
 */
public class Test029 {
    public static void main(String[] args) throws IOException  {

        // Python2.7 built-ins.  This comes from http://docs.python.org/library/functions.html.  The
        // params are removed and we evaluate each one.
        String builtIns = "abs() 	divmod() 	input() 	open() 	staticmethod() "
            + "all() 	enumerate() 	int() 	ord() 	str() "
            + "any() 	eval() 	isinstance() 	pow() 	sum() "
            + "basestring() 	execfile() 	issubclass() 	print() 	super() "
            + "bin() 	file() 	iter() 	property() 	tuple() "
            + "bool() 	filter() 	len() 	range() 	type() "
            + "bytearray() 	float() 	list() 	raw_input() 	unichr() "
            + "callable() 	format() 	locals() 	reduce() 	unicode()"
            + "chr() 	frozenset() 	long() 	reload() 	vars()"
            + "classmethod() 	getattr() 	map() 	repr() 	xrange()"
            + "cmp() 	globals() 	max() 	reversed() 	zip()"
            + "compile() 	hasattr() 	memoryview() 	round() 	__import__()"
            + "complex() 	hash() 	min() 	set() 	apply()"
            + "delattr() 	help() 	next() 	setattr() 	buffer()"
            + "dict() 	hex() 	object() 	slice() 	coerce()"
            + "dir() 	id() 	oct() 	sorted() 	intern()";
        String[] bb= builtIns.replaceAll("\\(\\)", " " ).split("\\s+");

        InteractiveInterpreter interp= JythonUtil.createInterpreter(false);  // this is with Autoplot add-ons.
        //InteractiveInterpreter interp = new InteractiveInterpreter();  // this is our goal.
        
        for ( int i=0; i<bb.length; i++ ) {
            String b= bb[i];
            try {
                PyObject result= interp.eval( b );
                if ( result instanceof PyReflectedFunction ) {
                    System.err.println( "!!!" + b + ": " + result);
                } else {
                    System.err.println( "   " + b + ": " + result);
                }
            } catch ( PyException ex ) {
                System.err.println("Error finding symbol \""+b+"\"" );
            }
        }

    }
}
