/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.bookmarks;

/**
 * holder for status of remote bookmarks
 * @author jbf
 */
public class RemoteStatus {
    String remoteURL; // location we tried to read
    int depth;
    int status;  // Bookmark.Folder.REMOTE_STATUS_*
    String statusMsg="";
    boolean remoteRemote; // true indicates there are more remote bookmarks to load in.
}
