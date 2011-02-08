/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.jythonsupport.Util;
import static org.virbo.autoplot.ScriptContext.*;

/**
 * tests of metadata representation in the ascii file parser, that provides a means
 * for putting metadata into ascii file headers.
 *
 * http://sourceforge.net/tracker/?func=detail&aid=3169739&group_id=199733&atid=970685
 * JSON-encoded metadata support in ASCII files
 *
 * @author jbf
 */
public class Test030 {

    public static void doTest( int id, String uri ) {

        try {
            QDataSet ds= Util.getDataSet( uri );

            QDataSet bds= (QDataSet) ds.property(QDataSet.BUNDLE_1);
            
            plot( DataSetOps.unbundle(ds,0), DataSetOps.unbundle(ds,1) );
            setTitle( uri );

            String label= String.format( "test030_%03d", id );

            writeToPng( label+".png" );
            //((MutablePropertyDataSet)bundle1).putProperty( QDataSet.LABEL, uri );
            formatDataSet( ds, label+".qds");

        } catch ( Exception ex ) {
            ex.printStackTrace();

        }

    }

    public static void main(String[] args) throws Exception  {
        doTest( 0, TestSupport.TEST_DATA + "dat/headers/demo1.txt?rank2" );
        doTest( 1, TestSupport.TEST_DATA + "dat/headers/proton_density.dat?rank2" );
    }
    
}
