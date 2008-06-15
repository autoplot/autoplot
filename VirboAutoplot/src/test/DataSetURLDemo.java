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
        
        int test=4;
        
        switch ( test ) {
            case 0:
                context= "file:/media/mini/data.backup/examples/asciitable/asciiTab.dat?skip=1&column=field1&fill=-9999";
                carotPos= 56;
                break;
            case 1:
                context= "file:/net/spot3/home/jbf/ct/lanl/gpsdata/omni/omni2_%Y.dat?time=field0&timerange=1960to2010&column=field1&fixedColumns=0-11,54-60&timeFormat=&fill=999.9";
                carotPos= context.indexOf("timeFormat=") + "timeFormat=".length();
                break;
            case 2:
                context= "file:/net/spot3/home/jbf/ct/lanl/gpsdata/omni/omni2_%Y.dat?time=field0&timerange=1960to2010&&timeFormat=&fill=999.9";
                carotPos= context.indexOf("1960to2010&") + "1960to2010&".length();
                break;
            case 3: // file system completion
                //context= "file:/net/spot3/home/jbf/ct/lanl/gpsdata/";
                context= "bin.file:/net/spot3/home/jbf/ct/lanl/gpsda";
                carotPos= context.indexOf("lanl/gp") + "lanl/gp".length();
                break;
            case 4: // file system completion
                //context= "file:/net/spot3/home/jbf/ct/lanl/gpsdata/";
                context= "bin.file:/n";
                carotPos= context.indexOf(":/n") + ":/n".length();
                break;
                              
            case 5: // bad insertion -- co was interpreted as arg_0...
                context= "dat.file:///media/mini/data.backup/examples/asciitable/2490lintest90005.raw?skip=34&co";
                carotPos= context.indexOf("34&co") + "34&co".length();
                break;
            case 6: // bad insertion -- doesn't work to go back
                context= "dat.file:///media/mini/data.backup/examples/asciitable/2490lintest90005.raw?skip=34&racolumn=field2";
                carotPos= context.indexOf("34&ra") + "34&ra".length();
                break;
            default:
                throw new IllegalArgumentException("bad test number");
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
