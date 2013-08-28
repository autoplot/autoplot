/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.spase;

import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;                // Core DOM classes

import org.w3c.dom.traversal.*;      // TreeWalker and related DOM classes

import org.xml.sax.*;                // Xerces DOM parser uses some SAX classes

import java.io.*;                    // For reading the input XML file

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.virbo.metatree.NameValueTreeModel;

/**
 *
 * @author jbf
 */
public class DOMWalker {

    TreeWalker walker;  // The TreeWalker we're modeling for JTree


    /** Create a TreeModel for the specified TreeWalker */
    public DOMWalker(TreeWalker walker) {
        this.walker = walker;
    }

    /** 
     * Create a TreeModel for a TreeWalker that returns all nodes
     * in the specified document
     **/
    public DOMWalker(Document document) {
        DocumentTraversal dt = (DocumentTraversal) document;
        walker = dt.createTreeWalker(document, NodeFilter.SHOW_ALL, null, false);
    }

    /** 
     * Create a TreeModel for a TreeWalker that returns the specified 
     * element and all of its descendant nodes.
     **/
    public DOMWalker(Element element) {
        DocumentTraversal dt = (DocumentTraversal) element.getOwnerDocument();
        walker = dt.createTreeWalker(element, NodeFilter.SHOW_ALL, null, false);
    }

    boolean isLeaf( Node domNode ) {
        return domNode.getChildNodes().getLength()==1 && domNode.getFirstChild().getNodeType()==Node.TEXT_NODE;
    }
    
    public Map<String, Object> getAttributes(Node node) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        walker.setCurrentNode(node);   // Set the current node
        // TreeWalker doesn't count children for us, so we count ourselves

        Node child = walker.firstChild();    // Start with the first child

        while (child != null) {               // Loop 'till there are no more

            if ( !isLeaf(child) ) {
                result.put(child.getNodeName(), child);
            } else {
                result.put(child.getNodeName(), child.getFirstChild().getNodeValue());
            }
            child = walker.nextSibling();    // Get next child

        }

        // now recurse
        for ( Entry<String,Object> ent : result.entrySet() ) {
            String key= ent.getKey();
            Object o = ent.getValue();
            if (o instanceof Node) {
                result.put( key, getAttributes((Node)o) );
            }
        }

        return result;

    }

    public Node getRoot() {
        return walker.getRoot();
    }

    // How many children does this node have?
    public int getChildCount(Object node) {
        walker.setCurrentNode(((TreeNode) node).getDomNode());   // Set the current node
        // TreeWalker doesn't count children for us, so we count ourselves

        int numkids = 0;
        Node child = walker.firstChild();    // Start with the first child

        while (child != null) {               // Loop 'till there are no more

            numkids++;                       // Update the count

            child = walker.nextSibling();    // Get next child

        }
        return numkids;                      // This is the number of children

    }

    // Return the specified child of a parent node.
    public Object getChild(Object parent, int index) {
        walker.setCurrentNode(((TreeNode) parent).getDomNode());  // Set the current node
        // TreeWalker provides sequential access to children, not random
        // access, so we've got to loop through the kids one by one

        Node child = walker.firstChild();
        while (index-- > 0) {
            child = walker.nextSibling();
        }
        return new TreeNode(child);
    }

    // Return the index of the child node in the parent node
    public int getIndexOfChild(Object parent, Object child) {
        walker.setCurrentNode(((TreeNode) parent).getDomNode());    // Set current node

        int index = 0;
        Node c = walker.firstChild();           // Start with first child

        while ((c != child) && (c != null)) {    // Loop 'till we find a match

            index++;
            c = walker.nextSibling();           // Get the next child

        }
        return index;                           // Return matching position

    }

    /**
     * This main() method demonstrates the use of this class, the use of the
     * Xerces DOM parser, and the creation of a DOM Level 2 TreeWalker object.
     **/
    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
        
            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(new FileReader(args[0]));
            Document document = builder.parse(source);
            
        // If we're using a DOM Level 2 implementation, then our Document
        // object ought to implement DocumentTraversal 
        DocumentTraversal traversal = (DocumentTraversal) document;

        // For this demonstration, we create a NodeFilter that filters out
        // Text nodes containing only space; these just clutter up the tree
        NodeFilter filter = new NodeFilter() {

            public short acceptNode(Node n) {
                if (n.getNodeType() == Node.TEXT_NODE) {
                    // Use trim() to strip off leading and trailing space.
                    // If nothing is left, then reject the node
                    if (((Text) n).getData().trim().length() == 0) {
                        return NodeFilter.FILTER_REJECT;
                    }
                }
                return NodeFilter.FILTER_ACCEPT;
            }
        };

        // This set of flags says to "show" all node types except comments
        int whatToShow = NodeFilter.SHOW_ALL & ~NodeFilter.SHOW_COMMENT;

        // Create a TreeWalker using the filter and the flags
        TreeWalker walker = traversal.createTreeWalker(document, whatToShow,
                filter, false);

        DOMWalker walk = new DOMWalker(walker);

        Map<String, Object> map = walk.getAttributes(walk.getRoot());


        // Instantiate a TreeModel and a JTree to display it
        JTree tree = new JTree(NameValueTreeModel.create("TEST", map));

        // Create a frame and a scrollpane to display the tree, and pop them up
        JFrame frame = new JFrame("DOMTreeWalkerTreeModel Demo");
        frame.getContentPane().add(new JScrollPane(tree));
        frame.setSize(500, 250);
        frame.setVisible(true);
    }
}
