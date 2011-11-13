/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.net.URI;
import java.text.ParseException;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class CsvDataSource extends AbstractDataSource {

    public CsvDataSource(URI uri) {
        super(uri);
    }

    private static Units getUnits( String header, String u ) {
        try {
            Units.dimensionless.parse(u);
            return Units.dimensionless;
        } catch ( Exception ex ) {
        }
        try {
            Units.us2000.parse(u);
            return Units.us2000;
        } catch ( Exception ex ) {
        }
        return EnumerationUnits.create("enum");
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        File f= getFile(uri, mon);
        CsvReader reader= new CsvReader( new FileReader(f) );

        int ncol=-1;

        String[] headers= null;

        if ( reader.readHeaders() ) {
            ncol= reader.getHeaderCount();
            headers= reader.getHeaders();
        }

        String column= getParam( "column", null );
        int icolumn;
        if ( column==null ) {
            icolumn= ncol==-1 ? -1 : ncol-1;
        } else {
            icolumn= TableOps.columnIndex( column, headers );
            if ( icolumn==-1 ) {
                throw new IllegalArgumentException("column not found: "+column);
            }
        }

        String bundle= getParam( "bundle", null );
        int[] cols;
        if ( bundle==null ) {
            cols= null;
        } else {
            cols= TableOps.parseRangeStr( bundle, headers );
            icolumn= cols[0]; // get the units from this column
        }

        String dep0column= getParam( "depend0", null );
        int idep0column;
        if ( dep0column==null ) {
            idep0column= -1;
        } else {
            idep0column= TableOps.columnIndex( dep0column, headers );
            if ( idep0column==-1 ) {
                throw new IllegalArgumentException("column not found: "+dep0column);
            }
        }

        Units dep0u= Units.dimensionless;
        Units u= Units.dimensionless;

        boolean init= true;

        int hline=2; // allow top two lines to be header lines.

        double tb=0, cb=0;  // temporary holders for data
        double[] bundleb=null;
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

        while ( reader.readRecord() ) {
            line++;
            mon.setProgressMessage("read line "+line);
            if ( hline>0 ) {
                if ( icolumn==-1 ) {
                    icolumn= reader.getColumnCount()-1;
                    headers= new String[reader.getColumnCount()];
                    for ( int i=0; i<reader.getColumnCount(); i++ ) {
                        headers[i]= "column_"+i;
                    }
                }
                if ( idep0column==-1 && reader.getColumnCount()==2 ) {
                    idep0column= 0;
                }
                Units oldDep0u= dep0u;
                Units oldU= u;

                if ( idep0column>=0 && !(dep0u instanceof TimeLocationUnits) ) dep0u= getUnits( reader.getHeader(idep0column),reader.get(idep0column) );
                if ( !( u instanceof TimeLocationUnits ) ) u= getUnits( reader.getHeader(icolumn),reader.get(icolumn) );
                hline= hline-1;

                if ( hline==0 ) {
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


            try {
                if ( idep0column>=0 ) {
                    if ( dep0u instanceof EnumerationUnits ) {
                        tb= ((EnumerationUnits)dep0u).createDatum( reader.get(idep0column) ).doubleValue(dep0u) ;
                    } else {
                        tb= dep0u.parse(reader.get(idep0column)).doubleValue(dep0u);
                    }
                }
                if ( bundleb!=null ) {
                    for ( int j=0; j<bundleb.length; j++ ) {
                        if ( u instanceof EnumerationUnits ) {
                            bundleb[j]= ((EnumerationUnits)u).createDatum( reader.get(icolumn+j) ).doubleValue(u);
                        } else {
                            bundleb[j]= u.parse(reader.get(icolumn+j)).doubleValue(u);
                        }
                    }
                } else {
                    if ( u instanceof EnumerationUnits ) {
                        cb= ((EnumerationUnits)u).createDatum( reader.get(icolumn) ).doubleValue(u);
                    } else {
                        cb= u.parse(reader.get(icolumn)).doubleValue(u);
                    }
                }


            } catch ( ParseException ex ) {
                System.err.println("skipping line: "+reader.getRawRecord() );
                continue;
            }

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
            line++;
        }

        mon.finished();

        DDataSet ds= builder.getDataSet();
        if ( idep0column>=0 ) {
            DDataSet tds= tbuilder.getDataSet();
            tds.putProperty(QDataSet.UNITS,dep0u);
            tds.putProperty(QDataSet.NAME,Ops.safeName(headers[idep0column]));
            tds.putProperty(QDataSet.LABEL,headers[idep0column]);
            ds.putProperty(QDataSet.DEPEND_0, tds);
        }
        ds.putProperty(QDataSet.UNITS,u);
        ds.putProperty(QDataSet.NAME,Ops.safeName(headers[icolumn]));
        ds.putProperty(QDataSet.LABEL,headers[icolumn]);

        return ds;
    }

}
