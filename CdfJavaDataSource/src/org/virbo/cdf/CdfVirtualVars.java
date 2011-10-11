/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdf;

import java.util.Arrays;
import java.util.List;
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
    public static QDataSet execute( String function, List<QDataSet> args, ProgressMonitor mon ) {
        if ( function.equals("compute_magnitude") ) {
            return computeMagnitude( args.get(0) );
        } else if (function.equals("convert_log10")) {
            return convertLog10( args.get(0) );
        } else if (function.equals("fftPower512")) {
            return Ops.fftPower(args.get(0), 512, mon );
        } else if (function.equals("fftPower")) { // Dan Crawford's generic fft function.  args[0] is rank 2 waveforms, args[1] is the fft size, which must be 2**k and be smaller than args[0].length(0)
            QDataSet size=  args.get(1);
            while ( size.rank()>0 ) size= size.slice(0); // avoid any runtime errors by reducing to one scalar (rank 0) number.
            QDataSet hanningSet = Ops.fftFilter(args.get(0), (int)size.value(), Ops.FFTFilterType.Hanning);
            return Ops.fftPower(hanningSet, (int)size.value(), new NullProgressMonitor());
        } else if (function.equals("fftPowerDelta512")) {
            QDataSet deltaT = args.get(1);       // time between successive measurements.
            MutablePropertyDataSet waves= DataSetOps.makePropertiesMutable( args.get(0) );
            while ( deltaT.rank()>0 ) deltaT= deltaT.slice(0);
            waves.putProperty( QDataSet.DEPEND_1, Ops.multiply(deltaT,Ops.findgen(waves.length(0)) ) );
            QDataSet pow= Ops.fftPower( waves, 512, mon );
            return pow;
        } else if (function.equals("fftPowerDeltaTranslation512")) {
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
        } else if ( function.equals("calc_p") ) {
            return calcP( args );
        } else if ( function.equals("conv_pos1") ) {
            return convPos( args, "ANG-GSE"  );
        } else if ( function.equals("alternate_view") ) {
            return args.get(0);
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
        List<String> functions= Arrays.asList( "compute_magnitude", "convert_log10", "fftPowerDelta512",
                "fftPower","fftPowerDeltaTranslation512", "alternate_view", "calc_p" );
        return functions.contains(function);
    }
}
