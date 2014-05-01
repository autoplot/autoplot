/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.html;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;

/**
 * Generic class for converting a table of ascii strings to datums.
 * @author jbf
 */
public class AsciiTableMaker {

    DataSetBuilder builder = null;
    QDataSet desc = null;  // bundle descriptor
    List<Units> units = null;
    List<String> labels = null;
    List<String> names = null;
    List<String> format= null;
    
    int fieldCount= -1;
    boolean initializedFields= false;
    
    private void setUnitsAndFormat( List<String> values ) {
        for (int i = 0; i < fieldCount; i++) {
            String field = values.get(i).trim();
            boolean isTime= false;
            try {
                if ( field.contains("T") ) { // allow ISO8601 times.
                    Units.us2000.parse(field);
                    isTime= true;
                }
            } catch (ParseException ex) {
                Logger.getLogger(AsciiTableMaker.class.getName()).log(Level.SEVERE, null, ex);
            }
            if ( field.contains("$") ) {
                units.set(i,Units.dollars);
                format.set(i,"%.2f");
            } else if ( field.endsWith("%") ) {
                units.set(i,Units.percent);
                format.set(i,null);
            } else if ( isTime ) {
                units.set(i,Units.us2000);
                format.set(i,null);
            } else {
                try {
                    Integer.parseInt(field);
                    units.set(i,Units.dimensionless);
                    format.set(i,"%d");
                } catch ( NumberFormatException ex ) {
                    try {
                        Double.parseDouble(field);
                        units.set(i,Units.dimensionless);
                        format.set(i,null);
                    } catch ( NumberFormatException ex2 ) {
                        units.set( i, new EnumerationUnits("default") );
                        format.set(i,null);
                    }
                }
            }
        }
    }
    
    void addRecord(List<String> values) {
        if ( fieldCount==-1 ) return;
        if ( initializedFields==false ) {
            setUnitsAndFormat(values);
            initializedFields= true;
        }
        for (int i = 0; i < fieldCount; i++) {
            String field = values.get(i).trim();
            if ( field.trim().length()==0 ) {
                builder.putValue(-1, i, builder.getFillValue() );
            } else {
                try {
                    Units u= units.get(i);
                    double d;
                    if ( u instanceof EnumerationUnits ) {
                        d= ((EnumerationUnits)u).createDatum(field).doubleValue(u);
                    } else {
                        d= u.parse( field ).doubleValue( u );
                    }
                    builder.putValue(-1, i, d);
                } catch (ParseException ex) {
                    builder.putValue(-1, i, builder.getFillValue() );
                }
            }
        }
        builder.nextRecord();
    }

    void initialize(List<String> values) {
        fieldCount = values.size();

        builder = new DataSetBuilder(2, 100, fieldCount);
        units = new ArrayList<Units>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            units.add(i, Units.dimensionless);
        }
        format= new ArrayList<String>(fieldCount);
        
        labels = new ArrayList<String>(fieldCount);
        names = new ArrayList<String>(fieldCount);
        if (labels.isEmpty()) {
            for (int i = 0; i < fieldCount; i++) {
                labels.add(i, values.get(i));
                names.add(i, Ops.safeName(values.get(i)));
                format.add("");
            }
        }

    }

    void addHeader(List<String> values) {
        if (fieldCount == -1) {
            initialize(values);
        }
    }

    void addUnits(List<String> units) {
    }

    void addUnits(int icol, String units) {
    }

    private QDataSet getBundleDescriptor() {
        return new AbstractDataSet() {
            @Override
            public int rank() {
                return 2;
            }

            @Override
            public Object property(String name, int i) {
                if ( name.equals(QDataSet.LABEL ) ) {
                    return labels.get(i);
                } else if ( name.equals(QDataSet.NAME ) ) {
                    return names.get(i);
                } else if ( name.equals(QDataSet.FORMAT ) ) {
                    return format.get(i);
                } else if ( name.equals(QDataSet.UNITS ) ) {
                    return units.get(i);
                }
                return property(name);
            }

            @Override
            public double value(int i0, int i1) {
                return 0;
            }

            @Override
            public int length() {
                return labels.size();
            }

            @Override
            public int length(int i) {
                return 0;
            }
        };
    }

    DDataSet getDataSet() {
        if ( builder==null ) {
            throw new IllegalArgumentException("no records found");
        }
        DDataSet result = builder.getDataSet();
        desc= getBundleDescriptor();
        result.putProperty(QDataSet.BUNDLE_1, desc);
        return result;
    }

}
