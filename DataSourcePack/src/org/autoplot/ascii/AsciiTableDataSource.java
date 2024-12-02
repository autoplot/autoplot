/*
 * AsciiTableDataSource.java
 *
 * Created on March 31, 2007, 8:22 AM
 *
 */
package org.autoplot.ascii;

import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetOps;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.util.AsciiParser;
import org.das2.datum.TimeParser;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import org.das2.CancelledOperationException;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.ByteBufferInputStream;
import org.das2.util.LoggerManager;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.qds.SparseDataSetBuilder;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.AsciiHeadersParser;
import org.das2.qds.util.AsciiParser.FieldParser;

/**
 * DataSource for reading data in ASCII files, where each record is 
 * one line of the file, and each record has the same number of fields.  
 * This reads in each record and splits on the delimiter, which is typically
 * guessed by examining the first 5 viable records.  This also supports
 * combining times that are in several fields.  
 * 
 * This also handles true CSV files, where a quoted field may contain a 
 * newline character.  
 * 
 * Last, a three or four column ASCII file containing two ISO8601 strings 
 * for the first two columns is automatically treated as an "events list",
 * a list of named intervals.
 * 
 * @author jbf
 */
public class AsciiTableDataSource extends AbstractDataSource {

    AsciiParser parser;
    File file;
    String column = null;
    String depend0 = null;

    private final static Logger logger= LoggerManager.getLogger("apdss.ascii");

    public final static String PARAM_INTERVAL_TAG="intervalTag";

    /**
     * if non-null, then this is used to parse the times.  For a fixed-column parser, a field
     * handler is added to the parser.  
     */
    TimeParser timeParser;

    /**
     * time format of each digit
     */
    String[] timeFormats;
    /**
     * the column containing times, or -1.  This will be the first column when the times span multiple columns.
     */
    int timeColumn = -1;
    DDataSet ds = null;
    /**
     * non-null indicates the columns should be interpreted as rank2.  rank2[0] is first column, rank2[1] is last column exclusive.
     */
    int[] rank2 = null;

    /**
     * like rank2, but interpret columns as bundle rather than rank 2 dataset.
     */
    int[] bundle= null;
    /**
     * non-null indicates the first record will provide the labels for the rows of the rank 2 dataset.
     */
    int[] depend1Labels = null;

    /**
     * non-null indicates these will contain the values for the labels.
     */
    String[] depend1Label= null;
    
    /**
     * non-null indicates the first record will provide the values for the rows of the rank 2 dataset.
     */
    int[] depend1Values = null;

    private double validMin = Double.NEGATIVE_INFINITY;
    private double validMax = Double.POSITIVE_INFINITY;

    /**
     * name of the event list column.
     */
    String eventListColumn= null;
    
    /**
     * column for colors, if available.
     */
    int eventListColorColumn= -1;
    
    /** 
     * Creates a new instance of AsciiTableDataSource
     * @param uri the URI to read.
     * @throws java.io.FileNotFoundException 
     */
    public AsciiTableDataSource(URI uri) throws FileNotFoundException, IOException {
        super(uri);

    }
 
