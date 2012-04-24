/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;

/**
 * Specialized reader only reads csv files.  These csv files must be simple tables with the same number of fields in each record.
 * @author jbf
 */
public class CsvDataSource extends AbstractDataSource {

    public CsvDataSource(URI uri) {
        super(uri);
    }

    QDataSet parseHeader( int icol, String header, String sval ) {
        header= header.trim();
        DDataSet result= DDataSet.create( new int[0] ); // rank 0 dataset

        Units u= guessUnits(sval);
        if ( u!=Units.dimensionless ) result.putProperty( QDataSet.UNITS, u );
        if ( UnitsUtil.isTimeLocation(u) ) result.putProperty( QDataSet.NAME, "UTC" );
        if ( header.length()==0 ) {
            try {
                result.putValue(u.parse(sval).doubleValue(u));
            } catch (ParseException ex) {
                Logger.getLogger(CsvDataSource.class.getName()).log(Level.SEVERE, null, ex);
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
        } catch ( Exception ex ) {
        }
        try {
            Units.us2000.parse(sval);
            return Units.us2000;
        } catch ( Exception ex ) {
        }
        return EnumerationUnits.create("enum");
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        InputStream in = DataSetURI.getInputStream(uri, mon);

        CsvReader reader= new CsvReader( new InputStreamReader(in) );

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
        QDataSet icolumnDs=null; // metadata for column

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
        QDataSet dep0ds= null;

        Units dep0u= Units.dimensionless;
        Units u= Units.dimensionless;

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

                if ( idep0column>=0 && !(dep0u instanceof TimeLocationUnits) ) {
                    dep0ds= parseHeader( idep0column, reader.getHeader(idep0column),reader.get(idep0column) );
                    dep0u= SemanticOps.getUnits(dep0ds);
                }
                if ( !( u instanceof TimeLocationUnits ) ) {
                    icolumnDs= parseHeader( icolumn, reader.getHeader(icolumn),reader.get(icolumn) );
                    u= SemanticOps.getUnits(icolumnDs);
                }
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

        reader.close();
        
        mon.finished();

        DDataSet ds= builder.getDataSet();
        if ( idep0column>=0 ) {
            DDataSet tds= tbuilder.getDataSet();
            tds.putProperty(QDataSet.UNITS,dep0u);
            tds.putProperty(QDataSet.NAME,dep0ds.property(QDataSet.NAME));
            tds.putProperty(QDataSet.LABEL,dep0ds.property(QDataSet.LABEL));
            ds.putProperty(QDataSet.DEPEND_0, tds);
        }
        ds.putProperty(QDataSet.UNITS,u);
        ds.putProperty(QDataSet.NAME,icolumnDs.property(QDataSet.NAME));
        ds.putProperty(QDataSet.LABEL,icolumnDs.property(QDataSet.LABEL));

        return ds;
    }

}
