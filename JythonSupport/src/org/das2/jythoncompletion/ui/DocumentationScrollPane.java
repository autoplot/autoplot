/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.das2.jythoncompletion.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.TextUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.Utilities;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import org.das2.jythoncompletion.support.CompletionDocumentation;

/**
 *
 *  @author  Martin Roskanin, Dusan Balek
 */
public class DocumentationScrollPane extends JScrollPane {

    private static final Logger logger= Logger.getLogger("jython.editor");

    private static final String BACK = "org/netbeans/modules/editor/completion/resources/back.png"; //NOI18N
    private static final String FORWARD = "org/netbeans/modules/editor/completion/resources/forward.png"; //NOI18N
    private static final String GOTO_SOURCE = "org/netbeans/modules/editor/completion/resources/open_source_in_editor.png"; //NOI18N
    private static final String SHOW_WEB = "org/netbeans/modules/editor/completion/resources/open_in_external_browser.png"; //NOI18N
    private static final String JAVADOC_ESCAPE = "javadoc-escape"; //NOI18N
    private static final String JAVADOC_BACK = "javadoc-back"; //NOI18N
    private static final String JAVADOC_FORWARD = "javadoc-forward"; //NOI18N    
    private static final String JAVADOC_OPEN_IN_BROWSER = "javadoc-open-in-browser"; //NOI18N    
    private static final String JAVADOC_OPEN_SOURCE = "javadoc-open-source"; //NOI18N    
    private static final int ACTION_JAVADOC_ESCAPE = 0;
    private static final int ACTION_JAVADOC_BACK = 1;
    private static final int ACTION_JAVADOC_FORWARD = 2;
    private static final int ACTION_JAVADOC_OPEN_IN_BROWSER = 3;
    private static final int ACTION_JAVADOC_OPEN_SOURCE = 4;
    private JButton bBack,  bForward,  bGoToSource,  bShowWeb;
    private HTMLDocView view;    // doc browser history
    private List<CompletionDocumentation> history = new ArrayList<CompletionDocumentation>(5);
    private int currentHistoryIndex = -1;
    protected CompletionDocumentation currentDocumentation = null;
    private Dimension documentationPreferredSize;

    /** Creates a new instance of ScrollJavaDocPane */
    public DocumentationScrollPane(JTextComponent editorComponent) {
        super();

        // Determine and use fixed preferred size
        documentationPreferredSize = CompletionSettings.INSTANCE.documentationPopupPreferredSize();
        setPreferredSize(null); // Use the documentationPopupPreferredSize

        Color bgColor = CompletionSettings.INSTANCE.documentationBackgroundColor();

        /*    // XXX Workaround. If the option is set to default use system settings.
        // The bg color oprion should die.
        if (ExtSettingsDefaults.defaultJavaDocBGColor.equals(bgColor)) {
        bgColor = new JEditorPane().getBackground();
        bgColor = new Color(
        Math.max(bgColor.getRed() - 8, 0 ), 
        Math.max(bgColor.getGreen() - 8, 0 ), 
        bgColor.getBlue());
        }
         */
        // Add the completion doc view
        view = new HTMLDocView(bgColor);
        view.addHyperlinkListener(new HyperlinkAction());
        setViewportView(view);

        installTitleComponent();
        installKeybindings(editorComponent);
        setFocusable(false);
    }

    public void setPreferredSize(Dimension preferredSize) {
        if (preferredSize == null) {
            preferredSize = documentationPreferredSize;
        }
        super.setPreferredSize(preferredSize);
    }

    public void setData(final CompletionDocumentation doc) {
        Runnable run = new Runnable() {
            public void run() {
                setDocumentation(doc);
            }
        };
        SwingUtilities.invokeLater( run ); //TODO: there's a deadlock that happens here
        addToHistory(doc);
    }

    private ImageIcon resolveIcon(String res) {
        return new ImageIcon( DocumentationScrollPane.class.getResource( "/" + res));
                
        // return new ImageIcon(org.openide.util.Utilities.loadImage (res));
        //return null;
    }

