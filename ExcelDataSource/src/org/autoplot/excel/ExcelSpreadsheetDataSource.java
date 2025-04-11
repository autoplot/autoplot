
package org.autoplot.excel;

import java.util.logging.Level;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;

/**
 * Creates QDataSets from an Excel spreadsheet using Apache POI library.<ul>
 * <li>http://www.autoplot.org/data/swe-np.xls?column=data&depend0=dep0
 * <li>http://sarahandjeremy.net/~jbf/autoplot/data/hudson_data/xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency
 * <li>http://www.icip.iastate.edu/sites/default/files/uploads/tables/agriculture/ag-land-hist.xls?column=C14:BM14
 * <li>file:///home/jbf/ct/hudson/data/xls/c2-70landvalues.xls?sheet=County&column=BM&depend0=A
 * </ul>
 * @author jbf
 */
public class ExcelSpreadsheetDataSource extends AbstractDataSource {

    POIFSFileSystem fs;
    HSSFSheet sheet;
    boolean isUsing1904DateWindowing;
    ExcelSpreadsheetDataSet data;
    static Logger logger= Logger.getLogger("org.autoplot.ExcelSpreadsheetDataSource");

    /** Creates a new instance of ExcelSpreadsheetDataSource.
     * file://c:/myfile.xls?column=N&depend0=G
     *   parameters:
     *     column=id  the column ID for the data
     *     depend0=id the column ID for the x tags
     * file://C:/iowaCitySales2004-2006.latlong.xls?column=M[2:]&depend0=B[2:]&plane0=C[2:]
     * @param uri
     * @throws java.io.IOException
     */
    public ExcelSpreadsheetDataSource(URI uri) throws IOException {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws IOException {
        File file = getFile(mon);
        InputStream in = new FileInputStream(file);
        fs = new POIFSFileSystem(in);
        HSSFWorkbook wb = new HSSFWorkbook(fs);

        String ssheet = (String) params.get("sheet");
        if (ssheet == null) {
            sheet = wb.getSheetAt(0);
        } else {
            ssheet= DataSetURI.maybePlusToSpace(ssheet);
            sheet = wb.getSheet(ssheet);
            if ( sheet==null ) throw new IllegalArgumentException("no such sheet: "+ssheet);
            logger.log(Level.FINE, "found sheet {0} with {1} rows", new Object[]{ssheet, sheet.getLastRowNum()});
        }
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + ssheet);
        }

        String firstRowString = (String) params.get("firstRow");
        int firstRow = firstRowString == null ? -1 : Integer.parseInt(firstRowString) - 1;

        
        String d = (String) params.get("column");

        int[] spec = parseDataSetSpec(d, Math.max(0,firstRow), -1);
        
        firstRow= spec[2];
        
        boolean labels=true;
        HSSFRow row= sheet.getRow(firstRow);
        for ( short i=0; labels && i<row.getLastCellNum(); i++ ) {
            HSSFCell cell= row.getCell(i);
            if ( cell==null ) continue;
            if ( cell.getCellType() == 0 ) {
                labels= false;
            }
            if ( i==spec[0] &&  cell.getCellType() != 1 ) {  // for the dataset column, this must be a string.
                labels= false;
            }
        }
        
        String recCountString= (String) params.get("recCount" ) ;
        if ( recCountString!=null ) {
            int recCount= Integer.parseInt(recCountString);
            spec[3]= Math.min( spec[3], spec[2]+recCount );
        }
        
        data = new ExcelSpreadsheetDataSet( (short)spec[0], (short)spec[1], spec[2], spec[3], labels );
        if ( firstRow==0 && data.firstRow > 1 ) {//TODO stupid kludge for http://www.autoplot.org/data/swe-np.xls?column=data&depend0=dep0
            firstRow= data.firstRow;
        }
        if ( d.length()>1 ) data.putProperty( QDataSet.NAME, d );

        d = (String) params.get("depend0");
        if (d != null) {
            int[] spec2 = parseDataSetSpec(d, firstRow, -1);
            spec[0]= spec2[0];
            ExcelSpreadsheetDataSet depend0 = new ExcelSpreadsheetDataSet( (short)spec[0], (short)spec[1], spec[2], spec[3], labels );
            if ( d.length()>1 ) depend0.putProperty( QDataSet.NAME, d );        
            data.putProperty(QDataSet.DEPEND_0, depend0);
            if ( data.getFirstRow()!=depend0.getFirstRow() ) {
                throw new IllegalArgumentException("rows must not contain empty cells in the first row");
            }
        }

