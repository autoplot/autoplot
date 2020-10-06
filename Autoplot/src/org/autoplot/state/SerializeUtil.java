
package org.autoplot.state;

import org.autoplot.dom.DomUtil;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.Connector;
import java.awt.Color;
import java.beans.IntrospectionException;
import java.text.ParseException;
import java.beans.BeanInfo;
import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.beans.BeansUtil;
import org.das2.datum.Datum;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
import org.das2.graph.BorderType;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.LegendPosition;
import org.das2.graph.PlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.graph.SpectrogramRenderer;
import org.autoplot.MouseModuleType;
import org.autoplot.RenderType;
import org.das2.graph.ErrorBarType;
import org.das2.qstream.SerializeDelegate;
import org.das2.qstream.SerializeRegistry;
import org.das2.qstream.XMLSerializeDelegate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility class for creating a Document from a DomNode.  Note that there is special
 * handling for:<ul>
 * <li>controller -- these nodes are dropped.
 * <li>class -- this is noise from java.
 * <li>*Automatically -- properties ending in "Automatically" are used to set another property.
 * </ul>
 * There may be other exceptional properties that are not documented here.
 * @author jbf
 */
public class SerializeUtil {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.dom.vap");

    static {
        SerializeRegistry.register( BindingModel.class, new BindingModelSerializeDelegate() );
        SerializeRegistry.register( Connector.class, new ConnectorSerializeDelegate() );
        SerializeRegistry.register( Datum.class, new DatumSerializeDelegate() );
        //SerializeRegistry.register( Displayable.class, new EnumSerializeDelegate() );
        SerializeRegistry.register( Enum.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( Color.class, new ColorSerializeDelegate() );
        SerializeRegistry.register( DasColorBar.Type.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( DefaultPlotSymbol.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( PsymConnector.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( SpectrogramRenderer.RebinnerEnum.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( RenderType.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( MouseModuleType.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( PlotSymbol.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( LegendPosition.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( AnchorPosition.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( BorderType.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( AnchorType.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( ErrorBarType.class, new TypeSafeEnumSerializeDelegate() );
        SerializeRegistry.register( Level.class, new LevelSerializeDelegate() );
    }
    
    /**
     * Return the XML for the node.
     * @param document the document to which the node is added.
     * @param node the dom node (Application, Plot, PlotElement, etc.)
     * @param scheme the version of the vap that we are writing.  This identifies the scheme, but also provides names for nodes.
     * @return the Document (XML) element for the node.
     */    
    public static Element getDomElement( Document document, DomNode node, VapScheme scheme ) {
        return getDomElement( document, node, scheme, true );
    }
        
    /**
     * Return the XML for the node.
     * @param document the document to which the node is added.
     * @param node the dom node (Application, Plot, PlotElement, etc.)
     * @param scheme the version of the vap that we are writing.  This identifies the scheme, but also provides names for nodes.
     * @param includeDefaults if true, include nodes which are the default setting
     * @return the Document (XML) element for the node.
     */
    public static Element getDomElement( Document document, DomNode node, VapScheme scheme, boolean includeDefaults ) {
        try {
            String elementName = scheme.getName(node.getClass());
            DomNode defl = node.getClass().newInstance();
            Element element;
            
            element = document.createElement(elementName);
            
            BeanInfo info = BeansUtil.getBeanInfo(node.getClass());
            PropertyDescriptor[] properties = info.getPropertyDescriptors();
            for (PropertyDescriptor pd : properties) {
                String propertyName = pd.getName();

                if ( propertyName.equals("class") ) continue;
                
                if (propertyName.equals("controller")) {
                    //special node should runtime data
                    continue;
                }

                // I made the mistake of making the connectors a proper DOM node,
                // without realizing this was going to affect the vap.  This is
                // kludge to avoid saving out the node and preserving v1.07 for
                // the vap files.
                boolean connectorKludge107= false;
                if ( propertyName.equals("connectors") ) {
                    connectorKludge107= true;
                }

                // setters like "setComponentAutomatically" which should probably not be in the dom node anyway.
                if ( propertyName.endsWith("Automatically" ) ) {
                    continue;
                }
                
                logger.log(Level.FINE, "serializing property \"{0}\" of {1} id={2}", new Object[]{propertyName, elementName, node.getId()});
                Method readMethod = pd.getReadMethod();
                Method writeMethod = pd.getWriteMethod();
                if (writeMethod == null || readMethod == null) {
                    logger.log(Level.FINE, "skipping property \"{0}\" of {1}, failed to find read and write method.", new Object[]{propertyName, elementName});
                    continue;
                }
                Object value;
                try {
                    value = readMethod.invoke(node, new Object[0]);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                    continue;
                }
                if (value == null) {
                    logger.log(Level.INFO, "skipping property {0} of {1}, value is null.", new Object[]{propertyName, elementName});
                    continue;
                }
                if (propertyName.equals("id") && ((String) value).length() > 0) {
                    element.setAttribute(propertyName, (String) value);
                    continue;
                }
                IndexedPropertyDescriptor ipd = null;
                if (pd instanceof IndexedPropertyDescriptor) {
                    ipd = (IndexedPropertyDescriptor) pd;
                }                
                if (value instanceof DomNode) {
                    // special optimization, only serialize at the first reference to DCC, afterwards just use name
                    Element propertyElement = document.createElement("property");
                    propertyElement.setAttribute("name", propertyName);
                    propertyElement.setAttribute("type", "DomNode");
                    Element child = getDomElement(document, (DomNode) value, scheme, includeDefaults );
                    propertyElement.appendChild(child);
                    element.appendChild(propertyElement);
                } else if (ipd != null && !connectorKludge107 && (DomNode.class.isAssignableFrom(ipd.getIndexedPropertyType()))) {
                    // serialize each element of the array.  Assumes order doesn't change
                    Element propertyElement = document.createElement("property");
                    propertyElement.setAttribute("name", propertyName);
                    String clasName = scheme.getName(ipd.getIndexedPropertyType());
                    propertyElement.setAttribute("class", clasName);
                    propertyElement.setAttribute("length", String.valueOf(Array.getLength(value)));
                    for (int j = 0; j < Array.getLength(value); j++) {
                        Object value1 = Array.get(value, j);
                        Element child = getDomElement(document, (DomNode) value1, scheme, includeDefaults );
                        propertyElement.appendChild(child);
                    }
                    element.appendChild(propertyElement);
                } else if (ipd != null) { // array of non-DomNodes, such as bindings.
                    Element propertyElement = document.createElement("property");
                    propertyElement.setAttribute("name", propertyName);
                    String clasName = scheme.getName(ipd.getIndexedPropertyType());
                    propertyElement.setAttribute("class", clasName);
                    propertyElement.setAttribute("length", String.valueOf(Array.getLength(value)));
                    for (int j = 0; j < Array.getLength(value); j++) {
                        Object value1 = Array.get(value, j);
                        Element child = getElementForLeafNode(document, ipd.getIndexedPropertyType(), value1, null);
                        propertyElement.appendChild(child);
                    }
                    element.appendChild(propertyElement);
                } else {
                    Object defltValue = DomUtil.getPropertyValue(defl, pd.getName());
                    if ( !value.equals(defltValue) || includeDefaults ) {                        
                        Element prop = getElementForLeafNode(document, pd.getPropertyType(), value, defltValue);
                        if (prop == null) {
                            logger.log(Level.WARNING, "unable to serialize {0}", propertyName);
                            //prop = getElementForLeafNode(document, pd.getPropertyType(), value, defltValue);
                            continue;
                        }
                        prop.setAttribute("name", pd.getName());
                        element.appendChild(prop);                        
                    }
                }
            }
            return element;
        } catch (IntrospectionException | IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
            
    }

    /**
     * return the Element, or null if we can't handle it
     * @param document
     * @param propClass 
     * @param value
     * @param defltValue 
     * @return
     */
    public static Element getElementForLeafNode( Document document, Class propClass, Object value, Object defltValue ) {
        boolean isDef= defltValue==value || (defltValue!=null && defltValue.equals(value) );

        SerializeDelegate sd= SerializeRegistry.getDelegate(propClass);
        if ( sd==null ) {
            return null;
        } else {
            Element prop = document.createElement("property");
            if ( sd instanceof XMLSerializeDelegate ) {
                prop.appendChild( ((XMLSerializeDelegate)sd).xmlFormat(document,value) );
                prop.setAttribute("type", sd.typeId(value.getClass()));
            } else {
                prop.setAttribute("type", sd.typeId(value.getClass()));
                prop.setAttribute("value", sd.format(value));
                if ( !isDef ) {
                    if ( defltValue==null ) {
                        prop.setAttribute("default", "null" );                        
                    } else {
                        prop.setAttribute("default", sd.format(defltValue) );
                    }
                }
            }
            return prop;
        }
    }

    /**
     * returns the first child that is an element.
     * @param e
     * @throws IllegalArgumentException if the element has not children that are elements.
     * @return
     */
    private static Element firstChildElement( Element element ) {
        NodeList nl= element.getChildNodes();
        for ( int i=0; i<nl.getLength(); i++ ) {
            if ( nl.item(i) instanceof Element ) {
                return (Element)nl.item(i);
            }
        }
        throw new IllegalArgumentException("Element has no children that are elements");
    }

    public static Object getLeafNode( Element element ) throws ParseException {
        String type= element.getAttribute("type");
        SerializeDelegate sd= SerializeRegistry.getByName(type);
        if ( sd==null ) {
            throw new IllegalArgumentException("unable to find serialize delegate for \""+type+"\"");
        }
        if ( element.hasChildNodes() ) {
            return ((XMLSerializeDelegate)sd).xmlParse( firstChildElement( element ) );
        } else {
            return sd.parse(type, element.getAttribute("value") );
        }
    }

    /**
     * decode the DomNode from the document element.
     * @param element the DOM element
     * @param scheme the current version
     * @return
     * @throws ParseException
     */
    public static DomNode getDomNode( Element element, VapScheme scheme ) throws ParseException {
        try {
            DomNode node;

            String clasName= element.getNodeName();

            Class claz= scheme.getClass(clasName);
            
            if ( claz==null ) {
                logger.log( Level.WARNING, "unable to resolve: {0}", element.getTagName());
                throw new ParseException("unable to resolve class: "+ clasName, 0 );
            }

            node = (DomNode) claz.newInstance();

            BeanInfo info = BeansUtil.getBeanInfo(node.getClass());

            PropertyDescriptor[] properties = info.getPropertyDescriptors();
            Map<String,PropertyDescriptor> pp= new HashMap();
            for (PropertyDescriptor property : properties) {
                pp.put(property.getName(), property);
            }

            if ( element.hasAttribute("id") ) {
                node.setId( element.getAttribute("id") );
            }

            NodeList kids= element.getChildNodes();

            for ( int i=0; i<kids.getLength(); i++ ) {
                Node k= kids.item(i);
                if ( k instanceof Element ) {
                    logger.log(Level.FINE, "reading node {0}{1} {2}", new Object[]{k.getNodeName(), k.getAttributes().getNamedItem("name"), k.getAttributes().getNamedItem("value")});
                    //Node nameNode= k.getAttributes().getNamedItem("name");
                    //if ( node instanceof Application && nameNode!=null && nameNode.getNodeValue().equals("connectors") ) {
                    //    System.err.println("here connectors");
                    //}
                    Element e= (Element)k;
                    try {
                        //System.err.println( e.getAttribute("name") );
                        PropertyDescriptor pd= pp.get( e.getAttribute("name") );
                        if ( pd==null ) throw new NullPointerException("expected to find attribute \"name\"");
                        String slen= e.getAttribute("length");
                        if ( slen.length()>0 ) {
                            clasName= e.getAttribute("class");
                            Class c= scheme.getClass(clasName);
                            int n= Integer.parseInt(e.getAttribute("length"));
                            Object arr= Array.newInstance( c,n );
                            boolean connectorKludge107= c==Connector.class;
                            if ( !connectorKludge107 && DomNode.class.isAssignableFrom(c) ) {
                                NodeList arraykids= e.getChildNodes();
                                int ik=0;
                                for ( int j=0; j<n; j++ ) { //DANGER
                                    while ( ik<arraykids.getLength() && !( arraykids.item(ik) instanceof Element ) ) ik++;
                                    if ( ! ( arraykids.item(ik) instanceof Element ) ) {
                                        throw new ParseException( "didn't find "+n+" elements under array item in "+e.getAttribute("name"), 0);
                                    }
                                    DomNode c1= getDomNode( (Element)arraykids.item(ik), scheme );
                                    ik++;
                                    Array.set( arr, j, c1 );
                                }
                                pd.getWriteMethod().invoke( node, arr );
                            } else {
                                NodeList arraykids= e.getChildNodes();
                                int ik=0;
                                for ( int j=0; j<n; j++ ) { //DANGER
                                    Object c1;
                                    while ( ik<arraykids.getLength() && !( arraykids.item(ik) instanceof Element ) ) ik++;
                                    if ( ! ( arraykids.item(ik) instanceof Element ) ) {
                                        throw new ParseException( "didn't find "+n+" elements under array item in "+e.getAttribute("name"), 0);
                                    }
                                    c1 = getLeafNode( (Element) arraykids.item(ik));
                                    ik++;
                                    Array.set( arr, j, c1 );
                                }
                                pd.getWriteMethod().invoke( node, arr );
                            }
                        } else {
                            String stype= e.getAttribute("type");
                            if ( !stype.equals("DomNode") ) {
                                Object child= getLeafNode( e );
                                logger.log( Level.FINEST, "leafNode={0} type {1}",  new Object[] { child, stype } );
                                pd.getWriteMethod().invoke( node, child );
                            } else {
                                Node childElement= e.getFirstChild();
                                while ( !( childElement instanceof Element ) ) childElement= childElement.getNextSibling();
                                DomNode child= getDomNode( (Element)childElement, scheme );
                                logger.log( Level.FINEST, "firstChild={0}", child );
                                pd.getWriteMethod().invoke( node, child );
                            }
                        }
                    } catch ( RuntimeException ex) {
                        if ( scheme.resolveProperty(e, node) ) {
                            logger.log(Level.INFO, "imported {0}", e.getAttribute("name"));
                        } else {
                            scheme.addUnresolvedProperty(e,node, ex);
                        }
                    } catch ( Exception ex ) {
                        if ( scheme.resolveProperty(e, node) ) {
                            logger.log( Level.INFO, "imported {0}", e.getAttribute("name"));
                        } else {
                            scheme.addUnresolvedProperty(e,node, ex);
                        }
                    }
                }
            }

            String unres= scheme.describeUnresolved();
            if ( unres!=null && unres.trim().length()>0 ) {
                logger.log( Level.WARNING, "Unresolved: {0}", unres);
            }

            return node;

        } catch (IntrospectionException | IllegalArgumentException | InstantiationException | IllegalAccessException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

}
