/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.bookmarks;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.datasource.DataSetSelector;

/**
 * JMenu that delays creating children until the folder is exposed.  Otherwise we would have thousands of
 * JMenuItems created at once, which showed to be slow.
 * @author jbf
 */
public class DelayMenu extends JMenu {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.bookmarks");

    final static int MAX_TITLE_LEN = 50; // bookmark item description length
    final static int MAX_LABEL_LEN = 30; // folder item description length
    final static int TRIM_TAIL_LEN = 10;


    final DataSetSelector sel;

    protected static void calculateMenu( JMenu menu, final List<Bookmark> bookmarks, final int treeDepth, final DataSetSelector sel ) {
        List<Bookmark> content= bookmarks;
        for ( int i=0; i<content.size(); i++ ) {
            final Bookmark book= content.get(i);

            if (book instanceof Bookmark.Item) {
                String title= book.getTitle();
                if ( title.length()>MAX_TITLE_LEN ) title= title.substring(0,MAX_TITLE_LEN)+"...";

                if ( book.isHidden() ) {

                } else {
                    JMenuItem mi = new JMenuItem(new AbstractAction(title) {
                        public void actionPerformed(ActionEvent e) {
                            sel.setValue(((Bookmark.Item) book).getUri());
                            sel.maybePlot(e.getModifiers());
                        }
                    });

                    if (book.getIcon() != null) {
                        mi.setIcon(AutoplotUtil.scaleIcon(book.getIcon(), -1, 16));
                    }
                    menu.add(mi); //TODO: this should not happen off the event thread.  Instead we should keep a separate model that is used to populate the GUI.
                }

            } else {

                Bookmark.Folder folder = (Bookmark.Folder) book;
                String title= book.getTitle();
                if ( title.length()>MAX_TITLE_LEN ) title= title.substring(0,MAX_TITLE_LEN)+"...";

                String tooltip;
                Icon icon;
                if ( folder.getRemoteUrl()!=null ) {
                    if ( folder.getRemoteStatus()== Bookmark.Folder.REMOTE_STATUS_SUCCESSFUL ) {
                        //title= title + " " + Bookmark.MSG_REMOTE;
                        tooltip= Bookmark.TOOLTIP_REMOTE;
                        icon=null;
                    } else if ( folder.getRemoteStatus()== Bookmark.Folder.REMOTE_STATUS_NOT_LOADED  ) {
                        //title= title + " " + Bookmark.MSG_NOT_LOADED; // we use this now that we add bookmarks in stages
                        tooltip= Bookmark.TOOLTIP_NOT_LOADED;
                        icon= AutoplotUI.BUSY_OPAQUE_ICON;
                    } else if ( folder.getRemoteStatus()== Bookmark.Folder.REMOTE_STATUS_UNSUCCESSFUL  ) {
                        //title= title + " " + Bookmark.MSG_NO_REMOTE;
                        tooltip= Bookmark.TOOLTIP_NO_REMOTE + "<br>" + folder.getRemoteStatusMsg();
                        icon= AutoplotUI.WARNING_ICON;
                    } else {
                        throw new IllegalArgumentException("internal error...");
                    }
                } else {
                    tooltip= "";
                    icon=null;
                }

                if ( folder.isHidden() ) {

                } else {
                    String titl= title.trim();
                    if ( titl.length()>MAX_LABEL_LEN && treeDepth>0 ) {
                        titl= titl.substring( 0,MAX_LABEL_LEN-(TRIM_TAIL_LEN+3) ) + "..."+ titl.substring( titl.length()-TRIM_TAIL_LEN,titl.length() );
                    }
                    final JMenu subMenu = new DelayMenu( titl, folder.getBookmarks(), treeDepth+1, sel );
                    subMenu.setIcon(icon);

                    if ( tooltip.contains("%{URL}") ) {
                        tooltip= tooltip.replace("%{URL}",folder.getRemoteUrl());
                    }

                    if ( treeDepth==0 && folder.getRemoteUrl()!=null ) {
                        if ( book.getDescription()!=null && book.getDescription().length()>0 ) {
                            String ttext=  "<html><em>"+ title + "<br>" + book.getDescription()+"</em>";
                            subMenu.setToolTipText( ttext + "<br>" + tooltip );
                        } else {
                            if ( tooltip.length()>0 ) subMenu.setToolTipText( "<html>"+tooltip );
                        }
                    }

                    menu.add( subMenu );

                }
            }
        }

    }

    DelayMenu( final String label, final List<Bookmark> bookmarks, final int treeDepth, DataSetSelector lsel ) {
        super(label);
        this.sel= lsel;

        addMenuListener( new MenuListener() {

            public void menuSelected(MenuEvent e) {
                logger.log(Level.FINE, "resolving menu {0}...", label);
                DelayMenu.this.removeAll();
                calculateMenu( DelayMenu.this, bookmarks, treeDepth, sel );
            }

            public void menuDeselected(MenuEvent e) {

            }

            public void menuCanceled(MenuEvent e) {

            }

        });
    }



}
