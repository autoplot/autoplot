/*
 * AsciiTableDataSource.java
 *
 * Created on March 31, 2007, 8:22 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.ascii;

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
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.dsutil.AsciiParser;
import org.das2.datum.TimeParser;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.CancelledOperationException;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.ByteBufferInputStream;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.AsciiHeadersParser;
import org.virbo.dsutil.AsciiParser.FieldParser;

/**
 *
 * @author jbf
 */
public class AsciiTableDataSource extends AbstractDataSource {

    AsciiParser parser;
    File file;
    String column = null;
    String depend0 = null;

    private final static Logger logger= Logger.getLogger("apdss.ascii");

    public final static String PARAM_INTERVAL_TAG="intervalTag";

    /**
     * if non-null, then this is used to parse the times.  For a fixed-column parser, a field
     * handler is added to the parser.  
     */
    TimeParser timeParser;
    /**
     * the number of columns to combine into time
     */
    int timeColumns = -1;
    /**
     * time format of each digit
     */
    String[] timeFormats;
    /**
     * the column containing times, or -1.
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
     * column for colors, if available.
     */
    int eventListColorColumn= -1;
    
    /** Creates a new instance of AsciiTableDataSource */
    public AsciiTableDataSource(URI uri) throws FileNotFoundException, IOException {
        super(uri);

    }