    /**
     * return a list of column names, supporting:<ul>
     * <li>field1,field3,field5
     * <li>field1,field3-field5
     * <li>field1,field3:field5  exclusive
     * </ul>
     * @param s
     * @param fieldCount 
     * @return 
     * @see #parseRangeStr(java.lang.String, int) 
     */
    public int[] parseColumns( String s, int fieldCount ) {
        String[] ss= s.split(",");
        ArrayList<Integer> r= new ArrayList<>();
        for ( String sss: ss ) {
            if ( sss.contains("-") ) {
                String[] sss4= sss.split("-");
                if ( sss4.length!=2 ) {
                    throw new IllegalArgumentException("must be name-name");
                }
                int i1= columnIndex( sss4[0], fieldCount );
                int i2= columnIndex( sss4[1], fieldCount );
                if ( i2<i1 ) {
                    throw new IllegalArgumentException("start column must be before end column");
                }
                for ( int i=i1; i<=i2; i++ ) {
                    r.add(i);
                }
            } else if ( sss.contains(":") ) {
                String[] sss4= sss.split(":");
                int i1= columnIndex( sss4[0], fieldCount );
                int i2= columnIndex( sss4[1], fieldCount );
                int st= sss4.length==3 ? Integer.parseInt(sss4[2]) : 1;
                if ( sss4.length==3 ) {                    
                    for ( int i=i1; i<i2; i+=st ) {
                        r.add( i );
                    }
                }
            } else {
                r.add( columnIndex( sss, fieldCount ) );
            }
        }
        int[] result= new int[r.size()];
        for ( int i=0; i<r.size(); i++ ) {
            result[i]= r.get(i);
        }
        return result;
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws IOException, CancelledOperationException, NoDataInIntervalException {
       
//        boolean useReferenceCache= "true".equals( System.getProperty( ReferenceCache.PROP_ENABLE_REFERENCE_CACHE, "false" ) );
//
//        ReferenceCache.ReferenceCacheEntry rcent=null;
//        if ( useReferenceCache ) {
//            rcent= ReferenceCache.getInstance().getDataSetOrLock( getURI(), mon);
//            if ( !rcent.shouldILoad( Thread.currentThread() ) ) {
//                try {
//                    logger.log(Level.FINE, "wait for other thread {0}", uri);
//                    QDataSet result= rcent.park( mon );
//                    if ( result==null ) { 
//                        logger.fine("result after parking is null");
//                        Thread.sleep(100); // experiment with this condition.
//                        result= rcent.park( mon ); // this is still null
//                        return getDataSet(mon);    // this is successful.
//                    } else {
//                        return result;
//                    }
//                } catch ( Exception ex ) {
//                    throw new RuntimeException(ex);
//                }
//            } else {
//                logger.log(Level.FINE, "reference cache in use, {0} is loading {1}", new Object[] { Thread.currentThread().toString(), resourceURI } );
//            }
//        }
//        
//        try {

        logger.fine("read file");
        
        ds = doReadFile(mon);
        
        logger.fine("done read file");
        
        if ( mon.isCancelled() ) {
            throw new CancelledOperationException("cancelled data read");
        }

/*        String o= params.get("tail");
        if ( o!=null ) {
            int itail= Integer.parseInt(o);
            int nrec= ds.length();
            if ( nrec>itail ) {
                ds= DDataSet.copy( DataSetOps.trim( ds, nrec-itail, itail ) );
            }
        }*/

        // old code that handled timeFormats removed.  It was no longer in use.
        
        MutablePropertyDataSet vds = null;
        ArrayDataSet dep0 = null;

        if ((column == null) && (timeColumn != -1) ) {
            column = parser.getFieldNames()[timeColumn];
        }

        QDataSet bundleDescriptor= (QDataSet) ds.property(QDataSet.BUNDLE_1);

        //auto-detect event lists
        if ( eventListColumn==null ) { 
            if ( ds.length(0)>2 && ds.length(0)<5 ) {
                Units u0= parser.getUnits(0);
                Units u1= parser.getUnits(1);
                if ( UnitsUtil.isTimeLocation(u0) && ( u1==u0 ) ) {
                    eventListColumn= "field"+(ds.length(0)-1);
                }
            }
        }
        
        if ( eventListColumn!=null ) {
            dep0= ArrayDataSet.maybeCopy( DataSetOps.leafTrim( ds, 0, 2) );
            Units u0= parser.getUnits(0);
            Units u1= parser.getUnits(1);
            if ( u0!=u1 ) {
                if ( UnitsUtil.isTimeLocation(u0) && UnitsUtil.isTimeLocation(u1) ) {
                    throw new IllegalArgumentException("somehow the parser was misconfigured to have two different time units.");
                }
                if ( !u1.isConvertibleTo(u0.getOffsetUnits()) ) { // allow "s" to go with UTC
                    throw new IllegalArgumentException("first two columns should have the same units, or "+
                            "second column should be offset (e.g. seconds) from first");
                }
            }
            dep0.putProperty( QDataSet.UNITS, parser.getUnits(0) );
            dep0.putProperty( QDataSet.BINS_1, QDataSet.VALUE_BINS_MIN_MAX );
            if ( !eventListColumn.equals("") ) {
                column= eventListColumn;
            }
        }

        if ( ds.length()==0 ) {
            logger.info("===========================================");
            logger.info("no records found when parsing ascii file!!!");
            logger.info("===========================================");
            // this may raise an exception in a future version.
            throw new NoDataInIntervalException("no records found");
        }
        
        String group= getParam( "group", null );
        if ( group!=null ) {
            vds= ArrayDataSet.copy( DataSetOps.unbundle( ds, group ) );

        } else if (column != null) {
            if ( bundleDescriptor!=null ) {
                int[] columns= parseColumns( column, parser.getFieldCount() );

                QDataSet vdss=null;
                for ( int c: columns ) {
                    try {
                        if ( c==-1 ) {
                            vdss= Ops.bundle( vdss, ArrayDataSet.copy( Ops.unbundle( ds, column ) ) );
                        } else {
                            vdss= Ops.bundle( vdss, ArrayDataSet.copy(DataSetOps.unbundle(ds,c)) );
                        }
                    } catch ( IllegalArgumentException ex ) {
                        int icol = parser.getFieldIndex(column);
                        if ( icol!=-1 ) {
                            MutablePropertyDataSet vds1 = ArrayDataSet.copy(DataSetOps.slice1(ds, icol));
                            vds1.putProperty( QDataSet.CONTEXT_0, null );
                            vds1.putProperty(QDataSet.UNITS, parser.getUnits(icol));
                            if ( column.length()>1 ) vds1.putProperty( QDataSet.NAME, column );
                            vds1.putProperty( QDataSet.LABEL, parser.getFieldNames()[icol] );
                        } else {
                            //BUG2000: bundleDescriptor is supposed to be a AsciiHeadersParser.BundleDescriptor.  Message is poor 
                            //when wrong column name is used.  https://sourceforge.net/p/autoplot/bugs/1999/
                            if ( bundleDescriptor instanceof AsciiHeadersParser.BundleDescriptor ) {
                                QDataSet _vds= AsciiHeadersParser.getInlineDataSet( bundleDescriptor, column );
                                if ( _vds==null ) {
                                    throw new IllegalArgumentException("No such dataset: " +column );
                                } else {
                                    vdss= Ops.bundle( vdss, ArrayDataSet.maybeCopy(_vds) );
                                }
                            } else {
                                throw new IllegalArgumentException("No such dataset: " +column );
                            }
                        }
                    }
                }
                if ( columns.length==1 ) {
                    vds= (MutablePropertyDataSet) Ops.unbundle( vdss, 0 );
                } else {
                    vds= Ops.maybeCopy( vdss );
                }
            } else {
                int icol = parser.getFieldIndex(column);
                if (icol == -1) {
                    throw new IllegalArgumentException("bad column parameter: " + column + ", should be field1, or 1, or <name>");
                }
                vds = ArrayDataSet.copy(DataSetOps.slice1(ds, icol));
                vds.putProperty( QDataSet.CONTEXT_0, null );
                vds.putProperty(QDataSet.UNITS, parser.getUnits(icol));
                if ( column.length()>1 ) vds.putProperty( QDataSet.NAME, column );
                vds.putProperty( QDataSet.LABEL, parser.getFieldNames()[icol] );
            }
            
            if (validMax != Double.POSITIVE_INFINITY) {
                vds.putProperty(QDataSet.VALID_MAX, validMax);
            }
            if (validMin != Double.NEGATIVE_INFINITY) {
                vds.putProperty(QDataSet.VALID_MIN, validMin);
            }
        } else if ( eventListColumn!=null ) {
            EnumerationUnits eu= EnumerationUnits.create("events");
            vds= ArrayDataSet.maybeCopy( Ops.replicate( DataSetUtil.asDataSet(eu.createDatum("event")), ds.length() ) );
        }

        if (depend0 != null) {
            int icol = parser.getFieldIndex(depend0);
            if (icol == -1) {
                throw new IllegalArgumentException("bad depend0 parameter: " + depend0 + ", should be field1, or 1, or <name>");
            }
            if ( ds.property(QDataSet.BUNDLE_1)!=null ) {
                dep0 = ArrayDataSet.copy(DataSetOps.unbundle(ds,icol)); // avoid warning message about slicing to unbundle
            } else {
                dep0 = ArrayDataSet.copy(DataSetOps.slice1(ds, icol));
            }
            dep0.putProperty(QDataSet.UNITS, parser.getUnits(icol));
            //String tf= params.get("timeFormat");
            if ( UnitsUtil.isTimeLocation( parser.getUnits(icol) ) ) {
                dep0.putProperty(QDataSet.LABEL,null);
                dep0.putProperty(QDataSet.NAME,"time");
            }
            
            if (DataSetUtil.isMonotonic(dep0)) {
                dep0.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
            }
            String intervalType= params.get( PARAM_INTERVAL_TAG );
            if ( intervalType!=null && intervalType.equals("start") ) {
                QDataSet cadence= DataSetUtil.guessCadenceNew( dep0, null );
                if ( cadence!=null && !"log".equals( cadence.property(QDataSet.SCALE_TYPE) ) ) {
                    double add= cadence.value()/2; //DANGER--should really check units.
                    logger.log( Level.FINE, "adding half-interval width to dep0 because of %s: %s", 
                            new Object[] { PARAM_INTERVAL_TAG, cadence } );
                    for ( int i=0; i<dep0.length(); i++ ) {
                        dep0.putValue( i, dep0.value(i)+add );
                    }
                }
            }
            if ( depend0.length()>1 ) dep0.putProperty( QDataSet.NAME, depend0 );
            Units xunits= (Units) dep0.property(QDataSet.UNITS);
            if ( xunits==null || !UnitsUtil.isTimeLocation( xunits ) ) {
                if ( dep0.property(QDataSet.LABEL)==null ) {
                    dep0.putProperty( QDataSet.LABEL, parser.getFieldNames()[icol] );
                }
            }
            String dep0Units= getParam( "depend0Units", null );
            if ( dep0Units!=null ) {
                dep0Units= dep0Units.replaceAll("\\+"," ");
                Units newDep0Units= Units.lookupUnits(dep0Units);
                if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(dep0) ) && UnitsUtil.isTimeLocation(newDep0Units) ) {
                    dep0= ArrayDataSet.maybeCopy( Ops.convertUnitsTo( dep0, newDep0Units ) );
                } else {
                    dep0.putProperty( QDataSet.UNITS, newDep0Units );
                }
            }
        } else {
            if ( ds.rank()==2 ) {
                MutablePropertyDataSet bds= (MutablePropertyDataSet) ds.property(QDataSet.BUNDLE_1);
                if ( bds!=null ) {
                    for ( int i=0; i<bds.length(); i++ ) {
                        Units u= (Units)bds.property(QDataSet.UNITS,i);
                        if ( u!=null && UnitsUtil.isTimeLocation(u) ) {
                            bds.putProperty( QDataSet.LABEL, i, null );
                        }
                    }
                }
            }
        }

