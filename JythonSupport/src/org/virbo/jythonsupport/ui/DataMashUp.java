
package org.virbo.jythonsupport.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.Call;
import org.python.parser.ast.Module;
import org.python.parser.ast.Name;
import org.python.parser.ast.Num;
import org.python.parser.ast.Str;
import org.python.parser.ast.UnaryOp;
import org.python.parser.ast.exprType;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * GUI for specifying mashups, where a number of 
 * data sets are loaded and combined.  These are implemented as small
 * jython scripts, consisting only of declarations and an expression.
 * @author jbf
 */
public class DataMashUp extends javax.swing.JPanel {

    private static final Logger logger = LoggerManager.getLogger("jython.dashup");

    private static final String LABEL_DIRECTIONS = "Double-click on the name to set the data set.  Shift-click for popup plot.";
    
    /**
     * when a URI results in an exception
     */
    private static final QDataSet ERROR_DS= DataSetUtil.asDataSet( new EnumerationUnits("DataMashUp").createDatum("*Fail*") );
    
    /**
     * when a data source returns null.
     */
    private static final QDataSet NULL_DS= DataSetUtil.asDataSet( new EnumerationUnits("DataMashUp").createDatum("*Null*") );
    
    /**
     * set the list of URIs.  
     * @param uris a list of URIs.
     */
    public void setUris( List<String> uris ) {
        this.namedURIListTool1.setUris( uris );
    }
    
    /**
     * set the ids for each of the URIs.
     * @param ids list of Java identifiers.
     */
    public void setIds( List<String> ids ) {
        this.namedURIListTool1.setIds( ids );
    }
    
    /**
     * rename the parameter and all usages within the tree.
     * @param oldName
     * @param newName 
     */
    public void rename( String oldName, String newName ) {
        DefaultTreeModel tm= (DefaultTreeModel) jTree1.getModel();
        renameImpl( tm, tm.getRoot(), oldName, newName );
        jTree1.treeDidChange();
        jTree1.revalidate();
        jTree1.repaint();
    }
            
    private void renameImpl( DefaultTreeModel tm, Object parent, String oldName, String newName ) {
        int n= tm.getChildCount(parent);
        for ( int i=0; i<n; i++ ) {
            DefaultMutableTreeNode dmtn= (DefaultMutableTreeNode)tm.getChild(parent, i);
            if ( dmtn.isLeaf() ) {  
                if ( dmtn.getUserObject().equals(oldName) ) {
                    dmtn.setUserObject(newName);
                }
            } else {
                renameImpl( tm, dmtn, oldName, newName );
            }
        }
    }
    
    /**
     * recalculate the images
     */
    protected void refresh() {
        resolved.clear();
        imaged.clear();
        jTree1.treeDidChange();
        jTree1.revalidate();
        jTree1.repaint();
        checkForTSB();
    }

