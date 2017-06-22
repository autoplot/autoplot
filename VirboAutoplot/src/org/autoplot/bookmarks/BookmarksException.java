/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.bookmarks;

/**
 * Any sort of problem with the bookmarks files, including semantic errors.
 * @author jbf
 */
public class BookmarksException extends Exception {
    public BookmarksException( String s ) {
        super( s );
    }
    public BookmarksException( Exception cause ) {
        super( cause );
    }
    public BookmarksException( String s, Exception cause ) {
        super( s, cause );
    }
}