        String x= getParam( "X", null );
        String y= getParam( "Y", null );
        String z= getParam( "Z", null );
        
        if ( z!=null ) {
            QDataSet zds= ArrayDataSet.copy( DataSetOps.unbundle( ds, parser.getFieldIndex(z) ) );
            QDataSet yds;
            QDataSet xds;
            if ( y!=null ) {
                yds= ArrayDataSet.copy( DataSetOps.unbundle( ds, parser.getFieldIndex(y) ) );
            } else {
                throw new IllegalArgumentException("expected param Y");
            }
            if ( x!=null ) {
                xds= ArrayDataSet.copy( DataSetOps.unbundle( ds, parser.getFieldIndex(x) ) );
            } else {
                if ( dep0!=null ) {
                    xds= dep0;
                } else {
                    throw new IllegalArgumentException("expected param X"); 
                }
            }
            vds= (MutablePropertyDataSet)Ops.bundle( xds, yds, zds );
        } else if ( y!=null ) {
            QDataSet yds= ArrayDataSet.copy( DataSetOps.unbundle( ds, parser.getFieldIndex(y) ) );
            QDataSet xds;
            if ( x!=null ) {
                xds= ArrayDataSet.copy( DataSetOps.unbundle( ds, parser.getFieldIndex(x) ) );
            } else {
                if ( dep0!=null ) {
                    xds= dep0;
                } else {
                    throw new IllegalArgumentException("expected param X"); 
                }
            }
            vds= (MutablePropertyDataSet)Ops.link( xds, yds );
        } 
        
        
        if ( bundle!=null ) {
            if ( bundle[0]==-1 ) {
                throw new IllegalArgumentException("bad parameter: bundle");
            }
            rank2= bundle;
        }

