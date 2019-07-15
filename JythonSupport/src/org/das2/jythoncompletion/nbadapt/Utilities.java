/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.das2.jythoncompletion.nbadapt;

import java.lang.reflect.Method;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

/**
 *
 * @author jbf
 */
public class Utilities {
    
    /** from NETBEANS/openide/util/src/org/openide/util/Utilities.java */
    public static boolean isMac() {
        String osName = System.getProperty("os.name");
        if (osName.equals("Mac OS X")) { // NOI18N
            return true;
        } else if (osName.startsWith("Darwin")) { // NOI18N
            return true;
        }
        return false;
    }
    
        /**
     * open the URL in a browser.   Borrowed from http://www.centerkey.com/java/browser/.
     */
    public static void openBrowser(String url) {
        final String errMsg = "Error attempting to launch web browser";
        String osName = System.getProperty("os.name");
        try {
            if (osName.startsWith("Mac OS")) {
                Class fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[]{String.class});
                openURL.invoke(null, new Object[]{url});
            } else if (osName.startsWith("Windows")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else { //assume Unix or Linux
                String[] browsers = {"firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape"};
                String browser = null;
                for (int count = 0; count < browsers.length && browser == null; count++) {
                    if (Runtime.getRuntime().exec(new String[]{"which", browsers[count]}).waitFor() == 0) {
                        browser = browsers[count];
                    }
                }
                if (browser == null) {
                    throw new Exception("Could not find web browser");
                } else {
                    Runtime.getRuntime().exec(new String[]{browser, url});
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, errMsg + ":\n" + e.getLocalizedMessage());
        }
    }
    
    /** Get the identifier around the given position or null if there's no identifier
     * around the given position. The identifier is not verified against SyntaxSupport.isIdentifier().
     * @param c JTextComponent to work on
     * @param offset position in document - usually the caret.getDot()
     * @return the block (starting and ending position) enclosing the identifier
     * or null if no identifier was found
     * 
     * from NETBEANS/libsrc/org/netbeans/editor/Utilities.java
     * @throws javax.swing.text.BadLocationException
     */
    public static int[] getIdentifierBlock(JTextComponent c, int offset)
    throws BadLocationException {
        CharSequence id = null;
        int[] ret = null;
        Document doc = c.getDocument();
        int idStart = javax.swing.text.Utilities.getWordStart(c, offset);
        if (idStart >= 0) {
            int idEnd = javax.swing.text.Utilities.getWordEnd(c, idStart);
            if (idEnd >= 0) {
                id= doc.getText(  idStart, idEnd - idStart );
                //id = DocumentUtilities.getText(doc, idStart, idEnd - idStart);
                ret = new int[] { idStart, idEnd };
                CharSequence trim = CharSequenceUtilities.trim(id);
                if (trim.length() == 0 || (trim.length() == 1 && !Character.isJavaIdentifierPart(trim.charAt(0)))) {
                    int prevWordStart = javax.swing.text.Utilities.getPreviousWord(c, offset);
                    if (offset == javax.swing.text.Utilities.getWordEnd(c,prevWordStart )){
                        ret = new int[] { prevWordStart, offset };
                    } else {
                        return null;
                    }
                } else if ( (id.length() != 0)  && (CharSequenceUtilities.indexOf(id, '.') != -1)){ //NOI18N
                    int index = offset - idStart;
                    int begin = CharSequenceUtilities.lastIndexOf(id.subSequence(0, index), '.');
                    begin = (begin == -1) ? 0 : begin + 1; //first index after the dot, if exists
                    int end = CharSequenceUtilities.indexOf(id, '.', index);
                    end = (end == -1) ? id.length() : end;
                    ret = new int[] { idStart+begin, idStart+end };
                }
            }
        }
        return ret;
    }
    
    /** 
     * jbf simple implementation
     * @param doc
     * @param pos
     * @return
     * @throws javax.swing.text.BadLocationException
     */
    public static int[] getIdentifierBlock(BaseDocument doc, int pos)
    throws BadLocationException {
        int[] ret = null;
        Element root = doc.getDefaultRootElement();
        int iele= root.getElementIndex(pos);
        int start= root.getElement(iele).getStartOffset();
        int end= root.getElement(iele).getEndOffset();
        
        String s= doc.getText(start, end-start);
        
        int idStart= s.lastIndexOf(" ",pos-start);
        if ( idStart==-1 ) idStart= 0;
        int idEnd= s.indexOf(" ",pos-start);
        if ( idEnd==-1 ) idEnd= end-start;
        
        return new int[] { idStart+start, idEnd+start };
    }
    
}
