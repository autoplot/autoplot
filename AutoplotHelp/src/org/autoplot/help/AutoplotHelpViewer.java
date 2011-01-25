package org.autoplot.help;

import java.net.URL;
import javax.help.JHelpContentViewer;
import javax.help.plaf.basic.BasicContentViewerUI;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;

/**
 * <p>Extends the BasicContentViewerUI to allow the use of external links in
 * the JavaHelp html.  Any link with an explicitly specified protocol of <code>http</code>,
 * <code>ftp</code>, or <code>mailto</code> will be opened in the desktop
 * default application.</p>
 *
 * <p>This class never need be instantiated directly; the JavaHelp system is told
 * to use it as the viewer.</p>
 * 
 * @author ed
 */
public class AutoplotHelpViewer extends BasicContentViewerUI {

    public AutoplotHelpViewer(JHelpContentViewer x) {
        super(x);
    }

    public static javax.swing.plaf.ComponentUI createUI(JComponent x) {
        return new AutoplotHelpViewer((JHelpContentViewer) x);
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent he) {
        if (he.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            try {
                URL u = he.getURL();
                if (u.getProtocol().equalsIgnoreCase("mailto") ||
                        u.getProtocol().equalsIgnoreCase("http") ||
                        u.getProtocol().equalsIgnoreCase("https") ||
                        u.getProtocol().equalsIgnoreCase("ftp")) {
                    Util.openBrowser(u.toString());
                    return;
                }
            } catch (Throwable t) {
                // errors are silently ignored and the link is passed to superclass
            }
        }
        // Links not meeting the above criteria are handled by the default
        // JavaHelp handler.
        super.hyperlinkUpdate(he);
    }
}

