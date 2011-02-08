/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.jythonsupport.Util;
import static org.virbo.autoplot.ScriptContext.*;

/**
 * tests of tick labels, using the new DomainDivider.  This was a system written
 * a while ago that will fix a bunch of problems, but has a couple of bugs that
 * need to be resolved first.
 *
 * @author jbf
 */
public class Test031 {

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
        Application dom= getDocumentModel();
        dom.getPlots(0).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
        dom.getPlots(0).getYaxis().getController().getDasAxis().setUseDomainDivider(true);
        dom.getPlots(0).getZaxis().getController().getDasAxis().setUseDomainDivider(true);

        doTest( 0, TestSupport.TEST_DATA + "qds/bad_ticks.qds" );
    }
    
}
