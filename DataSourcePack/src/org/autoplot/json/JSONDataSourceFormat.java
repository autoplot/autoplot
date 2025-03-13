/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.autoplot.json;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.ascii.AsciiTableDataSourceFormat;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.autoplot.datasource.URISplit;
import org.das2.datum.Datum;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.DefaultDatumFormatter;
import org.das2.datum.format.FormatStringFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.datum.format.TimeDatumFormatterFactory;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;

/**
 * formats to JSONL format, soon other JSON formats.
 * @author jbf
 */
public class JSONDataSourceFormat extends AbstractDataSourceFormat {
    private static final Logger logger= LoggerManager.getLogger("apdss.ascii.csv");
     
    private DatumFormatter getTimeFormatter( ) {
        DatumFormatter timeFormatter;
        String tformat= getParam( "tformat", "ISO8601" );
        String ft= tformat.toLowerCase();
        String depend0Units= getParam( "depend0Units", "" );
        Units dep0units= null;
        
        if ( depend0Units.length()>0 ) {
            try {
                dep0units= Units.lookupTimeUnits(depend0Units);
            } catch (ParseException ex) {
                Logger.getLogger(AsciiTableDataSourceFormat.class.getName()).log(Level.SEVERE, null, ex);
            }

            final Units tu= dep0units;
            if ( ft.equals("iso8601") ) ft=null;
            final String sformat= ft;
            timeFormatter= new DefaultDatumFormatter() {
                @Override
                public String format(Datum datum) {
                    return format(datum, tu);
                }
                @Override
                public String format(Datum datum, Units units) {
                    if ( datum.isFill() ) {
                        return "fill";
                    } else {
                        if ( sformat!=null && sformat.startsWith("%") ) {
                            return String.format( sformat, datum.doubleValue(tu) );
                        } else {
                            return String.valueOf( datum.doubleValue(tu) );
                        }
                    }
                }
            };
        } else if (ft.equals("iso8601")) {
            timeFormatter = TimeDatumFormatterFactory.getInstance().defaultFormatter();
            
        } else if ( tformat.startsWith("%")
                || ft.startsWith("$") ) {
            if ( tformat.startsWith("$") ) { // provide convenient URI-friendly spec
                tformat= tformat.replaceAll("\\$", "%");
            }
            tformat= tformat.replaceAll("\\+",getParam("delim",","));
            try {
                timeFormatter = new TimeDatumFormatter(tformat);
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                try {
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S");
                } catch (ParseException ex1) {
                    throw new RuntimeException(ex1);
                }
            }
        } else {
            try {
                if (ft.equals("day")) {
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%d");
                } else if (ft.equals("hour")) {
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%dT%H:%MZ");
                } else if (ft.startsWith("min")) {
                    timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%MZ");
                } else if (ft.startsWith("sec")) {
                    timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%SZ");
                } else if (ft.startsWith("millisec")) {
                    final TimeParser tp= TimeParser.create("$Y-$m-$dT$H:$M:$S.$(subsec,places=3)");
                    timeFormatter= new DatumFormatter() {
                        @Override
                        public String format(Datum datum) {
                            return tp.format(datum);
                        }
                    };
                    //timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}Z");
                } else if (ft.startsWith("microsec")) {
                    final TimeParser tp= TimeParser.create("$Y-$m-$dT$H:$M:$S.$(subsec,places=6)");
                    timeFormatter= new DatumFormatter() {
                        @Override
                        public String format(Datum datum) {
                            return tp.format(datum);
                        }
                    };
                    //timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}%{micro}Z");
                } else if (ft.startsWith("nanosec")) {
                    final TimeParser tp= TimeParser.create("$Y-$m-$dT$H:$M:$S.$(subsec,places=9)");
                    timeFormatter= new DatumFormatter() {
                        @Override
                        public String format(Datum datum) {
                            return tp.format(datum);
                        }
                    };
                    //timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}%{micro}Z");
                } else {
                    logger.log(Level.FINE, "not implemented: {0}", ft);
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S");
                }

            } catch (ParseException ex) {
                logger.log( Level.SEVERE, ex.getMessage(), ex);
                timeFormatter = TimeDatumFormatterFactory.getInstance().defaultFormatter();
                
            }
        }
        return timeFormatter;
    }
    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        super.setUri(uri);
                
        URISplit split = URISplit.parse( uri );
        
        Map<String,String> params= URISplit.parseParams(split.params);
            
        super.maybeMkdirs();
        
        mon.setTaskSize( data.length() );
        mon.started();
        
        File outFile= new File( split.resourceUri );
        
        if ( data.rank()==1 ) {
            try {
                formatDataRank1( new PrintWriter( new FileWriter( outFile ) ), data, mon );
            } catch ( IOException ex ) {
                
            }
        }
    }

    public void formatDataRank1( PrintWriter writer, QDataSet data, ProgressMonitor mon) throws Exception {
        Units u= SemanticOps.getUnits(data);
        DatumFormatter df= getDataFormatter( data );
        QDataSet dep0= (QDataSet) data.property( QDataSet.DEPEND_0 );
        if ( dep0==null ) {
            for ( int i=0; i<data.length(); i++ ) {
                writer.write("[");
                writer.write(df.format(u.createDatum(data.value(i)),u));
                writer.println("]");
            }
        } else {
            DatumFormatter depf= getDataFormatter(dep0);
            Units depu= SemanticOps.getUnits(dep0);
            for ( int i=0; i<data.length(); i++ ) {
                writer.write("[");
                writer.write(depf.format(depu.createDatum(dep0.value(i)),depu));
                writer.write(",");
                writer.write(df.format(u.createDatum(data.value(i)),u));
                writer.println("]");
            }
        }
    }
    
    public void formatDataRank2( PrintWriter writer, QDataSet data, ProgressMonitor mon) throws Exception {
        Units u= SemanticOps.getUnits(data);
        DatumFormatter df= getDataFormatter( data );
        QDataSet dep0= (QDataSet) data.property( QDataSet.DEPEND_0 );
        if ( dep0==null ) {
            for ( int i=0; i<data.length(); i++ ) {
                writer.write("[");
                writer.write("[");
                for ( int j=0; j<data.length(0); j++ ) {
                    writer.write(df.format(u.createDatum(data.value(i)),u));
                }
                writer.write("]");
                writer.println("]");
            }
        } else {
            DatumFormatter depf= getDataFormatter(dep0);
            Units depu= SemanticOps.getUnits(dep0);
            for ( int i=0; i<data.length(); i++ ) {
                writer.write("[");
                writer.write(depf.format(depu.createDatum(dep0.value(i)),depu));
                writer.write(",");
                writer.write("[");
                for ( int j=0; j<data.length(0); j++ ) {
                    writer.write(df.format(u.createDatum(data.value(i)),u));
                }
                writer.write("]");
                writer.println("]");
            }
        }
    }
    
    private DatumFormatter getDataFormatter( QDataSet ds ) {
        String df= (String) ds.property(QDataSet.FORMAT);
        Units u= SemanticOps.getUnits(ds);
        if ( UnitsUtil.isTimeLocation(u) ) {
            return getTimeFormatter();
        }
        try {            
            if ( df==null ) {
                return u.getDatumFormatterFactory().defaultFormatter();
            }
            if ( !df.contains("%") ) df= "%"+df;
            return new FormatStringFormatter( df, false );
        } catch ( RuntimeException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex);
            return u.getDatumFormatterFactory().defaultFormatter();
        }
    }
        
    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1 || ds.rank()==2;
    }

    @Override
    public String getDescription() {
        return "Formats data to jsonl or json documents";
    }
    
}
