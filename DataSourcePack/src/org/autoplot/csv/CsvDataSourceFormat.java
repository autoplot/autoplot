
package org.autoplot.csv;

import com.csvreader.CsvWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.ascii.AsciiTableDataSourceFormat;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.format.DatumFormatter;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DefaultDatumFormatter;
import org.das2.datum.format.FormatStringFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.datum.format.TimeDatumFormatterFactory;
import org.das2.qds.ops.Ops;
import org.das2.util.LoggerManager;

/**
 * Format data to CSV (comma separated values) file.
 * @author jbf
 */
public class CsvDataSourceFormat extends AbstractDataSourceFormat {

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
        
    private DatumFormatter getDataFormatter( String df, Units u ) {
        try {
            if ( !df.contains("%") ) df= "%"+df;
            //TODO: would be nice if we could verify formatter.  I had %f5.2 instead of %5.2f and it wasn't telling me.
            return new FormatStringFormatter( df, false );
        } catch ( RuntimeException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex);
            return u.getDatumFormatterFactory().defaultFormatter();
        }
    }
    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        
        super.setUri(uri);
                
        URISplit split = URISplit.parse( uri );
        
        Map<String,String> params= URISplit.parseParams(split.params);
        
        char delim= ',';
        if ( params.containsKey("delim") ) {
            String sdelimiter= params.get("delim");
            if ( sdelimiter.equals("COMMA") ) sdelimiter= ",";
            if ( sdelimiter.equals("SEMICOLON") ) sdelimiter= ";";
            delim= sdelimiter.charAt(0);
        }
        
        super.maybeMkdirs();
        
        mon.setTaskSize( data.length() );
        mon.started();
        
        File outFile= new File( split.resourceUri );
        FileWriter fw= new FileWriter(outFile);
        CsvWriter writer= null;
        try {
            writer= new CsvWriter( fw, delim );
            
            writer.setForceQualifier(true);
            writer.setUseTextQualifier(true);  // force quotes on header

            String[] values;
            String[] labels;

            int col=0;

            QDataSet[] dss;
            QDataSet[] wdss;

            List<QDataSet> ldss= new ArrayList();
            List<QDataSet> lwdss= new ArrayList();
            if ( data.property(QDataSet.DEPEND_0)!=null ) {
                ldss.add( (QDataSet) data.property(QDataSet.DEPEND_0));
                lwdss.add( DataSetUtil.weightsDataSet((QDataSet) data.property(QDataSet.DEPEND_0) ) );
                col++;
            }
            switch (data.rank()) {
                case 1:
                    ldss.add(data);
                    lwdss.add(DataSetUtil.weightsDataSet(data));
                    col++;
                    break;
                case 2:
                    if ( SemanticOps.isBundle(data) ) {
                        for ( int k=0; k<data.length(0); k++ ) {
                            QDataSet d1= Ops.unbundle(data, k);
                            ldss.add( d1 );
                            lwdss.add(DataSetUtil.weightsDataSet(d1));
                        }
                    } else {
                        ldss.add(data); // spectrogram
                        lwdss.add(DataSetUtil.weightsDataSet(data));
                    }
                    col+= data.length(0);
                    break;
                default:
                    throw new IllegalArgumentException("rank limit, data must be rank 1 sequence or a rank 2 table of data");
            }
            dss= ldss.toArray( new QDataSet[ldss.size()] );
            wdss= lwdss.toArray( new QDataSet[lwdss.size()] );
            values= new String[col];
            labels= new String[col];

            //set the headers
            {
                col= 0;
                for ( int ids=0; ids<dss.length; ids++ ) {
                    String name= (String)dss[ids].property(QDataSet.LABEL);
                    if ( name==null ) {
                        name=  (String)dss[ids].property(QDataSet.NAME);
                    }
                    if ( name==null ) {
                        name= "data"+ids;
                    }
                    String sunits;
                    Units units= SemanticOps.getUnits(dss[ids]);
                    if ( UnitsUtil.isTimeLocation(units) ) {
                        sunits= "UTC";
                    } else if ( units.isConvertibleTo(Units.dimensionless) ) {
                        sunits= null;
                    } else {
                        sunits= String.valueOf(units);
                    }
                    if ( dss[ids].rank()==1 ) {
                        labels[col++]= sunits==null ? name : name+" ("+sunits+")";
                    } else {
                        QDataSet dep1= (QDataSet) dss[ids].property(QDataSet.DEPEND_1);
                        if (dep1!=null && dep1.rank()==1) {
                            Units dep1units= SemanticOps.getUnits(dep1);
                            for ( int j=0;j<dss[ids].length(0); j++ ) {
                                labels[col++]= dep1units.format( Datum.create( dep1.value(j), dep1units ) );
                            }
                        } else {
                            for ( int j=0;j<dss[ids].length(0); j++ ) {
                                labels[col++]= name+" " +j+" ("+sunits+")";
                            }
                        }
                    }
                }
            }

            writer.writeRecord(labels);

            writer.setForceQualifier(false);
            writer.setUseTextQualifier(true);

            QDataSet bundleDesc= (QDataSet) data.property(QDataSet.BUNDLE_1);
            
            DatumFormatter tf= getTimeFormatter( );
            
            String df= getParam( "format", "" );
            
            DatumFormatter[] formats= new DatumFormatter[dss.length];
            for ( int jj=0; jj<dss.length; jj++ ) {
                QDataSet dssjj=dss[jj];
                Units u= SemanticOps.getUnits(dssjj);
                Units uu_jj=u;
                formats[jj]= u.getDatumFormatterFactory().defaultFormatter();
                if ( !( uu_jj instanceof EnumerationUnits ) ) {
                    String ff= bundleDesc!=null ? (String) dssjj.property(QDataSet.FORMAT) : null;
                    if ( ff==null && bundleDesc!=null ) {
                        if ( bundleDesc.length()==dss.length-1 && jj>0 ) {
                            ff= (String) bundleDesc.property(QDataSet.FORMAT,jj-1);
                        } else if ( bundleDesc.length()==dss.length  ) {
                            ff= (String) bundleDesc.property(QDataSet.FORMAT,jj);
                        }
                    }
                    if ( df.equals("") ) {
                        if ( ff==null ) {
                            double d1= dssjj.rank()==1 ? dssjj.value(0) : dssjj.value(0,0);
                            formats[jj]= uu_jj.createDatum(d1).getFormatter();
                        } else {
                            formats[jj]= getDataFormatter( ff, uu_jj );
                        }
                    } else {
                        if ( UnitsUtil.isTimeLocation( uu_jj ) ) {
                            formats[jj]= tf;
                        } else {
                            if ( ff==null ) {
                                formats[jj]= getDataFormatter( df, uu_jj );
                            } else {
                                formats[jj]= getDataFormatter( ff, uu_jj ); //TODO: what is user wants to override format? 
                            }
                        }
                    }
                } else {
                    formats[jj]= uu_jj.createDatum( dssjj.rank()==1 ? dssjj.value(0) : dssjj.value(0,0) ).getFormatter();
                }                
            }

            for ( int i=0; i<data.length(); i++ ) {
                mon.setTaskProgress(i);
                col= 0;
                for ( int ids=0; ids<dss.length; ids++ ) {
                    Units u= SemanticOps.getUnits(dss[ids]);
                    if ( dss[ids].rank()==1 ) {
                        if ( wdss[ids].value(i)==0 ) {
                            values[col++]= "NaN";
                        } else {
                            values[col++]= formats[ids].format( u.createDatum( dss[ids].value(i) ), u );
                        }
                    } else {
                        for ( int j=0;j<dss[ids].length(0); j++ ) {
                            if ( wdss[ids].value(i,j)==0 ) {
                                values[col++]= "NaN";
                            } else {
                                values[col++]= formats[ids].format( u.createDatum( dss[ids].value(i,j) ), u );
                            }
                        }
                    }
                }
                writer.writeRecord(values);
            }
        } finally {
            if ( writer!=null ) writer.close();
            fw.close();
            mon.finished();
        }
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1 || ds.rank()==2;
    }

    @Override
    public String getDescription() {
        return "Comma Separated Values";
    }
}
