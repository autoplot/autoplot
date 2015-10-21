
package org.autoplot.pngwalk;

import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * At the 2012 MMS meeting at UNH, a group of us proposed that we
 * embed a little metadata within PNG images which provides lookup 
 * information in JSON format.  
 *
 * Here is an example:
 *<blockquote><pre>
 * { "size":[722,639],
 * "numberOfPlots":1,
 * "plots": [
 *    {
 *      "title":"AC/MFI  [PRELIMINARY VALUES - BROWSE USE ONLY] B-field magnitude", 
 *      "xaxis": { "label":"", "min":"2014-01-02T00:00:00.000Z", "max":"2014-01-03T00:00:00.000Z", "left":78, "right":644, "type":"lin", "units":"UTC" },
 *      "yaxis": { "label":"[PRELIM] <|B|> (nT)", "min":4.440892098500626E-16, "max":8.9, "top":52, "bottom":587, "type":"lin", "units":"nT" }
 *    }
 * ] }
 *</pre></blockquote>
 *
 * See http://autoplot.org/richPng
 *
 * @author faden@cottagesystems.com
 */
public class RichPngUtil {
    
    /**
     * attempt to get the time range of the plots, looking at each x axis
     * for a timerange.
     * @param json rich png ascii string
     * @return null or the range found.
     */
    public static DatumRange getXRange( String json ) {
        try {
            JSONObject jo = new JSONObject( json );
            JSONArray plots= jo.getJSONArray("plots");
            List<DatumRange> ranges= new LinkedList();
            for ( int i=0; i<plots.length(); i++ ) {
                JSONObject plot= plots.getJSONObject(i);
                if ( plot!=null ) {
                    JSONObject xaxis= plot.getJSONObject("xaxis");
                    DatumRange range= getRange(xaxis);
                    if ( UnitsUtil.isTimeLocation( range.getUnits() ) ) {
                        ranges.add(0,range);
                    } else {
                        ranges.add(range);
                    }
                }
            }
            if ( ranges.size()>0 ) {
                return ranges.get(0);
            } else {
                return null;
            }
        } catch (JSONException ex) {
            return null;
        } catch (ParseException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    /**
     * return the range setting of the axis.  This looks at the "min" and
     * "max" keys of the axis node.
     * @param axis
     * @return the axis range
     * @throws JSONException for malformed JSON
     * @throws ParseException for malformed ISO8601 times.
     */
    public static DatumRange getRange( JSONObject  axis ) throws JSONException, ParseException {
        DatumRange range;

        String sunits;
        if ( axis.has("units") ) {
            sunits= axis.getString("units"); 
        } else {
            sunits= "";
        }
        
        if ( "UTC".equals( sunits ) ) {
            range= DatumRangeUtil.parseISO8601Range( axis.getString("min")+"/"+axis.getString("max") );

        } else {
            Units units= Units.lookupUnits(sunits);
            range= new DatumRange(units.parse(axis.getString("min")),
                  units.parse(axis.getString("max")) );
            
        }
        return range;
    }
}
