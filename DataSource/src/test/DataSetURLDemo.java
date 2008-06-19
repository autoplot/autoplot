/*
 * DataSetURLDemo.java
 *
 * Created on December 10, 2007, 6:40 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package test;

import edu.uiowa.physics.pw.das.components.DasProgressPanel;
import org.virbo.datasource.DataSetURL;

/**
 *
 * @author jbf
 */
public class DataSetURLDemo {
    
    public static void demoGetCompletions() throws Exception {
        String context;
        int carotPos;
        
        String spaces= "                                                         ";
        spaces= spaces+spaces+spaces+spaces;
        
        if ( false ) {
            context= "/media/mini/data.backup/examples/asciitable/asciiTab.dat?skip=1&column=field1&fill=-9999";
            carotPos= 56;
        } else if ( false ) {
            context= "/net/spot3/home/jbf/ct/lanl/gpsdata/omni/omni2_%Y.dat?time=field0&timerange=1960to2010&column=field1&fixedColumns=0-11,54-60&timeFormat=&fill=999.9";
            carotPos= context.indexOf("timeFormat=") + "timeFormat=".length();
        } else {
            context= "/net/spot3/home/jbf/ct/lanl/gpsdata/omni/omni2_%Y.dat?time=field0&timerange=1960to2010&&timeFormat=&fill=999.9";
            carotPos= context.indexOf("1960to2010&") + "1960to2010&".length();
        }
        
        String[] ccs= DataSetURL.getCompletions2( context, carotPos, new DasProgressPanel("completions" ) );
       
        System.err.println(context);
        System.err.println(spaces.substring(0,carotPos)+"L" );
        
        for ( int i=0; i<ccs.length; i++ ) {
            System.err.println(ccs[i]);
        }
        
    }
    
    public static void main(String[] args) throws Exception {
        demoGetCompletions();
    }
    
}
