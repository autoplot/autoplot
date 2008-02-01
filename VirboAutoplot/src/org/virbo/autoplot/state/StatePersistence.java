/*
 * StatePersistence.java
 *
 * Created on August 8, 2007, 10:47 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.Units;
import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author jbf
 */
public class StatePersistence {
    
    private StatePersistence() {
    }

    public static void saveState( File f, Object state ) throws IOException {
        XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(
                new FileOutputStream(f)));
        
        e.setPersistenceDelegate( DatumRange.class, new DatumRangePersistenceDelegate() );
        e.setPersistenceDelegate( Units.class, new UnitsPersistenceDelegate() );
        e.setPersistenceDelegate( Datum.class, new DatumPersistenceDelegate() );
        
        e.setExceptionListener( new ExceptionListener() {
            public void exceptionThrown(Exception e) {
                e.printStackTrace();
            }
        } );
        e.writeObject(state);
        e.close();                
    }
    
    public static Object restoreState( File f )  throws IOException {
        XMLDecoder decode= new XMLDecoder( new FileInputStream(f ) );
        return decode.readObject();
    }
}
