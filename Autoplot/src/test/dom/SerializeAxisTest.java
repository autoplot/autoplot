/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.dom;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.autoplot.dom.Axis;
import org.autoplot.state.DatumRangePersistenceDelegate;

/**
 *
 * @author jbf
 */
public class SerializeAxisTest {

    public static void main(String[] args) throws FileNotFoundException {
        Axis a = new Axis();
        a.setRange( DatumRange.newDatumRange( 0.2, 2000, Units.dimensionless ) );
        a.setLog(true);

        XMLEncoder enc= new XMLEncoder( new FileOutputStream( "/tmp/foo.xml" ) );
        enc.setPersistenceDelegate( DatumRange.class, new DatumRangePersistenceDelegate() );
        enc.writeObject(a);
        enc.close();

        XMLDecoder dec= new XMLDecoder( new FileInputStream( "/tmp/foo.xml" ) );

        Axis a2= (Axis) dec.readObject();
        System.err.println( a2.diffs(a) );
    }
}
