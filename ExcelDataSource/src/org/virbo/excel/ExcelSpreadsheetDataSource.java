/*
 * ExcelSpreadsheetDataSource.java
 *
 * Created on April 1, 2007, 6:55 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.excel;

import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;

/**
 *
 * @author jbf
 */
public class ExcelSpreadsheetDataSource extends AbstractDataSource {

    POIFSFileSystem fs;
    HSSFSheet sheet;
    ExcelSpreadsheetDataSet data;

    /** Creates a new instance of ExcelSpreadsheetDataSource.
     * file://c:/myfile.xls?column=N&depend0=G
     *   parameters:
     *     column=id  the column ID for the data
     *     depend0=id the column ID for the x tags
     * file://C:/iowaCitySales2004-2006.latlong.xls?column=M[2:]&depend0=B[2:]&plane0=C[2:]
     */
    public ExcelSpreadsheetDataSource(URL url) throws IOException {
	super(url);
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws IOException {
	File file = DataSetURL.getFile(url, mon);
	InputStream in = new FileInputStream(file);
	fs = new POIFSFileSystem(in);
	HSSFWorkbook wb = new HSSFWorkbook(fs);

	String query = url.getQuery(); // the part after the ?
	Map m = DataSetURL.parseParams(query);

	String ssheet = (String) m.get("sheet");
	if (ssheet == null) {
	    sheet = wb.getSheetAt(0);
	} else {
	    sheet = wb.getSheet(ssheet);
	}
	if ( sheet==null ) {
	    throw new IllegalArgumentException("Sheet not found: "+ssheet);
	}

	String firstRowString= (String) m.get("firstRow");
	int firstRow= firstRowString==null ? -1 : Integer.parseInt(firstRowString)-1;
	
	String d = (String) m.get("column");
	int[] spec= parseDataSetSpec(d,firstRow,-1);
	data = new ExcelSpreadsheetDataSet( (short)spec[0], spec[1], spec[2] );

	d = (String) m.get("depend0");
	if (d != null) {
	    spec= parseDataSetSpec(d,firstRow,-1);
	    ExcelSpreadsheetDataSet depend0 = new ExcelSpreadsheetDataSet( (short)spec[0], spec[1], spec[2] );
	    data.putProperty(QDataSet.DEPEND_0, depend0);
	}

	d = (String) m.get("plane0");
	if (d != null) {
	    spec= parseDataSetSpec(d,firstRow,-1);
	    ExcelSpreadsheetDataSet p0 = new ExcelSpreadsheetDataSet( (short)spec[0], spec[1], spec[2] );
	    data.putProperty(QDataSet.PLANE_0, p0);
	}
	return data;
    }

    /**
     * Returns [ columnNumber, first, last ] 
     * @param spec
     * @return
     */
    private int[] parseDataSetSpec(String spec,int firstRow, int lastRow ) {
	Pattern p = Pattern.compile("([A-Z]+)(\\[(\\d+):(\\d+)?\\])?");
	Matcher m = p.matcher(spec);

	short columnNumber;

	if (!m.matches()) {
	    throw new IllegalArgumentException("bad spec!");
	} else {
	    String col = m.group(1);
	    columnNumber = (short) (col.charAt(0) - 'A');
	    if (m.group(3) == null) {
		if ( firstRow==-1 ) firstRow = 0;
		if ( lastRow==-1 ) lastRow = sheet.getLastRowNum();
	    } else {
		firstRow = Integer.parseInt(m.group(3));
		if (m.group(4) == null) {
		    if ( lastRow==-1 ) lastRow = sheet.getLastRowNum();
		} else {
		    lastRow = Integer.parseInt(m.group(4));
		}
	    }
	    return new int[] { columnNumber, firstRow, lastRow };
	}


    }

    class ExcelSpreadsheetDataSet implements QDataSet {

	short columnNumber;
	int firstRow;
	int length;
	Map properties = new HashMap();

	/**
	 * @param firstRow is the first row to read.
	 * @param lastRow is the last row number, exclusive.
	 */
	ExcelSpreadsheetDataSet( short columnNumber, int firstRow, int lastRow ) {
	    this.columnNumber = columnNumber;
	    this.firstRow = firstRow;
	    this.length = lastRow - firstRow;
	    HSSFCell cell = sheet.getRow(firstRow).getCell(columnNumber);

	}

	public int rank() {
	    return 1;
	}

	public double value(int i) {
	    HSSFRow row = null;
	    HSSFCell cell = null;
	    try {
		row = sheet.getRow(i + firstRow);
		cell = row.getCell(columnNumber);
		return cell.getNumericCellValue();
	    } catch (RuntimeException e) {
		String cellID = String.valueOf((char) ('A' + columnNumber)) + (firstRow + i);
		return -1e31;
	    }
	}

	public double value(int i0, int i1) {
	    throw new IllegalArgumentException("rank");
	}

	public double value(int i0, int i1, int i2) {
	    throw new IllegalArgumentException("rank");
	}

	public Object property(String name) {
	    return properties.get(name);
	}

	public Object property(String name, int i) {
	    return properties.get(name);
	}

	public Object property(String name, int i0, int i1) {
	    return properties.get(name);
	}

	public void putProperty(String name, Object value) {
	    properties.put(name, value);
	}

	public int length() {
	    return length;
	}

	public int length(int i) {
	    throw new IllegalArgumentException("ranklimit");
	}

	public int length(int i, int j) {
	    throw new IllegalArgumentException("ranklimit");
	}

	public String toString() {
	    return DataSetUtil.toString(this);
	}
    }
}
