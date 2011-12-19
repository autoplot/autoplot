package org.autoplot.help;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.help.HelpSet;
import javax.help.SwingHelpUtilities;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * Encapsulates JavaHelp functionality for convenient access by components.
 * @author ed
 */
public class AutoplotHelpSystem {
    
    private static AutoplotHelpSystem instance;

    private static final Logger log= Logger.getLogger("org.autoplot.help");

    //This is the pathname used for all help EXCEPT main autplot help:
    private HelpSet mainHS;
    //private HelpBroker broker;
    //private CSH.DisplayHelpFromSource helper;

    private Map<Component,String> helpIds;

    private AutoplotHelpSystem(Component uiBase) {
        // custom viewer supports external web links
        SwingHelpUtilities.setContentViewerUI("org.autoplot.help.AutoplotHelpViewer");

        helpIds= new HashMap<Component, String>();
        
        // First, load the main autoplot helpset.
        URL hsurl;
        try {
            hsurl= getClass().getResource("/helpfiles/autoplotHelp.hs");
            mainHS = new HelpSet(null, hsurl);
        } catch ( Exception ex ) {
            log.warning("Error loading helpset " + "/helpfiles/autoplotHelp.hs" );
        }
        // Now find and merge any additional helpsets that are present
        Enumeration<URL> hsurls=null;
        try {
            hsurls = getClass().getClassLoader().getResources("META-INF/helpsets.txt");
        } catch (IOException ex) {
            log.warning(ex.toString());
        }

        while( hsurls!=null && hsurls.hasMoreElements()) {
            hsurl = hsurls.nextElement();
            log.log(Level.FINE, "found /META-INF/helpsets.txt at {0}", hsurl);
            BufferedReader read = null;
            try {
                read= new BufferedReader( new InputStreamReader( hsurl.openStream() ) );
                String spec= read.readLine();
                while ( spec!=null ) {
                    int i= spec.indexOf("#");
                    if ( i!=-1 ) {
                        spec= spec.substring(0,i);
                    }
                    spec= spec.trim();
                    if ( spec.length()>0 ) {
                        URL hsurl1=null;
                        try {
                            log.log(Level.FINE, "Merging external helpset: {0}", hsurl);
                            if ( spec.startsWith("/") ) {
                                hsurl1= getClass().getResource(spec);
                            } else {
                                hsurl1= new URL(spec);
                            }
                            mainHS.add(new HelpSet(null, hsurl1));
                        } catch ( Exception ex ) {
                            log.log(Level.WARNING, "Error loading helpset {0}", hsurl1);
                        }
                    }
                    spec= read.readLine();
                }
            } catch ( IOException ex ) {
                log.warning(ex.toString());
            } finally {  // make sure stream is closed
                try {
                    if (read != null) read.close();
                } catch(IOException ex) {
                    log.warning(ex.toString());
                }
            }

        }
    //    broker = mainHS.createHelpBroker();

        // Bind the F1 help key. The keystroke will percolate up the component hierarchy
        // until it reaches one that has had a helpID defined, allowing context sensitivity.
        // If it reaches root pane, dispaly default help.
     //broker.enableHelpKey(uiBase, "aphelp_main", mainHS);

        // This is the actionListener used by displayHelpFromEvent
        //helper = new CSH.DisplayHelpFromSource(broker);
        
    }
    
    public static synchronized void initialize(Component uiBase) {
        if (instance == null) {
            instance = new AutoplotHelpSystem(uiBase);
        } else {
            System.err.println("Ignoring attempt to re-initialize help system.");
        }
    }

    /** Returns a reference to the help system, or <code>null</code> if it hasn't been
     * initialized.
     */
    public static AutoplotHelpSystem getHelpSystem() {
        return instance;
    }

    /**
     * Components can call this method to register a help ID string.  The JavaHelp
     * system will use this ID string as a hash key to find the correct HTML file
     * to display for context-sensitive help.
     *
     * TitledBorder panels and children that are TitledBorders will have their
     * title behave like a link into the documentation.
     *
     * @param c
     * @param helpID
     */
    public void registerHelpID( final Component c, final String helpID) {
     //  broker.enableHelp(c, helpID, mainHS);
        c.setFocusable(true);
        helpIds.put(c, helpID);
       c.addKeyListener( new KeyListener() {

            public void keyTyped(KeyEvent e) {
            }

            public void keyPressed(KeyEvent e) {
            }

            public void keyReleased(KeyEvent e) {
                if ( e.getKeyCode()==KeyEvent.VK_F1 ) {
                    Util.openBrowser( "http://autoplot.org/help#"+helpID );
                    e.consume();
                }
            }
        } );

        c.addMouseListener( new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                c.requestFocus();
            }

        });

        if ( c instanceof JPanel ) {
            JPanel jPanel1= (JPanel)c;
            Border b= jPanel1.getBorder();
            if ( ( b instanceof TitledBorder ) ) {
                TitledBorderDecorator.makeLink( jPanel1, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        Util.openBrowser( "http://autoplot.org/help#"+helpID );
                    }
                });
            }
            Component[] cc= jPanel1.getComponents();
            for ( Component child: cc ) {
                if ( child instanceof JPanel ) {
                    JPanel jPanel2= (JPanel)child;
                    b= jPanel2.getBorder();
                    if ( ( b instanceof TitledBorder ) ) {
                        TitledBorderDecorator.makeLink( jPanel2, new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                Util.openBrowser( "http://autoplot.org/help#"+helpID );
                            }
                        });
                    }
                }
            }
        }
    }

    private Component findRegisteredParent( Component c ) {
        while ( c!=null ) {
            String helpId =helpIds.get(c);
            if ( helpId!=null ) {
                return c;
            }
            c= c.getParent();
        }
        return null;
    }

    public void displayHelpFromEvent(ActionEvent e) {
        displayHelpFromEvent( e, e.getSource() );
    }

    /** A component action listener can pass the event here and the
     * help topic corresponding to the event source will be displayed, assuming an
     * appropriate call has been made to <code>registerHelpID</code>.
     */
    public void displayHelpFromEvent(ActionEvent e, Object focus ) {
        //helper.actionPerformed(e);
        if ( focus==null ) focus= e.getSource();
        if ( focus instanceof Component ) {
            Component c= (Component)focus;
            c= findRegisteredParent(c);
            if (c==null ) {
                Util.openBrowser( "http://autoplot.org/help" );
            } else {
                String helpId =helpIds.get(c);
                Util.openBrowser( "http://autoplot.org/help#"+helpId );
            }
        } else {
            Util.openBrowser( "http://autoplot.org/help" );
        }

        
    }

    /** Display the help window with default page displayed */
    public void displayDefaultHelp() {
        //broker.setCurrentID("aphelp_main");
        //broker.setDisplayed(true);
        Util.openBrowser( "http://autoplot.org/help" );
    }

    /** Request another helpset be merged with the main help. This way, plugin
     * authors can have their help displayed in the main help window.
     * @param hsPath
     */
    /*public void addHelpSet(String hsPath) {
    HelpSet newHS;
    try {
    URL hsurl = getClass().getResource(hsPath);
    newHS = new HelpSet(null, hsurl);
    } catch (Exception e) {
    System.err.println("Error merging helpset: " + hsPath);
    return;
    }

    mainHS.add(newHS);
    }*/
}
