
package org.autoplot.matsupport;

import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLDouble;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.das2.qds.DataSetUtil;
import org.das2.qds.ops.Ops;

/**
 * Export to .mat file for easy import into Matlab.  rank 1 datasets, rank 2 datasets, rank 3 datasets, and rank 2 bundles are supported.
 * jmatio library is used to format the files:  https://github.com/gradusnikov/jmatio/ copied on 2018-07-28.
 * @author jbf
 */
public class MatDataSourceFormat extends AbstractDataSourceFormat {

    private MLArray formatRank1( QDataSet data, String guessName ) {

        String su= getParam( "tunits", "t1970" );

        QDataSet wds= Ops.valid(data);

        double[] dd= new double[data.length()];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= wds.value(i)==0 ? Double.NaN : data.value(i);
        }

        Units dep0u= SemanticOps.getUnits(data);
        
        if ( UnitsUtil.isTimeLocation( dep0u ) ) {
            Units targetUnits= Units.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(dep0u) ) {
                uc= UnitsConverter.getConverter(dep0u,targetUnits);
            }
            for ( int i=0; i<dd.length; i++ ) {
                dd[i]= uc.convert( data.value(i) );
            }
        }
        
        return new MLDouble( guessName, dd, dd.length );
         
    }
    
    private MLArray formatRank2( QDataSet data, String guessName ) {

        String su= getParam( "tunits", "t1970" );

        QDataSet wds= Ops.valid(data);

        // Note Matlab arrays are transposed.
        double[][] dd= new double[data.length(0)][];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= new double[data.length()];
            for ( int j=0; j<data.length(); j++ ) {
                dd[i][j]= wds.value(j,i)==0 ? Double.NaN : data.value(j,i);
            }
        }

        Units dep0u= SemanticOps.getUnits(data);
        
        if ( UnitsUtil.isTimeLocation( dep0u ) ) {
            Units targetUnits= Units.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(dep0u) ) {
                uc= UnitsConverter.getConverter(dep0u,targetUnits);
            }
            for (double[] dd1 : dd) {
                for (int j = 0; j < dd1.length; j++) {
                    dd1[j] = uc.convert(dd1[j]);
                }
            }
        }
        
        return new MLDouble( guessName, dd );
         
    }
    
    private List<MLArray> formatRank2Bundle(  String uri, QDataSet data ) throws Exception {
        setUri(uri);

        List<MLArray> stage= new ArrayList<>();

        for ( int i=0; i<data.length(0); i++ ) {
            QDataSet ds1= Ops.unbundle( data, i );
            stage.add( formatRank1( ds1,"data"+i ) );
        }
        
        return stage;
    }
    
    
    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);
        maybeMkdirs();
        
        String name= getParam( "arg_0", "data" );
        
        List<MLArray> stage= new ArrayList<>();
        
        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            stage.add( formatRank1( dep0, "dep0" ) );
        }
        
        if ( SemanticOps.isBundle(data) ) {
            stage.addAll( formatRank2Bundle( uri, data ) );
        } else {
            switch (data.rank()) {
                case 2:
                    stage.add( formatRank2( data, name ) );
                    break;
                case 1:
                    stage.add( formatRank1( data, name ) );
                    break;
                default:
                    throw new IllegalArgumentException("unsupported rank: "+data.rank());
            }
        }
        
        QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
        if ( dep1!=null ) {
            if ( dep1.rank()==2 ) {
                stage.add( formatRank2( dep1, "dep1" ) );
            } else {
                stage.add( formatRank1( dep1, "dep1" ) ) ;
            }
        }
        
        setUri(uri);

        
        File f= new File( getResourceURI().toURL().getFile() );
        MatFileWriter w= new MatFileWriter();

        w.write(f, stage);

    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return DataSetUtil.isQube(ds) && ( ds.rank()==1 || ds.rank()==2 );
    }

    @Override
    public String getDescription() {
        return "Matlab .mat file";
    }

}
