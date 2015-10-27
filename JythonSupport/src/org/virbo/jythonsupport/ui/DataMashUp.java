
package org.virbo.jythonsupport.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import org.das2.util.LoggerManager;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Call;
import org.python.parser.ast.Module;
import org.python.parser.ast.Name;
import org.python.parser.ast.exprType;

/**
 * GUI for specifying mashups, where a number of 
 * data sets are loaded and combined.  These are implemented as small
 * jython scripts, consisting only of declarations and an expression.
 * @author jbf
 */
public class DataMashUp extends javax.swing.JPanel {

    private static final Logger logger = LoggerManager.getLogger("jython.dashup");
    
    public void setUris( List<String> uris ) {
        this.namedURIListTool1.setUris( uris );
    }
    
    public void setIds( List<String> ids ) {
        this.namedURIListTool1.setIds( ids );
    }
    
    /**
     * Creates new form DataMashUp
     */
    public DataMashUp() {
        initComponents();

        DragSource dragSource = DragSource.getDefaultDragSource();
        DropTarget dropTarget = new DropTarget();
        try {
            dropTarget.addDropTargetListener(createDropTargetListener());
        } catch (TooManyListenersException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        jTree1.setDropTarget(dropTarget);
        dragSource.createDefaultDragGestureRecognizer( jTree1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( jList1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( namedURIListTool1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );

        String data = "add(x,y)";
        TreePath tp= new TreePath( ( (DefaultMutableTreeNode) jTree1.getModel().getRoot() ).getPath() );
        doDrop(data,tp);
    }

    /**
     * return the jython expression for this tree.
     * @param m
     * @param n
     * @return 
     */
    private String getJython( DefaultTreeModel m, Object n ) {
        if ( m.isLeaf(n) ) {
            return n.toString();
        } else {
            String sn= n.toString();
            int iparen= sn.indexOf("(");
            StringBuilder t= new StringBuilder( sn.substring(0,iparen) + "(" );
            int nchild= m.getChildCount(n);
            for ( int i=0; i<nchild; i++ ) {
                if ( i>0 ) t.append(",");
                t.append( getJython( m, m.getChild( n, i ) ) );
            }
            t.append(")");
            return t.toString();
        }
    }
    
    /**
     * return the mashup as a jython inline script.
     * @return  the mashup as a jython inline script.
     */
    public String getAsJythonInline() {
        StringBuilder b= new StringBuilder("vap+inline:");
        b.append( namedURIListTool1.getAsJythonInline() );
        
        DefaultTreeModel m= (DefaultTreeModel) jTree1.getModel();
        
        b.append( getJython( m, m.getRoot() ) );
        
        return b.toString();
        
    }
    
    private void fillTreeCall( Call c, DefaultTreeModel m, MutableTreeNode parent ) {
        for ( int i=0; i<c.args.length; i++ ) {
            exprType et= c.args[i];
            if ( et instanceof Name ) {
                parent.insert( new DefaultMutableTreeNode(((Name)et).id), i );
            } else {
                Call call= (Call)et;
                DefaultMutableTreeNode child= new DefaultMutableTreeNode( funcCallName( call ) );
                fillTreeCall( call, m, child );
            }
        }
    }
    
    private String funcCallName( Call c ) {
        Name name= (Name)c.func;
        return name.id;
    }
    
    private void fillTree( String expr ) {
        Module n= (Module)org.python.core.parser.parse( "x="+expr, "exec" );
        
        Assign assign= (Assign)n.body[0];
        DefaultMutableTreeNode root= new DefaultMutableTreeNode( funcCallName( (Call)assign.value ) );
        DefaultTreeModel model= new DefaultTreeModel( root );
        if ( assign.value instanceof Call ) {
            Call c= (Call)assign.value;
            fillTreeCall( c, model, root );
        }
        jTree1.setModel(model);
    }
    
    /**
     * only split on the delimiter when we are not within the exclude delimiters.  For example,
     * <code>
     * x=getDataSet("http://autoplot.org/data/autoplot.cdf?Magnitude&noDep=T")&y=getDataSet('http://autoplot.org/data/autoplot.cdf?BGSEc&slice1=2')&sqrt(x)
     * </code>
     * @param s the string to split.
     * @param delim the delimiter to split on, for example the ampersand (&).
     * @param exclude1 for example the single quote (')
     * @param exclude2 for example the double quote (")  Note URIs don't support these anyway.
     * @return the split.
     * 
     * This is a copy of another code.
     */
    protected static String[] guardedSplit( String s, char delim, char exclude1, char exclude2 ) {    
        if ( delim=='_') throw new IllegalArgumentException("_ not allowed for delim");
        StringBuilder scopyb= new StringBuilder(s.length());
        char inExclude= (char)0;
        
        for ( int i=0; i<s.length(); i++ ) {
            char c= s.charAt(i);
            if ( inExclude==0 ) {
                if ( c==exclude1 || c==exclude2 ) inExclude= c;
            } else {
                if ( c==inExclude ) inExclude= 0;
            }
            if ( inExclude>(char)0 ) c='_';
            scopyb.append(c);            
        }
        String[] ss= scopyb.toString().split(""+delim);
        
        int i1= 0;
        for ( int i=0; i<ss.length; i++ ) {
            int i2= i1+ss[i].length();
            ss[i]= s.substring(i1,i2);
            i1= i2+1;
        } 
        return ss;
    }
    
    public void setAsJythonInline( String script ) {
        if ( script.startsWith("vap+inline:") ) {
            script= script.substring(11);
        }
        String[] ss= guardedSplit( script, '&', '\'', '\"' );
        List<String> ids= new ArrayList<String>();
        List<String> uris= new ArrayList<String>();
        for ( String s: ss ) {
            int i= s.indexOf("=");
            if ( i>-1 ) {
                Pattern p= Pattern.compile("(.*)=getDataSet\\('(.+)'\\)");
                Matcher m= p.matcher(s);
                if ( m.matches() ) {
                    ids.add(m.group(1));
                    uris.add(m.group(2));
                } else {
                    throw new IllegalArgumentException("script is not jython mashup");
                }
            } else {
                fillTree( s );
            }
        }
        setIds(ids);
        setUris(uris);
    }
    
    private void doDrop( String data, final TreePath tp ) {

        DefaultTreeModel model= (DefaultTreeModel) jTree1.getModel();

        MutableTreeNode mtn= (MutableTreeNode)tp.getLastPathComponent();
        MutableTreeNode parent= (MutableTreeNode)mtn.getParent();

        mtn.setUserObject(data);
        for ( int i=mtn.getChildCount()-1; i>=0; i-- ) {
            mtn.remove(i);
        }

        if ( !data.startsWith("vap+") && data.endsWith(")") ) { //TODO: cheesy vap+ to detect URIs.
            int i= data.indexOf("(");
            int j= data.length()-1;
            String[] ss= data.substring(i+1,j).split(",",-2);
            int n= ss.length;
            for ( int k=0; k<n; k++ ) {
                mtn.insert( new DefaultMutableTreeNode(ss[k]), k );
            }
        }

        if ( parent==null ) {
            model.setRoot( mtn );
        } else { 
            int index= model.getIndexOfChild( parent, mtn );
            model.removeNodeFromParent(mtn);
            model.insertNodeInto( mtn, parent, index );
        }


        jTree1.collapsePath(tp);
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                jTree1.expandPath(tp);
            }
        });        
    }
    
