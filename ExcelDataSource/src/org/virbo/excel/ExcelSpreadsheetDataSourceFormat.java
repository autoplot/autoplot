/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.excel;

import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.das2.datum.EnumerationUnits;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;

/**
 * Format the QDataSet into Ascii tables.  
 * @author jbf
 */
public class ExcelSpreadsheetDataSourceFormat implements DataSourceFormat {

    private void formatRank2( HSSFSheet sheet, QDataSet data, ProgressMonitor mon) throws IOException {

        int irow= 0;
        short icell=0;
        HSSFRow row= sheet.createRow(irow++);
        HSSFCell cell;
        
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        
        boolean okay= DataSetUtil.checkQube(data); // some RBSP/ECT data has rank 2 DEPEND_1 when it is really a qube.
        
        if ( !okay ) {
            throw new IllegalArgumentException("Data is not a qube.  Each record must have the same DEPEND_1.");
        }
        
        if ( dep1!=null && dep1.rank()==2 ) {
            dep1= dep1.slice(0);
        }
                
        if (dep1 != null) {
            if (dep0 != null) {
                String l = (String) dep0.property(QDataSet.LABEL);
                cell= row.createCell(icell++);
                cell.setCellValue( (l == null ? "dep0" : l) );
            }
            Units u = (Units) dep1.property(QDataSet.UNITS);
            if (u == null) {
                u = Units.dimensionless;
            }
            int i;
            for (  i = 0; i < dep1.length(); i++) {
                cell= row.createCell(icell++);
                
                Datum d= u.createDatum(dep1.value(i));

                if ( u instanceof EnumerationUnits ) {
                    cell.setCellValue( d.toString() );
                } else {
                    setCellValue( cell, d);
                }
            }
        }

        Units u0 = null;
        if (dep0 != null) {
            u0 = (Units) dep0.property(QDataSet.UNITS);
            if (u0 == null) {
                u0 = Units.dimensionless;
            }
        }

        Units u = (Units) data.property(QDataSet.UNITS);
        if ( u==null ) {
            u= Units.dimensionless;
        }
        
        mon.setTaskSize(data.length());
        mon.started();
        
        for (int i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            
            row= sheet.createRow(irow++);
            icell= 0;
            
            if (dep0 != null) {
                cell= row.createCell(icell++);
                setCellValue( cell, u0.createDatum(dep0.value(i)) );
            }

            int j;
            for ( j = 0; j < data.length(i); j++) {
                cell= row.createCell(icell++);
                setCellValue( cell, u.createDatum(data.value(i, j)) );
            }
        }
        
        mon.finished();
    }

    private void formatRank1( HSSFSheet sheet, QDataSet data, ProgressMonitor mon) throws IOException {
                
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        int irow= 0;
        HSSFRow row= sheet.createRow(irow++);
        HSSFCell cell;
        short icell=0;
        
        if (dep0 != null) {
            String l = (String) dep0.property(QDataSet.LABEL);
            cell= row.createCell(icell++);                    
            cell.setCellValue( new HSSFRichTextString( (l == null ? "dep0" : l) ) );
        }

        {
            String l = (String) data.property(QDataSet.LABEL);
            cell= row.createCell(icell++);
            cell.setCellValue( new HSSFRichTextString( (l == null ? "data" : l) ) );
        }

        Units u0 = null;
        if (dep0 != null) {
            u0 = (Units) dep0.property(QDataSet.UNITS);
            if (u0 == null) {
                u0 = Units.dimensionless;
            }
        }
        Units u = (Units) data.property(QDataSet.UNITS);
        if ( u==null ) u= Units.dimensionless;
        
        mon.setTaskSize(data.length());
        mon.started();
        
        for (int i = 0; i < data.length(); i++ ) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            
            row= sheet.createRow(irow++);
            icell= 0;
            
            if (dep0 != null) {
                cell= row.createCell(icell++);
                setCellValue( cell, u0.createDatum(dep0.value(i)) );
            }

            cell= row.createCell(icell++);
            setCellValue( cell, u.createDatum(data.value(i)) );

        }
        
        mon.finished();
    }

    HSSFCellStyle dateCellStyle;
    
    Calendar c= Calendar.getInstance( TimeZone.getTimeZone("GMT") );
    
    private void setCellValue( HSSFCell cell, Datum datum ) {
        Units u= datum.getUnits();
        if ( UnitsUtil.isTimeLocation(u) ) {
            c.setTimeInMillis( (long)( datum.doubleValue(Units.t1970) * 1000 ) );
            cell.setCellValue( c );
            cell.setCellStyle(dateCellStyle);
        } else {
            cell.setCellValue( datum.doubleValue(u) );
        }
    }
    
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {
	URISplit split= URISplit.parse(uri);
        java.util.Map<String,String> params= URISplit.parseParams(split.params);

        FileOutputStream out = new FileOutputStream( new File( split.resourceUri ) );
        try {
            HSSFWorkbook wb= new HSSFWorkbook();
            HSSFSheet sheet= wb.createSheet();
            dateCellStyle= wb.createCellStyle();
            dateCellStyle.setDataFormat( HSSFDataFormat.getBuiltinFormat("m/d/yy h:mm") );

            if (data.rank() == 2) {
                formatRank2(sheet, data, mon);
            } else if (data.rank() == 1) {
                formatRank1(sheet, data, mon);
            }

            wb.write(out);
        } finally {
            out.close();
        }
    }

    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1 || ds.rank()==2;
    }

    public String getDescription() {
        return "Excel Spreadsheet";
    }
}
