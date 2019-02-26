
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.EnumerationDatumFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.qds.DDataSet;
import org.das2.util.NumberFormatUtil;
import org.json.JSONObject;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SparseDataSetBuilder;
import org.das2.qds.WritableDataSet;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Comma Separated Value (CSV) formatter
 * @author jbf
 */
public class CsvDataFormatter implements DataFormatter {

    private static final Logger logger= Logger.getLogger("hapi.csv");
    
    boolean[] unitsFormatter;
    DatumFormatter[] datumFormatter;
    boolean[] quotes;
    Units[] units;
    
    @Override
    public void initialize( JSONObject info, OutputStream out, QDataSet record) {
        unitsFormatter= new boolean[record.length()];
        datumFormatter= new DatumFormatter[record.length()];
        quotes= new boolean[record.length()];
        units= new Units[record.length()];
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet field= record.slice(i);
            Units u= (Units)field.property(QDataSet.UNITS);
            units[i]= u;
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
    
    public QDataSet initializeReader( JSONObject info, String record ) {
        
        String[] ss= Util.csvSplit( record, -1 );
        
        DDataSet result;
        SparseDataSetBuilder sdsb= new SparseDataSetBuilder(2);
        sdsb.setLength(ss.length);
        
        result= DDataSet.createRank1(ss.length);
        try {
            JSONArray parameters= info.getJSONArray("parameters");
            for ( int iparameter=0; iparameter<parameters.length(); iparameter++ ) {
                JSONObject parameter= parameters.getJSONObject(iparameter);
                String t= parameter.getString("type");
                Units u;
                if ( t.equals("isotime") ) {
                    u= Units.us2000;
                } else {
                    u= Units.lookupUnits( parameter.getString("units") );
                }
                try {
                    result.putValue(iparameter,u.parse(ss[iparameter]).doubleValue(u));
                    result.putProperty(t, iparameter, u);
                } catch (ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                sdsb.putProperty( QDataSet.UNITS, iparameter, u );
            }
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        result.putProperty( QDataSet.BUNDLE_0, sdsb.getDataSet() );
        return result;
    }
    
    /**
     * read a record which has been formatted by this.
     * @param record the formatted record.
     * @param recordInfo example record which provides the units for parsing.
     * @return the parsed record.
     */
    public QDataSet readRecord( String record, QDataSet recordInfo ) {
        
        WritableDataSet result= DDataSet.copy(recordInfo);
        
        String[] ss= Util.csvSplit(record, -1);
                
        for ( int i=0; i<recordInfo.length(); i++ ) {
            Units u= (Units)recordInfo.slice(i).property(QDataSet.UNITS);
            try {
                result.putValue( i,u.parse(ss[i]).doubleValue(u) );
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        
        return result;
    }

    @Override
    public void finalize(OutputStream out) {
        
    }
    
    
    public static void main( String[] args ) throws JSONException {
        JSONObject info= new JSONObject("{\n" +
"    \"HAPI\": \"1.1\",\n" +
"    \"__infoVersion__\": \"1.0\",\n" +
"    \"cadence\": \"PT1M\",\n" +
"    \"createdAt\": \"2017-08-23T16:18Z\",\n" +
"    \"parameters\": [\n" +
"        {\n" +
"            \"fill\": null,\n" +
"            \"length\": 24,\n" +
"            \"name\": \"Time\",\n" +
"            \"type\": \"isotime\",\n" +
"            \"units\": \"UTC\"\n" +
"        },\n" +
"        {\n" +
"            \"description\": \"temperature in garage, car\",\n" +
"            \"fill\": \"-1e31\",\n" +
"            \"name\": \"Temperature\",\n" +
"            \"type\": \"double\",\n" +
"            \"units\": \"deg F\"\n" +
"        }\n" +
"    ],\n" +
"    \"sampleStartDate\": \"2017-08-22T00:00:00.000Z\",\n" +
"    \"sampleStopDate\": \"2017-08-23T00:00:00.000Z\",\n" +
"    \"startDate\": \"2012-01-09T00:00:00.000Z\",\n" +
"    \"status\": {\n" +
"        \"code\": 1200,\n" +
"        \"message\": \"OK request successful\"\n" +
"    },\n" +
"    \"stopDate\": \"2017-08-23T16:00:00.000Z\",\n" +
"    \"x_uri\": \"file:/home/jbf/public_html/1wire/data/$Y/$m/$d/0B000800408DD710.$Y$m$d.d2s\"\n" +
"}");
        CsvDataFormatter m= new CsvDataFormatter();
        QDataSet rec= m.initializeReader( info, "2017-08-22T22:22:03.000Z,7.318E01");
        QDataSet r1= m.readRecord( "2017-08-22T22:23:03.000Z,7.318E01", rec);
        System.err.println( r1.slice(0).toString() );
        System.err.println( r1.slice(1).toString() );
        
        r1= m.readRecord( "2017-08-22T23:23:03.000Z,7.428E01", rec);
        System.err.println( r1.slice(0).toString() );
        System.err.println( r1.slice(1).toString() );
        
    }
}
