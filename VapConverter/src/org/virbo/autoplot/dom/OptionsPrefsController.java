/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.prefs.Preferences;

/**
 * listen to an Options class and keep prefs up to date.
 * @author jbf
 */
public class OptionsPrefsController {

    Preferences prefs;
    Options options;


    /**
     * create a new controller with preferences for the options class.
     * @param options
     */
    public OptionsPrefsController( Options options ) {
        prefs = Preferences.userNodeForPackage( options.getClass() );
        this.options= options;
    }

}
