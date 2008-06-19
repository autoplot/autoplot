/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.util;

import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import edu.uiowa.physics.pw.das.datum.DatumVector;
import edu.uiowa.physics.pw.das.datum.TimeUtil;
import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.datum.format.DatumFormatter;
import edu.uiowa.physics.pw.das.datum.format.TimeDatumFormatter;

/**
 *
 * @author jbf
 */
public class DateTimeDatumFormatter extends DatumFormatter {

    @Override
    public String format(Datum datum) {
        double ssm= TimeUtil.getSecondsSinceMidnight(datum);
        String date= null;
        String time= TimeDatumFormatter.MINUTES.format(datum);
        if ( ssm==0 ) {
            date= TimeDatumFormatter.DAYS.format(datum);
        }
        return date==null ? time : date + " " + time;
    }

    @Override
    public String grannyFormat(Datum datum) {
        double ssm= TimeUtil.getSecondsSinceMidnight(datum);
        String date= null;
        String time= TimeDatumFormatter.MINUTES.format(datum);
        if ( ssm==0 ) {
            date= TimeDatumFormatter.DAYS.format(datum);
        }
        return date==null ? time : time + "!c" + date;
    }

    @Override
    public String[] axisFormat( DatumVector datums, DatumRange context ) {
        boolean haveMidnight= false;
        boolean haveNonMidnight= false;
        
        int firstIndex= -1;
        String[] result= new String[datums.getLength()];
        
        // calculate the scale between successive datums.
        int scale;
        double width= datums.get(1).subtract(datums.get(0)).doubleValue(Units.microseconds);
        if ( width>=60e6 ) {
            scale= TimeUtil.MINUTE;
        } else if ( width>=1e6 ) {
            scale= TimeUtil.SECOND;
        } else if ( width>=1e3 ) {
            scale= TimeUtil.MILLI;
        } else {
            scale= TimeUtil.MICRO;
        }
        
        TimeDatumFormatter delegate= TimeDatumFormatter.formatterForScale(scale, context);
        
        for ( int i=0; i<datums.getLength(); i++ ) {
            Datum datum= datums.get(i);
            String date= null;
            String time= delegate.format(datum);
            result[i]= time;
            if ( DatumRangeUtil.sloppyContains( context, datum) ) {
                if ( firstIndex==-1 ) firstIndex= i;
                double ssm= TimeUtil.getSecondsSinceMidnight(datum);
                if ( ssm==0 ) {
                    date= TimeDatumFormatter.DAYS.format(datum);
                    haveMidnight= true;
                    result[i]= result[i] + "!c" + date;
                } else {
                    haveNonMidnight= true;
                }
            }
        }
                
        if ( haveNonMidnight ) {            
            if ( !haveMidnight && firstIndex>-1 ) {
                Datum datum= datums.get(firstIndex);
                String date= TimeDatumFormatter.DAYS.format(datum);
                result[firstIndex]= result[firstIndex] + "!c" + date;
            }
        } else {
            for ( int i=0; i<datums.getLength(); i++ ) {
                Datum datum= datums.get(i);
                String date= TimeDatumFormatter.DAYS.format(datum);
                result[i]= date;
            }
        }            
            
        return result;
    }
    
    

}