        if (rank2 != null) {
            if (dep0 != null) {
                ds.putProperty(QDataSet.DEPEND_0, dep0); // DANGER
            }
            if ( rank2[0]==-1 ) {
                throw new IllegalArgumentException("bad parameter: rank2");
            }
            
            Units u = parser.getUnits(rank2[0]);
            for (int i = rank2[0]; i < rank2[1]; i++) {
                if (u != parser.getUnits(i)) {
                    u = null;
                }
            }
            if (u != null) {
                ds.putProperty(QDataSet.UNITS, u);
            }
            if (validMax != Double.POSITIVE_INFINITY) {
                ds.putProperty(QDataSet.VALID_MAX, validMax);
            }
            if (validMin != Double.NEGATIVE_INFINITY) {
                ds.putProperty(QDataSet.VALID_MIN, validMin);
            }

            MutablePropertyDataSet mds;
            if ( rank2[0]==0 && rank2[1]==ds.length(0) ) {
                mds= ds;
            } else {
                mds= DataSetOps.leafTrim(ds, rank2[0], rank2[1]);
            }

            if ( bundle!=null ) {
                QDataSet labelsds = Ops.labelsDataset(parser.getFieldLabels());
                labelsds = labelsds.trim( bundle[0], bundle[1]);
                mds.putProperty(QDataSet.DEPEND_1, labelsds);
                SparseDataSetBuilder sdsb= new SparseDataSetBuilder(2);
                sdsb.setLength( bundle[1]-bundle[0] );
                sdsb.setQube( new int[] { bundle[1]-bundle[0], 0 } );
                String[] names= parser.getFieldNames();
                String[] labels= parser.getFieldLabels();
                String[] sunits= parser.getFieldUnits();
                boolean nothingAdded= true;
                for ( int i=0; nothingAdded && i<names.length; i++ ) {
                    if ( !("field"+i).equals(names[i]) ) nothingAdded= false;
                    if ( !("field"+i).equals(labels[i]) ) nothingAdded= false;
                    if ( sunits[i]!=null ) nothingAdded= false;
                }
                nothingAdded= false;
                logger.log(Level.FINER, "nothing added={0}", nothingAdded);
                for ( int i=bundle[0]; i<bundle[1]; i++ ) {
                    int index= i-bundle[0];
                    sdsb.putProperty( QDataSet.NAME, index, names[i] );
                    sdsb.putProperty( QDataSet.LABEL, index, labels[i] );
                    sdsb.putProperty( QDataSet.UNITS, index, parser.getUnits(i) );
                }
                mds.putProperty(QDataSet.BUNDLE_1, sdsb.getDataSet() );
            }

            if ( depend1Label!=null ) {
                mds.putProperty(QDataSet.DEPEND_1, Ops.labelsDataset(depend1Label) );
            }

            if (depend1Labels != null) {
                QDataSet labels = Ops.labelsDataset(parser.getFieldLabels());
                labels = labels.trim( depend1Labels[0], depend1Labels[1] );
                mds.putProperty(QDataSet.DEPEND_1, labels);
            }

            if (depend1Values != null) {
                String[] fieldNames = parser.getFieldNames();
                String[] fieldUnits = parser.getFieldUnits();
                DDataSet dep1 = DDataSet.createRank1(depend1Values[1] - depend1Values[0]);
                boolean firstRecordIsDep1= false;
                for (int i = depend1Values[0]; i < depend1Values[1]; i++) {
                    double d;
                    if ( firstRecordIsDep1 ) {
                        d= mds.value( 0, i - depend1Values[0] );
                    } else {
                        try {
                            d = Double.parseDouble(fieldNames[i]);
                        } catch (NumberFormatException ex) {
                            try {
                                if ( fieldUnits[i]!=null ) {
                                    d = Double.parseDouble(fieldUnits[i]);
                                } else {
                                    d= mds.value( 0, i - depend1Values[0] );
                                    firstRecordIsDep1= true;
                                }
                            } catch (NumberFormatException ex2) {
                                d = i - depend1Values[0];
                            }
                        }
                    }
                    dep1.putValue(i-depend1Values[0], d);
                }
                mds.putProperty(QDataSet.DEPEND_1, dep1);
                if ( firstRecordIsDep1 ) {
                    mds= (MutablePropertyDataSet) mds.trim(1,mds.length());
                }
            }
            
            if ( bundle==null && rank2!=null && !parser.isRichHeader() ) { 
                //http://autoplot.org/data/autoplot.xml, test005_demo6
                mds.putProperty( QDataSet.BUNDLE_1, null );
            }
            
            
            String label= getParam( "label", null );
            if ( label!=null ) {
                mds.putProperty( QDataSet.LABEL, label );
            }

            String title= getParam( "title", null );
            if ( title!=null ) {
                mds.putProperty( QDataSet.TITLE, title );
            }
            //if ( rcent!=null ) rcent.finished(mds);     
            return mds;

        } else {
            if (vds == null) {
                if ( column==null ) {
                    throw new IllegalArgumentException("column was not specified.  "+
                            "Use column, rank2, or bundle to specify data to plot.");
                } else {
                    throw new IllegalArgumentException("didn't find column: " + column);
                }
            }

            String label= getParam( "label", null );
            if ( label!=null ) {
                vds.putProperty( QDataSet.LABEL, label );
            }

            String title= getParam( "title", null );
            if ( title!=null ) {
                vds.putProperty( QDataSet.TITLE, title );
            }
       

            if (dep0 != null) {
                if ( x==null ) {
                    vds.putProperty(QDataSet.DEPEND_0, dep0);
                }
            }
            if ( eventListColumn!=null && dep0!=null ) {
                Units u0= parser.getUnits(0);
                Units u1= parser.getUnits(1);
                if ( u0!=u1 ) {
                    if ( u1.isConvertibleTo(u0.getOffsetUnits()) ) { // allow "s" to go with UTC
                        UnitsConverter uc= u1.getConverter(u0.getOffsetUnits());
                        for ( int i=0;i<dep0.length(); i++ ) {
                            dep0.putValue(i,1,dep0.value(i,0)+uc.convert(dep0.value(i,1)) );
                        }
                    }
                }
                if ( eventListColorColumn>-1 ) {
                    vds= ArrayDataSet.copy( Ops.bundle( Ops.slice1(dep0,0), 
                            Ops.slice1(dep0,1),
                            Ops.slice1(ds,eventListColorColumn),
                            vds ) );
                    ((MutablePropertyDataSet)vds.property(QDataSet.BUNDLE_1)).putProperty( QDataSet.FORMAT,2,"0x%06x" );
                }
                vds.putProperty(QDataSet.RENDER_TYPE,"eventsBar");
            }
            //if ( rcent!=null ) rcent.finished(vds);            
            return vds;
        }
        
//
//        } catch ( RuntimeException ex ) {
//            if ( rcent!=null ) rcent.exception(ex);
//            throw ex;
//        } catch ( IOException ex ) {
//            if ( rcent!=null ) rcent.exception(ex);
//            throw ex;
//        } finally {
//            mon.finished();
//        }
        
        
    }

    /**
     * returns the rank 2 dataset produced by the ASCII table reader.
     * @param mon note monitor is used twice, so the progress bar jumps back.
     * @return
     * @throws java.lang.NumberFormatException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     */
    private DDataSet doReadFile(final ProgressMonitor mon) throws NumberFormatException, IOException, FileNotFoundException {

        logger.finer("maybe download file");
        
        String o;
        file = getFile(mon.getSubtaskMonitor("getFile"));

        logger.finer("got file");
        
        if ( file.isDirectory() ) {
            throw new IOException("expected file but got directory");
        }

        parser = new AsciiParser();


        boolean fixedColumns = false;

        int columnCount;

        /**
         * if non-null, this is the delim we are using to parse the file.
         */
        String delim;

        o = params.get("skip");
        if (o != null) {
            parser.setSkipLines(Integer.parseInt(o));
        }

        o = params.get("skipLines");
        if (o != null) {
            parser.setSkipLines(Integer.parseInt(o));
        }
        
        o = params.get("recCount");
        if (o != null) {
            parser.setRecordCountLimit(Integer.parseInt(o));
        }
        
        o= params.get("recStart");
        if ( o!=null ) {
            parser.setRecordStart(Integer.parseInt(o));
        }

        parser.setKeepFileHeader(true);

        o = params.get("comment");
        if (o != null) {
            if ( o.equals("") ) {
                parser.setCommentPrefix(null);
            } else {
                parser.setCommentPrefix(o);
            }
        }

        o = params.get("headerDelim");
        if (o != null) {
            parser.setHeaderDelimiter(o);
        }

        delim = params.get("delim");
        
        String spattern= params.get("pattern");
        String format= params.get("format");
        if ( format!=null ) {
            spattern= AsciiParser.getRegexForFormat( format );
        }
        
        String sFixedColumns = params.get("fixedColumns");
        
        if ( spattern!=null )  {
            AsciiParser.RegexParser p = new AsciiParser.RegexParser(parser,spattern);
            parser.setRecordParser( p );
            parser.setCommentPrefix(null);
            columnCount= p.fieldCount();
            delim= " "; // this is because timeformats needs a delimiter
            
        } else if (sFixedColumns == null) {
            if (delim == null) {
                AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
                if ( p == null) {
                    String cc= params.get("columnCount");
                    columnCount= ( cc==null ) ? 2 : Integer.parseInt(cc);
                    p= parser.getDelimParser( columnCount, "\\s+" );
                }
                columnCount = p.fieldCount();
                delim = p.getDelim();
                p.setShowException(true); 
                parser.setRecordParser( p );
            } else {
                delim= delim.replaceAll("WHITESPACE", "\\s+");
                delim= delim.replaceAll("SPACE", " ");
                delim= delim.replaceAll("COMMA", ",");
                delim= delim.replaceAll("SEMICOLON", ";");
                delim= delim.replaceAll("COLON", ":");
                delim= delim.replaceAll("TAB", "\t");
                delim= delim.replaceAll("whitespace", "\\s+");
                delim= delim.replaceAll("space", " ");
                delim= delim.replaceAll("comma", ",");
                delim= delim.replaceAll("semicolon", ";");
                delim= delim.replaceAll("colon", ":");
                delim= delim.replaceAll("tab", "\t");
                if (delim.equals("+")) {
                    delim = " ";
                }
                columnCount = parser.setDelimParser(file.toString(), delim).fieldCount();
            }
            //parser.setPropertyPattern( Pattern.compile("^#\\s*(.+)\\s*\\:\\s*(.+)\\s*") );
            parser.setPropertyPattern(AsciiParser.NAME_COLON_VALUE_PATTERN);
        } else {
            String s = sFixedColumns;
            AsciiParser.RecordParser p = parser.setFixedColumnsParser(file.toString(), "\\s+");
            try {
                columnCount = Integer.parseInt(sFixedColumns);
            } catch ( NumberFormatException ex ) {
                if (sFixedColumns.equals("")) {
                    columnCount = p.fieldCount();
                } else { // 0-10,20-34
                    String[] ss = s.split(",");
                    int[] starts = new int[ss.length];
                    int[] widths = new int[ss.length];
                    AsciiParser.FieldParser[] fparsers = new AsciiParser.FieldParser[ss.length];
                    for (int i = 0; i < ss.length; i++) {
                        String[] ss2 = ss[i].split("-");
                        starts[i] = Integer.parseInt(ss2[0]);
                        widths[i] = Integer.parseInt(ss2[1]) - starts[i] + 1;
                        fparsers[i] = AsciiParser.DOUBLE_PARSER;
                    }
                    p = parser.setFixedColumnsParser(starts, widths, fparsers);
                    columnCount= p.fieldCount();
                }
            }

            parser.setPropertyPattern(null); // don't look for these for speed
            fixedColumns = true;
            delim = null;
        }

        o = params.get("columnCount");
        if (columnCount == 0) {
            if (o != null) {
                columnCount = Integer.parseInt(o);
            } else {
                columnCount = AsciiParser.guessFieldCount(file.toString());
            }
        }

        o = params.get("fill");
        if (o != null) {
            parser.setFillValue(Double.parseDouble(o));
        }

        o = params.get("validMin");
        if (o != null) {
            this.validMin = Double.parseDouble(o);
        }

        o = params.get("validMax");
        if (o != null) {
            this.validMax = Double.parseDouble(o);
        }

        /* recognize the column as parsable times, parse with slow general purpose time parser */
        o = params.get("time");
        if (o != null) {
            int i = parser.getFieldIndex(o);
            if (i == -1) {
                throw new IllegalArgumentException("field not found for time in column named \"" + o + "\"");
            } else {
                parser.setFieldParser(i, parser.UNITS_PARSER);
                parser.setUnits(i, AsciiParser.UNIT_UTC );

                depend0 = o;
                timeColumn = i;
            }
        }

        o = params.get("timeFormat");
        if (o != null) {
            String timeFormat=o;
            if ( ",".equals(delim) && !timeFormat.contains(",") ) { 
                timeFormat= timeFormat.replaceAll("\\+",",");
            }
            if ( !timeFormat.contains(" ") ) {
                if ( "\t".equals(delim) || ";".equals(delim) ) {
                    timeFormat = timeFormat.replaceAll("\\+", delim);
                } else {
                    timeFormat = timeFormat.replaceAll("\\+", " ");
                }
            }
            timeFormat = timeFormat.replaceAll("\\%", "\\$");
            timeFormat = timeFormat.replaceAll("\\{", "(");
            timeFormat = timeFormat.replaceAll("\\}", ")");
            String timeColumnName = params.get("time");   
            if ( timeColumnName==null ) {
                timeColumn= 0;
            } else {
                // future-proof against field0-field4, coming soon.
                int i= timeColumnName.indexOf("-");
                if ( i>-1 ) {
                    timeColumnName= timeColumnName.substring(0,i);
                } else {
                    i= timeColumnName.indexOf(":");
                    if ( i>-1 ) {
                        timeColumnName= timeColumnName.substring(0,i);
                    }
                }
                timeColumn= parser.getFieldIndex(timeColumnName);
            }

            String timeFormatDelim= delim;
            if ( delim==null ) timeFormatDelim= " ";
            timeFormats= timeFormat.split(timeFormatDelim,-2);

            if (timeFormat.equals("ISO8601")) {
                String line = parser.readFirstParseableRecord(file.toString());
                if (line == null) {
                    throw new IllegalArgumentException("file contains no parseable records.");
                }
                String[] ss = new String[ parser.getRecordParser().fieldCount() ];
                parser.getRecordParser().splitRecord(line,ss);
                int i = timeColumn;
                if (i == -1) {
                    i = 0;
                }
                String atime = ss[i];
                timeFormat = TimeParser.iso8601String(atime.trim());
                timeParser = TimeParser.create(timeFormat);
                final Units u = AsciiParser.UNIT_UTC;
                parser.setUnits(i, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {
                    @Override
                    public double parseField(String field, int fieldIndex) throws ParseException {
                        return timeParser.parse(field).getTime(u);
                    }
                };
                parser.setFieldParser(i, timeFieldParser);

            } else if (delim != null && timeFormats.length > 1) {
                timeParser = TimeParser.create(timeFormat);
                // we've got a special case here: the time spans multiple columns, so we'll have to combine later.
                parser.setUnits(timeColumn, Units.dimensionless);

                //if ( true ) {
                final Units u= AsciiParser.UNIT_UTC;

                MultiFieldTimeParser timeFieldParser=
                        new MultiFieldTimeParser( timeColumn, timeFormats, timeParser, u );

                for ( int i=timeColumn; i<timeColumn+timeFormats.length; i++ ) {
                    parser.setFieldParser( i, timeFieldParser );
                    parser.setUnits( i, Units.dimensionless );
                }
                
                if ( parser.getRecordParser() instanceof AsciiParser.DelimParser ) {
                    ((AsciiParser.DelimParser)parser.getRecordParser()).setGuessUnits(false);
                }

                timeColumn= timeColumn + timeFormats.length - 1;
                if ( params.get("time")!=null )  {
                    depend0= parser.getFieldNames()[timeColumn];
                }
                parser.setUnits( timeColumn, u );

            } else {
                timeParser = TimeParser.create(timeFormat);
                final Units u = AsciiParser.UNIT_UTC;
                parser.setUnits(timeColumn, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {
                    @Override
                    public double parseField(String field, int fieldIndex) throws ParseException {
                        return timeParser.parse(field).getTime(u);
                    }
                };
                parser.setFieldParser(timeColumn, timeFieldParser);

            }
        } else {
            timeParser = null;
        }

        o = params.get( "arg_0" );
        if ( o!=null && o.length()>0 && !o.equals("rank2") ) {
            column = o;
            if ( parser.getFieldIndex(column)!=0 ) {
                timeColumn= 0;
                if ( UnitsUtil.isTimeLocation( parser.getUnits(0) ) ) { 
                    final Units u = AsciiParser.UNIT_UTC;
                    parser.setUnits(0, u);
                }
                depend0= "0";
            }
        } 

        o = params.get("column");
        if (o != null) {
            column = o;
        }
        
        o = params.get("depend0");
        if (o != null) {
            depend0 = o;
        }

        o = params.get("Z");
        if ( o!=null ) {
            column= o;
        } else {
            o = params.get("Y");
            if ( o!=null ) {
                column= o;
            }
        }
        
        o = params.get("X");
        if ( o!=null ) {
            depend0= o;
        }

        o = params.get("rank2");
        if (o != null) {
            rank2 = parseRangeStr(o, columnCount);
            column = null;
        }

        o = params.get("bundle");
        if (o != null) {
            if ( o.contains(",") || o.split(":",-2).length==3 ) {
                column= o;
                bundle= null;
            } else {
                bundle = parseRangeStr(o, columnCount);
                column = null;
            }
        }

        o = params.get("arg_0");
        if (o != null ) {
            if ( o.equals("rank2") ) {
                rank2 = new int[]{0, columnCount};
                column = null;
            } else if ( o.equals("bundle") ) {
                bundle = new int[]{0, columnCount};
                column = null;
            }
        }

        boolean haveColumn= column!=null;
        
        if (column == null && depend0 == null && rank2 == null) {
            if (parser.getFieldNames().length == 2) {
                depend0 = parser.getFieldNames()[0];
                column = parser.getFieldNames()[1];
            } else {
                column = parser.getFieldNames()[parser.getFieldNames().length-1];
            }
        }

        o = params.get("depend1Labels");
        if (o != null) {
            if ( o.contains(",") ) {
                depend1Label= o.split(",");
            } else {
                depend1Labels = parseRangeStr(o, columnCount);
            }
        }

        o = params.get("depend1Values");
        if (o != null) {
            depend1Values = parseRangeStr(o, columnCount);
        }

        eventListColumn= params.get("eventListColumn");
        
        // rfe https://sourceforge.net/p/autoplot/bugs/1425/: create events list automatically.
        if ( parser.getFieldLabels().length>=2 
                && parser.getFieldLabels().length <= 5 
                && UnitsUtil.isTimeLocation(parser.getUnits(0)) 
                && UnitsUtil.isTimeLocation(parser.getUnits(1)) && !haveColumn ) {
            if ( parser.getFieldCount()>2 ) {
                eventListColumn= "field"+(parser.getFieldLabels().length-1);
            } else {
                eventListColumn= "";
                depend0= null;
                column= null;
            }
        }
        
        // rfe https://sourceforge.net/p/autoplot/feature-requests/256: add support for HDMC's simple event list format, where 
        // the first two columns are start and stop times.
        if ( eventListColumn!=null ) {
            parser.setUnits( 0, AsciiParser.UNIT_UTC );
            parser.setUnits( 1, AsciiParser.UNIT_UTC );
            parser.setFieldParser(0, parser.UNITS_PARSER);
            parser.setFieldParser(1, parser.UNITS_PARSER);
            if ( !eventListColumn.equals("") ) { //"" means it is just two columns: st,en.
                int icol = parser.getFieldIndex(eventListColumn);
                EnumerationUnits eu= EnumerationUnits.create("events");
                parser.setUnits(icol,eu);
                parser.setFieldParser(icol,parser.ENUMERATION_PARSER);
                if ( icol>2 ) { //get the RGB color as well.
                    String[] fields = new String[parser.getRecordParser().fieldCount()];
                    String s= parser.readFirstParseableRecord(file.toString());
                    parser.getRecordParser().splitRecord(s,fields);
                    if ( fields[2].startsWith("x") || fields[2].startsWith("0x" ) ) { // RGB color third column starts with x or 0x
                        parser.setUnits(2,Units.dimensionless);
                        parser.setFieldParser( 2, new FieldParser() {
                            @Override
                            public double parseField(String field, int columnIndex) throws ParseException {
                                if ( field.startsWith("x") ) {
                                    return Integer.decode( "0"+field ); 
                                } else {
                                    return Integer.decode( field );
                                }
                            }
                        });
                        eventListColorColumn= 2;
                    }
                }
            }
        }

        // check to see if the depend0 or data column appear to be times.  I Promise I won't open the file again until it's read in.
        if ( timeColumn == -1 ) {
            String s = parser.readFirstParseableRecord(file.toString());
            if (s != null) {
                String[] fields = new String[parser.getRecordParser().fieldCount()];
                parser.getRecordParser().splitRecord(s,fields);
                if ( depend0!=null ) {
                    int idep0 = parser.getFieldIndex(depend0);
                    if (idep0 != -1) { // deal with -1 later
                        String field = fields[idep0];
                        try {
                            TimeUtil.parseTime(field);
                            if ( new StringTokenizer( field, ":T-/" ).countTokens()>1 ) {
                                parser.setUnits(idep0, AsciiParser.UNIT_UTC );
                                parser.setFieldParser(idep0, parser.UNITS_PARSER);
                            }
                        } catch (ParseException ex) {
                        }
                    }
                }
                if ( column!=null ) {
                    int icol = parser.getFieldIndex(column);
                    if (icol != -1) { // deal with -1 later
                        String field = fields[icol];
                        try {
                            field= field.trim();
                            if ( !UnitsUtil.isTimeLocation(parser.getUnits(icol)) && !field.startsWith("-") ) {
                                TimeUtil.parseTime(field);
                                if ( new StringTokenizer( field, ":T-/" ).countTokens()>2 ) {
                                    parser.setUnits(icol, AsciiParser.UNIT_UTC);
                                    parser.setFieldParser(icol, parser.UNITS_PARSER);
                                }
                            }
                        } catch (ParseException ex) {
                        }
                    }
                }
                // check to see if first two columns look like times, and go ahead and handle these automatically
                for ( int icol= 0; icol<fields.length && icol<2; icol++ ) {
                    String field = fields[icol];
                    try {
                        field= field.trim();
                        if ( !UnitsUtil.isTimeLocation(parser.getUnits(icol)) && !field.startsWith("-") ) {
                            TimeUtil.parseTime(field);
                            if ( new StringTokenizer( field, ":T-/" ).countTokens()>2 ) {
                                parser.setUnits(icol, AsciiParser.UNIT_UTC);
                                parser.setFieldParser(icol, parser.UNITS_PARSER);
                            }
                        }
                    } catch (ParseException ex) {
                    }
                }
            }
        }

        o = params.get("units");
        if (o != null) {
            String sunits = o;
            Units u;
            if ( sunits.equals("enum") ) {
                u = EnumerationUnits.create("default");
            } else if ( sunits.equals("nominal") ) {
                u= Units.nominal();
            } else {
                u= Units.lookupUnits(sunits);
            }
            if (column != null) {
                int icol = parser.getFieldIndex(column);
                parser.setUnits(icol, u);
                if ( sunits.equals("enum") ) {
                    parser.setFieldParser(icol, parser.ENUMERATION_PARSER);
                } else {
                    parser.setFieldParser(icol, parser.UNITS_PARSER);
                }
            }
        }
        
        o= params.get("ordinal");
        if (o!=null ) {
            String sunits = o;
            EnumerationUnits u = EnumerationUnits.create("default");
            if ( sunits.trim().length()>0 ) {
                String[] ss= sunits.split(",");
                for ( String s : ss ) {
                    u.createDatum(s);
                }
            }
            if (column != null) {
                int icol = parser.getFieldIndex(column);
                parser.setUnits(icol, u);
                parser.setFieldParser(icol,parser.ENUMERATION_PARSER);
            }
        }
        
        o= params.get("where");
        if ( o!=null ) {
            String w= o;
            if ( w.length()>0 ) {
                Pattern p= Pattern.compile("\\.([nelg][qte])\\(");
                Matcher m= p.matcher(w);
                int ieq;
                if ( !m.find() ) {
                    Pattern p2= Pattern.compile("\\.(within|matches)\\(");
                    Matcher m2= p2.matcher(w);
                    if ( !m2.find() ) {
                        throw new IllegalArgumentException("where can only contain .eq,.ne,.ge,.gt,.le,.lt, .within, or .matches");
                    } else {
                        ieq= m2.start();
                        String sop= m2.group(1);
                        String sval= w.substring(ieq+sop.length()+2,w.length()-1);
                        String sparm= w.substring(0,ieq);
                        parser.setWhereConstraint( sparm, sop, DataSourceUtil.unescape(sval) );
                    }
                } else {
                    ieq= m.start();
                    String op= m.group(1);
                    String sval= w.substring(ieq+4,w.length()-1);
                    String sparm= w.substring(0,ieq);
                    parser.setWhereConstraint( sparm, op, sval );
                }
            }
        }
                
        logger.fine("done process parameters and peeking at file");
        
        // --- done configuration, now read ---
        DDataSet ds1;
        o = params.get("tail");
        if (o != null) {
            ByteBuffer buff= new FileInputStream( file ).getChannel().map( MapMode.READ_ONLY, 0, file.length() );
            int tailNum= Integer.parseInt(o);
            int tailCount=0;
            int ipos=(int)file.length();
            boolean foundNonEOL= false;
            while ( tailCount<tailNum && ipos>0 ) {
                ipos--;
                byte ch= buff.get((int)ipos);
                switch (ch) {
                    case 10:
                        if ( ipos>1 && buff.get(ipos-1)==13 ) ipos=ipos-1;
                        if ( foundNonEOL ) tailCount++;
                        break;
                    case 13:
                        if ( foundNonEOL ) tailCount++;
                        break;
                    default:
                        foundNonEOL= true;
                        break;
                }
            }
            buff.position( tailCount<tailNum ? 0 : ipos+1 );
            InputStream in= new ByteBufferInputStream(buff);
            mon.setProgressMessage("reading "+file);
            ds1 = (DDataSet) parser.readStream( new InputStreamReader(in), mon.getSubtaskMonitor("read file")); //DANGER
        } else {
            int skipBytes= Integer.parseInt(getParam("skipBytes","0"));
            int fileLength= (int)file.length();
            mon.setProgressMessage("reading "+file);
            mon.setTaskSize(fileLength-skipBytes);
            InputStream ins= new FileInputStream(file);
            
            if ( skipBytes>0 ) {
                byte[] bb= new byte[skipBytes];
                int bytesRead=0;
                while ( bytesRead<skipBytes ) {
                    int n= ins.read(bb);
                    if ( n==-1 ) throw new IllegalArgumentException("unable to read skipBytes from file");
                    bytesRead+= n;
                }
            }
            ds1 = (DDataSet) parser.readStream( new InputStreamReader(ins), mon ); //DANGER
            
        }
        
        logger.fine("done parsing file");
        
        return ds1;
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        if (ds == null) {
            return new HashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) ds.property(QDataSet.USER_PROPERTIES);
        String header = (String) props.get("fileHeader");
        if (header != null) {
            header = header.replaceAll("\t", "\\\\t");
            props.put("fileHeader", header);
        }
        String firstRecord = (String) props.get("firstRecord");
        if (firstRecord != null) {
            firstRecord = firstRecord.replaceAll("\t", "\\\\t");
            props.put("firstRecord", firstRecord);
        }
        List<String> remove= new ArrayList();
        for ( Entry<String,Object> e: props.entrySet() ) {
            String k= e.getKey();
            Object v= e.getValue();
            if ( v==null ) continue;
            boolean isAllowed= v instanceof Number 
                || v instanceof String
                || v instanceof org.das2.datum.Datum 
                || v.getClass().isArray();
            if ( ! isAllowed ) {
                logger.log(Level.FINE, "removing user property because of type: {0}", k);
                remove.add(k);
            }
        }
        for ( String k: remove ) {
            props.remove(k);
        }

        return props;
    }

    /**
     * returns the field index of the name, which can be:<ul>
     * <li>  a column name
     * <li>  an implicit column name "field1"
     * <li>  a column index (0 is the first column)
     * <li>  a negative column index (-1 is the last column)
     * </ul>
     * @param name the column name 
     * @param count the number of columns (for negative column numbers)
     * @return the index of the field.
     */
    private int columnIndex( String name, int count ) {
        if ( Pattern.matches( "\\d+", name) ) {
            return Integer.parseInt(name);
        } else if ( Pattern.matches( "-\\d+", name) ) {
            return count + Integer.parseInt(name);
        } else if ( Pattern.matches( "field\\d+", name) ) {
            return Integer.parseInt( name.substring(5) );
        } else {
            int idx= parser.getFieldIndex(name);
            return idx;
        }
    }

    /**
     * parse range strings like "3:6", "3:-5", and "Bx_gsm-Bz_gsm"
     * if the delimiter is colon, then the end is exclusive.  If it is "-",
     * then it is inclusive.
     * @param o the string with the spec.
     * @param columnCount
     * @return two-element int array of the first and last indeces+1.
     * @throws java.lang.NumberFormatException
     * @see #parseColumns(java.lang.String) 
     */
    private int[] parseRangeStr(String o, int columnCount) throws NumberFormatException {
        String s = o;
        int first = 0;
        int last = columnCount;
        if (s.contains(":")) {
            String[] ss = s.split(":",-2);
            if ( ss[0].length() > 0 ) {
                first = columnIndex(ss[0],columnCount);
            }
            if ( ss[1].length() > 0 ) {
                last = columnIndex(ss[1],columnCount);
            }
        } else if ( s.contains("--") ) {
            int isplit= s.indexOf("--",1);
            if ( isplit > 0 ) {
                first = columnIndex( s.substring(0,isplit),columnCount);
            }
            if ( isplit < s.length()-2 ) {
                last = 1 + columnIndex( s.substring(isplit+1),columnCount);
            }
        } else if ( s.contains("-") ) {
            String[] ss = s.split("-",-2);
            if ( ss[0].length() > 0 ) {
                first = columnIndex(ss[0],columnCount);
            }
            if ( ss[1].length() > 0 ) {
                last = 1 + columnIndex(ss[1],columnCount);
            }
        }
        return new int[]{first, last};
    }
}
