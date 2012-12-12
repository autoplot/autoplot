/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

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
import org.virbo.dsutil.FFTUtil;

/**
 *
 * @author jbf
 */
public class CdfVirtualVars {
// Code in read_myCDF.pro at roughly line 3125:
//
//          findex = tagindex('FUNCT', vartags) ; find the FUNCT index number
//      if (findex(0) ne -1) then begin ;found a virtual value w/ a function definition
//         if keyword_set(DEBUG) then print,'VV function being called ',$
//            strlowcase(burley.(vindex).(findex)), ' for variable ',vir_vars.name(i)
//         case (strlowcase(burley.(vindex).(findex))) of
//         'crop_image': begin
//                          burley=crop_image(temporary(burley),orig_names,index=vindex)
//   		       end
//         'alternate_view': begin
//                              burley = alternate_view(temporary(burley),orig_names)
//                           end
//         'conv_pos': begin
//	                ; RCJ 11/21/2003  Added 'index=vindex'. It is necessary if all=1
//                        burley = conv_pos(temporary(burley),orig_names,$
//                           tstart=start_time, tstop=stop_time,index=vindex)
//                     end
//         'conv_pos_hungarian': begin
//                        burley = conv_pos_hungarian(temporary(burley),orig_names,index=vindex)
//                     end
//         'conv_pos1': begin
//                         burley = conv_pos(temporary(burley),orig_names,$
//                            tstart=start_time, tstop=stop_time, $
//                            COORD="ANG-GSE",INDEX=vindex)
//                      end
//         'conv_pos2': begin
//                         burley = conv_pos(temporary(burley),orig_names,$
//                            tstart=start_time, tstop=stop_time, $
//                            COORD="SYN-GEO",INDEX=vindex)
//                      end
//         'conv_map_image': begin
//                              burley = conv_map_image(temporary(burley),orig_names)
//                           end
//         'calc_p': begin
//                      burley = calc_p(temporary(burley),orig_names,INDEX=vindex)
//                   end
//         'create_vis': begin
//                          burley = create_vis(temporary(burley),orig_names)
//                       end
//         'create_plain_vis': begin
//                                burley = create_plain_vis(temporary(burley),orig_names)
//                             end
//         'create_plmap_vis': begin
//                                burley = create_plmap_vis(temporary(burley),orig_names)
//                             end
//         'apply_qflag': begin
//                           burley = apply_qflag(temporary(burley),orig_names,index=vindex)
//                        end
//         'region_filt': begin
//                           burley = region_filt(temporary(burley),orig_names,index=vindex)
//                        end
//         'convert_log10': begin
//                             burley = convert_log10(temporary(burley),orig_names)
//                          end
//         'add_51s': begin ;for po_h2_uvi
//                       burley = Add_seconds(temporary(burley),orig_names,index=vindex,seconds=51)
//                    end
//         'add_1800': begin ;for omni
//                       burley = Add_seconds(temporary(burley),orig_names,index=vindex,seconds=1800)
//                    end
//         'comp_themis_epoch': begin ;for computing THEMIS epoch
//                       burley = comp_themis_epoch(temporary(burley),orig_names,index=vindex)
//                    end
//         'comp_themis_epoch16': begin ;for computing THEMIS epoch
//                       burley = comp_themis_epoch(temporary(burley),orig_names,index=vindex,/sixteen)
//                    end
//         'apply_esa_qflag': begin
//                       burley = apply_esa_qflag(temporary(burley),orig_names,index=vindex)
//                    end
//         'compute_magnitude': begin
//                       burley = compute_magnitude(temporary(burley),orig_names,index=vindex)
//                    end
//         'height_isis': begin
//                       burley = height_isis(temporary(burley),orig_names,index=vindex)
//                    end
//         'flip_image': begin
//                       burley = flip_image(temporary(burley),orig_names,index=vindex)
//                    end
//         'wind_plot': begin
//                         burley = wind_plot(temporary(burley),orig_names,index=vindex)
//                      end
//         'error_bar_array': begin
//                           burley=error_bar_array(temporary(burley), $
//			                          index=vindex,value=0.02)
//   		       end
//         'convert_toev': begin
//                           burley=convert_toev(temporary(burley), orig_names, index=vindex)
//                       end
//         'convert_ni': begin
//                           burley=convert_Ni(temporary(burley), orig_names, index=vindex)
//                       end
//         else : print, 'WARNING= No function for:', vtags(vindex)
//         endcase
//      endif ;if function defined for this virtual variable

    /**
     *
     * @param function
     * @param args
     * @see isSupported
     * @return
     */
    public static QDataSet execute(  Map<String,Object> metadata, String function, List<QDataSet> args, ProgressMonitor mon ) {
        if ( function.equalsIgnoreCase("sum_values" ) ) {
            QDataSet sum= args.get(0);
            for ( int i=1; i<args.size(); i++ ) {
                sum= Ops.add( sum, args.get(i) );
            }
            return sum;
        } else if ( function.equalsIgnoreCase("compute_magnitude") ) {
            return computeMagnitude( args.get(0) );
        } else if (function.equalsIgnoreCase("convert_log10")) {
            return convertLog10( args.get(0) );
        } else if (function.equalsIgnoreCase("fftPower512")) {
            return Ops.fftPower(args.get(0), 512, mon );
        } else if (function.equalsIgnoreCase("fftPower")) {
            mon.setProgressMessage("apply Hann window");
            QDataSet hanningSet = Ops.fftFilter(args.get(0), (int) args.get(1).value(), Ops.FFTFilterType.Hanning);
            mon.setProgressMessage("apply FFT power");
            return Ops.fftPower(hanningSet, (int) args.get(1).value(), mon );
            //QDataSet hann= FFTUtil.getWindowHanning( (int) args.get(1).value() );
            //return Ops.fftPower( args.get(0), hann, mon );
        } else if (function.equalsIgnoreCase("fftPowerDelta512")) {
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
   throw new IllegalArgumentException("untested");
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
     * see virtual_funcs.pro functon calc_p
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
                "fftPowerDelta512", "fftpowerdelta1024", "fftpowerdelta2048",
                "fftPower","fftPowerDeltaTranslation512", "alternate_view", "calc_p", "region_filt", "apply_esa_qflag",
                "sum_values" );

        return functions.contains(function);
    }
}
