/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdf;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 * Implementations of the CDF virtual variables seen in CDAWeb, but implemented in QDataSet.
 * These should reflect a subset of those functions, with the IDL implementations at
 * http://spdf.gsfc.nasa.gov/CDAWlib.html
 * see ftp://cdaweb.gsfc.nasa.gov/pub/CDAWlib/unix/CDAWlib.tar.gz, routine read_myCDF.pro
 * @author jbf
 */
public class CdfVirtualVars {

    /**
     * Implementations of CDF virtual functions.  These are a subset of those in the CDAWeb library, plus a couple
     * extra that they will presumably add to the library at some point.
     * @param metadata the metadata for the new result dataset, in QDataSet semantics such as FILL_VALUE=-1e31
     * @param function the function name, which is case insensitive.  See code isSupported for list of function names.
     * @param args list of QDataSets that are the arguments to the function
     * @param mon monitor for the function
     * @see isSupported
     * @return
     */
    public static QDataSet execute( Map<String,Object> metadata, String function, List<QDataSet> args, ProgressMonitor mon ) {
        if ( function.equalsIgnoreCase("compute_magnitude") ) {
            return computeMagnitude( args.get(0) );
        } else if (function.equalsIgnoreCase("convert_log10")) {
            return convertLog10( args.get(0) );
        } else if (function.equalsIgnoreCase("fftPower512")) {
            return Ops.fftPower(args.get(0), 512, mon );
        } else if (function.equalsIgnoreCase("fftPower")) { // Dan Crawford's generic fft function.  args[0] is rank 2 waveforms, args[1] is the fft size, which must be 2**k and be smaller than args[0].length(0)
            QDataSet size=  args.get(1);
            while ( size.rank()>0 ) size= size.slice(0); // avoid any runtime errors by reducing to one scalar (rank 0) number.
            QDataSet hanningSet = Ops.fftFilter(args.get(0), (int)size.value(), Ops.FFTFilterType.Hanning);
            return Ops.fftPower(hanningSet, (int)size.value(), new NullProgressMonitor()); //TODO: these should be redone using fftPower(ds,FFTFilterType.TenPercentCos
        } else if (function.equalsIgnoreCase("fftPowerDelta512")) {
            //introduced to support PlasmaWaveGroup
            QDataSet deltaT = args.get(1);       // time between successive measurements.
            MutablePropertyDataSet waves= DataSetOps.makePropertiesMutable( args.get(0) );
            while ( deltaT.rank()>0 ) deltaT= deltaT.slice(0);
            waves.putProperty( QDataSet.DEPEND_1, Ops.multiply(deltaT,Ops.findgen(waves.length(0)) ) );
            QDataSet pow= Ops.fftPower( waves, 512, mon );
            return pow;
        } else if (function.equalsIgnoreCase("fftPowerDelta1024")) {
            QDataSet deltaT = args.get(1);       // time between successive measurements.
            MutablePropertyDataSet waves= DataSetOps.makePropertiesMutable( args.get(0) );
            while ( deltaT.rank()>0 ) deltaT= deltaT.slice(0);
            waves.putProperty( QDataSet.DEPEND_1, Ops.multiply(deltaT,Ops.findgen(waves.length(0)) ) );
            QDataSet pow= Ops.fftPower( waves, 1024, mon );
            return pow;
        } else if (function.equalsIgnoreCase("fftPowerDelta2048")) {
            QDataSet deltaT = args.get(1);       // time between successive measurements.
            MutablePropertyDataSet waves= DataSetOps.makePropertiesMutable( args.get(0) );
            while ( deltaT.rank()>0 ) deltaT= deltaT.slice(0);
            waves.putProperty( QDataSet.DEPEND_1, Ops.multiply(deltaT,Ops.findgen(waves.length(0)) ) );
            QDataSet pow= Ops.fftPower( waves, 2048, mon );
            return pow;
        } else if (function.equalsIgnoreCase("fftPowerDeltaTranslation512")) {
            QDataSet deltaT= args.get(1);       // time between successive measurements.
            QDataSet translation= args.get(2);  // shift this amount after fft (because it was with respect to another signal
            MutablePropertyDataSet waves= DataSetOps.makePropertiesMutable( args.get(0) );
            waves.putProperty( QDataSet.DEPEND_1, Ops.multiply(deltaT.slice(0),Ops.findgen(waves.length(0)) ) );
            QDataSet pow= Ops.fftPower( waves, 512, mon );
            MutablePropertyDataSet poww= DataSetOps.makePropertiesMutable(pow);
            QDataSet trs1= Ops.add( (QDataSet) pow.property(QDataSet.DEPEND_1),translation.slice(0));
            poww.putProperty( QDataSet.DEPEND_1, trs1 );
            throw new IllegalArgumentException("fftPowerDeltaTranslation512 is untested");
            //return poww;
        } else if ( function.equalsIgnoreCase("calc_p") ) {
            return calcP( args );
        } else if ( function.equalsIgnoreCase("conv_pos1") ) {
            return convPos( args, "ANG-GSE"  );
        } else if ( function.equalsIgnoreCase("alternate_view") ) {
            return args.get(0);
        } else if ( function.equalsIgnoreCase("region_filt") ) {
            //return args.get(0);
            ArrayDataSet real_data = ArrayDataSet.copy( args.get(0) );
            QDataSet region_data = args.get(1);
            Double fill= (Double) metadata.get(QDataSet.FILL_VALUE);
            if ( fill==null ) fill= Double.NaN;
            for ( int i=0; i<real_data.length(); i++ ) {
                if ( region_data.value(i) != 1 ) { // 1=solar wind
                    real_data.putValue(i,fill);
                }
            }
            return real_data;
        } else if ( function.equalsIgnoreCase("apply_esa_qflag") ) {
            ArrayDataSet esa_data= ArrayDataSet.copy(args.get(0));
            QDataSet quality_data= args.get(1);
            Double fill= (Double) metadata.get(QDataSet.FILL_VALUE);
            if ( fill==null ) fill= Double.NaN;
            int n= DataSetUtil.product(DataSetUtil.qubeDims(esa_data.slice(0)));
            for ( int i=0; i<quality_data.length(); i++ ) {
                if ( quality_data.value(i) > 0 ) {
                    if ( esa_data.rank()==1 ) {
                        esa_data.putValue(i,fill);
                    } else {
                        for ( int j=0; j<n; j++ ) {
                            esa_data.putValue(i,j,fill); // CAUTION: this uses array aliasing of ArrayDataSet for rank>2
                        }
                    }
                }
            }
            return esa_data;
        } else {
            throw new IllegalArgumentException("unimplemented function: "+function );
        }
    }

