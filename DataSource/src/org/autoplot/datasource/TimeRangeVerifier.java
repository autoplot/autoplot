
package org.autoplot.datasource;

import java.text.ParseException;
import org.das2.datum.DatumRangeUtil;

/**
 * verify time range Strings like "2001-03-04" or "orbit:rbspa-pp:103"
 * @author jbf
 */
public class TimeRangeVerifier implements InputVerifier {
    @Override
    public boolean verify(String text) {
        try {
            DatumRangeUtil.parseTimeRange(text);
            return true;
        } catch ( IllegalArgumentException ex ) {
            return false;
        } catch (ParseException e) {
            return false;
        }
    }
}