    private void installTitleComponent() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("controlDkShadow"))); //NOI18N
        toolbar.setLayout(new GridBagLayout());

        GridBagConstraints gdc = new GridBagConstraints();
        gdc.gridx = 0;
        gdc.gridy = 0;
        gdc.anchor = GridBagConstraints.WEST;
        ImageIcon icon = resolveIcon(BACK);
        if (icon != null) {
            bBack = new BrowserButton(icon);
            bBack.addMouseListener(new MouseEventListener(bBack));
            bBack.setEnabled(false);
            bBack.setFocusable(false);
            bBack.setContentAreaFilled(false);
            bBack.setMargin(new Insets(0, 0, 0, 0));
            bBack.setToolTipText("HINT_doc_browser_back_button"); //NOI18N
            toolbar.add(bBack, gdc);
        }

        gdc.gridx = 1;
        gdc.gridy = 0;
        gdc.anchor = GridBagConstraints.WEST;
        icon = resolveIcon(FORWARD);
        if (icon != null) {
            bForward = new BrowserButton(icon);
            bForward.addMouseListener(new MouseEventListener(bForward));
            bForward.setEnabled(false);
            bForward.setFocusable(false);
            bForward.setContentAreaFilled(false);
            bForward.setToolTipText("HINT_doc_browser_forward_button"); //NOI18N
            bForward.setMargin(new Insets(0, 0, 0, 0));
            toolbar.add(bForward, gdc);
        }

        gdc.gridx = 2;
        gdc.gridy = 0;
        gdc.anchor = GridBagConstraints.WEST;
        icon = resolveIcon(SHOW_WEB);
        if (icon != null) {
            bShowWeb = new BrowserButton(icon);
            bShowWeb.addMouseListener(new MouseEventListener(bShowWeb));
            bShowWeb.setEnabled(false);
            bShowWeb.setFocusable(false);
            bShowWeb.setContentAreaFilled(false);
            bShowWeb.setMargin(new Insets(0, 0, 0, 0));
            bShowWeb.setToolTipText("HINT_doc_browser_show_web_button"); //NOI18N
            toolbar.add(bShowWeb, gdc);
        }

        gdc.gridx = 3;
        gdc.gridy = 0;
        gdc.weightx = 1.0;
        gdc.anchor = GridBagConstraints.WEST;
        icon = resolveIcon(GOTO_SOURCE);
        if (icon != null) {
            bGoToSource = new BrowserButton(icon);
            bGoToSource.addMouseListener(new MouseEventListener(bGoToSource));
            bGoToSource.setEnabled(false);
            bGoToSource.setFocusable(false);
            bGoToSource.setContentAreaFilled(false);
            bGoToSource.setMargin(new Insets(0, 0, 0, 0));
            bGoToSource.setToolTipText("HINT_doc_browser_goto_source_button"); //NOI18N
            toolbar.add(bGoToSource, gdc);
        }
        //toolbar.add( new JButton( new AbstractAction("PUSH") {
        //    public void actionPerformed( ActionEvent e ) {
        //        myScrollToRef( currentDocumentation.getURL().getRef() );
        //    }
        //}));
        setColumnHeaderView(toolbar);
    }

    private synchronized void setDocumentation(CompletionDocumentation doc) {
        currentDocumentation = doc;
        String text = currentDocumentation.getText();
        URL url = currentDocumentation.getURL();
        if (text != null) {
            if (url != null) {
                // fix of issue #58658
                javax.swing.text.Document document = view.getDocument();
                if (document instanceof HTMLDocument) {
                    ((HTMLDocument) document).setBase(url);
                }
            }
            view.setContent(text, null);
        } else if (url != null) {
            try {
                view.setPage(url);
            } catch ( java.net.UnknownHostException ioe ) {
                view.setContent( ioe.toString(), null );
            } catch ( FileNotFoundException ex ) {
                view.setContent( ex.toString(), null );
            } catch (IOException ioe) {
                view.setContent( ioe.toString(), null );
            }
        }
        if (bShowWeb != null) {
            bShowWeb.setEnabled(url != null);
        }
        if (bGoToSource != null) {
            bGoToSource.setEnabled(currentDocumentation.getGotoSourceAction() != null);
        }
    }

    private void myScrollToRef(String reference) {
        Document d = view.getDocument();
        if (d instanceof HTMLDocument) {
            HTMLDocument doc = (HTMLDocument) d;
            HTMLDocument.Iterator iter = doc.getIterator(HTML.Tag.A);
            for (; iter.isValid(); iter.next()) {
                AttributeSet a = iter.getAttributes();
                String nm = (String) a.getAttribute(HTML.Attribute.NAME);
                logger.log(Level.FINE, "ref: {0}", nm);
                if ((nm != null) && nm.equals(reference)) {
                    // found a matching reference in the document.
                    try {
                        Rectangle r = view.modelToView(iter.getStartOffset());
                        if (r != null) {
                            // the view is visible, scroll it to the 
                            // center of the current visible area.
                            Rectangle vis = getVisibleRect();
                            //r.y -= (vis.height / 2);
                            r.height = vis.height;
                            scrollRectToVisible(r);
                        }
                    } catch (BadLocationException ble) {
                        //UIManager.getLookAndFeel().provideErrorFeedback(JEditorPane.this);
                        ble.printStackTrace();
                    }
                }
            }
        }
    }

    private synchronized void addToHistory(CompletionDocumentation doc) {
        int histSize = history.size();
        for (int i = currentHistoryIndex + 1; i <
                histSize; i++) {
            history.remove(history.size() - 1);
        }

        history.add(doc);
        currentHistoryIndex =
                history.size() - 1;
        if (currentHistoryIndex > 0) {
            if (bBack != null) {
                bBack.setEnabled(true);
            }

        }
        if (bForward != null) {
            bForward.setEnabled(false);
        }

    }

    private synchronized void backHistory() {
        if (currentHistoryIndex > 0) {
            currentHistoryIndex--;
            setDocumentation(history.get(currentHistoryIndex));
            if (currentHistoryIndex == 0) {
                if (bBack != null) {
                    bBack.setEnabled(false);
                }

            }
            if (bForward != null) {
                bForward.setEnabled(true);
            }

        }
    }

    private synchronized void forwardHistory() {
        if (currentHistoryIndex < history.size() - 1) {
            currentHistoryIndex++;
            setDocumentation(history.get(currentHistoryIndex));
            if (currentHistoryIndex == history.size() - 1) {
                if (bForward != null) {
                    bForward.setEnabled(false);
                }

            }
            if (bBack != null) {
                bBack.setEnabled(true);
            }

        }
    }

    synchronized void clearHistory() {
        currentHistoryIndex = -1;
        history.clear();
        if (bBack != null) {
            bBack.setEnabled(false);
        }

        if (bForward != null) {
            bForward.setEnabled(false);
        }

    }

    private void openInExternalBrowser() {
        URL url = currentDocumentation.getURL();
        if (url != null)
          org.das2.jythoncompletion.nbadapt.Utilities.openBrowser(url.toString());
    }

    private void goToSource() {
        //   Action action = currentDocumentation.getGotoSourceAction();
        // if (action != null)
        //   action.actionPerformed(new ActionEvent(currentDocumentation, 0, null));        
    }

    /** Attempt to find the editor keystroke for the given editor action. */
    private KeyStroke[] findEditorKeys(String editorActionName, KeyStroke defaultKey, JTextComponent component) {
        // This method is implemented due to the issue
        // #25715 - Attempt to search keymap for the keybinding that logically corresponds to the action
        KeyStroke[] ret = new KeyStroke[]{defaultKey};
        if (component != null) {
            TextUI ui = component.getUI();
            Keymap km = component.getKeymap();
            if (ui != null && km != null) {
                EditorKit kit = ui.getEditorKit(component);
            /*if (kit instanceof BaseKit) {
            Action a = ((BaseKit)kit).getActionByName(editorActionName);
            if (a != null) {
            KeyStroke[] keys = km.getKeyStrokesForAction(a);
            if (keys != null && keys.length > 0) {
            ret = keys;
            }
            }
            }*/
            }

        }
        return ret;
    }

    private void registerKeybinding(int action, String actionName, KeyStroke stroke, String editorActionName, JTextComponent component) {
        KeyStroke[] keys = findEditorKeys(editorActionName, stroke, component);
        for (int i = 0; i <
                keys.length; i++) {
            getInputMap().put(keys[i], actionName);
        }

        getActionMap().put(actionName, new DocPaneAction(action));
    }

    private void installKeybindings(JTextComponent component) {
        // Register Escape key
        registerKeybinding(ACTION_JAVADOC_ESCAPE, JAVADOC_ESCAPE,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "escape", component);

        // Register javadoc back key
        registerKeybinding(ACTION_JAVADOC_BACK, JAVADOC_BACK,
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_MASK),
                null, component);

        // Register javadoc forward key
        registerKeybinding(ACTION_JAVADOC_FORWARD, JAVADOC_FORWARD,
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_MASK),
                null, component);

        // Register open in external browser key
        registerKeybinding(ACTION_JAVADOC_OPEN_IN_BROWSER, JAVADOC_OPEN_IN_BROWSER,
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK),
                null, component);

        // Register open the source in editor key
        registerKeybinding(ACTION_JAVADOC_OPEN_SOURCE, JAVADOC_OPEN_SOURCE,
                KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.ALT_MASK | KeyEvent.CTRL_MASK),
                null, component);

        // Register movement keystrokes to be reachable through Shift+<orig-keystroke>
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, KeyEvent.CTRL_MASK));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_END, KeyEvent.CTRL_MASK));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
        mapWithShift(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
    }

    private void mapWithShift(KeyStroke key) {
        InputMap inputMap = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        Object actionKey = inputMap.get(key);
        if (actionKey != null) {
            key = KeyStroke.getKeyStroke(key.getKeyCode(), key.getModifiers() | InputEvent.SHIFT_MASK);
            getInputMap().put(key, actionKey);
        }




    }

    private class BrowserButton extends JButton {

        public BrowserButton() {
            setBorderPainted(false);
            setFocusPainted(false);
        }

        public BrowserButton(String text) {
            super(text);
            setBorderPainted(false);
            setFocusPainted(false);
        }

        public BrowserButton(Icon icon) {
            super(icon);
            setBorderPainted(false);
            setFocusPainted(false);
        }

        public void setEnabled(boolean b) {
            super.setEnabled(b);
        }
    }

    private class MouseEventListener extends MouseAdapter {

        private JButton button;

        MouseEventListener(JButton button) {
            this.button = button;
        }

        public void mouseEntered(MouseEvent ev) {
            if (button.isEnabled()) {
                button.setContentAreaFilled(true);
                button.setBorderPainted(true);
            }
        }

        public void mouseExited(MouseEvent ev) {
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
        }

        public void mouseClicked(MouseEvent evt) {
            if (button.equals(bBack)) {
                backHistory();
            } else if (button.equals(bForward)) {
                forwardHistory();
            } else if (button.equals(bGoToSource)) {
                goToSource();
            } else if (button.equals(bShowWeb)) {
                openInExternalBrowser();
            }
        }
    }

    private class HyperlinkAction implements HyperlinkListener {

        public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e != null && HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                final String desc = e.getDescription();
                if (desc != null) {
                    CompletionDocumentation doc = currentDocumentation.resolveLink(desc);
                    if (doc == null) {
                        try {
                            URL url = currentDocumentation.getURL();
                            url = url != null ? new URL(url, desc) : new URL(desc);
                            doc = new DefaultDoc(url);
                        } catch (MalformedURLException ex) {
                        }
                    }
                    if (doc != null) {
                        setData(doc);
                    }
                }
            }
        }
    }

    private class DefaultDoc implements CompletionDocumentation {

        private URL url = null;

        private DefaultDoc(URL url) {
            this.url = url;
        }

        public String getText() {
            return null;
        }

        public URL getURL() {
            return url;
        }

        public CompletionDocumentation resolveLink(String link) {
            return null;
        }

        public Action getGotoSourceAction() {
            return null;
        }
    }

    private class DocPaneAction extends AbstractAction {

        private int action;

        private DocPaneAction(int action) {
            this.action = action;
        }

        public void actionPerformed(java.awt.event.ActionEvent actionEvent) {
            switch (action) {
                case ACTION_JAVADOC_ESCAPE:
                    CompletionImpl.get().hideDocumentation(false);
                    break;
                case ACTION_JAVADOC_BACK:
                    backHistory();
                    break;
                case ACTION_JAVADOC_FORWARD:
                    forwardHistory();
                    break;
                case ACTION_JAVADOC_OPEN_IN_BROWSER:
                    openInExternalBrowser();
                    break;
                case ACTION_JAVADOC_OPEN_SOURCE:
                    goToSource();
                    break;
            }

        }
    }
}
