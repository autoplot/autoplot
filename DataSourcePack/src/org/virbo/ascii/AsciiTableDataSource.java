/*
 * AsciiTableDataSource.java
 *
 * Created on March 31, 2007, 8:22 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.ascii;

import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.dsutil.AsciiParser;
import edu.uiowa.physics.pw.das.util.TimeParser;
import java.text.ParseException;
import org.virbo.dataset.DataSetUtil;

/**
 *
 * @author jbf
 */
public class AsciiTableDataSource extends AbstractDataSource {

    AsciiParser parser;
    File file;
    String column = null;
    String depend0 = null;
    /**
     * if non-null, then this is used to parse the times.  For a fixed-column parser, a field
     * handler is added to the parser.  For delim parser, then the
     */
    TimeParser timeParser;
    /**
     * the number of columns to combine into time
     */
    int timeColumns = -1;
    /**
     * the column containing times, or -1.
     */
    int timeColumn = -1;
    DDataSet ds = null;
    /**
     * non-null indicates the columns should be interpretted as rank2.  rank2[0] is first column, rank2[1] is last column exclusive.
     */
    int[] rank2 = null;
    private double validMin= Double.NEGATIVE_INFINITY;
    private double validMax= Double.POSITIVE_INFINITY;

    /** Creates a new instance of AsciiTableDataSource */
    public AsciiTableDataSource(URL url) throws FileNotFoundException, IOException {
        super(url);

    }

    public QDataSet getDataSet(ProgressMonitor mon) throws IOException {

        ds = doReadFile(mon);

        // combine times if necessary
        if (timeColumns > 1) {
            final Units u = Units.t2000;
            // replace the first column with the datum time
            // timeParser knows the order of the digits.
            for (int i = 0; i < ds.length(); i++) {
                for (int j = 0; j < timeColumns; j++) {
                    timeParser.setDigit(j, (int) ds.value(i, timeColumn + j));
                }
                ds.putValue(i, timeColumn, timeParser.getTime(Units.t2000));
            }
            parser.setUnits(timeColumn, Units.t2000);
        }

        DDataSet vds = null;
        DDataSet dep0 = null;

        if (column != null) {
            int icol = parser.getFieldIndex(column);
            if (icol == -1) {
                if (Pattern.matches("field[0-9]+", column)) {
                    icol = Integer.parseInt(column.substring(5));
                }
            }
            vds = DDataSet.copy(DataSetOps.slice1(ds, icol));
            vds.putProperty( QDataSet.UNITS, parser.getUnits(icol));
            if ( validMax!=Double.POSITIVE_INFINITY ) vds.putProperty( QDataSet.VALID_MAX, validMax );
            if ( validMin!=Double.NEGATIVE_INFINITY ) vds.putProperty( QDataSet.VALID_MIN, validMin );                
        }

        if (depend0 != null) {
            int icol = parser.getFieldIndex(depend0);
            dep0 = DDataSet.copy(DataSetOps.slice1(ds, icol));
            dep0.putProperty(QDataSet.UNITS, parser.getUnits(icol));
            if (DataSetUtil.isMonotonic(dep0)) {
                dep0.putProperty(DDataSet.MONOTONIC, Boolean.TRUE);
            }
        }

        if (rank2 != null) {
            if (dep0 != null) {
                ds.putProperty(QDataSet.DEPEND_0, dep0); // DANGER
            }
            ds.putProperty( QDataSet.UNITS, parser.getUnits(rank2[0]) );
            if ( validMax!=Double.POSITIVE_INFINITY ) ds.putProperty( QDataSet.VALID_MAX, validMax );
            if ( validMin!=Double.NEGATIVE_INFINITY ) ds.putProperty( QDataSet.VALID_MIN, validMin );           
            
            return DataSetOps.leafTrim(ds, rank2[0], rank2[1]);
        } else {
            if (vds == null) {
                throw new IllegalArgumentException("didn't find column: " + column);
            }
            if (dep0 != null) {
                vds.putProperty(QDataSet.DEPEND_0, dep0);
            }
            return vds;
        }

    }

