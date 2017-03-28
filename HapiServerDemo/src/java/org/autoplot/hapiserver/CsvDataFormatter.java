
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.DefaultDatumFormatter;
import org.das2.datum.format.EnumerationDatumFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.util.NumberFormatUtil;
import org.json.JSONObject;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 * Comma Separated Value (CSV) formatter
 * @author jbf
 */
public class CsvDataFormatter implements DataFormatter {

    boolean[] unitsFormatter;
    DatumFormatter[] datumFormatter;
    boolean[] quotes;
    
    @Override
    public void initialize( JSONObject info, OutputStream out, QDataSet record) {
        unitsFormatter= new boolean[record.length()];
        datumFormatter= new DatumFormatter[record.length()];
        quotes= new boolean[record.length()];
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            Units u= (Units)field.property(QDataSet.UNITS);
            if (  u!=null && UnitsUtil.isTimeLocation(u) ) {
                unitsFormatter[i]= true;
                datumFormatter[i]= TimeDatumFormatter.DEFAULT;
                quotes[i]= false;
            } else if ( u!=null && UnitsUtil.isNominalMeasurement(u) ) {
                unitsFormatter[i]= true;
                datumFormatter[i]= new EnumerationDatumFormatter();
                quotes[i]= true;
            } else {
                unitsFormatter[i]= false;
                final DecimalFormat format= NumberFormatUtil.getDecimalFormat( "0.###E00;-#");
                datumFormatter[i]= new DatumFormatter() {
                    @Override
                    public String format(Datum datum) {
                        return format.format( datum.doubleValue(datum.getUnits()) );
                    }
                };
                quotes[i]= false;
            }
        }
        if ( false ) {
            System.err.println("===");
            for ( int i=0; i<record.length(); i++ ) {
                System.err.println( String.format( "%4d %s", i,datumFormatter[i] ) );
            }
            System.err.println("===");
        }
        
    }

    @Override
    public void sendRecord(OutputStream out, QDataSet record) throws IOException {
        int n= record.length();
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            Datum fieldDatum;
            fieldDatum= DataSetUtil.asDatum(field);
            if ( quotes[i] ) out.write('"');
            if ( fieldDatum.isFill() ) {
                out.write( String.valueOf( field.value() ).getBytes() );
            } else {
                out.write( datumFormatter[i].format( fieldDatum ).getBytes() );
            }
            if ( quotes[i] ) out.write('"');
            if ( i<n-1 ) out.write(',');
        }
        out.write((byte)10);
    }

    @Override
    public void finalize(OutputStream out) {
        
    }
    
}
