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

package org.autoplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Handler;
import java.util.logging.Level;
//import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
//import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.das2.datum.LoggerManager;
import org.das2.util.AboutUtil;

/**
 *
 * @author  jbf
 */
public class APSplash extends JFrame {

    private static APSplash instance=null;

    private Handler handler;
    private final JLabel messageLabel;
    private long t0; // time of application start

    private static final Logger logger= LoggerManager.getLogger("autoplot.splash");
    
    /**
     * return the Autoplot version.  Conventionally this is $Y$m$d$(x,regex='[a-z]') or v$Y$(x,regex='[ab]')_$(x,regex='\d+')
     * (20220302a or v2022a_3), or (dev).
     * @return 
     */
    public static String getVersion() {
        try {
            String tag= AboutUtil.getReleaseTag( APSplash.class );
            return tag;
        } catch (IOException ex) {
            return "untagged_version";
        }

    }

    /**
     * return the log handler which is used to echo messages as the application starts up.
     * @return the log handler
     */
    public synchronized Handler getLogHandler() {
        if ( handler==null ) {
            handler= createhandler();
        }
        return handler;
    }

    private Handler createhandler() {
        Handler result= new Handler() {
            @Override
            public void publish( LogRecord logRecord ) {
                messageLabel.setText(logRecord.getMessage() );
                messageLabel.paint( messageLabel.getGraphics() );
            }
            @Override
            public void flush() {}
            @Override
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
            instance.t0= System.currentTimeMillis();
        }
        return instance;
    }

    /**
     * show the splash image on the screen.
     */
    public static void showSplash() {
        getInstance();
        instance.setVisible(true);
        instance.paint( instance.getGraphics() );
        checkTime("showSplash");
    }

    /**
     * used for debugging, to log the current time 
     * @param msg 
     */
    public static void checkTime( String msg ) {
        if ( instance!=null ) {
            logger.log( Level.FINE, "checkTime {0} @ {1} ms ", new Object[]{msg.replaceAll(" ","_").replaceFirst("_", " "), String.valueOf( System.currentTimeMillis()-instance.t0 )} );
            //System.err.println( "checkTime " + msg.replaceAll(" ","_").replaceFirst("_", " ")+ " @ "+(System.currentTimeMillis()-instance.t0) +" ms ");
        } else {
            logger.log( Level.FINE, "checkTime {0} @ -1 ms ", msg.replaceAll(" ","_").replaceFirst("_", " ") );
            //System.err.println( "checkTime " + msg.replaceAll(" ","_").replaceFirst("_", " ")+ " @ -1 ms ");
        }
    }

    /**
     * hide the splash image.
     */
    public static void hideSplash() {
        getInstance();
        instance.setVisible(false);
        checkTime("hideSplash");
    }

    /** 
     * create a new APSplash, used to give something to look at while Autoplot is starting up.
     */
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
