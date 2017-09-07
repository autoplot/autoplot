
package org.autoplot.util.jemmy;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;
import org.netbeans.jemmy.ComponentChooser;

/**
 * Search for a component based on the name or title.
 * @author jbf
 */
public class RegexComponentChooser implements ComponentChooser {

    private static final Logger logger= Logger.getLogger("vatesting");
    
    String regex;
    Pattern pattern;

    /**
     * create a component chooser for the regex.  It will look at the
     * title of Dialogs, the text of JTextComponents and JLabels, etc.
     * @param regex regular expression
     */
    public RegexComponentChooser( String regex ) {
        this.regex= regex;
        this.pattern= Pattern.compile(regex);
    }

    @Override
    public boolean checkComponent(Component comp) {
        String text= null;
        if ( comp instanceof Dialog ) {
            text= ((Dialog)comp).getTitle();
        }
        if ( text==null && comp instanceof Frame ) {
            text= ((Frame)comp).getTitle();
        }
        if ( text==null && comp instanceof JTextComponent ) {
            text= ((JTextComponent)comp).getText();
        }
        if ( text==null && comp instanceof JLabel ) {
            text= ((JLabel)comp).getText();
        }
        if ( text==null && comp instanceof JButton ) {
            text= ((JButton)comp).getText();
        }
        if ( text==null && comp instanceof JToggleButton ) {
            text= ((JToggleButton)comp).getText();
        }
        if ( text==null && comp instanceof JMenu ) {
            text= ((JMenu)comp).getText();
        }
        if ( text==null && comp instanceof JMenuItem ) {
            text= ((JMenuItem)comp).getText();
        }
        String name = comp.getName();
        logger.log(Level.FINEST, "checkComponent for text \"{0}\" with regex \"{1}\": {2}", new Object[]{text, regex, comp});
        return ( ( name!=null && pattern.matcher(name).matches() ) || ( text!=null && pattern.matcher(text).matches() ) );
    }

    @Override
    public String getDescription() {
        return "Regex in Text Or Title or Component Name";
    }

}
