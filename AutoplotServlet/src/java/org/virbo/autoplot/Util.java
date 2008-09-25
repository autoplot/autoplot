/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.text.ParseException;
import javax.servlet.http.HttpServletRequest;
import org.das2.graph.DasDevicePosition;

/**
 *
 * @author jbf
 */
public class Util {

    public static int getIntParameter(HttpServletRequest request, String name, int dval) {
        String s = request.getParameter(name);
        if (s == null) return dval;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return dval;
        }
    }

    public static String getStringParameter(HttpServletRequest request, String name, String dval) {
        String s = request.getParameter(name);
        if (s == null) return dval;
        return s;

    }
    
    /**
     * set the device position, using spec string like "+5em,80%-5em"
     */
    public static void setDevicePosition( DasDevicePosition row, String spec ) throws ParseException {
        int i= spec.indexOf(",");
        if ( i==-1 ) throw new IllegalArgumentException("spec must contain one comma");
        double[] ddmin= DasDevicePosition.parseFormatStr(spec.substring(0,i));
        double[] ddmax= DasDevicePosition.parseFormatStr(spec.substring(i+1));
        row.setMinimum(ddmin[0]);
        row.setEmMinimum(ddmin[1]);
        row.setPtMinimum((int)ddmin[2]);
        row.setMaximum(ddmax[0]);
        row.setEmMaximum(ddmax[1]);
        row.setPtMaximum((int)ddmax[2]);        
    }
}
