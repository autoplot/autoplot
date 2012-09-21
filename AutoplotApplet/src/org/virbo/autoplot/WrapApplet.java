/*
 * 
 * 
 */
package org.virbo.autoplot;

import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.Image;
import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JApplet;

/**
 *
 * @author jbf
 */
public class WrapApplet extends JApplet {

    private static final Logger logger= Logger.getLogger("autoplot.applet");
    Applet applet;

    public WrapApplet() {
        try {
            System.err.println("WrapApplet 20090209.6");
            applet= new AutoplotApplet();
            logger.log(Level.FINE, "construct applet...{0}", applet);
            System.err.println("construct applet..."+applet);

            applet.setStub( new AppletStub() {

                public boolean isActive() {
                    return WrapApplet.this.isActive();
                }

                public URL getDocumentBase() {
                    return WrapApplet.this.getDocumentBase();
                }

                public URL getCodeBase() {
                    return WrapApplet.this.getCodeBase();
                }

                public String getParameter(String name) {
                    return WrapApplet.this.getParameter(name);
                }

                public AppletContext getAppletContext() {
                    return WrapApplet.this.getAppletContext();
                }

                public void appletResize(int width, int height) {
                    //TODO: what to do here?
                    WrapApplet.this.resize(width, height);
                }

            });
        } catch ( Exception ex ) {

        }
    }

    @Override
    public void stop() {
        try {
            applet.stop();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void start() {
        try {
            applet.start();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void showStatus(String msg) {
        try {
            applet.showStatus(msg);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }

    }

    @Override
    public void init() {
        try {
            applet.init();

            setLayout(new BorderLayout());
            add(applet, BorderLayout.CENTER);
            validate();
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void destroy() {
        try {
            applet.destroy();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void play(URL url, String name) {
        applet.play(url, name);
    }

    @Override
    public void play(URL url) {
        applet.play(url);
    }

    @Override
    public String[][] getParameterInfo() {
        return applet.getParameterInfo();
    }


    @Override
    public Locale getLocale() {
        return applet.getLocale();
    }

    @Override
    public Image getImage(URL url, String name) {
        return applet.getImage(url, name);
    }

    @Override
    public Image getImage(URL url) {
        return applet.getImage(url);
    }

    @Override
    public AudioClip getAudioClip(URL url, String name) {
        return applet.getAudioClip(url, name);
    }

    @Override
    public AudioClip getAudioClip(URL url) {
        return applet.getAudioClip(url);
    }

    @Override
    public String getAppletInfo() {
        return applet.getAppletInfo();
    }

}
