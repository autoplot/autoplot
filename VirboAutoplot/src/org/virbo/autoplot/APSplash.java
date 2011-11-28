/* File: Splash.java
 * Copyright (C) 2002-2003 The University of Iowa
 * Created by: Jeremy Faden <jbf@space.physics.uiowa.edu>
 *             Jessica Swanner <jessica@space.physics.uiowa.edu>
 *             Edward E. West <eew@space.physics.uiowa.edu>
 *
 * This file is part of the das2 library.
 *
 * das2 is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.virbo.autoplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Handler;
//import java.util.logging.Level;
import java.util.logging.LogRecord;
//import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.das2.util.AboutUtil;

/**
 *
 * @author  jbf
 */
public class APSplash extends JFrame {

    private static APSplash instance=null;

    private Handler handler;
    private JLabel messageLabel;
    //private long t0; // time of application start

    public static String getVersion() {
        try {
            String tag= AboutUtil.getReleaseTag( APSplash.class );
            if ( tag==null ) return "untagged_version"; else return tag;
        } catch (IOException ex) {
            return "untagged_version";
        }

    }

    public Handler getLogHandler() {
        if ( handler==null ) {
            handler= createhandler();
        }
        return handler;
    }

    private Handler createhandler() {
        Handler result= new Handler() {
            public void publish( LogRecord logRecord ) {
                messageLabel.setText(logRecord.getMessage() );
                messageLabel.paint( messageLabel.getGraphics() );
            }
            public void flush() {}
            public void close() {}
        };
        return result;
    }

    private static ImageIcon getSplashImage() {
        URL url= APSplash.class.getResource("/splash.png");
        if ( url==null ) return null;
        return new ImageIcon(url);
    }

    public synchronized static APSplash getInstance() {
        if ( instance==null ) {
            instance= new APSplash();
            //instance.t0= System.currentTimeMillis();
        }
        return instance;
    }

    public static void showSplash() {
        getInstance();
        instance.setVisible(true);
        instance.paint( instance.getGraphics() );
        checkTime("showSplash");
    }

    public static void checkTime( String msg ) {
        //System.err.println( "checkTime " + msg.replaceAll(" ","_").replaceFirst("_", " ")+ " @ "+(System.currentTimeMillis()-instance.t0) +" ms ");
    }

    public static void hideSplash() {
        getInstance();
        instance.setVisible(false);
        checkTime("hideSplash");
    }

    /** Creates a new instance of Splash */
    public APSplash() {
        super();

        setUndecorated(true);
        setIconImage( AutoplotUtil.getAutoplotIcon() );
        setTitle("Starting Autoplot");
        
        JPanel panel= new JPanel(new BorderLayout());
        panel.add(new JLabel(getSplashImage()),BorderLayout.CENTER);

        Box bottomPanel= Box.createHorizontalBox();

        messageLabel= new JLabel("");
        messageLabel.setMinimumSize( new Dimension( 200, 10 ) );
        bottomPanel.add( messageLabel );
        bottomPanel.add( Box.createHorizontalGlue() );
        bottomPanel.add( new JLabel("version "+getVersion()+"   ",JLabel.RIGHT) );

        panel.add( bottomPanel, BorderLayout.SOUTH );
        this.setContentPane(panel);
        this.pack();
        this.setLocationRelativeTo(null);
    }

//    public static void main( String[] args ) {
//        System.out.println("This is das2 version "+getVersion());
//        APSplash.showSplash();
//        Logger.getLogger("").addHandler( APSplash.getInstance().getLogHandler() );
//        try {
//            for ( int i=0; i<6; i++ ) {
//                Thread.sleep(500);
//                Logger.getLogger("").log(Level.WARNING, "i={0}", i);
//                //Splash.getInstance().messageLabel.setText( "ii-="+i );
//            }
//        } catch ( java.lang.InterruptedException e ) {}
//        APSplash.hideSplash();
//    }

}