    /**
     * returns the rank 2 dataset produced by the ascii table reader.
     * @param mon
     * @return
     * @throws java.lang.NumberFormatException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     */
    private DDataSet doReadFile(final ProgressMonitor mon) throws NumberFormatException, IOException, FileNotFoundException {

        Object o;
        file = DataSetURL.getFile(url, mon);

        parser = new AsciiParser();


        boolean fixedColumns = false;

        int columnCount = 0;

        /**
         * if non-null, this is the delim we are using to parse the file.
         */
        String delim;

        o = params.get("skip");
        if (o != null) {
            parser.setSkipLines(Integer.parseInt((String) o));
        }

        parser.setKeepFileHeader(true);

        o = params.get("comment");
        if (o != null) {
            parser.setCommentPrefix((String) o);
        }

        o = params.get("delim");
        if (o != null) {
            delim = (String) o;
        } else {
            delim = null;
        }
        if (delim == null) {
            AsciiParser.DelimParser p = parser.guessDelimParser(parser.readFirstRecord(file.toString()));
            columnCount = p.fieldCount();
            delim = p.getDelim();
        } else {
            columnCount = parser.setDelimParser(file.toString(), delim).fieldCount();
        }
        //parser.setPropertyPattern( Pattern.compile("^#\\s*(.+)\\s*\\:\\s*(.+)\\s*") );
        parser.setPropertyPattern(AsciiParser.NAME_COLON_VALUE_PATTERN);

        o = params.get("fixedColumns");
        if (o != null) {
            String s = (String) o;
            AsciiParser.RecordParser p = parser.setFixedColumnsParser(file.toString(), "\\s+");
            if (o.equals("")) {
                columnCount = p.fieldCount();
            } else if (s.contains(",")) { // 0-10,20-34
                String[] ss = s.split(",");
                int[] starts = new int[ss.length];
                int[] widths = new int[ss.length];
                AsciiParser.FieldParser[] fparsers = new AsciiParser.FieldParser[ss.length];
                for (int i = 0; i < ss.length; i++) {
                    String[] ss2 = ss[i].split("-");
                    starts[i] = Integer.parseInt(ss2[0]);
                    widths[i] = Integer.parseInt(ss2[1]) - starts[i];
                    fparsers[i] = AsciiParser.DOUBLE_PARSER;
                }
                p = parser.setFixedColumnsParser(starts, widths, fparsers);

            } else {
                columnCount = Integer.parseInt((String) o);
            }
            parser.setPropertyPattern(null); // don't look for these for speed
            fixedColumns = true;
            delim = null;
        }

        o = params.get("columnCount");
        if (columnCount == 0) {
            if (o != null) {
                columnCount = Integer.parseInt((String) o);
            } else {
                columnCount = AsciiParser.guessFieldCount(file.toString());
            }
        }

        o = params.get("fill");
        if (o != null) {
            parser.setFillValue(Double.parseDouble((String) o));
        }

        o= params.get("validMin");
        if (o != null) {
            this.validMin= Double.parseDouble((String) o);
        }
        
        o= params.get("validMax");
        if (o != null) {
            this.validMax= Double.parseDouble((String) o);
        }
        
        o = params.get("timeFormat");
        if (o != null) {
            String timeFormat = (String) o;
            timeParser = TimeParser.create((String) o);
            String timeColumnName = (String) params.get("time");

            if (delim != null && timeFormat.split("%").length > 1) {
                timeColumns = (timeFormat.split("%").length) - 1;  //TODO: consider simply splitting on %, regardless of delim.

            } else {
                int i = parser.getFieldIndex(timeColumnName);
                final Units u = Units.t2000;
                parser.setUnits(i, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {

                    public double parseField(String field, int fieldIndex) throws ParseException {
                        return timeParser.parse(field).getTime(u);
                    }
                };
                parser.setFieldParser(i, timeFieldParser);

            }
        } else {
            timeParser = null;
        }

        o = params.get("time");
        if (o != null) {
            int i = parser.getFieldIndex((String) o);
            if (i == -1) {
                System.err.println("field not found for time parameter: " + o);
                if (timeColumns > 1) {
                    timeColumns = -1;
                }
            } else {
                if (timeParser == null) {
                    parser.setFieldParser(i, parser.UNITS_PARSER);
                    parser.setUnits(i, Units.t2000);
                }
                depend0 = (String) o;
                timeColumn = i;
            }
        }

        o = params.get("depend0");
        if (o != null) {
            depend0 = (String) o;
        }

        o = params.get("column");
        if (o != null) {
            column = (String) o;
        }

        o = params.get("rank2");
        if (o != null) {
            String s = (String) o;
            int first = 0;
            int last = columnCount;
            if (s.contains(":")) {
                String[] ss = s.split(":");
                if (ss[0].length() > 0) {
                    first = Integer.parseInt(ss[0]);
                    if (first < 0) {
                        first = columnCount + first;
                    }
                }
                if (ss.length > 1 && ss[1].length() > 0) {
                    last = Integer.parseInt(ss[1]);
                    if (last < 0) {
                        last = columnCount + last;
                    }
                }
            }
            rank2 = new int[]{first, last};
            column = null;
        }

        o = params.get("arg_0");
        if (o != null && o.equals("rank2")) {
            rank2 = new int[]{0, columnCount};
            column = null;
        }

        if (column == null && depend0 == null && rank2 == null) {
            if (parser.getFieldNames().length > 1) {
                depend0 = parser.getFieldNames()[0];
                column = parser.getFieldNames()[1];
            } else {
                column = parser.getFieldNames()[0];
            }
        }

        // check to see if the depend0 column appears to be times.  I Promise I won't open the file again until it's read in.
        if (timeColumn == -1 && depend0 != null) {
            String s = parser.readFirstParseableRecord(file.toString());
            if (s != null) {
                String[] fields = parser.getRecordParser().fields(s);
                int idep0 = parser.getFieldIndex(depend0);
                if (idep0 != -1) { // deal with -1 later
                    String field = fields[idep0];
                    try {
                        Units.us2000.parse(field);
                        parser.setUnits(idep0, Units.us2000);
                        parser.setFieldParser(idep0, parser.UNITS_PARSER);
                    } catch (ParseException ex) {
                    }
                }
            }
        }

        // --- done configuration, now read ---
        DDataSet ds1 = (DDataSet) parser.readFile(file.toString(), mon); //DANGER

        return ds1;
    }

    @Override
    public Map<String, Object> getMetaData(ProgressMonitor mon) throws Exception {
        if (ds == null) {
            return new HashMap<String, Object>();
        }
        Map<String, Object> props = (Map<String, Object>) ds.property(QDataSet.USER_PROPERTIES);

        return props;
    }
}
