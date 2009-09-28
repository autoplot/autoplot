/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.metatree;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import org.das2.datum.Units;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.MetadataModel;
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
     * @deprecated use SemanticOps instead
     */
    public static synchronized Units lookupUnits(String sunits) {
        return SemanticOps.lookupUnits(sunits);
    }
    
    /**
     * return canonical das2 unit for colloquial time.
     * @param string
     * @return
     * @deprecated use SemanticOps instead
     */
    public static Units lookupTimeLengthUnit(String s) throws ParseException {
        return SemanticOps.lookupTimeLengthUnit(s);
    }
    
    /**
     * lookupUnits canonical units object, or allocate one.  If one is
     * allocated, then parse for "<unit> since <datum>"
     * @param timeUnits
     * @return
     * @deprecated use SemanticOps instead
     */
    public static synchronized Units lookupTimeUnits( String units ) throws ParseException {
        return SemanticOps.lookupTimeUnits(units);
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
    
    /**
     * returns the object at the path, or null if the path position doesn't exist.
     * @param tree
     * @param path
     * @return Object at the node, if it exists.
     */
    public static Object getNode( Map<String,Object> tree, String[] path ) {
        int i=0;
        Object child= tree.get( path[0] );
        i++;
        while ( i<path.length ) {
            if ( child==null ) return null;
            if ( !(child instanceof Map) ) return null;
            Object value;
            
            child= ((Map)child).get( path[i] );
            i++;
        }
        return child;
    }

    /**
     * slice the properties to reduce rank.
     * @param properties
     * @param sliceDimension
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> sliceProperties(Map<String,Object> properties, int sliceDimension) {
        Map result = new LinkedHashMap(properties);
        List<Object> deps = new ArrayList(3);
        for (int i = 0; i < 3; i++) {
            deps.add(i, properties.get("DEPEND_" + i));
        }

        deps.remove(sliceDimension);
        deps.add(2, null);

        for (int i = 0; i < 3; i++) {
            result.put("DEPEND_" + i, deps.get(i));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> transposeProperties(Map<String,Object> properties) {
        Map result = new LinkedHashMap(properties);
        result.put("DEPEND_1", properties.get("DEPEND_0"));
        result.put("DEPEND_0", properties.get("DEPEND_1"));
        return result;

    }

    public static Map<String,Object> sprocess( String c, Map<String,Object> properties ) {
        int i=1;
        Scanner s= new Scanner( c );
        s.useDelimiter("[\\(\\),]");

        while ( s.hasNext() ) {
            String cmd= s.next();
            if ( cmd.startsWith("|slice") ) {
                int dim= cmd.charAt(6)-'0';
                int idx= s.nextInt();
                properties= sliceProperties( properties, dim );
            } else if ( cmd.equals("|autoHistogram") ) {
                Map<String,Object> newproperties= new HashMap<String,Object>();
                newproperties.put( QDataSet.DEPEND_0, properties );
                properties= newproperties;
            } else if ( cmd.equals("|transpose") ) {
                properties= transposeProperties(properties);
            }
        }
        return properties;
    }

    /**
     * return the MetadataModel object for this identifier, or null.
     * @param t the id of the model, such as "ISTP-CDF", or null.
     * @return
     */
    public static MetadataModel getMetadataModel( String t ) {
        if ( t==null ) return null;
        if ( t.equals(QDataSet.VALUE_METADATA_MODEL_ISTP ) ) {
            return new IstpMetadataModel();
        } else if ( t.equals( QDataSet.VALUE_METADATA_MODEL_SPASE ) ) {
            return new SpaseMetadataModel();
        } else {
            return null;
        }
    }
}
