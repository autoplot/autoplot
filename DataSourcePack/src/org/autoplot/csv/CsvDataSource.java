
package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.SparseDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.capability.Streaming;
import org.autoplot.html.AsciiTableStreamer;
import org.das2.datum.Datum;
import org.das2.datum.DatumUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.AsciiParser;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * Specialized reader only reads csv files.  These csv files must be simple tables with the same number of fields in each record.
 * @author jbf
 */
public class CsvDataSource extends AbstractDataSource {
    private static final Logger logger= LoggerManager.getLogger("apdss.csv");
    
    /**
     * initializer
     * @param uri the URI
     */
    public CsvDataSource(URI uri) {
        super(uri);
        addCapability( Streaming.class, new CsvTableStreamingSource() );
    }

    private QDataSet parseHeader( int icol, String header, String sval ) {
        header= header.trim();
        DDataSet result= DDataSet.create( new int[0] ); // rank 0 dataset

        Units u= guessUnits(sval);
        if ( u!=Units.dimensionless ) result.putProperty( QDataSet.UNITS, u );
        if ( UnitsUtil.isTimeLocation(u) ) result.putProperty( QDataSet.NAME, "UTC" );
        if ( header.length()==0 ) {
            try {
                result.putValue(u.parse(sval).doubleValue(u));
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            return result;
        } else {
            Pattern p= Pattern.compile( "([a-zA-Z0-9\\-\\+ ]*)(\\(([a-zA-Z-0-9\\-\\+ ]*)\\))?");
            Matcher m= p.matcher(header);
            if ( m.matches() ) {
                String label= m.group(1).trim();
                String sunits= m.group(3);
                if ( header.length()>0 ) result.putProperty( QDataSet.NAME, Ops.safeName(label) );
                result.putProperty(QDataSet.LABEL,label);
                if ( sunits!=null ) result.putProperty(QDataSet.UNITS, SemanticOps.lookupUnits(sunits.trim()) );
            }
            return result;
        }
    }

    private static Units guessUnits( String sval ) {
        try {
            Units.dimensionless.parse(sval);
            return Units.dimensionless;
        } catch ( ParseException ex ) {
            logger.log(Level.FINER, "fails to parse as number: {0}", sval);
        }
        try {
            AsciiParser.UNIT_UTC.parse(sval);
            return AsciiParser.UNIT_UTC;
        } catch ( ParseException ex ) {
            logger.log(Level.FINER, "fails to parse as time: {0}", sval);
        }
        return EnumerationUnits.create("enum");
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        InputStream in = DataSetURI.getInputStream(uri, mon);
        
        //char delimiter= TableOps.getDelim(thein);
        
        String sdelimiter= getParam("delim", ",");
        if ( sdelimiter.equals("COMMA") ) sdelimiter= ",";
        if ( sdelimiter.equals("SEMICOLON") ) sdelimiter= ";";
        
        char delimiter= sdelimiter.charAt(0);
        
        BufferedReader breader= new BufferedReader( new InputStreamReader(in) );        
            
        String skip= getParam( "skipLines", "" );
        if ( skip.length()==0 ) skip= getParam( "skip", "" );
        if ( skip.length()>0 ) {
            int iskip= Integer.parseInt(skip);  // TODO: getIntegerParam( "skip", -1, "min=0,max=100" );
            for ( int i=0; i<iskip; i++ ) {
                breader.readLine();
            }
        }
        
        String recCount= getParam( "recCount", "" );
        int irecCount= recCount.length()==0 ? Integer.MAX_VALUE : Integer.parseInt(recCount);

        String recStart= getParam( "recStart", "" );
        int irecStart= recStart.length()==0 ? Integer.MAX_VALUE : Integer.parseInt(recStart);
        
        if ( irecStart>0 && irecCount<(Integer.MAX_VALUE-irecStart) ) {
            irecCount+= irecStart;
        }
        
        CsvReader reader= new CsvReader( breader );
        if ( delimiter!=',' ) reader.setDelimiter(delimiter);
        
        String[] columnHeaders;

        columnHeaders= CsvDataSourceFactory.getColumnHeaders(reader,true);
        
        String column= getParam( "column", null );
        /**
         * icolumn is the column we are reading, or -1 when reading multiple columns.
         */
        int icolumn;
        if ( column==null ) {
            icolumn= -1;
        } else {
            icolumn= TableOps.columnIndex( column, columnHeaders );
            if ( icolumn==-1 ) {
                throw new IllegalArgumentException("column not found: "+column);
            }
        }
        QDataSet icolumnDs=null; // metadata for column

        String bundle= getParam( "bundle", null );
        int[] cols;
        if ( bundle==null ) {
            cols= null;
        } else {
            cols= TableOps.parseRangeStr( bundle, columnHeaders );
            icolumn= cols[0]; // get the units from this column
        }

        String time= getParam("time",null);  // time or depend0 can be used.
        String dep0column= getParam( "depend0", time );
        
        int idep0column;
        if ( dep0column==null ) {
            idep0column= -1;
        } else {
            idep0column= TableOps.columnIndex( dep0column, columnHeaders );
            if ( idep0column==-1 ) {
                throw new IllegalArgumentException("column not found: "+dep0column);
            }
        }
        QDataSet dep0ds= null;

        Units dep0u= Units.dimensionless;
        Units u= Units.dimensionless;
        Units[] columnUnits= null;

        double tb=0, cb=0;  // temporary holders for data
        double[] bundleb=null; // temporary holders for each column.
        if ( cols!=null ) {
            bundleb= new double[ cols[1]-cols[0] ];
        }

        DataSetBuilder builder;
        if ( bundleb!=null ) {
            builder= new DataSetBuilder( 2, 100, bundleb.length );
        } else {
            builder= new DataSetBuilder( 1, 100 );
        }
        DataSetBuilder tbuilder= new DataSetBuilder( 1, 100 );

        mon.setTaskSize(-1);
        mon.started();

        int line=0;
        double fill= 0;

        boolean needToCheckHeader= true; // check to see if the first line of data was mistaken for a header
        
        while ( reader.readRecord() ) {
            line++;
            mon.setProgressMessage("read line "+line);
            if ( columnUnits==null ) {
                boolean foundColumnNumbers= false;
                columnUnits= new Units[reader.getColumnCount()];
                for ( int j=0; j<reader.getColumnCount(); j++ ) {
                    columnUnits[j]= guessUnits(reader.get(j));
                    reader.get(0);
                    if ( !( columnUnits[j] instanceof EnumerationUnits ) ) {
                        foundColumnNumbers= true;
                    }
                }
                if ( !foundColumnNumbers ) {
                    logger.log(Level.FINER, "line appears to be a header: {0}", line);
                    columnUnits= null;
                }
                if ( icolumn==-1 ) {
                    if ( TimeUtil.isValidTime(reader.get(0)) && TimeUtil.isValidTime(reader.get(1) ) && reader.getColumnCount()>=2 && reader.getColumnCount()<=5 ) {
                        builder= new DataSetBuilder( 2, 100, reader.getColumnCount() );
                        u= AsciiParser.UNIT_UTC;
                        bundleb= new double[reader.getColumnCount()];
                        icolumn= 0;
                    } else {
                        icolumn= reader.getColumnCount()-1;
                    }
                }
                if ( idep0column==-1 && reader.getColumnCount()==2 ) {
                    idep0column= 0;
                }
                Units oldDep0u= dep0u;
                Units oldU= u;

                if ( idep0column>=0 && !(dep0u instanceof TimeLocationUnits) ) {
                    dep0ds= parseHeader( idep0column, reader.getHeader(idep0column),reader.get(idep0column) );
                    dep0u= SemanticOps.getUnits(dep0ds);
                }
                if ( !( u instanceof TimeLocationUnits ) && bundleb==null ) {
                    icolumnDs= parseHeader( icolumn, reader.getHeader(icolumn),reader.get(icolumn) );
                    u= SemanticOps.getUnits(icolumnDs);
                }

                if ( columnUnits!=null ) {
                    if ( oldDep0u != dep0u || oldU!=u ) {
                        if ( bundleb!=null ) {
                            builder= new DataSetBuilder( 2, 100, bundleb.length );
                        } else {
                            builder= new DataSetBuilder( 1, 100 );
                        }
                        tbuilder= new DataSetBuilder( 1, 100 );
                    }
                }
            }

            String badTimeTag= null;
            if ( columnUnits!=null ) try {
                if ( idep0column>=0 ) {
                    if ( dep0u instanceof EnumerationUnits ) {
                        tb= ((EnumerationUnits)dep0u).createDatum( reader.get(idep0column) ).doubleValue(dep0u) ;
                    } else {
                        tb= dep0u.parse(reader.get(idep0column)).doubleValue(dep0u);
                    }
                }
                if ( bundleb!=null ) {
                    int validCount= 0;
                    for ( int j=0; j<bundleb.length; j++ ) {
                        Units u1= columnUnits[icolumn+j];
                        if ( u1 instanceof EnumerationUnits ) {
                            bundleb[j]= ((EnumerationUnits)u1).createDatum( reader.get(icolumn+j) ).doubleValue(u1);
                        } else {
                            try {
                                bundleb[j]= u1.parse(reader.get(icolumn+j)).doubleValue(u1);
                                validCount++;
                            } catch ( ParseException ex ) {
                                if ( UnitsUtil.isTimeLocation(u1) ) {
                                    badTimeTag= reader.get(icolumn+j);
                                    // we will drop the entire record or throw an exception.
                                    fill= -1e38;
                                    bundleb[j]= fill;
                                } else {
                                    fill= -1e38;
                                    bundleb[j]= fill;
                                }
                            }
                        }
                    }
                    if ( badTimeTag!=null ) {
                        String msg= "failed to parse timetag at line "+line+": "+badTimeTag;
                        if ( validCount==0 ) {
                            badTimeTag=null;
                        }
                        throw new ParseException(msg,0);
                    }
                } else {
                    if ( u instanceof EnumerationUnits ) {
                        cb= ((EnumerationUnits)u).createDatum( reader.get(icolumn) ).doubleValue(u);
                    } else {
                        cb= u.parse(reader.get(icolumn)).doubleValue(u);
                    }
                }


            } catch ( ParseException ex ) {
                if ( badTimeTag!=null ) {
                    throw ex;
                } else {
                    logger.log(Level.FINE, "skipping line: {0}", reader.getRawRecord());
                    continue;
                }
            }

            if ( needToCheckHeader && columnUnits!=null ) {
                boolean yepItsData= true;
                double[] cbs= new double[columnUnits.length];
                for ( int icol= 0; icol<columnUnits.length; icol++ ) {
                    try {
                        if ( icol==0 ) { 
                            if ( columnHeaders[icol].length()>1 && ((int)columnHeaders[icol].charAt(0))==0xFEFF ) { //Excel UTF non-space
                                columnHeaders[icol]= columnHeaders[icol].substring(1);
                            }
                        }
                        Units u1= columnUnits[icol];
                        if ( columnHeaders.length<=icol ) {
                            yepItsData= false;
                            continue;
                        }
                        if ( u1 instanceof EnumerationUnits ) {
                            cbs[icol]= ((EnumerationUnits)u1).createDatum( columnHeaders[icol] ).doubleValue(u1);
                        } else {
                            try {
                                cbs[icol]= u1.parse(columnHeaders[icol]).doubleValue(u1);
                            } catch ( InconvertibleUnitsException ex ) {
                                Datum d= DatumUtil.parse(columnHeaders[icol]);
                                cbs[icol]= d.doubleValue(d.getUnits());
                                if ( yepItsData && !UnitsUtil.isNominalMeasurement(d.getUnits()) ) columnUnits[icol]= d.getUnits();
                            }
                        }
                    } catch ( ParseException ex ) {
                        yepItsData= false;
                    }
                }
                
                if ( yepItsData && builder.getLength()<irecCount ) {
                    if ( idep0column>=0 ) {
                        tbuilder.putValue( -1, cbs[idep0column] );
                        tbuilder.nextRecord();
                    }
                    if ( bundleb!=null ) {
                        for ( int j=0; j<bundleb.length; j++ ) {
                            builder.putValue(-1,j,cbs[icolumn+j] );
                        }
                        builder.nextRecord();
                    } else {
                        builder.putValue(-1,cbs[icolumn] );
                        builder.nextRecord();
                    }
                    for ( int icol=0; icol<columnUnits.length; icol++ ) { 
                        columnHeaders[icol]= "field"+icol;
                    }
                }
                needToCheckHeader= false;
            }
                
            if ( columnUnits!=null && builder.getLength()<irecCount ) {
                if ( idep0column>=0 ) {
                    tbuilder.putValue( -1, tb );
                    tbuilder.nextRecord();
                }
                if ( bundleb!=null ) {
                    for ( int j=0; j<bundleb.length; j++ ) {
                        builder.putValue(-1,j,bundleb[j]);
                    }
                    builder.nextRecord();
                } else {
                    builder.putValue(-1,cb);
                    builder.nextRecord();
                }
            }

        }

        reader.close();
        
        mon.finished();

        if ( line==0 ) {
            throw new NoDataInIntervalException("file contains no data: "+uri);
        }
        
        DDataSet ds= builder.getDataSet();
        
        if ( irecStart>0 ) { // TODO: skip records, so that memory isn't consumed.
            ds= (DDataSet)ds.trim(irecStart,ds.length());
        }
        
        if ( idep0column>=0 && dep0ds!=null ) {
            DDataSet tds= tbuilder.getDataSet();
            tds.putProperty(QDataSet.UNITS,dep0u);
            tds.putProperty(QDataSet.NAME, dep0ds.property(QDataSet.NAME));
            tds.putProperty(QDataSet.LABEL,dep0ds.property(QDataSet.LABEL));
            ds.putProperty(QDataSet.DEPEND_0, tds);
        }
        if ( bundleb!=null ) {
            SparseDataSet bds= SparseDataSet.createRankLen( 2, bundleb.length );
            for ( int j=0; j<bundleb.length; j++ ) {
                bds.putProperty(QDataSet.UNITS, j, columnUnits[j+icolumn]);
                bds.putProperty(QDataSet.LABEL, j, columnHeaders[j+icolumn] );
                bds.putProperty(QDataSet.NAME, j, Ops.safeName(columnHeaders[j+icolumn]) );
            }
            ds.putProperty( QDataSet.BUNDLE_1, bds );
        } else {
            assert icolumnDs!=null;
            ds.putProperty(QDataSet.UNITS,u);
            ds.putProperty(QDataSet.NAME,icolumnDs.property(QDataSet.NAME));
            ds.putProperty(QDataSet.LABEL,icolumnDs.property(QDataSet.LABEL));
            if ( fill==-1e38 ) {
                ds.putProperty( QDataSet.FILL_VALUE, fill );
            }
        }

        return ds;
    }
    
    /**
     * like the non-streaming source, but:<ul>
     * <li> delimiter is not automatic.
     * <li> rank2 is always used.
     * </ul>
     */
    private class CsvTableStreamingSource implements Streaming {

        public CsvTableStreamingSource() {
        }
 
        
        @Override
        public Iterator<QDataSet> streamDataSet(ProgressMonitor mon) throws Exception {
            
            final AsciiTableStreamer result= new AsciiTableStreamer();
            
            final BufferedReader reader = new BufferedReader( new InputStreamReader( getInputStream(new NullProgressMonitor() ) ) );

            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        String line;
                        String sdelimiter= getParam("delim", ",");
                        if ( sdelimiter.equals("COMMA") ) sdelimiter= ",";
                        if ( sdelimiter.equals("SEMICOLON") ) sdelimiter= ";";
                        while ( (line=reader.readLine())!=null ) {
                            String[] fields= line.split(sdelimiter);
                            result.addRecord( Arrays.asList(fields) );
                        }
                        result.setHasNext(false);
                        logger.log(Level.FINE, "Done parsing {0}", getURI() );
                    } catch ( IOException ex ) {

                    } finally {
                        try {
                            reader.close();
                        } catch ( IOException ex ) {
                            logger.log( Level.WARNING, ex.getMessage(), ex );
                        }
                    }
                }
            };

            new Thread( run, "CsvTableDataStreamer" ).start();
            //new ParserDelegator().parse( reader, callback, true );

            return result;

        }
    }
}
