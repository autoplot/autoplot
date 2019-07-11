/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.bookmarks;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.autoplot.datasource.DataSetSelector;

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

    private static boolean oldLogic= false;

    /**
     * calculate a menu from the bookmarks, where when a bookmark is selected, an ActionEvent
     * is fired with the actionCommand equal to the URI.  This was introduced to support
     * invoking one of a set of scripts.
     * 
     * @param menu
     * @param bookmarks
     * @param a 
     */
    public static void calculateMenu( JMenu menu, final List<Bookmark> bookmarks, final ActionListener a ) {

        List<Bookmark> content= bookmarks;
        for ( int i=0; i<content.size(); i++ ) {
            final Bookmark book= content.get(i);

            if (book instanceof Bookmark.Item) {
                String title= book.getTitle();
                if ( title.length()>MAX_TITLE_LEN ) title= title.substring(0,MAX_TITLE_LEN)+"...";

                if ( book.isHidden() ) {

                } else {
                    JMenuItem mi = new JMenuItem(new AbstractAction(title) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            org.das2.util.LoggerManager.logGuiEvent(e);                        
                            ActionEvent ne= new ActionEvent(e.getSource(),e.getID(),((Bookmark.Item) book).getUri() );
                            a.actionPerformed( ne );
                        }
                    });
                    mi.setToolTipText( ((Bookmark.Item) book).getUri() );
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
                    if ( titl.length()>MAX_LABEL_LEN ) {
                        titl= titl.substring( 0,MAX_LABEL_LEN-(TRIM_TAIL_LEN+3) ) + "..."+ titl.substring( titl.length()-TRIM_TAIL_LEN,titl.length() );
                    }
                    final JMenu subMenu = new JMenu( titl );
                    calculateMenu( subMenu, folder.getBookmarks(), a );
                    
                    subMenu.setIcon(icon);

                    if ( tooltip.contains("%{URL}") ) {
                        tooltip= tooltip.replace("%{URL}",folder.getRemoteUrl());
                    }

                    if ( folder.getRemoteUrl()!=null ) {
                        if ( book.getDescription()!=null && book.getDescription().length()>0 ) {
                            String ttext=  "<html><i>"+ title + "<br>" + book.getDescription()+"</i>";
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

    /**
     * create the menu for this depth of the tree.
     * @param menu the menu to which items will be added.
     * @param bookmarks the bookmarks.
     * @param treeDepth the current depth.
     * @param sel null for the Tools menu, otherwise let this handle the URI.
     * @param ui the application to which we adding items.
     */
    protected static void calculateMenu( JMenu menu, final List<Bookmark> bookmarks, final int treeDepth, final DataSetSelector sel, final AutoplotUI ui ) {
        List<Bookmark> content= bookmarks;
        for ( int i=0; i<content.size(); i++ ) {
            final Bookmark book= content.get(i);

            if (book instanceof Bookmark.Item) {
                String title= book.getTitle();
                if ( title.length()>MAX_TITLE_LEN ) title= title.substring(0,MAX_TITLE_LEN)+"...";

                if ( book.isHidden() ) {

                } else {
                    JMenuItem mi = new JMenuItem(new AbstractAction(title) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            org.das2.util.LoggerManager.logGuiEvent(e);                        
                            //TODO: is might be nice to see if the URI can be rejected, and if it was going to reject anyway, enter the dialog.
                            if ( oldLogic || ui==null ) {
                                sel.setValue(((Bookmark.Item) book).getUri());
                                sel.maybePlot(e.getModifiers());
                            } else {
                                // if there is nothing of value on the plot, go ahead and use this, otherwise enter dialog.
                                String uri= ((Bookmark.Item) book).getUri();
                                if ( uri.endsWith(".jy") ) {
                                    ui.runScriptTools( uri );
                                    return;
                                }
                                if ( ui.getDom().getDataSourceFilters().length==1 &&  ui.getDom().getDataSourceFilters(0).getUri().length()==0 ) {
                                    sel.setValue(((Bookmark.Item) book).getUri());
                                    sel.maybePlot(e.getModifiers());
                                } else {
                                    ui.reviewBookmark(uri,e.getModifiers());
                                }
                            }
                        }
                    });
                    mi.setToolTipText( ((Bookmark.Item) book).getUri() );
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
                    final JMenu subMenu = new DelayMenu( titl, folder.getBookmarks(), treeDepth+1, sel, ui );
                    subMenu.setIcon(icon);

                    if ( tooltip.contains("%{URL}") ) {
                        tooltip= tooltip.replace("%{URL}",folder.getRemoteUrl());
                    }

                    if ( treeDepth==0 && folder.getRemoteUrl()!=null ) {
                        if ( book.getDescription()!=null && book.getDescription().length()>0 ) {
                            String ttext=  "<html><i>"+ title + "<br>" + book.getDescription()+"</i>";
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

    protected DelayMenu( final String label, final List<Bookmark> bookmarks, final int treeDepth, final DataSetSelector sel, final AutoplotUI ui ) {
        super(label);

        addMenuListener( new MenuListener() {

            @Override
            public void menuSelected(MenuEvent e) {
                logger.log(Level.FINEST, "resolving menu {0}...", label);
                DelayMenu.this.removeAll();
                calculateMenu( DelayMenu.this, bookmarks, treeDepth, sel, ui );
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }

        });
    }



}
