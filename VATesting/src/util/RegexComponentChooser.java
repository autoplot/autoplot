
package util;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.text.JTextComponent;
import org.netbeans.jemmy.ComponentChooser;

/**
 * Search for a component based on the name or title.
 * @author jbf
 */
public class RegexComponentChooser implements ComponentChooser {

    String regex;
    Pattern pattern;

    public RegexComponentChooser( String regex ) {
        this.regex= regex;
        this.pattern= Pattern.compile(regex);
    }

    @Override
    public boolean checkComponent(Component comp) {
        String text= null;
        if ( text==null && comp instanceof Dialog ) {
            text= ((Dialog)comp).getTitle();
        } else if ( text==null && comp instanceof Frame ) {
            text= ((Frame)comp).getTitle();
        } else if ( text==null && comp instanceof JTextComponent ) {
            text= ((JTextComponent)comp).getText();
        } else if ( text==null && comp instanceof JLabel ) {
            text= ((JLabel)comp).getText();
        } else if ( text==null && comp instanceof JButton ) {
            text= ((JButton)comp).getText();
        } else if ( text==null && comp instanceof JMenu ) {
            text= ((JMenu)comp).getText();
        } else if ( text==null && comp instanceof JMenuItem ) {
            text= ((JMenuItem)comp).getText();
        }
        String name = comp.getName();
        return ( ( name!=null && pattern.matcher(name).matches() ) || ( text!=null && pattern.matcher(text).matches() ) );
    }

    @Override
    public String getDescription() {
        return "Regex in Text Or Title or Component Name";
    }

}
