/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.virbo.datasource.LogNames;

/**
 *
 * @author jbf
 */
public class RegexTest {

    private static final Logger logger= Logger.getLogger( LogNames.APDSS_JYDS );

    private static void doTest( int num, String regex, String test ) {

        Pattern p= Pattern.compile(regex);
        String s= test;
        logger.info( String.format( "%03d: %5s %s",  num, p.matcher(s).matches(), regex ) );

    }

    public static void main( String[] args ) {

            String vnarg= "\\s*([a-z][a-z0-9]*)\\s*"; // any variable name  VERIFIED
            String sarg= "\\s*\\'([a-z][a-z0-9]*)\\'\\s*"; // any variable name  VERIFIED
            String aarg= "\\s*(\\'[^\\']+\\')\\s*"; // any argument
            String farg= "\\s*([0-9\\.\\+-eE]+)\\s*"; // any float variable name

            //String farg= "\\s*([^\\']+)\\s*"; // any float variable name

            Pattern p= Pattern.compile( vnarg+"=\\s*getParam\\("+sarg+","+aarg+"(,"+aarg + "\\))?.*" );
            Pattern fp= Pattern.compile(vnarg+"=\\s*getParam\\("+sarg+","+farg+"(,"+aarg + "\\))?.*" );

            doTest( 0, vnarg+"=\\s*getParam\\("+sarg+","+aarg+","+aarg + "\\).*",
                    "dir= getParam( 'dir', '/home/jbf/temp/ap4/', 'Directory containing builds $Y-$m-$d_$H-$M-$S' )" );

            doTest( 1, sarg, "'dir'" );

            doTest( 2, "getParam\\("+sarg+"\\).*", "getParam( 'dir' )" );
            
            doTest( 3, vnarg+"=\\s*getParam\\("+sarg+","+aarg+"(,"+aarg + "\\))?.*",
                    "dir= getParam( 'dir', '/home/jbf/temp/ap4/', 'Directory containing builds $Y-$m-$d_$H-$M-$S' )" );
            
            doTest( 4, "\\s*\\'([a-z]+)\\'\\s*", "'dir'" );

            doTest( 5, aarg, "'a new'" );

            doTest( 6, vnarg+"=getParam\\("+sarg+ "\\).*",
                    "dir=getParam('dir')" );

            doTest( 7, vnarg+"=getParam\\("+sarg+ "," + aarg + "\\).*",
                    "dir=getParam('dir','a new')" );

            doTest( 8, vnarg+"=getParam\\("+sarg+ "," + aarg + "," + aarg + "\\).*",
                    "dir=getParam('dir', ' a new' , ' a new')" );

            doTest( 9, vnarg+"=\\s*getParam\\("+sarg+","+aarg+"(,"+aarg + "\\))?.*",
                    "dir= getParam( 'dir', '/home/jbf/temp/ap4/' )" );

            doTest( 10, "\\s*([0-9\\.\\+-eE]+)\\s*", " 3.4e5" );

            doTest( 11, "\\s*([0-9\\.\\+-eE]+)\\s*", " +3.4E-5" );

            doTest( 12, farg, "  +3.4E-5 " );

            doTest( 13, vnarg+"=\\s*getParam\\("+sarg+","+farg + "\\).*",
                    "p2= getParam( 'factor', 3.4 )" );

            doTest( 14, vnarg+"=\\s*getParam\\("+sarg+","+farg+"(,"+aarg + "\\))?.*", "p2= getParam( 'factor', 3.4 )" );

    }
}
