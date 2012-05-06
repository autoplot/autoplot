/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.bookmarks;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSetSelector;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class Util {
    /**
     * load and maintain recent entries in the context name.  This will also add
     * a listener to save recent entries.
     * @param name
     */
    public static void loadRecent( final String nodeName, final DataSetSelector sel, List<Bookmark> deft ) {

        File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
        if ( !f2.exists() ) {
            boolean ok= f2.exists() || f2.mkdirs();
            if ( !ok ) {
                throw new RuntimeException("unable to create folder "+ f2 );
            }
        }

        List<Bookmark> recent;

        final File f = new File( f2, nodeName + ".xml");
        if ( f.exists() ) {
            try {
                recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new FileInputStream(f)).getDocumentElement());
                setRecent(sel,recent);
            } catch (BookmarksException ex) {
                ex.printStackTrace();
                Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                ex.printStackTrace();
                Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                ex.printStackTrace();
                Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ParserConfigurationException ex) {
                ex.printStackTrace();
                Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        sel.addPropertyChangeListener( DataSetSelector.PROP_RECENT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                PrintStream fout= null;
                try {
                    fout = new PrintStream(f);
                    Bookmark.formatBooks(fout,getRecent(sel));
                    fout.close();
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                } finally {
                    if ( fout!=null ) fout.close();
                }

            }
        });

    }


    public static List<Bookmark> getRecent( DataSetSelector sel ) {
        List<String> ls= sel.getRecent();
        List<Bookmark> result= new ArrayList();
        for ( int i=0; i<ls.size(); i++ ) {
            result.add( new Bookmark.Item(ls.get(i)) );
        }
        return result;
    }

    /**
     * allow List<Bookmark> to be used.  Note presently, this drops any folders.
     * @param recent
     */
    public static void setRecent( DataSetSelector sel, List<Bookmark> recent ) {
        List<String> result= new ArrayList();
        for ( Bookmark b: recent ) {
            if ( b instanceof Bookmark.Item ) {
                result.add( ((Bookmark.Item)b).getUri() );
            }
        }
        sel.setRecent(result);
    }


}
