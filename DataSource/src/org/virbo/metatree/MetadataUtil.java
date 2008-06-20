/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.metatree;

import edu.uiowa.physics.pw.das.datum.NumberUnits;
import edu.uiowa.physics.pw.das.datum.Units;

/**
 *
 * @author jbf
 */
public class MetadataUtil {
    /**
     * lookup canonical units object, or allocate one.
     * @param units string identifier.
     * @return canonical units object.
     */
    public static synchronized Units lookup(String units) {
        Units result;
        try {
            result= Units.getByName(units);
        } catch ( IllegalArgumentException ex ) {
            result= new NumberUnits( units );
        }
        return result;
    }
}
