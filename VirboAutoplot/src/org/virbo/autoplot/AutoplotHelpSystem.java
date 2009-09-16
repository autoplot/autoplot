package org.virbo.autoplot;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.net.URL;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.help.SwingHelpUtilities;

/**
 * Encapsulates JavaHelp functionality for convenient access by components.
 * @author ed
 */
public class AutoplotHelpSystem {
    
    private static AutoplotHelpSystem instance;

    private final static String helpPath = "/helpfiles/autoplotHelp.hs";  //must be in classpath
    private HelpSet mainHS;
    private HelpBroker broker;
    private CSH.DisplayHelpFromSource helper;
    
    private AutoplotHelpSystem(Component uiBase) {
        // custom viewer supports external web links
        SwingHelpUtilities.setContentViewerUI("org.virbo.autoplot.AutoplotHelpViewer");

        try {
            URL hsurl = getClass().getResource(helpPath);
            mainHS = new HelpSet(null, hsurl);
        } catch (Exception ex) {
            System.err.println("Error loading helpset " + helpPath);
            System.err.println(ex.getMessage());
        }
        broker = mainHS.createHelpBroker();

        // Bind the F1 help key. The keystroke will percolate up the component hierarchy
        // until it reaches one that has had a helpID defined, allowing context sensitivity.
        // If it reaches root pane, dispaly default help.
        broker.enableHelpKey(uiBase, "aphelp_main", mainHS);
        helper = new CSH.DisplayHelpFromSource(broker);
        
    }
    
    public static synchronized void initialize(Component uiBase) {
        if (instance == null) {
            instance = new AutoplotHelpSystem(uiBase);
        } else {
            System.err.println("Ignoring attempt to re-initialize help system.");
        }
    }

    /** Returns a reference to the help system, or <code>null</code> if it hasn't been
     * intitialized.
     */
    public static AutoplotHelpSystem getHelpSystem() {
        return instance;
    }

    /**
     * Components can call this method to register a help ID string.  The JavaHelp
     * system will use this ID string as a hash key to find the correct HTML file
     * to display for context-sensitive help.
     *
     * @param c
     * @param helpID
     */
    public void registerHelpID(Component c, String helpID) {
       broker.enableHelp(c, helpID, mainHS);
    }

    /** A component action listener can pass the event here and the
     * help topic corresponding to the event source will be displayed, assuming an
     * appropriate call has been made to <code>registerHelpID</code>.
     */
    public void displayHelpFromEvent(ActionEvent e) {
        helper.actionPerformed(e);
    }

    /** Display the help window with default page displayed */
    public void displayDefaultHelp() {
        broker.setCurrentID("aphelp_main");
        broker.setDisplayed(true);
    }

}
