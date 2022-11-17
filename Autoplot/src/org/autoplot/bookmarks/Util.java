/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.bookmarks;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import org.autoplot.AutoplotUtil;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetSelector;
import org.xml.sax.SAXException;

/**
 * Utility functions for the DataSetSelector.
 * @author jbf
 */
public class Util {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    /**
     * load and maintain recent entries in the context name.  This will also add
     * a listener to save recent entries.
     * @param nodeName the context
     * @param sel the recent entries are added to this selector.
     * @param deft the deft to use with the first invocation.
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
            } catch (BookmarksException | SAXException | IOException | ParserConfigurationException ex) {
                logger.log(Level.SEVERE, "Error when reading {0}:", f);
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }

        } else {
            setRecent( sel, deft );
            try (PrintStream fout = new PrintStream(f) ) { // redundant code, see below. 
                Bookmark.formatBooks(fout,getRecent(sel));
                fout.close();
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        
        sel.addPropertyChangeListener( DataSetSelector.PROP_RECENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                try (PrintStream fout = new PrintStream(f)) {
                    Bookmark.formatBooks(fout,getRecent(sel));
                    fout.close();
                } catch (FileNotFoundException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            }
        });
        
        sel.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try (PrintStream fout = new PrintStream(f)) {
                    List<Bookmark> result= getRecent(sel);
                    result.add( new Bookmark.Item( sel.getValue() ) );
                    Bookmark.formatBooks(fout,result);
                    fout.close();
                } catch (FileNotFoundException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        });        

    }

    /**
     * return the recent entries.
     * @param sel the selector containing the recent entries.
     * @return the list.
     */
    public static List<Bookmark> getRecent( DataSetSelector sel ) {
        List<String> ls= sel.getRecent();
        List<Bookmark> result= new ArrayList();
        for ( String l : ls ) {
            result.add(new Bookmark.Item(l));
        }
        return result;
    }

    /**
     * Convenience method allowing List&lt;Bookmark&gt; to be used.  Note presently, this drops any folders.
     * @param sel the select
     * @param recent the list of bookmarks
     */
    public static void setRecent( DataSetSelector sel, List<Bookmark> recent ) {
        List<String> result= new ArrayList();
        for ( Bookmark b: recent ) {
            if ( b instanceof Bookmark.Item ) {
                String s= ((Bookmark.Item)b).getUri();
                result.remove(s); // move s to the top of the list if it exists already
                result.add(s);
            }
        }
        sel.setRecent(result);
    }

}
