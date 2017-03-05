
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
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
    
    @Override
    public void initialize( JSONObject info, OutputStream out, QDataSet record) {
        unitsFormatter= new boolean[record.length()];
        datumFormatter= new DatumFormatter[record.length()];
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            Units u= (Units)field.property(QDataSet.UNITS);
            if (  u!=null && ( UnitsUtil.isTimeLocation(u) || UnitsUtil.isNominalMeasurement(u) ) ) {
                unitsFormatter[i]= true;
                datumFormatter[i]= TimeDatumFormatter.DEFAULT;
            } else {
                unitsFormatter[i]= false;
                final DecimalFormat format= NumberFormatUtil.getDecimalFormat( "0.###E00;-#");
                datumFormatter[i]= new DatumFormatter() {
                    @Override
                    public String format(Datum datum) {
                        return format.format( datum.value() );
                    }
                };
            }
        }
    }

    @Override
    public void sendRecord(OutputStream out, QDataSet record) throws IOException {
        int n= record.length();
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            Datum fieldDatum= DataSetUtil.asDatum(field);
            if ( fieldDatum.isFill() ) {
                out.write( String.valueOf( field.value() ).getBytes() );
            } else {
                out.write( datumFormatter[i].format( fieldDatum ).getBytes() );
            }
            if ( i<n-1 ) out.write(',');
        }
        out.write((byte)10);
    }

    @Override
    public void finalize(OutputStream out) {
        
    }
    
}
