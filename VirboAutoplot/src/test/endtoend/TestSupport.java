/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class TestSupport {

    /**
     * input vaps for testing.
     */
    public static final String TEST_HOME= "/home/jbf/ct/hudson/";


    /**
     * input data for testing
     */
    public static final String TEST_DATA= "file:" + TEST_HOME + "data.backup/";

    /**
     * input data for testing, small area that can be backed up.
     */
    public static final String TEST_DATA_SMALL= "file:" + TEST_HOME + "data/";

    /**
     * input vaps for testing.
     */
    public static final String TEST_VAP= "file:" + TEST_HOME + "vap/";

    public static MutablePropertyDataSet sampleDataRank2( int len0, int len1 ) {
        MutablePropertyDataSet rank2Rand= (MutablePropertyDataSet) Ops.add( Ops.randomn(-12345, len0,len1),
                    Ops.sin( Ops.add( Ops.outerProduct( Ops.linspace( 0, 1000.,len0), Ops.replicate(1,len1)),
                                        Ops.outerProduct( Ops.replicate(1,len0), Ops.linspace(0,10,len1) ) ) ) );
        rank2Rand.putProperty( QDataSet.NAME, "Randomn" );
        return rank2Rand;
    }
    /**
     * return rank 1 dataset that is a 1-D random walk.
     * @param len0
     * @return
     */
    public static MutablePropertyDataSet sampleDataRank1( int len0 ) {
        MutablePropertyDataSet rank1Rand= (MutablePropertyDataSet) Ops.accum( Ops.randomn(-12345,len0) );
        try {
            MutablePropertyDataSet xx= (MutablePropertyDataSet) Ops.timegen("2050-001T00:00", "75.23 ms", len0);
            xx.putProperty(QDataSet.NAME, "Time" );
            rank1Rand.putProperty(QDataSet.DEPEND_0, xx );
        } catch (ParseException ex) {
            Logger.getLogger(TestSupport.class.getName()).log(Level.SEVERE, null, ex);
        }
        Random r= new Random(-12345);
        // simulate data dropout.
        WritableDataSet wds= (WritableDataSet)rank1Rand;
        int len= Math.max( 5,len0/100 );
        for ( int i=0; i<5; i++ ) {
            int idx= r.nextInt(len0-len);
            for ( int j=idx; j<idx+len; j++ ) {
                wds.putValue( j,-1e38 );
            }
        }
        wds.putProperty( QDataSet.VALID_MIN, -1e30 );
        rank1Rand.putProperty(QDataSet.NAME,"Random");
        return rank1Rand;
    }

    public static MutablePropertyDataSet sampleQube1( double off1, double off2, int len1, int len2 ) {
        DDataSet result= (DDataSet) Ops.randn(len1,len2);
        MutablePropertyDataSet xx= (MutablePropertyDataSet)Ops.add( DataSetUtil.asDataSet(off1), Ops.dindgen(len1) );
        xx.putProperty(QDataSet.NAME, "xx" );
        result.putProperty( QDataSet.DEPEND_0, xx );
        MutablePropertyDataSet yy= (MutablePropertyDataSet) Ops.add( DataSetUtil.asDataSet(off2), Ops.dindgen(len2) );
        yy.putProperty(QDataSet.NAME, "yy" );
        result.putProperty( QDataSet.DEPEND_1, yy );
        result.putProperty( QDataSet.NAME, "x_"+String.valueOf(off1).replaceAll("\\.", "_") );
        return result;
    }

    public static MutablePropertyDataSet sampleRank3Join( ) {
        JoinDataSet jds= new JoinDataSet(3);

        MutablePropertyDataSet qds= sampleQube1( 0, 2.2, 6, 5 );
        jds.join( qds );
        qds= sampleQube1( qds.length(), 3.3, 5, 4 );
        jds.join( qds );
        
        return jds;
    }


    /**
     * run all tests.  This was introduced to allow running of all the tests so we could
     * try to exercise as much code as possible without committing new changes.  Note
     * JBF runs a separate Hudson server now, so this isn't so necessary.
     */
    public static void runAllTests() {
        String[] args= new String[0];

        try {
            Method[] tests= new Method[] {
                Test001.class.getMethod( "main",args.getClass()),
                Test002.class.getMethod( "main",args.getClass()),
                Test003.class.getMethod( "main",args.getClass()),
                Test004.class.getMethod( "main",args.getClass()),
                Test005.class.getMethod( "main",args.getClass()),
                Test006.class.getMethod( "main",args.getClass()),
                Test007.class.getMethod( "main",args.getClass()),
                Test008.class.getMethod( "main",args.getClass()),
                Test009.class.getMethod( "main",args.getClass()),
                Test010.class.getMethod( "main",args.getClass()),
                Test011.class.getMethod( "main",args.getClass()),
                Test012.class.getMethod( "main",args.getClass()),
                Test013.class.getMethod( "main",args.getClass()),
                Test014.class.getMethod( "main",args.getClass()),
                Test015.class.getMethod( "main",args.getClass()),
                Test016.class.getMethod( "main",args.getClass()),
                Test017.class.getMethod( "main",args.getClass()),
                Test018.class.getMethod( "main",args.getClass()),
                Test019.class.getMethod( "main",args.getClass()),
                Test020.class.getMethod( "main",args.getClass()),
                Test021.class.getMethod( "main",args.getClass()),
                Test022.class.getMethod( "main",args.getClass()),
                Test023.class.getMethod( "main",args.getClass()),
                Test024.class.getMethod( "main",args.getClass()),
                Test025.class.getMethod( "main",args.getClass()),
                Test026.class.getMethod( "main",args.getClass()),
                Test027.class.getMethod( "main",args.getClass()),
                Test028.class.getMethod( "main",args.getClass()),
                Test029.class.getMethod( "main",args.getClass()),
                Test030.class.getMethod( "main",args.getClass()),
                Test031.class.getMethod( "main",args.getClass()),
                Test032.class.getMethod( "main",args.getClass()),
            };

            final SecurityManager securityManager = new SecurityManager() {
                public void checkPermission(java.security.Permission permission) {
                    if (permission.getName().startsWith("exitVM")) {
                        throw new SecurityException(
                                "System.exit calls not allowed: exit level "+permission.getName() );
                    }
                }
            };
            System.setSecurityManager(securityManager);

            for ( int i=0; i<tests.length; i++ ) {                
                Method m= tests[i];
                long t0= System.currentTimeMillis();
                System.err.println("running test "+i +" "+ m);
                try {
                    m.invoke( null, (Object) args );
                } catch ( SecurityException ex ) {
                    //expected to come here because we disable System.exit.
                } catch (IllegalAccessException ex) {
                    //Logger.getLogger(TestSupport.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalArgumentException ex) {
                    //Logger.getLogger(TestSupport.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvocationTargetException ex) {
                    
                }

                System.err.println("Okay: "+i + "  "+(System.currentTimeMillis()-t0)+" millis" );
            }
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }



    }

    public static void main( String[] args ) {
        System.err.println("Run all tests...");
        runAllTests();
    }
}
