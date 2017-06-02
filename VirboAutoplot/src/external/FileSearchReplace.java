
package external;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

/**
 * Introduced to support compile-application-two-jars script, when I couldn't
 * figure out how to use sed to insert a URL into a file.
 * @author jbf
 */
public class FileSearchReplace {
    public static void main( String[] args ) throws IOException {
        if ( args.length<3 ) {
            System.err.println("java external.FileSearchReplace <file> <regex> <replace> ...");
            System.exit(0);
        }
        for ( int i=1; i<args.length; i+=2 ) {
            args[i]= Pattern.quote(args[i]);
        }
        String file= args[0];

        File fin= new File(file);
        File fout= new File(file+".1");
        try ( BufferedReader in = new BufferedReader( new FileReader(fin) );
            PrintWriter out= new PrintWriter( new FileWriter( fout ) ) ) {
            String s= in.readLine();
            while ( s!=null ) {
                for ( int i=1; i<args.length; i+=2 ) {
                    s= s.replaceAll( args[i], args[i+1] );
                }
                out.println(s);
                s= in.readLine();
            }
        }
        if ( !fout.renameTo( fin ) ) {
            System.err.println("unable to rename file " +fout + " to " + fin );
        }
    }
}
