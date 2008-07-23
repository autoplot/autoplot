/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.metatree;

import edu.uiowa.physics.pw.das.datum.NumberUnits;
import edu.uiowa.physics.pw.das.datum.Units;
import java.util.LinkedHashMap;
import java.util.Map;
import org.w3c.dom.Node;

/**
 *
 * @author jbf
 */
public class MetadataUtil {
    /**
     * lookupUnits canonical units object, or allocate one.
     * @param units string identifier.
     * @return canonical units object.
     */
    public static synchronized Units lookupUnits(String units) {
        Units result;
        try {
            result= Units.getByName(units);
        } catch ( IllegalArgumentException ex ) {
            result= new NumberUnits( units );
        }
        return result;
    }
    
    /**
     * converts tree model node into canonical Map<String,Object>.  Branch nodes
     * are HashMap<String,Object> as well.
     * @param node
     * @return
     */
    public static Map<String,Object> toMetaTree( Node node ) {
        Map<String,Object> result= new LinkedHashMap<String,Object>();
        Node child= node.getFirstChild();
        while ( child!=null ) {
            Object value;
            if ( child.hasChildNodes() ) {
                value= toMetaTree( child );
            } else {
                value= child.getNodeValue();
            }
            result.put( child.getNodeName(), value );
            child= child.getNextSibling();
        }
        return result;
    }
    
    public static Node getNode( Node node, String[] path ) {
        int i=0;
        Node child= node.getFirstChild();
        while ( i<path.length ) {
            if ( child==null ) throw new IllegalArgumentException("couldn't find node");
            Object value;
            if ( child.getNodeName().equals( path[i] ) ) {
                node= child;
                child= node.getFirstChild();
                i++;
            } else {
                child= child.getNextSibling();
            }
        }
        return node;
    }
}
