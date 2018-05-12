
package org.autoplot.html;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.util.LoggerManager;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;

/**
 * Generic class for converting a table of ASCII strings to QDataSet stream.
 * This supports streaming sources by reporting QDataSet records as they arrive.
 * @author jbf
 */
public class AsciiTableStreamer implements Iterator<QDataSet> {

    private static final Logger logger= LoggerManager.getLogger("apdss.html");
            
    QDataSet desc = null;  // bundle descriptor
    List<Units> units = null;
    Units defaultUnits= null;
    List<String> labels = null;
    List<String> names = null;
    List<String> format= null;
    List<Double> fillValues= null;
    
    List<QDataSet> records;
    QDataSet recordDescriptor;
    
    int fieldCount= -1;
    boolean initializedFields= false;
    
    /**
     * true indicates that there may be more records, while false asserts no more records.
     */
    boolean hasNextRecord= true;
    
    public AsciiTableStreamer() {
        records= Collections.synchronizedList( new LinkedList<QDataSet>() );
    }
    
    protected void setUnits(String units) {
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
                Logger.getLogger(AsciiTableStreamer.class.getName()).log(Level.SEVERE, null, ex);
            }
            if ( units.get(i)==null ) {
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
                                units.set( i, new EnumerationUnits("default") );
                                format.set(i,null);
                            }
                        }
                    }
                }
            }
        }
    }
    
    public void addRecord(List<String> values) {
        if ( units==null ) {
            String s= values.get(Math.min(1,values.size()-1)).trim();
            if ( s.length()>0 && Character.isAlphabetic(s.charAt(0)) ) {
                addHeader(values);
                return;
            }
        }
        if ( fieldCount==-1 ) {
            return;
        }
        if ( initializedFields==false ) {
            setUnitsAndFormat(values);
            initializedFields= true;
        }
        
        DDataSet result= DDataSet.createRank1(fieldCount);
        
        for (int i = 0; i < fieldCount; i++) {
            String field = values.get(i).trim();
            if ( field.trim().length()==0 ) {
                result.putValue( i, fillValues.get(i) );
            } else {
                try {
                    Units u= units.get(i);
                    double d;
                    if ( u instanceof EnumerationUnits ) {
                        d= ((EnumerationUnits)u).createDatum(field).doubleValue(u);
                    } else {
                        d= u.parse( field ).doubleValue( u );
                    }
                    result.putValue( i, d);
                } catch (ParseException ex) {
                    result.putValue( i, fillValues.get(i) );
                }
            }
        }
        if ( desc==null ) {
            desc=  getBundleDescriptor();
        }
        
        sendRecord( result );
    }

    /**
     * indicate that no more records will be added.  This can only be set to 
     * false.
     * @param t this must be set to false.
     */
    public void setHasNext( boolean t ) {
        hasNextRecord= false;
    }
    
    private void sendRecord( QDataSet result ) {
        records.add(result);
    }
    
    
    
    protected void initialize(List<String> values) {
        fieldCount = values.size();

        units = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            units.add(i, defaultUnits ); // null here means we can reset.
        }
        format= new ArrayList<>(fieldCount);
        labels = new ArrayList<>(fieldCount);
        names = new ArrayList<>(fieldCount);
        fillValues= new ArrayList<>(fieldCount);
        if (labels.isEmpty()) {
            for (int i = 0; i < fieldCount; i++) {
                labels.add(i, values.get(i));
                names.add(i, Ops.safeName(values.get(i)));
                format.add("");
                fillValues.add(-1e38);
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
    
    protected void addHeader(List<String> values) {
        if (fieldCount == -1) {
            initialize(values);
        }
    }

    protected void addUnits(List<String> units) {
    }

    protected void addUnits(int icol, String units) {
    }

    private QDataSet getBundleDescriptor() {
        return new AbstractDataSet() {
            @Override
            public int rank() {
                return 2;
            }

            @Override
            public Object property(String name, int i) {
                switch (name) {
                    case QDataSet.LABEL:
                        return labels.get(i);
                    case QDataSet.NAME:
                        return names.get(i);
                    case QDataSet.FORMAT:
                        return format.get(i);
                    case QDataSet.UNITS:
                        return units.get(i);
                    default:
                        break;
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

    @Override
    public boolean hasNext() {
        if ( !records.isEmpty() ) return true;
        while ( !initializedFields && hasNextRecord ) {
            Thread.yield();
        }
        while ( records.isEmpty() && hasNextRecord ) {
            Thread.yield();
        }
        return hasNextRecord;
    }

    @Override
    public QDataSet next() {
        while ( records.isEmpty() ) {
            Thread.yield();
        }
        QDataSet result= records.remove(0);
        //result.putProperty(QDataSet.BUNDLE_1, desc);
        return result;
    }

    @Override
    public void remove() {
        
    }

}
