
package org.autoplot.html;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.AsciiParser;
import org.das2.qds.util.AsciiParser.FieldParser;
import org.das2.qds.util.DataSetBuilder;

/**
 * Generic class for converting a table of ASCII strings to datums.
 * @author jbf
 */
public class AsciiTableMaker {

    DataSetBuilder builder = null;
    QDataSet desc = null;  // bundle descriptor
    List<Units> units = null;
    Units defaultUnits= null;
    List<String> labels = null;
    List<String> names = null;
    List<String> format= null;
    List<AsciiParser.FieldParser> fieldParsers= null;
    
    int fieldCount= -1;
    boolean initializedFields= false;
    
    void setUnits(String units) {
        this.defaultUnits= Units.lookupUnits(units);
    }
    
    private void setUnitsAndFormat( List<String> values ) {
        for (int i = 0; i < fieldCount; i++) {
            String field = values.get(i).trim();
            boolean isTime= false;
            try {
                if ( TimeParser.isIso8601String(field) ) { // allow ISO8601 times.
                    Units.cdfTT2000.parse(field);
                    isTime= true;
                } else if ( field.matches("\\d+/\\d+/\\d+") ) {
                    Units.cdfTT2000.parse(field);
                    isTime= true;
                }
            } catch (ParseException ex) {
                Logger.getLogger(AsciiTableMaker.class.getName()).log(Level.SEVERE, null, ex);
            }
            if ( units.get(i)==null ) {
                if ( isTime ) {
                    units.set(i,Units.us2000);
                    format.set(i,null);
                } else {
                    if ( field.contains("$") ) {
                        units.set(i,Units.dollars);
                        format.set(i,"%.2f");
                    } else if ( field.endsWith("%") ) {
                        units.set(i,Units.percent);
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
                                String[] ss= field.split("\\s",-2);  // "3.4 sec"
                                if ( ss.length>1 ) {
                                    try {
                                        Double.parseDouble(ss[0]);
                                        units.set(i,Units.lookupUnits( field.substring( ss[0].length() ).trim() ) );
                                        format.set(i,null);
                                    } catch ( NumberFormatException ex3 ) {
                                        units.set( i, new EnumerationUnits("default") );
                                        format.set(i,null);
                                    }
                                } else {
                                    if ( field.contains(",") && !field.endsWith(",") ) {
                                        try { 
                                            double d= Double.parseDouble(field.replace(",","" ) );
                                            this.fieldParsers.set( i, getCommaFieldParser(Units.dimensionless) );
                                        } catch ( NumberFormatException ex4 ) {
                                            units.set( i, new EnumerationUnits("default") );
                                            format.set(i,null);
                                        }
                                    } else {
                                        units.set( i, new EnumerationUnits("default") );
                                        format.set(i,null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * FieldParser removes the comma to parse things like "1,234" to 1234.
     * @param uu
     * @return 
     */
    FieldParser getCommaFieldParser( final Units uu ) {
        return new FieldParser() {
            @Override
            public double parseField(String field, int columnIndex) throws ParseException {
                return uu.parse( field.replaceAll(",","") ).doubleValue(uu); // sorry, rest of world
            }
        };
    }

    void addRecord(List<String> values) {
        if ( fieldCount==-1 ) {
            return;
        }
        if ( initializedFields==false ) {
            setUnitsAndFormat(values);
            initializedFields= true;
        }
        for (int i = 0; i < fieldCount; i++) {
            String field = values.get(i).trim();
            Units u= units.get(i);
            if ( field.trim().length()==0 ) {
                if ( u instanceof EnumerationUnits ) {
                    double d= ((EnumerationUnits)u).createDatum(field).doubleValue(u);
                    builder.putValue(-1, i, d);
                } else {
                    builder.putValue(-1, i, builder.getFillValue() );
                }
            } else {
                try {        
                    double d;
                    if ( u instanceof EnumerationUnits ) {
                        d= ((EnumerationUnits)u).createDatum(field).doubleValue(u);
                    } else {
                        FieldParser p= fieldParsers.get(i);
                        if ( p!=null ) {
                            d= p.parseField(field, i);
                        } else {
                            try {
                                d= u.parse( field ).doubleValue( u );
                            } catch (ParseException ex ) {
                                final Units uu= u;
                                p= new FieldParser() {
                                    @Override
                                    public double parseField(String field, int columnIndex) throws ParseException {
                                        return uu.parse( field.replaceAll(",","") ).doubleValue(uu); // sorry, rest of world
                                    }
                                };
                                fieldParsers.set( i, p );
                                d= p.parseField(field, i);
                            }
                        }
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
        units = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            units.add(i, defaultUnits ); // null here means we can reset.
        }
        format= new ArrayList<>(fieldCount);
        fieldParsers= new ArrayList<>(fieldCount);
        
        labels = new ArrayList<>(fieldCount);
        names = new ArrayList<>(fieldCount);
        if (labels.isEmpty()) {
            for (int i = 0; i < fieldCount; i++) {
                labels.add(i, values.get(i));
                names.add(i, Ops.safeName(values.get(i)));
                format.add("");
                fieldParsers.add(null);
            }
        }

    }

    /**
     * return true if the header has been set.
     * @return true if the header has been set.
     */
    public boolean hasHeader() {
        return this.fieldCount>-1;
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