    /**
     * return true if the script conforms to the jython dashup requirements.
     * @param jython script.
     * @return true if the script conforms to the jython dashup requirements.
     */
    public static boolean isDataMashupJythonInline( String jython ) {
        try {
            new DataMashUp().setAsJythonInline(jython);
        } catch ( Exception ex ) {
            return false;
        }
        return true;
    }
        
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this
     * code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        jSplitPane2 = new javax.swing.JSplitPane();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTree1 = new javax.swing.JTree();
        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel2 = new javax.swing.JPanel();
        namedURIListTool1 = new org.virbo.jythonsupport.ui.NamedURIListTool();

        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(0.5);

        jList1.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "add(x,y)", "sin(a)", "atan2(x,y)", "link(x,y,z)" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jList1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane3.setViewportView(jList1);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 357, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 219, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("tab1", jPanel1);

        jSplitPane2.setLeftComponent(jTabbedPane1);

        jTree1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTree1MouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(jTree1);

        jSplitPane2.setRightComponent(jScrollPane2);

        jSplitPane1.setBottomComponent(jSplitPane2);

        jPanel2.setLayout(new javax.swing.BoxLayout(jPanel2, javax.swing.BoxLayout.Y_AXIS));
        jPanel2.add(namedURIListTool1);

        jScrollPane1.setViewportView(jPanel2);

        jSplitPane1.setTopComponent(jScrollPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 603, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jTree1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTree1MouseClicked
        if ( evt.getClickCount()==2 ) {
            TreePath tp= jTree1.getClosestPathForLocation( evt.getX(), evt.getY() );
            jTree1.setSelectionPath(tp);
            String currentId= tp.getLastPathComponent().toString();
            String s= namedURIListTool1.selectDataId(currentId);
            if ( s!=null ) {
                doDrop(s,tp);
            }
        }
    }//GEN-LAST:event_jTree1MouseClicked

    final DropTargetListener createDropTargetListener() {
        return new DropTargetListener() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                TreePath tp= jTree1.getClosestPathForLocation( dtde.getLocation().x, dtde.getLocation().y );
                jTree1.setSelectionPath(tp);
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    String data = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);

                    TreePath tp= jTree1.getClosestPathForLocation( dtde.getLocation().x, dtde.getLocation().y );

                    doDrop(data,tp);
                    
                } catch (UnsupportedFlavorException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            }
        };
    }

    final DragGestureListener createDragGestureListener() {
        return new DragGestureListener() {

            @Override
            public void dragGestureRecognized(DragGestureEvent dge) {
                String s=null;
                
                System.err.println( "dragGestureRecognized "+dge.getComponent() );
                
                if ( dge.getComponent()==jList1 ) {
                    s= (String)jList1.getSelectedValue();
                } else if  ( dge.getComponent()==jTree1 ) {
                    if ( jTree1.getSelectionCount()==1 ) {
                        TreePath tp= jTree1.getSelectionPath();
                        s= (String)tp.getLastPathComponent();
                    }
                } else if  ( dge.getComponent()==namedURIListTool1 ) {
                    System.err.println("herehere");
                    
                }
                if ( s!=null ) {
                    dge.startDrag(null, new StringSelection(s) ) ;
                }
            }
            
            
        };
    };
    
    public static void main( String[] args ) {
        new DataMashUp().fillTree("sin(cos(a))");
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList jList1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTree jTree1;
    private org.virbo.jythonsupport.ui.NamedURIListTool namedURIListTool1;
    // End of variables declaration//GEN-END:variables
}
