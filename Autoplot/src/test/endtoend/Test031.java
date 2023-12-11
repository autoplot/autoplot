/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.das2.qds.QDataSet;
import org.autoplot.jythonsupport.Util;
import static org.autoplot.ScriptContext.*;

/**
 * tests of tick labels, using the new DomainDivider.  This was a system written
 * a while ago that will fix a bunch of problems, but has a couple of bugs that
 * need to be resolved first.
 *
 * @author jbf
 */
public class Test031 {
    private static ScriptContext scriptContext= ScriptContext.getInstance();
    
    public static void doTest( int id, String uri ) {

        try {
            QDataSet ds= Util.getDataSet( uri );

            QDataSet bds= (QDataSet) ds.property(QDataSet.BUNDLE_1);
            
            scriptContext.plot( ds );
            scriptContext.setTitle( uri );

            String label= String.format( "test031_%03d", id );

            scriptContext.writeToPng( label+".png" );
            //((MutablePropertyDataSet)bundle1).putProperty( QDataSet.LABEL, uri );
            formatDataSet( ds, label+".qds");

        } catch ( Exception ex ) {
            ex.printStackTrace();

        }

    }

    public static void main(String[] args) throws Exception  {
        try {
            Application dom= scriptContext.getDocumentModel();
            dom.getPlots(0).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
            dom.getPlots(0).getYaxis().getController().getDasAxis().setUseDomainDivider(true);
            dom.getPlots(0).getZaxis().getController().getDasAxis().setUseDomainDivider(true);

            doTest( 0, TestSupport.TEST_DATA + "qds/bad_ticks.qds" );
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }
    
}
