/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.util.ArrayList;
import java.util.List;
import org.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 *
 * @author jbf
 */
public abstract class AbstractVapScheme implements VapScheme {

    List<String> unresolved= new ArrayList();
    String packg;

    AbstractVapScheme() {
        this.packg= "org.autoplot.dom";
    }
    
    @Override
    public Class getClass(String clasName) {
        if ( !clasName.contains(".") )  clasName= packg + "." + clasName;
        Class c;
        try {
            c = Class.forName(clasName);
        } catch (ClassNotFoundException ex) {
            return null;
        }
        return c;
    }

    @Override
    public String getName(Class clas) {
        String elementName= clas.getName();
        if ( elementName.startsWith(packg+".") ) {
            elementName= elementName.substring(packg.length()+1);
        }
        elementName = elementName.replaceAll("\\$", "\\_dollar_");
        return elementName;
    }

    @Override
    public void addUnresolvedProperty( Element element, DomNode node, Exception exception) {
        String name =element.getAttribute("name");
        if ( name.length()==0 ) {
            name= element.getNodeName();
        }
        String value= element.getAttribute("value");
        unresolved.add( node.getId() + "  name=" + name + " value=\"" +value + "\"  "+exception );
        
    }

    @Override
    public String describeUnresolved() {
        StringBuilder buf= new StringBuilder();
        for ( String s: unresolved ) {
            buf.append(s).append( "\n");
        }
        return buf.toString();
    }

}
