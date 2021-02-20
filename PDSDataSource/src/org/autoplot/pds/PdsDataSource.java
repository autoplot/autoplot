
package org.autoplot.pds;

import gov.nasa.pds.label.Label;
import gov.nasa.pds.label.object.ArrayObject;
import gov.nasa.pds.label.object.FieldDescription;
import gov.nasa.pds.label.object.TableObject;
import gov.nasa.pds.label.object.TableRecord;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.das2.datum.Units;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;

/**
 *
 * @author jbf
 */
public class PdsDataSource extends AbstractDataSource {

    public PdsDataSource(URI uri) {
        super(uri);
    }

    private QDataSet getFromTable( TableObject t, String columnName ) throws IOException {
        TableRecord r= t.readNext();
        int icol= -1;
        if ( r!=null ) {
            icol= r.findColumn(columnName);
        }
        DataSetBuilder dsb= new DataSetBuilder(1,100);
        while ( r!=null ) {
            r= t.readNext();
            dsb.nextRecord( (double)r.getDouble(icol) );
        }
        return dsb.getDataSet();
    }
    
    private double[] flatten( double[][] dd ) {
        double[] rank1= new double[dd.length*dd[0].length];
        int nj= dd[0].length;
        int kk= 0;
        for ( int i=0; i<dd.length; i++ ) {
            double[] d= dd[i];
            for ( int j=0; j<nj; j++ ) {
                rank1[kk++]= d[j];
            }
        }
        return rank1;
    }
    
    @Override
    public org.das2.qds.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String name= getParam("arg_0","");
        
        URISplit split= URISplit.parse( getURI() );
            
        URL fileUrl= PdsDataSourceFactory.getFileResource( split.resourceUri.toURL(), mon );
        File xmlfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
        File datfile = DataSetURI.getFile(fileUrl,mon );
                    
        QDataSet result;
        
        Label label = Label.open( xmlfile.toURI().toURL() ); 
        
        for ( TableObject t : label.getObjects( TableObject.class) ) {
            
            for ( FieldDescription fd: t.getFields() ) {
                if ( name.startsWith( fd.getName() ) ) {
                    result= getFromTable( t, name.substring(fd.getLength()+1) );
                    return result;
                }
            }
        }
        
        for ( ArrayObject a: label.getObjects(ArrayObject.class) ) {
            if ( a.getName().equals(name) ) {
                if ( a.getAxes()==2 ) {
                    double[][] dd= a.getElements2D();
                    double[] rank1= flatten(dd);
                    int[] qube= new int[] { dd.length, dd[0].length };
                    return DDataSet.wrap( rank1, qube );
                } else if ( a.getAxes()==1 ) {
                    double[] dd= a.getElements1D();
                    int[] qube= new int[] { dd.length };
                    DDataSet ddresult= DDataSet.wrap( dd, qube );
                    if ( name.equals("Epoch") ) {
                        logger.info("Epoch kludge results in CDF_TT2000 units");
                        ddresult.putProperty( QDataSet.UNITS, Units.cdfTT2000 );
                    }
                    return ddresult;
                }
            }
        }
        return null;
    }
    
}
