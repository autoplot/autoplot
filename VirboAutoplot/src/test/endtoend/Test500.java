/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

/**
 * Mark this number as allocated to the Plasma Wave Group at U. Iowa.  They don't use this, but they have a test script.
 * See http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-test500/
 * @author jbf
 */
public class Test500 {
    public static void main( String[] args ) throws Exception {

        String[] args1= {  "--vap=/home/jbf/autoplot/test/test500_001.vap", "--outfile=/tmp/test500_001.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args1 );

        String[] args2= {  "--vap=/home/jbf/autoplot/test/test500_002.vap", "--outfile=/tmp/test500_002.png", "--noexit" };
        org.virbo.autoplot.AutoplotServer.main( args2 );

        System.err.println("DONE...");
    }
}