    /**
     * Creates new form DataMashUp
     */
    public DataMashUp() {
        initComponents();
        namedURIListTool1.setDataMashUp(this);
        namedURIListTool1.addPropertyChangeListener( NamedURIListTool.PROP_TIMERANGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                timeRangeTextField.setText( namedURIListTool1.getTimeRange().toString() );
            }
        });

        DragSource dragSource = DragSource.getDefaultDragSource();
        DropTarget dropTarget = new DropTarget();
        try {
            dropTarget.addDropTargetListener(createTreeDropTargetListener());
        } catch (TooManyListenersException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        jTree1.setDropTarget(dropTarget);
        
        DropTarget listDropTarget= new DropTarget();
        try {
            listDropTarget.addDropTargetListener( createListDropTargetListener() );
        } catch (TooManyListenersException ex ) {
            logger.log(Level.SEVERE, null, ex);
        }
        scratchList.setDropTarget(listDropTarget);

        dragSource.createDefaultDragGestureRecognizer( jTree1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        
        // add all jLists
        dragSource.createDefaultDragGestureRecognizer( jList1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( jList2, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( jList3, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( scratchList, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        
        dragSource.createDefaultDragGestureRecognizer( namedURIListTool1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );

        String data = "ds";
        TreePath tp= new TreePath( ( (DefaultMutableTreeNode) jTree1.getModel().getRoot() ).getPath() );
        doDrop(data,tp);
        
        Runnable run= new Runnable() {
            public void run() {
                backFromFile();
            }
        };
        new Thread(run).start();
    }

    private boolean isInfix( String op ) {
        return op.equals("or") || op.equals("and");
    }
    
    // this is not-trivial because of parentheses.
//    private String getOverloaded( String op ) {
//        if ( op.equals("add") ) {
//            return "+";
//        } else {
//            return null;
//        }
//    }
    
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
            if ( iparen>-1 ) sn= sn.substring(0,iparen);
            int nchild= m.getChildCount(n);
            if ( isInfix(sn) && nchild==2 ) {
                //String alt= null; //getOverloaded(sn);
                //if ( alt!=null ) {
                //    return getJython( m, m.getChild( n, 0 ) ) + alt + getJython( m, m.getChild(n,1) ) ;
                //} else {
                    return getJython( m, m.getChild( n, 0 ) ) + "."+sn+"("+ getJython( m, m.getChild(n,1) ) +")" ;
                //}
            } else {
                StringBuilder t= new StringBuilder( sn + "(" );
                for ( int i=0; i<nchild; i++ ) {
                    if ( i>0 ) t.append(",");
                    t.append( getJython( m, m.getChild( n, i ) ) );
                }
                t.append(")");
                return t.toString();
            }
        }
    }
    
    /**
     * return the mashup as a jython inline script.
     * @return  the mashup as a jython inline script.
     */
    public String getAsJythonInline() {
        StringBuilder b= new StringBuilder("vap+inline:");
        b.append( namedURIListTool1.getAsJythonInline() );

        b.append( getJythonSynchronize("&") );
        
        DefaultTreeModel m= (DefaultTreeModel) jTree1.getModel();
        
        b.append( getJython( m, m.getRoot() ) );
        String timerange= timeRangeTextField.getText();
        if ( timeRangeTextField.isEnabled() ) {
            b.append("&timerange=").append(timerange.trim().replaceAll(" ","+") );
        }
        
        return b.toString();
        
    }
    
    private StringBuilder getJythonSynchronize(String delim) {
        StringBuilder b= new StringBuilder();
        String[] ids= namedURIListTool1.getIds();
        if ( synchronizeCB.isSelected() && ids.length>1 ) {
            StringBuilder list=new StringBuilder(ids[0]);
            for ( int i=1; i<ids.length; i++ ) {
                list.append(",").append(ids[i]);
            }
            b.append( "(" ).append( list ).append( ")=synchronize(").append(ids[0]).append(",").append(list).append(")").append(delim);
        }
        return b;
    }
    
    /**
     * return the jython for just the node.
     * @param tn
     * @return 
     */
    public String getAsJythonInline( TreeNode tn ) {
        StringBuilder b= new StringBuilder("vap+inline:");
        b.append( namedURIListTool1.getAsJythonInline() );
        
        DefaultTreeModel m= (DefaultTreeModel) jTree1.getModel();
        
        b.append( getJython( m, tn ) );
        b.append( getJythonSynchronize("&") );
        
        String timerange= timeRangeTextField.getText();
        if ( timeRangeTextField.isEnabled() ) {
            b.append("&timerange=").append(timerange.trim().replaceAll(" ","+") );
        }
        
        return b.toString();
    }
    
    private void fillTreeExprType( exprType et, MutableTreeNode parent, int i ) {
        if ( et instanceof Name ) {
            parent.insert( new DefaultMutableTreeNode(((Name)et).id), i );
        } else if ( et instanceof Num ) {
            parent.insert( new DefaultMutableTreeNode( String.valueOf(((Num)et).n) ),i );
        } else if ( et instanceof Str ) {
            parent.insert( new DefaultMutableTreeNode( "'"+String.valueOf(((Str)et).s)+"'" ),i );
        } else if ( et instanceof UnaryOp ) { // a negative number appears as a unary minus op and positive number.
            exprType et1= ((UnaryOp)et).operand;
            switch (((UnaryOp)et).op) {
                case 4:
                    fillTreeExprType( et1, parent, i );
                    ((DefaultMutableTreeNode)parent.getChildAt(i)).setUserObject( "-"+((DefaultMutableTreeNode)parent.getChildAt(i)).getUserObject() );
                    break;
                case 3:
                    fillTreeExprType( et1, parent, i );
                    ((DefaultMutableTreeNode)parent.getChildAt(i)).setUserObject( "+"+((DefaultMutableTreeNode)parent.getChildAt(i)).getUserObject() );
                    break;            
                default:
                    fillTreeExprType( et1, parent, i );
                    break;
            }
        } else {
            Call call= (Call)et;
            DefaultMutableTreeNode child= new DefaultMutableTreeNode( funcCallName( call ) );
            if ( call.func instanceof Attribute ) {
                fillTreeCall( ((Attribute)call.func).value, call, child );
            } else {
                fillTreeCall( call, child );
            }
            parent.insert( child, i);
        }        
    }
    
    private void fillTreeCall( Call c, MutableTreeNode parent ) {
        for ( int i=0; i<c.args.length; i++ ) {
            exprType et= c.args[i];
            fillTreeExprType( et, parent, i );
        }
    }
    
    private void fillTreeCall( exprType n, Call c, MutableTreeNode parent ) {
        fillTreeExprType( n, parent, 0 );
        for ( int i=0; i<c.args.length; i++ ) {
            exprType et= c.args[i];
            fillTreeExprType( et, parent, i+1 );
        }
    }    
    
    private String funcCallName( Call c ) {
        exprType et= c.func;
        if ( et instanceof Name ) {
            Name name= (Name)et;
            return name.id;
        } else if ( et instanceof Attribute ) {  // x.or(y)
            Attribute attr= (Attribute)et;
            return attr.attr;
        } else {
            throw new IllegalArgumentException("unsupported call type");
        }
    }
    
    public interface Resolver {
        QDataSet getDataSet( String uri );
        BufferedImage getImage( QDataSet qds );
        void interactivePlot( QDataSet qds );
    }
    
    private Resolver resolver;
    
    public void setResolver( Resolver r ) {
        this.directionsLabel.setText(LABEL_DIRECTIONS);
        this.jTree1.setRowHeight(0);
        this.resolver= r;
    }
    
    final Map<String,QDataSet> resolved= new HashMap();
    final Map<String,String> resolvePending= new HashMap();
    final Map<QDataSet,BufferedImage> imaged= new HashMap();
    final Map<QDataSet,String> imagePending= new HashMap();
    
    /**
     * implement a cache to get the dataset from the node.
     * @param value the node
     * @return the dataset at this node.
     */
    private QDataSet getDataSet( final TreeNode value ) {
        String uri= namedURIListTool1.getUriForId(value.toString());
        if ( uri==null ) {
            uri= getAsJythonInline( value );
        }
        if ( SwingUtilities.isEventDispatchThread() ) {
            QDataSet qds= resolved.get(uri);
            if ( qds==null ) {
                synchronized ( resolvePending ) {
                    if ( resolvePending.containsKey(uri) ) { // TODO: locking
                        return null;
                    } else {
                        resolvePending.put( uri, "" );
                    }
                }
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        getDataSet( value ); // call back on a different thread.
                        jTree1.treeDidChange();
                    }
                };
                new Thread(run).start(); 
            }
            return qds;
            
        } else {
            synchronized ( resolved ) {
                QDataSet qds= resolved.get(uri);
                if ( qds==null ) {
                    logger.log(Level.FINE, "resolving URI {0}", uri );
                    long t0= System.currentTimeMillis();
                    try {
                        qds= resolver.getDataSet( uri );
                        if ( qds==null ) qds= NULL_DS;
                        resolved.put( uri, qds );
                        resolvePending.remove( uri );
                        jTree1.treeDidChange();
                        logger.log(Level.FINE, "done resolving URI in {0} ms: {1}", new Object[]{System.currentTimeMillis()-t0, uri });
                    } catch ( Exception ex  ) {
                        resolved.put( uri, ERROR_DS );
                        resolvePending.remove( uri );
                        jTree1.treeDidChange();
                    }
                }
                return qds;
            }
        }
        
    }
    
    private BufferedImage getImage( final QDataSet qds  ) {
        if ( SwingUtilities.isEventDispatchThread() ) {
            BufferedImage im= imaged.get(qds);
            if ( im==null ) {
                synchronized ( imagePending ) {
                    if ( imagePending.containsKey(qds) ) { // TODO: locking
                        return null;
                    } else {
                        imagePending.put( qds, "" );                    
                    }
                }
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        if ( qds!=null ) {
                            getImage( qds );
                            jTree1.treeDidChange();
                        }
                    }
                };
                new Thread(run).start();
            }
            return im;
            
        } else {
            synchronized ( imaged ) {
                BufferedImage im= imaged.get(qds);
                if ( im==null ) {
                    if ( qds!=null ) {
                        logger.log(Level.FINE, "rendering dataset {0}", qds.toString() );
                        long t0= System.currentTimeMillis();
                        im= resolver.getImage( qds );
                        Graphics g= im.getGraphics();
                        g.setColor(Color.lightGray);
                        g.drawRect(0,0,im.getWidth()-1,im.getHeight()-1);
                        imaged.put( qds, im );
                        imagePending.remove( qds );
                        jTree1.treeDidChange();
                        logger.log(Level.FINE, "done rendering dataset in {0} ms: {1}",  new Object[]{System.currentTimeMillis()-t0,qds.toString()} );
                    }
                }
                return im;
            }
        }
    }
    
    private TreeCellRenderer getCellRenderer( ) {
        return new TreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                String s= value.toString();
                Icon icon=null;
                if ( resolver!=null ) {
                    QDataSet ds= getDataSet( (TreeNode)value );
                    if ( ds!=null ) {
                        s= "<html>" + s + " <span color='gray'>" +ds.toString() + "</span>";
                        BufferedImage im= getImage( ds );
                        if ( im!=null ) {
                            icon= new ImageIcon(im);
                        }
                    }
                    
                }
                JLabel result= new JLabel( s );
                if ( icon!=null ) {
                    result.setIcon(icon);
                    Dimension d= new Dimension( icon.getIconWidth(), icon.getIconHeight() );
                    result.setMinimumSize(d);
                    result.setPreferredSize( new Dimension( 600, icon.getIconHeight() ) );
                } else {
                    if ( resolver!=null ) {
                        BufferedImage im= new BufferedImage(60,60,BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g= (Graphics2D)im.getGraphics();
                        g.setColor(Color.lightGray);
                        g.drawRect( 0,0, im.getWidth()-1, im.getHeight()-1 );
                        result.setIcon( new ImageIcon(im) );
                        Dimension d= new Dimension( 60, 60 );
                        result.setMinimumSize(d);
                        result.setPreferredSize( new Dimension( 600, 60 ) );
                    }
                }
                
                return result;
            }
        };     
    }
    
    private MutableTreeNode getTreeNode( String expr ) {
        Module n= (Module)org.python.core.parser.parse( "x="+expr, "exec" );
        
        DefaultMutableTreeNode root;
        Assign assign= (Assign)n.body[0];
        if ( assign.value instanceof Name ) {
            root= new DefaultMutableTreeNode( ((Name)assign.value).id );
        } else if ( assign.value instanceof Num ) {
            root= new DefaultMutableTreeNode( ((Num)assign.value).n );
        } else {
            root= new DefaultMutableTreeNode( funcCallName( (Call)assign.value ) );
            if ( assign.value instanceof Call ) {
                Call c= (Call)assign.value;
                if ( c.func instanceof Attribute ) {
                    Attribute attr= (Attribute)c.func;
                    fillTreeCall( attr.value, c, root );
                } else {
                    fillTreeCall( c, root );
                }
            }            
        }
        return root;
    }
    
    private void fillTree( String expr ) {
        Module n= (Module)org.python.core.parser.parse( "x="+expr, "exec" );
        
        Assign assign= (Assign)n.body[0];
        if ( assign.value instanceof Name ) {
            DefaultMutableTreeNode root= new DefaultMutableTreeNode( ((Name)assign.value).id );
            DefaultTreeModel model= new DefaultTreeModel( root );
            jTree1.setModel(model);
            jTree1.setCellRenderer( getCellRenderer() );
        } else {
            DefaultMutableTreeNode root= new DefaultMutableTreeNode( funcCallName( (Call)assign.value ) );
            DefaultTreeModel model= new DefaultTreeModel( root );
            if ( assign.value instanceof Call ) {
                Call c= (Call)assign.value;
                if ( c.func instanceof Attribute ) {
                    Attribute attr= (Attribute)c.func;
                    fillTreeCall( attr.value, c, root );
                } else {
                    fillTreeCall( c, root );
                }
            }            
            jTree1.setModel(model);
            for (int i = 0; i < jTree1.getRowCount(); i++) {
                jTree1.expandRow(i);
            }
            jTree1.setCellRenderer( getCellRenderer() );            
        }
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
    
    /**
     * configure the mashup tool using the "vap+inline" URI.
     * @param script 
     */
    public void setAsJythonInline( String script ) {
        if ( script.startsWith("vap+inline:") ) {
            script= script.substring(11);
        }
        String[] ss= guardedSplit( script, '&', '\'', '\"' );
        List<String> ids= new ArrayList<>();
        List<String> uris= new ArrayList<>();
        boolean haveAllIds= false;
        String timerange= null;
        for ( String s: ss ) {
            if ( s.trim().length()==0 ) continue;
            int i= s.indexOf("=");
            if ( i>-1 ) {
                Pattern p= Pattern.compile("(.+)=getDataSet\\('(.*)'\\)");
                Matcher m= p.matcher(s);
                if ( m.matches() ) {
                    ids.add(m.group(1));
                    String suri= m.group(2);
                    URI uri;
                    try {
                        uri= new URI(suri);
                    } catch (URISyntaxException ex) {
                        uri= null;
                    }
                    uris.add(suri);
                    if ( uri!=null ) {
                        try {
                            DataSourceFactory dsf= DataSetURI.getDataSourceFactory(uri,new NullProgressMonitor());
                            if ( dsf!=null ) {
                                try {
                                    DataSource dss= dsf.getDataSource(new URI(suri));
                                    TimeSeriesBrowse tsb= dss.getCapability( TimeSeriesBrowse.class );
                                    if ( tsb!=null ) {
                                        timerange= tsb.getTimeRange().toString();
                                    }
                                } catch (Exception ex) {
                                    logger.log(Level.SEVERE, null, ex);
                                }
                            }
                        } catch (IOException | IllegalArgumentException | URISyntaxException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                } else if ( s.contains("synchronize(") ) {
                    synchronizeCB.setSelected(true);
                } else {
                     if ( s.substring(0,i).trim().equals("timerange") ) {
                        timerange= s.substring(i+1).trim();
                    } else {
                        throw new IllegalArgumentException("script is not jython mashup");
                    }
                }
            } else {
                if ( haveAllIds==false ) {
                    haveAllIds= true;
                    setIds(ids);
                    setUris(uris);
                }
                fillTree( s );
            }
        }
        if ( haveAllIds==false ) {
            setIds(ids);
            setUris(uris);            
        }
        if ( timerange==null ) {
            timeRangeTextField.setText( "" );
            timeRangeTextField.setEnabled(false);
            timeRangeLabel.setEnabled(false);
            timeRangeLabel.setToolTipText("In-line code does not support Time Series Browse");
        } else {
            timeRangeTextField.setText( timerange.replaceAll("\\+", " " ) );
            timeRangeTextField.setEnabled(true);
            timeRangeLabel.setEnabled(true);
            timeRangeLabel.setToolTipText("Current time range for data requests");
        }
    }
    
    public void enableTimeRange() {
        timeRangeLabel.setEnabled(true);
        timeRangeTextField.setEnabled(true);
    }
    
    private void doDrop( String data, TreePath tp ) {

        DefaultTreeModel model= (DefaultTreeModel) jTree1.getModel();

        MutableTreeNode mtn= (MutableTreeNode)tp.getLastPathComponent();
        MutableTreeNode parent= (MutableTreeNode)mtn.getParent();
        
        if ( !data.startsWith("vap+") ) { //TODO: cheesy vap+ to detect URIs.
            int index= -1;
            if ( parent!=null ) {
                index= parent.getIndex(mtn);
                parent.remove(mtn);
            } 

            MutableTreeNode n= getTreeNode(data);
            if ( parent==null ) {
                model.setRoot(n);
            } else {
                parent.insert( n, index );
                model.nodeStructureChanged(parent);
            }
            tp= new TreePath(n);
            
        } else {
            mtn.setUserObject(data);
            model.nodeChanged(mtn);
        }

//        if ( parent==null ) {
//            model.setRoot( mtn );
//        } else { 
//            int index= model.getIndexOfChild( parent, mtn );
//            model.removeNodeFromParent(mtn);
//            model.insertNodeInto( mtn, parent, index );
//        }

        final TreePath ftp= tp;
        
        jTree1.collapsePath(ftp);
        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run() {
                jTree1.expandPath(ftp);
                imaged.clear();
                resolved.clear();
                jTree1.treeDidChange();
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
            logger.log( Level.FINER, null, ex );
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

        jPopupMenu1 = new javax.swing.JPopupMenu();
        addItemMenuItem = new javax.swing.JMenuItem();
        deleteItemsMenuItem = new javax.swing.JMenuItem();
        jSplitPane1 = new javax.swing.JSplitPane();
        jSplitPane2 = new javax.swing.JSplitPane();
        jPanel4 = new javax.swing.JPanel();
        directionsLabel = new javax.swing.JLabel();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTree1 = new javax.swing.JTree();
        jPanel7 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        jList2 = new javax.swing.JList();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jList3 = new javax.swing.JList();
        myFunctionsPanel = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        scratchList = new javax.swing.JList();
        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel2 = new javax.swing.JPanel();
        namedURIListTool1 = new org.virbo.jythonsupport.ui.NamedURIListTool();
        synchronizeCB = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        timeRangeLabel = new javax.swing.JLabel();
        timeRangeTextField = new javax.swing.JTextField();

        addItemMenuItem.setText("Add function...");
        addItemMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addItemMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(addItemMenuItem);

        deleteItemsMenuItem.setText("Delete Items");
        deleteItemsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteItemsMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(deleteItemsMenuItem);

        jSplitPane1.setDividerLocation(100);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(0.5);

        jSplitPane2.setDividerLocation(300);

        directionsLabel.setText("Double-click on the name to set the variable or constant argument.");

        jTree1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTree1MouseClicked(evt);
            }
        });
        jScrollPane6.setViewportView(jTree1);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(directionsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
            .addComponent(jScrollPane6)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(directionsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 302, Short.MAX_VALUE))
        );

        jSplitPane2.setRightComponent(jPanel4);

        jLabel2.setText("Drag functions onto the palette to the right.");

        jList1.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "add(x,y)", "subtract(x,y)", "multiply(x,y)", "divide(x,y)", "pow(x,y)", "log10(x)", "sqrt(x)", "atan2(x,y)", "toRadians(x)", "toDegrees(x)", "sin(x)", "cos(x)", "tan(x)", "asin(x)", "acos(x)", "atan2(y,x)", "atan(x)", " " };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jList1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane3.setViewportView(jList1);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("mathematics", jPanel1);

        jList2.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "link(x,y)", "link(x,y,z)", "slice1(ds,0)", "smooth(ds,5)" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jList2.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane4.setViewportView(jList2);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("dataset", jPanel3);

        jList3.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "putValues(ds,w,v)", "removeValues(ds,w)", "removeValuesGreaterThan(ds,v)", "removeValuesLessThan(ds,v)", "where(c)", "lt(ds1,ds2)", "le(ds1,ds2)", "gt(ds1,ds2)", "ge(ds1,ds2)", "eq(ds1,ds2)", "ne(ds1,ds2)", "or(ds1,ds2)", "and(ds1,ds2)" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jList3.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(jList3);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("filters", jPanel5);

        scratchList.setToolTipText("scratch is a list for storing expressions");
        scratchList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                scratchListMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                scratchListMouseReleased(evt);
            }
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scratchListMouseClicked(evt);
            }
        });
        jScrollPane5.setViewportView(scratchList);

        javax.swing.GroupLayout myFunctionsPanelLayout = new javax.swing.GroupLayout(myFunctionsPanel);
        myFunctionsPanel.setLayout(myFunctionsPanelLayout);
        myFunctionsPanelLayout.setHorizontalGroup(
            myFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
        );
        myFunctionsPanelLayout.setVerticalGroup(
            myFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("my functions", myFunctionsPanel);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 286, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jTabbedPane1))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(jLabel2)
                .addContainerGap(308, Short.MAX_VALUE))
            .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                    .addGap(32, 32, 32)
                    .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE)))
        );

        jSplitPane2.setLeftComponent(jPanel7);

        jSplitPane1.setBottomComponent(jSplitPane2);

        namedURIListTool1.setMinimumSize(new java.awt.Dimension(100, 100));
        namedURIListTool1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                namedURIListTool1FocusLost(evt);
            }
        });

        synchronizeCB.setText("synchronize timetags, interpolating data to the first dataset's time tags");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(synchronizeCB)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(namedURIListTool1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addComponent(namedURIListTool1, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(synchronizeCB)
                .addContainerGap())
        );

        jScrollPane1.setViewportView(jPanel2);

        jSplitPane1.setTopComponent(jScrollPane1);

        jLabel1.setText("Load these Data Sets into variable names:");

        timeRangeLabel.setText("Time Range:");
        timeRangeLabel.setEnabled(false);
        timeRangeLabel.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeRangeTextFieldFocusLost(evt);
            }
        });

        timeRangeTextField.setEnabled(false);
        timeRangeTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeRangeTextFieldFocusLost(evt);
            }
        });
        timeRangeTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeTextFieldActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 498, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeRangeLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeRangeTextField)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(timeRangeLabel)
                    .addComponent(timeRangeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 444, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void timeRangeTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeRangeTextFieldFocusLost
        try {
            namedURIListTool1.setTimeRange( DatumRangeUtil.parseTimeRange(timeRangeTextField.getText()));
        } catch (ParseException ex) {
            Logger.getLogger(DataMashUp.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_timeRangeTextFieldFocusLost

    private void timeRangeTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeTextFieldActionPerformed
        try {
            namedURIListTool1.setTimeRange( DatumRangeUtil.parseTimeRange(timeRangeTextField.getText()));
        } catch (ParseException ex) {
            Logger.getLogger(DataMashUp.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_timeRangeTextFieldActionPerformed

    private void jTree1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTree1MouseClicked
        if ( evt.isShiftDown() ) {
            TreePath tp= jTree1.getClosestPathForLocation( evt.getX(), evt.getY() );
            jTree1.setSelectionPath(tp);
            QDataSet showMe= resolved.get( getAsJythonInline( (TreeNode)jTree1.getSelectionPath().getLastPathComponent() ));
            if ( showMe!=null ) {
                resolver.interactivePlot( showMe );
            } else {
                if ( resolver!=null ) {
                    Runnable run= new Runnable() {
                        @Override
                        public void run() {
                            QDataSet showMe= resolver.getDataSet( getAsJythonInline( (TreeNode)jTree1.getSelectionPath().getLastPathComponent() ) );
                            resolver.interactivePlot( showMe );
                        }
                    };
                    new Thread(run).start();
                }
            }

        } else if ( evt.getClickCount()==2 ) {
            TreePath tp= jTree1.getClosestPathForLocation( evt.getX(), evt.getY() );
            if ( !jTree1.getModel().isLeaf(tp.getLastPathComponent()) ) {
                return;
            }
            jTree1.setSelectionPath(tp);
            String currentId= tp.getLastPathComponent().toString();
            String s= namedURIListTool1.selectDataId(currentId);
            if ( s!=null ) {
                doDrop(s,tp);
            }
        }
    }//GEN-LAST:event_jTree1MouseClicked

    private void scratchListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scratchListMouseClicked
        if ( jTabbedPane1.getSelectedComponent()==myFunctionsPanel ) {
            if ( evt.isPopupTrigger() ) {
                jPopupMenu1.show( evt.getComponent(), evt.getX(), evt.getY() );
            }
        }
    }//GEN-LAST:event_scratchListMouseClicked

    private void scratchListMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scratchListMousePressed
        if ( jTabbedPane1.getSelectedComponent()==myFunctionsPanel ) {
            if ( evt.isPopupTrigger() ) {
                jPopupMenu1.show( evt.getComponent(), evt.getX(), evt.getY() );
            }
        }
    }//GEN-LAST:event_scratchListMousePressed

    private void scratchListMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scratchListMouseReleased
        if ( jTabbedPane1.getSelectedComponent()==myFunctionsPanel ) {
            if ( evt.isPopupTrigger() ) {
                jPopupMenu1.show( evt.getComponent(), evt.getX(), evt.getY() );
            }
        }
    }//GEN-LAST:event_scratchListMouseReleased

    private void addItemMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addItemMenuItemActionPerformed
        String s= JOptionPane.showInputDialog( this, "Add function" );
        if ( !( s.trim().length()==0 ) ) {
            addToScratch( s.trim() );
        }
    }//GEN-LAST:event_addItemMenuItemActionPerformed

    private void deleteItemsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemsMenuItemActionPerformed
        int[] indices= scratchList.getSelectedIndices();
        for ( int i=indices.length-1; i>=0; i-- ) {
            removeFromScratch(indices[i]);
        }
    }//GEN-LAST:event_deleteItemsMenuItemActionPerformed

    private void namedURIListTool1FocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_namedURIListTool1FocusLost
        // check for TSB when a TSB URI is found.
        checkForTSB();
    }//GEN-LAST:event_namedURIListTool1FocusLost

    private void checkForTSB() {
        String[] suris= namedURIListTool1.getUris();
        String timerange= null;
        for ( String suri: suris ) {
            URI uri;
            try {
                uri= new URI(suri);
            } catch (URISyntaxException ex) {
                uri= null;
            }
            if ( uri!=null ) {
                try {
                    DataSourceFactory dsf= DataSetURI.getDataSourceFactory(uri,new NullProgressMonitor());
                    if ( dsf!=null ) {
                        try {
                            DataSource dss= dsf.getDataSource(new URI(suri));
                            TimeSeriesBrowse tsb= dss.getCapability( TimeSeriesBrowse.class );
                            if ( tsb!=null ) {
                                timerange= tsb.getTimeRange().toString();
                            }
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                } catch (IOException | IllegalArgumentException | URISyntaxException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
        if ( timerange!=null ) {
            timeRangeTextField.setEnabled(true);
            timeRangeLabel.setEnabled(true);
            timeRangeTextField.setText( timerange );
        } else {
            timeRangeTextField.setEnabled(false);
            timeRangeLabel.setEnabled(false);
        }
    }
    
    private void removeFromScratch( int index ) {
        ListModel lm= scratchList.getModel();
        DefaultListModel dlm;
        if ( lm instanceof DefaultListModel ) {
            dlm= (DefaultListModel)lm;
        } else {
            dlm= new DefaultListModel();
            for ( int i=0; i<lm.getSize(); i++ ) {
                dlm.add(i,lm.getElementAt(i));
            }
        }
        dlm.remove(index);
        scratchList.setModel(dlm);
        Runnable run= new Runnable() {
            @Override
            public void run() {
                backToFile();
            }
        };
        new Thread( run ).start();
    }
    
    /**
     * add the expression to the scratch list.
     * @param expression 
     */
    private void addToScratch(String expression) {
        ListModel lm= scratchList.getModel();
        DefaultListModel dlm;
        if ( lm instanceof DefaultListModel ) {
            dlm= (DefaultListModel)lm;
        } else {
            dlm= new DefaultListModel();
            for ( int i=0; i<lm.getSize(); i++ ) {
                dlm.add(i,lm.getElementAt(i));
            }
        }
        int remove= -1;
        for ( int i=0; i<dlm.size(); i++ ) {
            if ( dlm.get(i).toString().equals(expression) ) {
                remove= i;
            }
        }
        if ( remove>-1 ) dlm.removeElementAt(remove);
        dlm.add( dlm.getSize(), expression );
        
        scratchList.setModel(dlm);
        Runnable run= new Runnable() {
            @Override
            public void run() {
                backToFile();
            }
        };
        new Thread( run ).start();
        
    }
    
    private void backToFile( ) {
        try {
            ListModel m= scratchList.getModel();
            File f= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA) );
            File f1= new File( f, "bookmarks" );
            File f2= new File( f1, "mashup.myfunctions.txt" );
            try ( PrintWriter w = new PrintWriter( new FileWriter(f2) ) ) {
                for ( int i=0; i<m.getSize(); i++ ) {
                    w.println(m.getElementAt(i).toString());
                }
            }
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
    }
    
    private void backFromFile() {
        final DefaultListModel dlm= new DefaultListModel();
        try {
            File f= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA) );
            File f1= new File( f, "bookmarks" );
            File f2= new File( f1, "mashup.myfunctions.txt" );
            if ( f2.exists() ) {
                try ( BufferedReader r = new BufferedReader( new FileReader( f2 ) ) ) {
                    String s;
                    while ( ( s= r.readLine() )!=null ) { 
                        dlm.addElement(s);
                    }
                }
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        scratchList.setModel(dlm);
                    }
                };
                SwingUtilities.invokeLater(run);
            }
            
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
    }
    
    final DropTargetListener createTreeDropTargetListener() {
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

                    DefaultMutableTreeNode n= (DefaultMutableTreeNode)tp.getLastPathComponent();
                    String old= getJython( (DefaultTreeModel)jTree1.getModel(), n );
                    addToScratch( old );

                    doDrop(data,tp);
                    
                } catch (UnsupportedFlavorException | IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            }
        };
    }
    
    final DropTargetListener createListDropTargetListener() {
        return new DropTargetListener() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {

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

                    addToScratch( data );
                    
                } catch (UnsupportedFlavorException | IOException ex) {
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
                
                if ( dge.getComponent() instanceof JList ) {
                    s= (String)((JList)dge.getComponent()).getSelectedValue();
                } else if  ( dge.getComponent()==jTree1 ) {
                    if ( jTree1.getSelectionCount()==1 ) {
                        TreePath tp= jTree1.getSelectionPath();
                        DefaultMutableTreeNode n= (DefaultMutableTreeNode)tp.getLastPathComponent();
                        s= getJython( (DefaultTreeModel)jTree1.getModel(), n );
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
        DataMashUp dmu= new DataMashUp();
        dmu.setResolver( new Resolver() {
            EnumerationUnits eu= new EnumerationUnits("foo");
            @Override
            public QDataSet getDataSet(String uri) {
                return DataSetUtil.asDataSet( eu.createDatum(uri) );
            }
            @Override
            public BufferedImage getImage(QDataSet qds) {
                BufferedImage result= new BufferedImage(64,64,BufferedImage.TYPE_4BYTE_ABGR);
                result.getGraphics().drawString( eu.createDatum(qds.value()).toString(), 2, 40 );
                return result;
            }
            @Override
            public void interactivePlot(QDataSet qds) {
                System.err.println( qds );
            }

        });
        dmu.fillTree("add(a,b)");
        JOptionPane.showConfirmDialog( null, dmu );
        System.err.println( dmu.getAsJythonInline() );
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem addItemMenuItem;
    private javax.swing.JMenuItem deleteItemsMenuItem;
    private javax.swing.JLabel directionsLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JList jList1;
    private javax.swing.JList jList2;
    private javax.swing.JList jList3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTree jTree1;
    private javax.swing.JPanel myFunctionsPanel;
    private org.virbo.jythonsupport.ui.NamedURIListTool namedURIListTool1;
    private javax.swing.JList scratchList;
    private javax.swing.JCheckBox synchronizeCB;
    private javax.swing.JLabel timeRangeLabel;
    private javax.swing.JTextField timeRangeTextField;
    // End of variables declaration//GEN-END:variables
}
