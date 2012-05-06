/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import org.das2.datum.DatumRange;
import org.das2.util.ArgumentList;
import org.das2.datum.TimeParser;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import org.virbo.autoplot.bookmarks.BookmarksManagerModel;
import org.virbo.datasource.DataSetURI;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class DemoPngWalk {

    public static void main(String[] args) {

        DataSetURI.init();  // FtpFileSystem implementation
        
        System.err.println("this is pngwalk 20090529");
        final ArgumentList alm = new ArgumentList("DemoPngWalk");
        alm.addBooleanSwitchArgument("nativeLAF", "n", "nativeLAF", "use the system look and feel");
        alm.addOptionalPositionArgument(0, "template",  "file:/tmp/pngwalk/product_$Y$m$d.png", "initial template to use.");
        
        alm.process(args);
        
        if (alm.getBooleanValue("nativeLAF")) {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        String template = alm.getValue("template"); // One Slash!!
        //final String template=  "file:/home/jbf/temp/product_$Y$m$d.png" ; // One Slash!!
        //final String template=  "file:/net/spot3/home/jbf/fun/pics/2001minnesota/.*JPG" ;
        //final String template= "file:/home/jbf/public_html/voyager/VGPW_0201/BROWSE/V1/.*.PNG";
        //final String template= "file:///net/spot3/home/jbf/fun/pics/20080315_tenerife_masca_hike/IMG_.*.JPG";
        //final String template= "http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_...._S_.*_.*_.*_.*.gif";

        start( template, null );

    }

    public static PngWalkTool1 start( String template, Window parent ) {

        final PngWalkTool1 tool = new PngWalkTool1();

        if ( template!=null ) {
            tool.setTemplate(template);
        } else {
            Preferences prefs = Preferences.userNodeForPackage(PngWalkTool1.class);
            String srecent = prefs.get( PngWalkTool1.PREF_RECENT,"");
            if ( srecent.equals("") ) {
                tool.setTemplate("file:/tmp/pngwalk/product_$Y$m$d.png");
            } else {
                try {
                    List<Bookmark> books = Bookmark.parseBookmarks(srecent);
                    tool.setTemplate( ((Bookmark.Item)books.get(0)).getUri() );
                } catch (BookmarksException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SAXException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                }
                tool.setTemplate("file:/tmp/pngwalk/product_$Y$m$d.png");
            }

        }

        PngWalkTool1.ActionEnabler enabler= new PngWalkTool1.ActionEnabler() {
            public boolean isActionEnabled(String filename) {
                String s = filename;
                String template = tool.getTemplate();
                int i0 = template.indexOf("_$Y");
                if ( i0==-1 ) i0= template.indexOf("_%Y");
                int i1 = s.indexOf(".png");
                if ( i1==-1 || i0==-1 ) return false;
                //String timeRange = s.substring(i0 + 1, i1);
                String productFile = template.substring(0, i0) + ".vap";
                try {
                    return WalkUtil.fileExists(productFile);
                } catch (FileSystemOfflineException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                } catch (URISyntaxException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                }
            }
        };

        final int op= parent==null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE;
        
        tool.addFileAction( enabler, "autoplot", new AbstractAction("Launch Autoplot") {
            public void actionPerformed(ActionEvent e) {
                String s = tool.getSelectedFile();
                String template = tool.getTemplate();
                int i0 = template.indexOf("_$Y");
                if ( i0==-1 ) i0= template.indexOf("_%Y");
                int i1 = s.indexOf(".png");
                if ( i1==-1 ) return;
                TimeParser tp= TimeParser.create( template.substring(i0 + 1, i1) );
                String timeRange = s.substring(i0 + 1, i1);
                try {
                    DatumRange dr= tp.parse(timeRange).getTimeRange();
                    timeRange= dr.toString().replaceAll(" ", "+");
                } catch ( ParseException ex ) {
                    throw new RuntimeException(ex);
                }
                String productFile = template.substring(0, i0) + ".vap";

                final String suri = productFile + "?timeRange=" + timeRange;

                Runnable run = new Runnable() {
                    public void run() {
                        try {
                            ScriptContext.createGui();
                            ScriptContext.plot(suri);
                            ((JFrame)ScriptContext.getViewWindow()).setDefaultCloseOperation(op);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                };
                new Thread(run).start();
            }
        });

        JFrame frame = new JFrame("PNG Walk Tool");

        if ( parent==null ) {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } else {
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        }
        
        frame.setJMenuBar( createMenuBar(tool,frame) );
        
        frame.getContentPane().add(tool);


        frame.pack();

        frame.setVisible(true);

        return tool;
    }

    private static JMenuBar createMenuBar( final PngWalkTool1 tool, final JFrame f ) {
        JMenuBar result= new JMenuBar();
        JMenu fileMenu= new JMenu("File");
        fileMenu.add( new AbstractAction( f.getDefaultCloseOperation()==JFrame.EXIT_ON_CLOSE ? "Exit" : "Close" ) {
            public void actionPerformed(ActionEvent e) {
                f.dispose();
            }
        } );
        result.add(fileMenu);

        final JMenu bookmarksMenu= new JMenu("Bookmarks");
        final BookmarksManager man= new BookmarksManager(f,true);

        man.getModel().addPropertyChangeListener( BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                man.updateBookmarks( bookmarksMenu, tool.getSelector() );
            }
        });
        man.setVisible(false);
        man.setPrefNode("pngwalk");

        man.updateBookmarks( bookmarksMenu, tool.getSelector() );

        result.add( bookmarksMenu );
        
        return result;
    }
}