    /**
     * see virtual_funcs.pro function calc_p
     */
    protected static QDataSet calcP( List<QDataSet> args ) {
        QDataSet coefficient= DataSetUtil.asDataSet( 1.6726e-6 );
        QDataSet V_GSE_p= args.get(0);
        QDataSet np= args.get(1);
        //coefficient*np[i]*V_GSE_p[0,i]^2.0
        QDataSet pressure = Ops.multiply( Ops.multiply( coefficient, np), Ops.pow( DataSetOps.slice1( V_GSE_p,0 ), 2 ) );
        return pressure;
    }
    
    public static QDataSet convPos( List<QDataSet> args, String coordSys) {
        throw new IllegalArgumentException("not implemented");
        //return args.get(0);
    }

    protected static QDataSet alternateView( QDataSet burley ) {
        // not supported
        return burley;
    }

    protected static QDataSet computeMagnitude( QDataSet burley ) {
        return Ops.magnitude(burley);
    }

    protected static QDataSet convertLog10( QDataSet burley ) {
        return Ops.log10(burley);
    }

    protected static QDataSet addSeconds( QDataSet burley, double seconds ) {
        QDataSet dep0= (QDataSet) burley.property( QDataSet.DEPEND_0 );
        if ( dep0==null ) throw new IllegalArgumentException("DEPEND_0 not found");
        dep0= Ops.add( dep0, DataSetUtil.asDataSet( seconds, Units.seconds ) );
        ArrayDataSet result= ArrayDataSet.maybeCopy(burley);

        result.putProperty( QDataSet.DEPEND_0, dep0 );
        return result;
    }

    public static boolean isSupported(String function) {
        List<String> functions= Arrays.asList( "compute_magnitude", "convert_log10", 
                "fftpowerdelta512", "fftpowerdelta1024", "fftpowerdelta2048",
                "fftpower","fftpowerdeltatranslation512", "alternate_view", "calc_p", "region_filt", "apply_esa_qflag");
        return functions.contains(function.toLowerCase());
    }
}