    public QDataSet getDataSet(ProgressMonitor mon) throws IOException, CancelledOperationException {

        ds = doReadFile(mon);

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


        // combine times if necessary
        if (timeColumns > 1) {
            final Units u = Units.t2000;
            int warnCount=10;
            // replace the first column with the datum time
            for (int i = 0; i < ds.length(); i++) {
                try {
                    timeParser.resetSeconds();
                    for (int j = 0; j < timeColumns; j++) {
                        double d = ds.value(i, timeColumn + j);
                        double fp = d - (int) Math.floor(d);
                        if (fp == 0) {
                            timeParser.setDigit(timeFormats[j], (int) d);
                        } else {
                            timeParser.setDigit(timeFormats[j], d);
                        }
                    }
                    ds.putValue(i, timeColumn, timeParser.getTime(Units.t2000) );
                } catch ( IllegalArgumentException ex ) {
                    if ( warnCount>0 ) { // prevent errors from bogging down
                        new RuntimeException("failed to read time at record "+i, ex ).printStackTrace();
                        warnCount--;
                    }
                    ds.putValue( i, timeColumn, Units.t2000.getFillDouble() );
                }
            }
            parser.setUnits(timeColumn, Units.t2000);
        }

        ArrayDataSet vds = null;
        ArrayDataSet dep0 = null;

        if ((column == null) && (timeColumn != -1)) {
            column = parser.getFieldNames()[timeColumn];
        }

        QDataSet bundleDescriptor= (QDataSet) ds.property(QDataSet.BUNDLE_1);

        String eventListColumn= getParam( "eventListColumn", null );
        if ( eventListColumn!=null ) {
            dep0= ArrayDataSet.maybeCopy( DataSetOps.leafTrim( ds, 0, 2) );
            Units u0= parser.getUnits(0);
            Units u1= parser.getUnits(1);
            if ( u0!=u1 ) {
                if ( !u1.isConvertableTo(u0.getOffsetUnits()) ) { // allow "s" to go with UTC
                    throw new IllegalArgumentException("first two columns should have the same units");
                }
            }
            dep0.putProperty( QDataSet.UNITS, parser.getUnits(0) );
            dep0.putProperty( QDataSet.BINS_1, QDataSet.VALUE_BINS_MIN_MAX );
            column= eventListColumn;
        }

        if ( ds.length()==0 ) {
            logger.info("===========================================");
            logger.info("no records found when parsing ascii file!!!");
            logger.info("===========================================");
            // this may raise an exception in a future version.
        }

        String group= getParam( "group", null );
        if ( group!=null ) {
            vds= ArrayDataSet.copy( DataSetOps.unbundle( ds, group ) );

        } else if (column != null) {
            if ( bundleDescriptor!=null ) {
                //vds = ArrayDataSet.copy(DataSetOps.slice1(ds, icol));
                try {
                    vds= ArrayDataSet.copy(DataSetOps.unbundle(ds,column));
                } catch ( IllegalArgumentException ex ) {
                    QDataSet _vds= AsciiHeadersParser.getInlineDataSet( bundleDescriptor, column );
                    if ( _vds==null ) {
                        throw new IllegalArgumentException("No such dataset: " +column );
                    } else {
                        vds= ArrayDataSet.maybeCopy(_vds);
                    }
                }

            } else {
                int icol = parser.getFieldIndex(column);
                if (icol == -1) {
                    throw new IllegalArgumentException("bad column parameter: " + column + ", should be field1, or 1, or <name>");
                }
                vds = ArrayDataSet.copy(DataSetOps.slice1(ds, icol));
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
            if (DataSetUtil.isMonotonic(dep0)) {
                dep0.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
            }
            String intervalType= params.get( PARAM_INTERVAL_TAG );
            if ( intervalType!=null && intervalType.equals("start") ) {
                QDataSet cadence= DataSetUtil.guessCadenceNew( dep0, null );
                if ( cadence!=null && !"log".equals( cadence.property(QDataSet.SCALE_TYPE) ) ) {
                    double add= cadence.value()/2; //DANGER--should really check units.
                    logger.log( Level.FINE, "adding half-interval width to dep0 because of %s: %s", new Object[] { PARAM_INTERVAL_TAG, cadence } );
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
                dep0.putProperty( QDataSet.UNITS, SemanticOps.lookupUnits(dep0Units) );
            }
        }

        if ( bundle!=null ) {
            rank2= bundle;
        }

        if (rank2 != null) {
            if (dep0 != null) {
                ds.putProperty(QDataSet.DEPEND_0, dep0); // DANGER
            }
            if ( rank2[0]==-1 ) {
                throw new IllegalArgumentException("bad parameter: rank2");
            }
            if ( bundleDescriptor==null ) {
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
            } else {
                logger.fine("removing bundleDescriptor because of rank2");
            }

            MutablePropertyDataSet mds;
            if ( rank2[0]==0 && rank2[1]==ds.length(0) ) {
                mds= ds;
            } else {
                mds= DataSetOps.leafTrim(ds, rank2[0], rank2[1]);
            }

            if ( bundle!=null ) {
                QDataSet labels = Ops.labels(parser.getFieldLabels());
                labels = labels.trim( bundle[0], bundle[1]);
                mds.putProperty(QDataSet.DEPEND_1, labels);
            }

            if ( depend1Label!=null ) {
                mds.putProperty(QDataSet.DEPEND_1, Ops.labels(depend1Label) );
            }

            if (depend1Labels != null) {
                QDataSet labels = Ops.labels(parser.getFieldLabels());
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
            
            String label= getParam( "label", null );
            if ( label!=null ) {
                mds.putProperty( QDataSet.LABEL, label );
            }

            String title= getParam( "title", null );
            if ( title!=null ) {
                mds.putProperty( QDataSet.TITLE, title );
            }
            return mds;

        } else {
            if (vds == null) {
                throw new IllegalArgumentException("didn't find column: " + column);
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
                vds.putProperty(QDataSet.DEPEND_0, dep0);
            }
            if ( eventListColumn!=null ) {
                Units u0= parser.getUnits(0);
                Units u1= parser.getUnits(1);
                if ( u0!=u1 ) {
                    if ( u1.isConvertableTo(u0.getOffsetUnits()) ) { // allow "s" to go with UTC
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
                }
                vds.putProperty(QDataSet.RENDER_TYPE,"eventsBar");
            }
            return vds;
        }

    }

    /**
     * returns the rank 2 dataset produced by the ascii table reader.
     * @param mon note monitor is used twice, so the progress bar jumps back.
     * @return
     * @throws java.lang.NumberFormatException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     */
    private DDataSet doReadFile(final ProgressMonitor mon) throws NumberFormatException, IOException, FileNotFoundException {

        String o;
        file = getFile(mon);

        if ( file.isDirectory() ) {
            throw new IOException("expected file but got directory");
        }

        parser = new AsciiParser();


        boolean fixedColumns = false;

        int columnCount = 0;

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
        String sFixedColumns = params.get("fixedColumns");
        if (sFixedColumns == null) {
            String format= params.get("format");
            if ( format!=null ) {
                AsciiParser.RegexParser p= parser.getRegexParserForFormat( format );
                columnCount = p.fieldCount();
                parser.setRecordParser(p);
                delim= " "; // this is because timeformats needs a delimiter
            } else if (delim == null) {
                AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
                if ( p == null) {
                    throw new IllegalArgumentException("no records found");
                }
                columnCount = p.fieldCount();
                delim = p.getDelim();
                p.setShowException(true);
            } else {
                if ( delim.equals(",") ) delim="COMMA";
                delim= delim.replaceAll("WHITESPACE", "\\s+");
                delim= delim.replaceAll("SPACE", " ");
                delim= delim.replaceAll("COMMA", ",");
                delim= delim.replaceAll("COLON", ":");
                delim= delim.replaceAll("TAB", "\t");
                delim= delim.replaceAll("whitespace", "\\s+");
                delim= delim.replaceAll("space", " ");
                delim= delim.replaceAll("comma", ",");
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
                parser.setUnits(i, Units.t2000);

                depend0 = o;
                timeColumn = i;
            }
        }

        o = params.get("timeFormat");
        if (o != null) {
            String timeFormat = o.replaceAll("\\+", " ");
            timeFormat = timeFormat.replaceAll("\\%", "\\$");
            timeFormat = timeFormat.replaceAll("\\{", "(");
            timeFormat = timeFormat.replaceAll("\\}", ")");
            String timeColumnName = params.get("time");
            timeColumn = timeColumnName == null ? 0 : parser.getFieldIndex(timeColumnName);

            String timeFormatDelim= delim;
            if ( delim==null ) timeFormatDelim= " ";
            timeFormats= timeFormat.split(timeFormatDelim,-2);
            if ( timeFormats.length==1 ) {
                timeFormatDelim= " ";
                timeFormats= timeFormat.split(timeFormatDelim,-2);
            }
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
                final Units u = Units.t2000;
                parser.setUnits(i, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {
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
                final Units u= Units.t2000;

                MultiFieldTimeParser timeFieldParser=
                        new MultiFieldTimeParser( timeColumn, timeFormats, timeParser, u );

                for ( int i=timeColumn; i<timeColumn+timeFormats.length; i++ ) {
                    parser.setFieldParser( i, timeFieldParser );
                    parser.setUnits( i, Units.dimensionless );
                }

                timeColumn= timeColumn + timeFormats.length - 1;
                if ( params.get("time")!=null )  {
                    depend0= parser.getFieldNames()[timeColumn];
                }
                parser.setUnits( timeColumn, u );

            } else {
                timeParser = TimeParser.create(timeFormat);
                final Units u = Units.t2000;
                parser.setUnits(timeColumn, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {

                    public double parseField(String field, int fieldIndex) throws ParseException {
                        return timeParser.parse(field).getTime(u);
                    }
                };
                parser.setFieldParser(timeColumn, timeFieldParser);

            }
        } else {
            timeParser = null;
        }

        o = params.get("depend0");
        if (o != null) {
            depend0 = o;
        }

        o = params.get("column");
        if (o != null) {
            column = o;
        }

        o = params.get("rank2");
        if (o != null) {
            rank2 = parseRangeStr(o, columnCount);
            column = null;
        }

        o = params.get("bundle");
        if (o != null) {
            bundle = parseRangeStr(o, columnCount);
            column = null;
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

        if (column == null && depend0 == null && rank2 == null) {
            if (parser.getFieldNames().length == 2) {
                depend0 = parser.getFieldNames()[0];
                column = parser.getFieldNames()[1];
            } else {
                column = parser.getFieldNames()[0];
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

        // rfe3489706: add support for HDMC's simple event list format, where the first two columns are start and stop times.
        o= params.get("eventListColumn");
        if ( o!=null ) {
            parser.setUnits(0,Units.us2000); // enough of a guess that this will find a good record.
            parser.setFieldParser(0, parser.UNITS_PARSER);
            parser.setFieldParser(1, parser.UNITS_PARSER);
            int icol = parser.getFieldIndex(o);
            EnumerationUnits eu= EnumerationUnits.create("events");
            parser.setUnits(icol,eu);
            parser.setFieldParser(icol,parser.ENUMERATION_PARSER);
            if ( icol>2 ) { //get the RGB color as well.
                String[] fields = new String[parser.getRecordParser().fieldCount()];
                String s= parser.readFirstParseableRecord(file.toString());
                parser.getRecordParser().splitRecord(s,fields);
                if ( fields[2].startsWith("x") || fields[2].startsWith("0x" ) ) { // kludge for RGB color third column starts with x
                    parser.setUnits(2,Units.dimensionless);
                    parser.setFieldParser( 2, new FieldParser() {
                        public double parseField(String field, int columnIndex) throws ParseException {
                            if ( field.startsWith("x") ) {
                                return Integer.decode( "0"+field ); // kludge for Scott
                            } else {
                                return Integer.decode( field );
                            }
                        }
                    });
                    eventListColorColumn= 2;
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
                                parser.setUnits(idep0, Units.us2000);
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
                                    parser.setUnits(icol, Units.us2000);
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
                                parser.setUnits(icol, Units.us2000);
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
            Units u = SemanticOps.lookupUnits(sunits);
            if (column != null) {
                int icol = parser.getFieldIndex(column);
                parser.setUnits(icol, u);
                parser.setFieldParser(icol, parser.UNITS_PARSER);
            }
        }

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
                if ( ch==10 ) {
                    if ( ipos>1 && buff.get(ipos-1)==13 ) ipos=ipos-1;
                    if ( foundNonEOL ) tailCount++;
                } else if ( ch==13 ) {
                    if ( foundNonEOL ) tailCount++;
                } else {
                    foundNonEOL= true;
                }
            }
            buff.position( tailCount<tailNum ? 0 : ipos+1 );
            InputStream in= new ByteBufferInputStream(buff);
            mon.setProgressMessage("reading "+file);
            ds1 = (DDataSet) parser.readStream( new InputStreamReader(in), mon); //DANGER
        } else {
            mon.setProgressMessage("reading "+file);
            ds1 = (DDataSet) parser.readFile(file.toString(), mon); //DANGER
        }

        return ds1;
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        if (ds == null) {
            return new HashMap<String, Object>();
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
            if ( v==null || !( v instanceof Number || v instanceof String || v instanceof org.das2.datum.Datum ) ) remove.add(k);
        }
        for ( String k: remove ) {
            props.remove(k);
        }

        return props;
    }

    /**
     * returns the field index of the name, which can be:
     *   a column name
     *   an implicit column name "field1"
     *   a column index (0 is the first column)
     *   a negative column index (-1 is the last column)
     * @param name
     * @param count
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
     * @param o
     * @param columnCount
     * @return
     * @throws java.lang.NumberFormatException
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
