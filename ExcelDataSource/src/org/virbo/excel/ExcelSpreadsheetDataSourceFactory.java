/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class ExcelSpreadsheetDataSourceFactory implements DataSourceFactory {
    private static final String FIRST_ROW_DOC = "the row that contains the either the first record of data, or data column headings.  1 is the first row.";

    public DataSource getDataSource(URL url) throws IOException {
	return new ExcelSpreadsheetDataSource(url);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws IOException {
	List<CompletionContext> result = new ArrayList<CompletionContext>();
	if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
	    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column="));
	    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0="));
	    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "plane0="));
	    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "sheet="));
	    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "firstRow=",FIRST_ROW_DOC));
	} else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
	    String param = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
	    if (param.equals("column")) {
		result.addAll( toCC( CompletionContext.CONTEXT_PARAMETER_VALUE, getColumns(cc, mon), null ));
	    } else if (param.equals("depend0")) {
		result.addAll( toCC( CompletionContext.CONTEXT_PARAMETER_VALUE, getColumns(cc, mon), null ));
	    } else if (param.equals("plane0")) {
		result.addAll( toCC( CompletionContext.CONTEXT_PARAMETER_VALUE, getColumns(cc, mon), null ));
	    } else if (param.equals("sheet")) {
		result.addAll( toCC( CompletionContext.CONTEXT_PARAMETER_VALUE, getSheets(cc, mon), "worksheet source" ) );
	    } else if ( param.equals("firstRow") ) {
		result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", FIRST_ROW_DOC));
	    }
	}
	return result;
    }
    
    List<CompletionContext> toCC( Object context, List<String> results, String doc ) {
	List<CompletionContext> result= new ArrayList<CompletionContext>();
	for ( String s: results ) {
	    result.add( new CompletionContext( context, s, doc ) );
	}
	return result;
    }

    private HSSFWorkbook getWorkbook( URL url, ProgressMonitor mon ) throws IOException {
	File file = DataSetURL.getFile(url, mon);
	InputStream in = new FileInputStream(file);
	POIFSFileSystem fs = new POIFSFileSystem(in);
	HSSFWorkbook wb = new HSSFWorkbook(fs);
	return wb;
    }

    private List<String> getSheets(CompletionContext cc, ProgressMonitor mon) throws IOException {
	HSSFWorkbook wb= getWorkbook(cc.resource, mon);
	List<String> result= new ArrayList<String>();
	for ( int i=0; i<wb.getNumberOfSheets(); i++ ) {
	    String s= wb.getSheetName(i);
	    result.add( s );
	}
	return result;
    }
	    
    private List<String> getColumns( CompletionContext cc, ProgressMonitor mon) throws IOException {
	HSSFWorkbook wb= getWorkbook(cc.resource, mon);
	Map params= DataSetURL.parseParams(cc.params);
	List<String> result= new ArrayList<String>();
	HSSFSheet sheet;
	String ssheet= (String) params.get("sheet");
	if ( ssheet==null ) {
	    sheet= wb.getSheetAt(0);
	} else {
	    sheet= wb.getSheet(ssheet);
	}
	
	String firstRowString= (String) params.get("firstRow");
	int firstRow= firstRowString==null ? 0 : Integer.parseInt(firstRowString)-1;
	HSSFRow row= sheet.getRow(firstRow);
	
	row.getLastCellNum();
	for ( int i=row.getFirstCellNum(); i<row.getLastCellNum(); i++ ) {
	    HSSFCell cell= row.getCell((short)i);
	    if ( cell!=null && cell.getCellType()==0 ) { // 1=String
		result.add( ""+(char)(i+'A') );
	    }
	}
	return result; 
    }
		
    public MetadataModel getMetadataModel(URL url) {
	return MetadataModel.createNullModel();
    }

    public boolean reject(String surl, ProgressMonitor mon) {
	return !surl.contains("column=");
    }

    public String urlForServer(String surl) {
	return surl; // TODO
    }
}