        d = (String) params.get("plane0");
        if (d != null) {
            int[] spec2 = parseDataSetSpec(d, firstRow, -1);
            spec[0]= spec2[0];
            ExcelSpreadsheetDataSet p0 = new ExcelSpreadsheetDataSet( (short)spec[0], (short)spec[1], spec[2], spec[3], labels );
            if ( d.length()>1 ) p0.putProperty( QDataSet.NAME, d );        
            data.putProperty(QDataSet.PLANE_0, p0);
            if ( data.getFirstRow()!=p0.getFirstRow() ) {
                throw new IllegalArgumentException("rows must not contain empty cells in the first row");
            }
        }

        //isUsing1904DateWindowing= wb.isUsing1904DateWindowing(); // TODO: upgrade to 3.6.
        isUsing1904DateWindowing= false;
        
        return data;
    }

    /**
     * parse spec into [icstart,icend,jrstart,jrend]<ul>
     * <li>AC11:AC23
     * <li>A1:10
     * <li>A
     * </ul>
     * Returns [ columnNumber, lastColumnNumber, first, last ].  lastColumnNumber=-1 means just one column (rank 1).
     * @param spec
     * @param firstRow
     * @param lastRow exclusive last row index, or -1.
     * @return [ columnNumber, lastColumnNumber, first, lastRowExclusive ].  lastColumnNumber=-1 means just one column (rank 1).
     */
    private int[] parseDataSetSpec( String spec, int firstRow, int lastRow ) {

        short columnNumber;
        short columnNumber1;

        Pattern pc= Pattern.compile("([a-zA-Z_\\d]+)");
        Matcher m= pc.matcher(spec);
        if ( m.matches() ) { // kludge for http://www.autoplot.org/data/swe-np.xls?column=data&depend0=dep0
            columnNumber= getColumnNumber(m.group(1), firstRow);
            return new int[] { columnNumber, -1, Math.max(0,firstRow), sheet.getLastRowNum()+1 };
        }

        Pattern p = Pattern.compile("([^\\s:]+)((\\d+):([a-zA-Z_]*)?(\\d+)?)?");
        m = p.matcher(spec);


        if (!m.matches()) {
            throw new IllegalArgumentException("bad spec!");
        } else {
            String col = m.group(1);
            if (m.group(3) == null) {
                if (firstRow == -1) {
                    firstRow = 0;
                }
                if (lastRow == -1) {
                    lastRow = sheet.getLastRowNum()+1;
                }
            } else {
                firstRow = Integer.parseInt(m.group(3));
                if (m.group(5) == null) {
                    if (lastRow == -1) {
                        lastRow = sheet.getLastRowNum()+1;
                    }
                } else {
                    lastRow = Integer.parseInt(m.group(5))+1;
                }
            }
            if ( m.group(4)!=null ) {
                String c= m.group(4);
                if ( c.length()==0 ) c= m.group(1); // easy mistake to make.
                columnNumber1= getColumnNumber( c, firstRow);
            } else {
                columnNumber1= -1;
            }
            columnNumber = getColumnNumber(col, firstRow);
            if ( columnNumber1>-1 && columnNumber1==columnNumber ) columnNumber1=-1;
            return new int[]{columnNumber, columnNumber1, firstRow, lastRow};
        }
    }

    /**
     *
     * @param id
     * @param firstRow
     * @throws IllegalArgumentException if there is no row in the sheet.
     * @return
     */
    private short getColumnNumber(String id, int firstRow) {
        return ExcelUtil.getColumnNumber( sheet, id, firstRow);
    }
    
    protected static int findFirstRow( HSSFSheet sheet, int firstRow ) {
        int ilastRow= sheet.getPhysicalNumberOfRows();
        int inextRow = firstRow;
        HSSFRow nextRow;        // first row of data        

        int dataCellCount = 0;
        while (inextRow < ilastRow && inextRow < firstRow + 4) {
            nextRow = sheet.getRow(inextRow);
            if (nextRow != null) {
                int n = nextRow.getLastCellNum();
                for (int i = nextRow.getFirstCellNum(); i < n; i++) {
                    HSSFCell nextCell = nextRow.getCell((short) i);
                    if (nextCell != null && nextCell.getCellType() == 0) {
                        dataCellCount++;
                    }
                }
            }
            if (dataCellCount == 0) {
                inextRow++;
            } else {
                break;
            }
        }
        return inextRow;
    }

    private class ExcelSpreadsheetDataSet extends AbstractDataSet {

        short columnNumber;
        int firstRow;
        int length;
        int length1;
        boolean isDate;
        Units units;
        boolean transpose= false;
        int rank;
        
        /**
         * @param lastColumnNumber if not -1, then the rank 2 column number.
         * @param firstRow is the first row to read.  0 is the first row.
         * @param lastRow is the last row number, exclusive.  If -1, then rank 1 row.
         */
        ExcelSpreadsheetDataSet( short columnNumber, short lastColumnNumber, int firstRow, int lastRow, boolean firstRowIsLabels ) {
            
            if ( firstRowIsLabels ) {
                firstRow= findFirstRow(sheet, firstRow);
            }

            this.columnNumber = columnNumber;
            this.firstRow = firstRow;
            if ( lastRow==-1 && lastColumnNumber!=-1 ) {
                this.length = lastColumnNumber - columnNumber;
                this.transpose= true;
            } else {
                this.length = lastRow - firstRow;
            }
            if ( lastRow>-1 && lastColumnNumber>-1 ) {
                this.rank=2;
                if ( this.transpose ) {
                    this.length1= lastRow - firstRow;
                } else {
                    if ( this.length==0 ) {
                        this.length= lastColumnNumber - columnNumber;
                        this.rank=1;
                        this.transpose= true;
                    } else {
                        this.length1= lastColumnNumber - columnNumber;
                    }
                }
            } else {
                this.rank=1;
            }

            HSSFRow row= sheet.getRow(this.firstRow);
            HSSFCell cell = row.getCell(columnNumber);
            if ( cell==null ) {
                row= null;
            }
            while ( row==null && firstRow<lastRow ) {
                firstRow++;
                row= sheet.getRow(firstRow);
                if ( row!=null ) cell = row.getCell(columnNumber);
                if ( cell==null ) {
                    row= null;
                }
            }
            if ( row==null ) {
                throw new IllegalArgumentException("unable to identify first row");
            }

            this.firstRow= firstRow;
            cell = row.getCell(columnNumber);
            units= Units.dimensionless;
            if ( cell.getCellType()!=HSSFCell.CELL_TYPE_STRING ) {
                isDate = HSSFDateUtil.isCellDateFormatted(cell);
                if (isDate) {
                    if ( isUsing1904DateWindowing ) {
                        //throw new IllegalArgumentException("isUsing1904DateWindowing is not implemented");
                        units= Units.lookupUnits("days since 1903-12-31T00:00Z"); // this is a guess and is untested, because my old version of apache poi doesn't have the check.
                        properties.put(QDataSet.VALID_MIN,0);
                    } else {
                        units= Units.lookupUnits("days since 1899-12-30T00:00Z"); // "Excel thinks 2/29/1900 is a valid date, which it isn't" // see org.apache.poi.ss.usermodel.DateUtil source
                        properties.put(QDataSet.VALID_MIN,61); // get past the leap day to avoid fancy code that would handle this.
                    }
                    properties.put(QDataSet.UNITS, units);
                }
            } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_STRING ) {
                String s= cell.getStringCellValue();
                try {
                    Units.t1970.parse(s);
                    properties.put(QDataSet.UNITS, Units.t1970);
                    units= Units.t1970;
                } catch ( ParseException ex ) {
                    properties.put(QDataSet.UNITS, Units.dimensionless);
                }
            }
        }

        public int getFirstRow() {
            return firstRow;
        }
        
        @Override
        public int rank() {
            return rank;
        }

        @Override
        public double value(int i) {
            HSSFRow row;
            HSSFCell cell;
            try {
                if ( transpose ) {
                    row = sheet.getRow(firstRow);
                    cell = row.getCell(columnNumber + i );
                } else {
                    row = sheet.getRow(i + firstRow);
                    cell = row.getCell(columnNumber);
                }
                if (isDate) {
                    double d= cell.getNumericCellValue();
                    return d;
                } else {
                    if ( cell==null ) {
                        return Double.NaN;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_NUMERIC ) {
                        double d = cell.getNumericCellValue();
                        return d;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_STRING ) {
                        try {
                            double d= units.parse(cell.getStringCellValue()).doubleValue(units);
                            return d;
                        } catch ( ParseException ex ) {
                            return Double.NaN;
                        }
                    } else {
                        return Double.NaN;
                    }
                }
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }

        @Override
        public double value(int i,int j) {
            HSSFRow row;
            HSSFCell cell;
            try {
                if ( transpose ) {
                    row = sheet.getRow( j + firstRow );
                    cell = row.getCell( i + columnNumber );
                } else {
                    row = sheet.getRow( i + firstRow );
                    cell = row.getCell( j + columnNumber );
                }
                if (isDate) {
                    double d= cell.getNumericCellValue();
                    return d;
                } else {
                    if ( cell==null ) {
                        return Double.NaN;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_NUMERIC ) {
                        double d = cell.getNumericCellValue();
                        return d;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_STRING ) {
                        try {
                            double d= units.parse(cell.getStringCellValue()).doubleValue(units);
                            return d;
                        } catch ( ParseException ex ) {
                            return Double.NaN;
                        }
                    } else {
                        return Double.NaN;
                    }
                }
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public int length( int i ) {
            return length1;
        }

    }
}
