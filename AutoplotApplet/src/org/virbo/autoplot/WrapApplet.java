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
import java.awt.Graphics;
import java.awt.Image;
import java.net.URL;
import java.util.Locale;
import javax.swing.JApplet;

/**
 *
 * @author jbf
 */
public class WrapApplet extends JApplet {

    Applet applet;

    public WrapApplet() {
        try {
            System.err.println("WrapApplet 20090209.6");
            applet= new AutoplotApplet();
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

    public void stop() {
        try {
            applet.stop();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void start() {
        try {
            applet.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void showStatus(String msg) {
        try {
            applet.showStatus(msg);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void init() {
        try {
            applet.init();

            setLayout(new BorderLayout());
            add(applet, BorderLayout.CENTER);
            validate();
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void destroy() {
        try {
            applet.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void play(URL url, String name) {
        applet.play(url, name);
    }

    public void play(URL url) {
        applet.play(url);
    }

    public String[][] getParameterInfo() {
        return applet.getParameterInfo();
    }


    public Locale getLocale() {
        return applet.getLocale();
    }

    public Image getImage(URL url, String name) {
        return applet.getImage(url, name);
    }

    public Image getImage(URL url) {
        return applet.getImage(url);
    }

    public AudioClip getAudioClip(URL url, String name) {
        return applet.getAudioClip(url, name);
    }

    public AudioClip getAudioClip(URL url) {
        return applet.getAudioClip(url);
    }

    public String getAppletInfo() {
        return applet.getAppletInfo();
    }

}
