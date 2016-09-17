/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.virbo.dataset.QDataSet;

/**
 * Comma Separated Value (CSV) formatter
 * @author jbf
 */
public class CsvDataFormatter implements DataFormatter {

    boolean[] unitsFormatter;
    
    @Override
    public void initialize(OutputStream out, QDataSet record) {
        unitsFormatter= new boolean[record.length()];
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            Units u= (Units)field.property(QDataSet.UNITS);
            unitsFormatter[i] = u!=null && ( UnitsUtil.isTimeLocation(u) || UnitsUtil.isNominalMeasurement(u) );
        }
    }

    @Override
    public void sendRecord(OutputStream out, QDataSet record) throws IOException {
        int n= record.length();
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            if ( unitsFormatter[i] ) {
                out.write( field.toString().getBytes() );
            } else {
                out.write( String.valueOf( field.value() ).getBytes() );
            }
            if ( i<n-1 ) out.write(',');
        }
        out.write((byte)10);
    }

    @Override
    public void finalize(OutputStream out) {
        
    }
    
}
