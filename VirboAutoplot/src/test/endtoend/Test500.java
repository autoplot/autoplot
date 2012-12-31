/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.util.logging.Level;

/**
 * Mark this number as allocated to the Plasma Wave Group at U. Iowa.  They don't use this, but they have a test script.
 * See http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-test500/
 * @author jbf
 */
public class Test500 {
    public static void main( String[] args ) throws Exception {

        org.das2.util.LoggerManager.getLogger( "autoplot.tca.uritcasource" ).setLevel(Level.ALL);

        String[] args10= {  "--vap=/home/jbf/autoplot/test/test500_010.vap", "--outfile=/tmp/test500_010.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args10 );

        String[] args1= {  "--vap=/home/jbf/autoplot/test/test500_001.vap", "--outfile=/tmp/test500_001.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args1 );

        String[] args2= {  "--vap=/home/jbf/autoplot/test/test500_002.vap", "--outfile=/tmp/test500_002.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args2 );

        String[] args3= {  "--vap=/home/jbf/autoplot/test/test500_003.vap", "--outfile=/tmp/test500_003.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args3 );

        String[] args4= {  "--vap=/home/jbf/autoplot/test/test500_004.vap", "--outfile=/tmp/test500_004.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args4 );

        String[] args5= {  "--vap=/home/jbf/autoplot/test/test500_005.vap", "--outfile=/tmp/test500_005.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args5 );

        String[] args6= {  "--vap=/home/jbf/autoplot/test/test500_006.vap", "--outfile=/tmp/test500_006.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args6 );

        System.err.println("DONE...");
    }
}
