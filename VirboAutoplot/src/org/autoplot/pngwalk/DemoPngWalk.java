/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import org.das2.util.ArgumentList;
import org.virbo.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class DemoPngWalk {
    public static void main( String[] args ) {
        final PngWalkTool tool= new PngWalkTool();
        
        final ArgumentList alm = new ArgumentList("AutoPlotUI");
        alm.addBooleanSwitchArgument("nativeLAF", "n", "nativeLAF", "use the system look and feel");

        if (alm.getBooleanValue("nativeLAF")) {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        final String template=  "file:/home/jbf/temp/product_$Y$m$d.png" ; // One Slash!!
        //final String template=  "file:/net/spot3/home/jbf/fun/pics/2001minnesota/.*JPG" ;
        //final String template= "file:/home/jbf/public_html/voyager/VGPW_0201/BROWSE/V1/.*.PNG";
        //final String template= "file:///net/spot3/home/jbf/fun/pics/20080315_tenerife_masca_hike/IMG_.*.JPG";
        //final String template= "http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_...._S_.*_.*_.*_.*.gif";

        tool.setTemplate( template );
        tool.addFileAction( "file:/home/jbf/temp/product_.*.png", "autoplot", new AbstractAction( "launch Autoplot" ) {
            public void actionPerformed(ActionEvent e) {
                    String s = tool.getSelectedFile();
                    int i = template.indexOf("$Y");
                    String timeRange = s.substring(i, i + 8);
                    String productFile = "file:/home/jbf/temp/product.vap";
                    final String suri = productFile + "?timeRange=" + timeRange;
                    Runnable run= new Runnable() {
                        public void run() {
                        try {
                            ScriptContext.plot(suri);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        }
                    };
                    new Thread(run).start();
            }
        } );

        JFrame frame= new JFrame("png walk demo");
        frame.getContentPane().add(tool);

        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        frame.pack();

        frame.setVisible(true);
        
    }
}
