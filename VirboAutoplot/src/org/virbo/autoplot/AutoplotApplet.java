/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.applet.AppletContext;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetSelectorSupport;

/**
 *
 * @author jbf
 */
public class AutoplotApplet extends JApplet {

    
    ApplicationModel model;
    static Logger logger= Logger.getLogger("virbo.autoplot.applet");
    
    @Override
    public void init() {
        super.init();
        
        model = new ApplicationModel();
	setLayout( new BorderLayout() );
        add(model.getCanvas(), BorderLayout.CENTER );
	
	final DataSetSelector select= new DataSetSelector();

        if ( getCodeBase()!=null ) select.setValue(getCodeBase().toString());
	select.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		System.err.println("actionPerformed()");
		System.err.println("  "+select.getValue());
		setDataSetURL(select.getValue());
	    }
	});
	
	add( select, BorderLayout.NORTH );
	
       // createAppletTester();
        //Logger.getLogger("").setLevel( Level.WARNING );
    }
    
    @Override
    public void start() {
        super.start();
        String surl= getParameter("dataSetURL");
        if ( surl!=null ) {
            setDataSetURL(surl);
        }
    }

    private void createAppletTester() {
        JFrame frame= new JFrame();
        JButton button= new JButton( new AbstractAction("pushme"){
            public void actionPerformed( ActionEvent e ) {
                URL url= getCodeBase();
                String surl= ""+url.toString()+"Capture_00158.jpg?channel=red";
                System.err.println("************************************************");
                System.err.println("************************************************");
                System.err.println(surl);
                System.err.println("************************************************");
                System.err.println("************************************************");
                setDataSetURL( surl );
               // setDataSetURL("file:/media/mini/data.backup/examples/jpg/Capture_00158.jpg?channel=red");
               // testDownload();
            }
        } );
        frame.getContentPane().add(button);
        frame.pack();
        frame.setVisible(true);
    }
    
    private void testDownload() {
        try {
            FileSystem fs = FileSystem.create(new URL("http://www.das2.org/wiki/data/"));
            String[] files = fs.listDirectory("/");
            FileObject fo = fs.getFileObject("afile.dat");

            BufferedReader r = new BufferedReader(new InputStreamReader(fo.getInputStream()));

            String s = r.readLine();
            while (s != null) {
                System.err.println(s);
                s = r.readLine();
            }
        } catch (IOException ex) {
            Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
        }
                
        
        
    }
    
    public void setDataSetURL( final String surl ) {
        try {
            logger.info(surl);
            System.err.println("***************");
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    if ( surl.equals("about:plugins") ) {
                        String text= DataSetSelectorSupport.getPluginsText();
                        JOptionPane.showMessageDialog(AutoplotApplet.this, text);
                    }
                    System.err.println(surl);
                    model.setDataSourceURL(surl);
                }
            } );
        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
    }
    
    public static void main( String[] args ) {
        JFrame frame= new JFrame("autoplot applet");
        AppletStub stub= new AppletStub() {

            public boolean isActive() {
                return true;
            }

            public URL getDocumentBase() {
                return null;
            }

            public URL getCodeBase() {
                return null;
            }

            public String getParameter(String name) {
                if ( name.equals("dataSetURL") ) {
                    return "tsds.http://timeseries.org/get.cgi?StartDate=19890101&EndDate=19890101&ext=bin&out=tsml&ppd=1440&param1=SourceAcronym_Subset3-1-v0";
                } else {
                    return null;
                }
            }

            public AppletContext getAppletContext() {
                return null;
            }

            public void appletResize(int width, int height) {
                
            }
        };
        
        AutoplotApplet applet= new AutoplotApplet();
        applet.setStub(stub);
        
        Dimension size= new Dimension( 400,300 );
        applet.setPreferredSize( size );
        frame.getContentPane().add( applet );
        frame.pack();
        applet.init();
        applet.start();
        frame.setVisible(true);
    }
}
