
package org.autoplot.cdf;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.WritableDataSet;
import org.das2.qds.ops.Ops;

/**
 * Implementations of the CDF virtual variables seen in CDAWeb, but implemented in QDataSet.
 * These should reflect a subset of those functions, with the IDL implementations at
 * http://spdf.gsfc.nasa.gov/CDAWlib.html
 * see ftp://cdaweb.gsfc.nasa.gov/pub/CDAWlib/unix/CDAWlib.tar.gz, routine read_myCDF.pro
 * @see https://cdaweb.gsfc.nasa.gov/pub/software/cdawlib/source/virtual_funcs.pro
 * @author jbf
 */
public class CdfVirtualVars {
    private static final Logger logger= LoggerManager.getLogger("apdss.cdf");
    
    /**
     * Implementations of CDF virtual functions.  These are a subset of those in the CDAWeb library, plus a couple
     * extra that they will presumably add to the library at some point.
     * @param metadata the metadata for the new result dataset, CDF semantics such as FILLVAL=-1e31
     * @param function the function name, which is case insensitive.  See code isSupported for list of function names.
     * @param args list of QDataSets that are the arguments to the function
     * @param mon monitor for the function
     * @see #isSupported(java.lang.String) 
     * @return the computed variable.
     * @see https://spdf.gsfc.nasa.gov/istp_guide/vattributes.html
     */
    public static QDataSet execute( Map<String,Object> metadata, String function, List<QDataSet> args, ProgressMonitor mon ) throws IllegalArgumentException {
        logger.log(Level.FINE, "implement virtual variable \"{0}\"", function);
        if ( function.equalsIgnoreCase("sum_values" ) ) {
            if ( args.size()<1 ) throw new IllegalArgumentException("virtual variable function sum_values expects at least one argument");
            QDataSet sum= args.get(0);
            for ( int i=1; i<args.size(); i++ ) {
                sum= Ops.add( sum, args.get(i) );
            }
            return sum;
        } else if (function.equalsIgnoreCase("compute_magnitude")) {
            return computeMagnitude( args.get(0) );
        } else if (function.equalsIgnoreCase("convert_log10")) {
            return convertLog10( args.get(0) );
        } else if (function.equalsIgnoreCase("fftPower512")) {
            return Ops.fftPower(args.get(0), 512, mon );
        } else if (function.equalsIgnoreCase("fftPower1024")) {
            return Ops.fftPower(args.get(0), 1024, mon );
        } else if (function.equalsIgnoreCase("fftPower")) { // Dan Crawford's generic fft function.  args[0] is rank 2 waveforms, args[1] is the fft size, which must be 2**k and be smaller than args[0].length(0)
            if ( args.size()!=2 ) throw new IllegalArgumentException("virtual variable function fftPower expects two arguments");
            QDataSet size=  args.get(1);
            while ( size.rank()>0 ) size= size.slice(0); // avoid any runtime errors by reducing to one scalar (rank 0) number.
            mon.setProgressMessage("apply FFT power");
            return Ops.fftPower( args.get(0), Ops.windowFunction( Ops.FFTFilterType.Hann, (int)size.value() ), mon ); //TODO: these should be redone using fftPower(ds,FFTFilterType.TenPercentCos
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
            Number fill= (Number) metadata.get("FILLVAL");
            if ( fill==null ) fill= Double.NaN;
            for ( int i=0; i<real_data.length(); i++ ) {
                if ( region_data.value(i) != 1 ) { // 1=solar wind
                    real_data.putValue(i,fill.doubleValue());
                }
            }
            return real_data;
        } else if ( function.equalsIgnoreCase("apply_qflag" ) ) {
            QDataSet quality_data= args.get(1);
            QDataSet data= args.get(0);
            String n= ((String)data.property(QDataSet.NAME)).toLowerCase();
            int channel=0;
            switch (n) {
                case "flux_h":
                case "sigma_h":
                    channel= 0;
                    break;
                case "flux_o":
                case "sigma_o":
                    channel= 1;
                    break;
                case "flux_he_1":
                case "sigma_he_1":
                    channel= 2;
                    break;
                case "flux_he_2":
                case "sigma_he_2":
                    channel= 3;
                    break;
                default:
                    break;
            }
            quality_data= Ops.slice1( quality_data, channel );
            QDataSet rBad= Ops.where( Ops.ge( quality_data, 4 ) );
            double fill= (Double)data.property(QDataSet.FILL_VALUE);
            WritableDataSet wdata= Ops.copy(data);
            for ( int i=0; i<rBad.length(); i++ ) {
                for ( int j=0; j<data.length(i); j++ ) {
                    for ( int k=0; k<data.length(i,j); k++ ) {
                        wdata.putValue( i,j,k, fill);
                    }
                }
            }
            return wdata;
        } else if ( function.equalsIgnoreCase("apply_esa_qflag") ) {
            ArrayDataSet esa_data= ArrayDataSet.copy(args.get(0));
            QDataSet quality_data= args.get(1);
            Number fill= (Number) metadata.get("FILLVAL");
            if ( fill==null ) fill= Double.NaN;
            double dfill= fill.doubleValue();
            int n= DataSetUtil.product(DataSetUtil.qubeDims(esa_data.slice(0)));
            for ( int i=0; i<quality_data.length(); i++ ) {
                if ( quality_data.value(i) > 0 ) {
                    switch (esa_data.rank()) {
                        case 1:
                            esa_data.putValue(i,dfill);
                            break;
                        case 2:
                            {
                                int n1= esa_data.length(0);
                                for ( int j=0; j<n1; j++ ) {
                                    esa_data.putValue(i,j,dfill);
                                }       break;
                            }
                        case 3:
                            {
                                int n1= esa_data.length(0);
                                for ( int j=0; j<n1; j++ ) {
                                    int n2= esa_data.length(0,0);
                                    for ( int k=0; k<n2; k++ ) {
                                        esa_data.putValue(i,j,k,dfill);
                                    }
                                }       break;
                            }
                        case 4:
                            {
                                int n1= esa_data.length(0);
                                for ( int j=0; j<n1; j++ ) {
                                    int n2= esa_data.length(0,0);
                                    for ( int k=0; k<n2; k++ ) {
                                        int n3= esa_data.length(0,0);
                                        for ( int l=0; l<n3; l++ ) {
                                            esa_data.putValue(i,j,k,1,dfill);
                                        }
                                    }
                                }       break;
                            }
                        default:
                            throw new IllegalArgumentException("unsupported rank ");
                    } 
                }
            }
            return esa_data;
        } else if ( function.equalsIgnoreCase("arr_slice") ) {
            //TODO: how is the index communicated?
            QDataSet sliceable= args.get(0);
            Map<String,Object> m= metadata;
            if ( m==null ) {
                throw new IllegalArgumentException("unable to implement because metadata is needed");
            } else {
                Object oi= m.get("ARR_INDEX");
                Object od= m.get("ARR_DIM");
                QDataSet result;
                if ( od instanceof Number ) {
                    int i= ((Number)od).intValue();
                    switch ( i ) {
                        case 1:
                            result= Ops.slice2(sliceable,((Number)oi).intValue());
                            break;
                        case 2:
                            result= Ops.slice3(sliceable,((Number)oi).intValue());
                            break;
                        default:
                            throw new IllegalArgumentException("not supported slice dimension");
                    }
                } else {
                    throw new IllegalArgumentException("ARR_DIM property in metadata should be a number");
                }
                return result;
            }
            
        } else {
            throw new IllegalArgumentException("virtual variable function not implemented: "+function );
        }
    }

    /**
     * @see https://cdaweb.gsfc.nasa.gov/pub/software/cdawlib/source/virtual_funcs.pro around "FUNCTION calc_p"
     * @param args list of datasets: [ V_GSE_p, np ]
     * @return result which is 1.6726e-6 *np[i]*V_GSE_p[0,i]^2.0
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

    /**
     * return true if the function is supported.
     * @param function the function name, such as "compute_magnitude"
     * @return true if the function is supported.
     */
    public static boolean isSupported(String function) {
        List<String> functions= Arrays.asList( "compute_magnitude", "convert_log10", 
                "fftpowerdelta512", "fftpowerdelta1024", "fftpowerdelta2048",
                "fftpower","fftPower512","fftPower1024","fftpowerdeltatranslation512", 
                "alternate_view", "calc_p", "region_filt", 
                "apply_esa_qflag", "apply_qflag",
                "sum_values", "arr_slice" );
        boolean supported= functions.contains(function.toLowerCase());
        logger.log(Level.FINE, "virtual variable function \"{0}\" is supported: {1}", new Object[]{function, supported});
        return supported;
    }
}
