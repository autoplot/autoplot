/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package vapconverter;

import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import org.das2.persistence.DatumPersistenceDelegate;
import org.das2.persistence.DatumRangePersistenceDelegate;
import org.das2.persistence.UnitsPersistenceDelegate;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.state.BindingPersistenceDelegate;
import org.virbo.autoplot.state.ConnectorPersistenceDelegate;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.state.TypeSafeEnumPersistenceDelegate;

/**
 *
 * @author jbf
 */
public class Main {

    void test() throws FileNotFoundException, IOException {

        InputStream in= 
                new FileInputStream("/media/mini/data.backup/examples/vap/genesisTwoPanelCorrellate.vap");
        XMLDecoder decode = new XMLDecoder( in );

        // add a direct reference to these guys for compile-all script.
        new DatumRangePersistenceDelegate();
        new UnitsPersistenceDelegate();
        new DatumPersistenceDelegate();
        new TypeSafeEnumPersistenceDelegate();
        new BindingPersistenceDelegate();
        new ConnectorPersistenceDelegate();

        Object state = decode.readObject();

        Application app = (Application) state;
        for ( Panel p : app.getPanels()) {
            if (p.getRenderType() == null) {
                p.setRenderTypeAutomatically(RenderType.series);
            }
        }

        StatePersistence.saveState( new File("/tmp/genesisTwoPanelCorrellate.vap"), app );

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        File[] ff= new File( "/media/mini/data.backup/examples/vap/" ).listFiles( new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if ( name.endsWith(".vap") ) {
                    return true;
                } else {
                    return false;
                }
            }
        } );
        for ( int i=0; i<ff.length; i++ ) {
            try {
                String s= ff[i].toString();
                String s1= ff[i].toString()+"x";
                System.err.println( "converting "+s+" to " + s1 + "..." );
                new VapConverter().doConvert( s, s1 );
                System.err.println( "converted "+s+" to " + s1 + "." );
            } catch ( Exception e ) {
                System.err.println("exception "+e );
            }
        }
    }
}
