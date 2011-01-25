/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.html;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    EnumerationUnits enumeration = EnumerationUnits.create("html");
    NumberFormat nf = DecimalFormat.getInstance(Locale.US); // sorry, rest of world.
    int fieldCount= -1;

    void addRecord(List<String> values) {
        if ( fieldCount==-1 ) return;
        for (int i = 0; i < fieldCount; i++) {
            String field = values.get(i);
            if ( field.trim().length()==0 ) {
                builder.putValue(-1, i, builder.getFillValue() );
            } else {
                try {
                    double d = nf.parse(field).doubleValue();
                    builder.putValue(-1, i, d);
                } catch (ParseException ex) {
                    try {
                        Date d = SimpleDateFormat.getDateInstance().parse(field);
                        builder.putValue(-1, i, d.getTime());
                        units.set(i, Units.t1970);
                    } catch (ParseException ex2) {
                        builder.putValue(-1, i, enumeration.createDatum(field).doubleValue(enumeration));
                        units.set(i, enumeration);
                    }
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
        labels = new ArrayList<String>(fieldCount);
        names = new ArrayList<String>(fieldCount);
        if (labels.size() == 0) {
            for (int i = 0; i < fieldCount; i++) {
                labels.add(i, values.get(i));
                names.add(i, Ops.safeName(values.get(i)));
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
