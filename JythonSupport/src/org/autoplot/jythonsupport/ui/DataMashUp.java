
package org.autoplot.jythonsupport.ui;

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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultListCellRenderer;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.Timer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import org.das2.datum.DatumRange;
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
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.TimeRangeTool;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.python.core.PyException;
import org.python.parser.ast.BinOp;

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
        List<Boolean> isAuto= new ArrayList<>(ids.size());
        for ( int i=0; i<ids.size(); i++ ) isAuto.add(i,Boolean.FALSE);
        this.namedURIListTool1.setIsAuto(isAuto);
    }
    
    /**
     * rename the parameter and all usages within the tree.
     * @param oldName
     * @param newName 
     */
    public void rename( String oldName, String newName ) {
        DefaultTreeModel tm= (DefaultTreeModel) expressionTree.getModel();
        renameImpl( tm, tm.getRoot(), oldName, newName );
        tm.reload();
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
        expressionTree.treeDidChange();
        expressionTree.revalidate();
        expressionTree.repaint();
        checkForTSB();
    }

    private ListCellRenderer myListCellRenderer=  new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            final javax.swing.JLabel label= (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String v= value.toString();
            if ( v.contains(": ") ) {
                int i= v.lastIndexOf(": ");
                String newv= "<html><b>"+v.substring(0,i)+"</b>: <i>"+v.substring(i+2)+"</i>";
                label.setText(newv);
            }
            return label;
        }
    };
    
    /**
     * Creates new form DataMashUp
     */
    public DataMashUp() {
        initComponents();
        List<String> allItems= new ArrayList<>();
        List<JList> lsms= new ArrayList<>();
        lsms.add(mathematicsList);
        lsms.add(datasetList);
        lsms.add(filtersList);
        lsms.add(scratchList);
        for ( int i=0; i< lsms.size(); i++ ) {
            JList jc= (JList)lsms.get(i);
            for ( int j=0; j<jc.getModel().getSize(); j++ ) {
                String s= (String)jc.getModel().getElementAt(j);
                allItems.add(s);
            }
        }
        Collections.sort(allItems);
        DefaultListModel dlm= new DefaultListModel();
        for ( String s: allItems ) {
            if ( s.trim().length()>0 ) {
                dlm.addElement(s);
            }
        }
        allList.setModel( dlm );
        
        timeRangeRecentComboBox.setPreferenceNode( "timerange" );
        namedURIListTool1.setDataMashUp(this);
        namedURIListTool1.addPropertyChangeListener( NamedURIListTool.PROP_TIMERANGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                timeRangeRecentComboBox.setText( namedURIListTool1.getTimeRange().toString() );
            }
        });

        DragSource dragSource = DragSource.getDefaultDragSource();
        DropTarget dropTarget = new DropTarget();
        try {
            dropTarget.addDropTargetListener(createTreeDropTargetListener());
        } catch (TooManyListenersException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        expressionTree.setDropTarget(dropTarget);
        
        DropTarget listDropTarget= new DropTarget();
        try {
            listDropTarget.addDropTargetListener( createListDropTargetListener() );
        } catch (TooManyListenersException ex ) {
            logger.log(Level.SEVERE, null, ex);
        }
        scratchList.setDropTarget(listDropTarget);

        dragSource.createDefaultDragGestureRecognizer(expressionTree, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        
        // add all jLists
        dragSource.createDefaultDragGestureRecognizer(mathematicsList, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer(datasetList, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer(filtersList, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( scratchList, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( allList, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );
        dragSource.createDefaultDragGestureRecognizer( namedURIListTool1, DnDConstants.ACTION_COPY_OR_MOVE, createDragGestureListener() );

        mathematicsList.setCellRenderer( myListCellRenderer );
        datasetList.setCellRenderer( myListCellRenderer );
        filtersList.setCellRenderer( myListCellRenderer );
        scratchList.setCellRenderer( myListCellRenderer );
        allList.setCellRenderer( myListCellRenderer );
        
        String data = "ds";
        TreePath tp= new TreePath( ( (DefaultMutableTreeNode) expressionTree.getModel().getRoot() ).getPath() );
        doDrop(data,tp);
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                backFromFile();
            }
        };
        new Thread(run).start();
    }

    private boolean isInfix( String op ) {
        switch (op) {
            case "and":
            case "or":
            //case "add":
            //case "multiply":
                return true;
            default:
                return false;
        }
    }
    
    // this is not-trivial because of parentheses.
    // Shouldn't  this be getInfix
    private String getInline( DefaultTreeModel m, Object o) {
        String op= o.toString();
        DefaultMutableTreeNode n= (DefaultMutableTreeNode)o;
        switch (op) {
            case "and":
                return getJython( m, m.getChild( n, 0 ) ) + ".and(" + getJython( m, m.getChild( n, 1 ) ) +")";
            case "or":
                return getJython( m, m.getChild( n, 0 ) ) + ".or(" + getJython( m, m.getChild( n, 1 ) ) +")";
            case "multiply":
                return getJython( m, m.getChild( n, 0 ) ) + "*" + getJython( m, m.getChild( n, 1 ) );
            case "add":
                return getJython( m, m.getChild( n, 0 ) ) + "+" + getJython( m, m.getChild( n, 1 ) );
            default:
                return null;
        }
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
            if ( iparen>-1 ) sn= sn.substring(0,iparen);
            int nchild= m.getChildCount(n);
            if ( isInfix(sn) && nchild==2 ) {
                String alt= getInline(m, n);
                if ( alt!=null ) {
                    if ( m.getRoot()==n ) {
                        return alt;
                    } else {
                        return "("+ alt + ")" ;
                    }
                } else {
                    return getJython( m, m.getChild( n, 0 ) ) + "."+sn+"("+ getJython( m, m.getChild(n,1) ) +")" ;
                }
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
        
        DefaultTreeModel m= (DefaultTreeModel) expressionTree.getModel();
        
        b.append( getJython( m, m.getRoot() ) );
        String timerange= timeRangeRecentComboBox.getText();
        if ( timeRangeRecentComboBox.isEnabled() ) {
            b.append("&timerange=").append(timerange.trim().replaceAll(" ","+") );
        }
        
        return b.toString();
        
    }
    
    private StringBuilder getJythonSynchronize(String delim) {
        StringBuilder b= new StringBuilder();
        if ( synchronizeCB.isSelected() ) {
            String[] ids= namedURIListTool1.getIds();
            if ( ids.length>2 ) {
                StringBuilder list=new StringBuilder("(");
                list.append(ids[1]);
                for ( int i=2; i<ids.length; i++ ) {
                    list.append(",").append(ids[i]);
                }
                list.append(")");
                b.append( list ).append( "=synchronize(").append(ids[0]).append(",").append(list).append(")").append(delim);
            } else if ( ids.length==2 ) {
                StringBuilder list=new StringBuilder("");
                list.append(ids[1]);
                b.append( list ).append( "=synchronizeOne(").append(ids[0]).append(",").append(list).append(")").append(delim);
            }
        }
        return b;
    }
    
    /**
     * return the Jython for just the node.
     * @param tn
     * @return 
     */
    public String getAsJythonInline( TreeNode tn ) {
        StringBuilder b= new StringBuilder("vap+inline:");
        b.append( namedURIListTool1.getAsJythonInline() );
        
        String timerange= timeRangeRecentComboBox.getText();
        if ( timeRangeRecentComboBox.isEnabled() ) {
            b.append("timerange=\'").append(timerange.trim().replaceAll(" ","+")).append("\'&");
        }
        
        DefaultTreeModel m= (DefaultTreeModel) expressionTree.getModel();
        
        b.append( getJython( m, tn ) );
        b.append( getJythonSynchronize("&") );
        
        return b.toString();
    }
    
    private void fillTreeExprType( exprType et, MutableTreeNode parent, int i, List<String> datasets, List<String> usedDatasets) {
        if ( et instanceof Name ) {
            String name= ((Name)et).id;
            if ( datasets.contains(name) || datasets.isEmpty() ) {
                parent.insert( new DefaultMutableTreeNode( name ), i );
            } else {
                parent.insert( new DefaultMutableTreeNode( datasets.get(0) ), i );
            }
        } else if ( et instanceof Num ) {
            parent.insert( new DefaultMutableTreeNode( String.valueOf(((Num)et).n) ),i );
        } else if ( et instanceof Str ) {
            parent.insert(new DefaultMutableTreeNode( "'"+String.valueOf(((Str)et).s)+"'" ),i );
        } else if ( et instanceof Attribute ) {
            exprType vv= ((Attribute)et).value;
            if ( vv instanceof Name ) {
                parent.insert(new DefaultMutableTreeNode( ((Name)vv).id + "." + ((Attribute)et).attr), i );
            } else {
                logger.log(Level.FINE, "expected Name at {0}", (et).toString());
                parent.insert(new DefaultMutableTreeNode( "." + ((Attribute)et).attr), i );
            }
        } else if ( et instanceof UnaryOp ) { // a negative number appears as a unary minus op and positive number.
            exprType et1= ((UnaryOp)et).operand;
            switch (((UnaryOp)et).op) {
                case 4:
                    fillTreeExprType( et1, parent, i, datasets, usedDatasets );
                    ((DefaultMutableTreeNode)parent.getChildAt(i)).setUserObject("-"+((DefaultMutableTreeNode)parent.getChildAt(i)).getUserObject() );
                    break;
                case 3:
                    fillTreeExprType( et1, parent, i, datasets, usedDatasets );
                    ((DefaultMutableTreeNode)parent.getChildAt(i)).setUserObject("+"+((DefaultMutableTreeNode)parent.getChildAt(i)).getUserObject() );
                    break;            
                default:
                    fillTreeExprType( et1, parent, i, datasets, usedDatasets );
                    break;
            }
        } else if ( et instanceof BinOp ) { // a negative number appears as a unary minus op and positive number.
            DefaultMutableTreeNode child= new DefaultMutableTreeNode( nameForBinOp( ((BinOp)et).op ) );
            fillTreeBinOp( (BinOp)et, child, datasets, usedDatasets );            
            parent.insert( child, i );
        } else {
            Call call= (Call)et;
            DefaultMutableTreeNode child= new DefaultMutableTreeNode( funcCallName( call ) );
            if ( call.func instanceof Attribute ) {
                fillTreeCall( ((Attribute)call.func).value, call, child, datasets, usedDatasets  );
            } else {
                fillTreeCall(call, child, datasets, usedDatasets  );
            }
            parent.insert( child, i);
        }        
    }
    
    private void fillTreeCall( Call c, MutableTreeNode parent, List<String> datasets, List<String> usedDatasets) {
        for ( int i=0; i<c.args.length; i++ ) {
            exprType et= c.args[i];
            fillTreeExprType(et, parent, i, datasets, usedDatasets );
        }
    }
    
    private void fillTreeCall( exprType n, Call c, MutableTreeNode parent, List<String> datasets, List<String> usedDatasets ) {
        fillTreeExprType(n, parent, 0, datasets, usedDatasets );
        for ( int i=0; i<c.args.length; i++ ) {
            exprType et= c.args[i];
            fillTreeExprType(et, parent, i+1, datasets, usedDatasets );
        }
    }    
    
    private void fillTreeBinOp( BinOp c, MutableTreeNode parent, List<String> datasets, List<String> usedDatasets ) {
        fillTreeExprType(c.left, parent, 0, datasets, usedDatasets );
        fillTreeExprType(c.right, parent, 1, datasets, usedDatasets );
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
        this.expressionTree.setRowHeight(0);
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
                        expressionTree.treeDidChange();
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
                        expressionTree.treeDidChange();
                        logger.log(Level.FINE, "done resolving URI in {0} ms: {1}", new Object[]{System.currentTimeMillis()-t0, uri });
                    } catch ( Exception ex  ) {
                        resolved.put( uri, ERROR_DS );
                        resolvePending.remove( uri );
                        expressionTree.treeDidChange();
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
                            expressionTree.treeDidChange();
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
                        expressionTree.treeDidChange();
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
                    
                } else {
                    if ( !((DefaultMutableTreeNode)value).isLeaf() && tree.isCollapsed( row ) ) {
                        //DefaultMutableTreeNode n= (DefaultMutableTreeNode)value;
                        String jy=  getJython( (DefaultTreeModel)tree.getModel(), value );
                            //getAsJythonInline(n);
                        s= "<html>" + s + " <span color='gray'>" +jy + "</span>";
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
    
    private MutableTreeNode getTreeNode( String expr, List<String> datasets, List<String> usedDatasets ) {
        Module n;
        
        try {
            n= (Module)org.python.core.parser.parse( "x="+expr, "exec" );
        } catch ( PyException ex ) {
            ex.printStackTrace();
            n= (Module)org.python.core.parser.parse( "x='error'", "exec" ); // x=and(ds1,ds2)
            expr="error "+expr;
        }
        
        DefaultMutableTreeNode root;
        Assign assign= (Assign)n.body[0];
        if ( assign.value instanceof Name ) {
            String name= ((Name)assign.value).id;
            if ( datasets.contains(name) || datasets.isEmpty() ) {
                root= new DefaultMutableTreeNode( name );
            } else {
                root= new DefaultMutableTreeNode( datasets.get(datasets.size()-1) );
            }
        } else if ( assign.value instanceof Num ) {
            root= new DefaultMutableTreeNode( ((Num)assign.value).n );
        } else if ( assign.value instanceof Str ) {
            root= new DefaultMutableTreeNode( expr );
        } else if ( assign.value instanceof Attribute ) {
            root= new DefaultMutableTreeNode( expr );
        } else if ( assign.value instanceof UnaryOp ) {  // negation, eg: -1.0
            UnaryOp op= (UnaryOp)assign.value;
            if ( op.operand instanceof Num ) {
                root= new DefaultMutableTreeNode( "-" + String.valueOf(((Num)op.operand).n).trim() );
            } else {
                root= new DefaultMutableTreeNode( "0.0" );
            }
        } else if ( assign.value instanceof BinOp ) {
            BinOp op= (BinOp)assign.value;
            String sop= nameForBinOp( op.op );
            root= new DefaultMutableTreeNode( sop );
            fillTreeBinOp( op, root, datasets, usedDatasets );
        } else {
            root= new DefaultMutableTreeNode( funcCallName( (Call)assign.value ) );
            if ( assign.value instanceof Call ) {
                Call c= (Call)assign.value;
                if ( c.func instanceof Attribute ) {
                    Attribute attr= (Attribute)c.func;
                    fillTreeCall( attr.value, c, root, datasets, usedDatasets );
                } else {
                    fillTreeCall(c, root, datasets, usedDatasets );
                }
            }            
        }
        return root;
    }
    
    private String nameForBinOp( int op ) {
        String sop;
        switch(op) {
            case 1:
                sop= "add"; break;
            case 2:
                sop= "subtract"; break;
            case 3:
                sop= "multiply"; break;
            case 4:
                sop= "divide"; break;
            default:
                throw new IllegalArgumentException("not supported 720" );
        }
        return sop;
    }
    
    private void fillTree( String expr, List<String> datasets, List<String> usedDatasets) {
        Module n= (Module)org.python.core.parser.parse("x="+expr, "exec" );
        
        Assign assign= (Assign)n.body[0];
        if ( assign.value instanceof Name ) {
            DefaultMutableTreeNode root= new DefaultMutableTreeNode( ((Name)assign.value).id );
            DefaultTreeModel model= new DefaultTreeModel( root );
            expressionTree.setModel(model);
            expressionTree.setCellRenderer( getCellRenderer() );
        } else {
            exprType et= assign.value;
            if ( et instanceof Call ) {
                DefaultMutableTreeNode root= new DefaultMutableTreeNode( funcCallName( (Call)assign.value ) );
                DefaultTreeModel model= new DefaultTreeModel( root );
                Call c= (Call)assign.value;
                if ( c.func instanceof Attribute ) {
                    Attribute attr= (Attribute)c.func;
                    fillTreeCall( attr.value, c, root, datasets, usedDatasets );
                } else {
                    fillTreeCall(c, root, datasets, usedDatasets );
                }
                expressionTree.setModel(model);
            } else if ( et instanceof BinOp ) {
                String sop= nameForBinOp( ((BinOp)et).op );
                DefaultMutableTreeNode root= new DefaultMutableTreeNode( sop );
                DefaultTreeModel model= new DefaultTreeModel( root );
                fillTreeBinOp( (BinOp)et, root, datasets, usedDatasets );
                expressionTree.setModel(model);
            }
            
            for (int i = 0; i < expressionTree.getRowCount(); i++) {
                expressionTree.expandRow(i);
            }
            expressionTree.setCellRenderer( getCellRenderer() );            
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
        boolean synch= false;
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
                                        DatumRange tr= tsb.getTimeRange();
										if ( tr!=null ) {
											timerange= tr.toString();
										} else {
											timerange= "";
										}
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
                    synch=true;
                } else if ( s.contains("synchronizeOne(") ) {
                    synch=true;
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
                fillTree(s, ids, new ArrayList<String>() );
            }
        }
        synchronizeCB.setSelected(synch);
        
        if ( haveAllIds==false ) {
            setIds(ids);
            setUris(uris);
        }
        if ( timerange==null ) {
            timeRangeRecentComboBox.setText( "" );
            timeRangeRecentComboBox.setEnabled(false);
            timeRangeLabel.setEnabled(false);
            timeRangeLabel.setToolTipText("In-line code does not support Time Series Browse");
        } else {
            timeRangeRecentComboBox.setText( timerange.replaceAll("\\+", " " ) );
            timeRangeRecentComboBox.setEnabled(true);
            timeRangeLabel.setEnabled(true);
            timeRangeLabel.setToolTipText("Current time range for data requests");
        }
    }
    
    public void enableTimeRange() {
        timeRangeLabel.setEnabled(true);
        timeRangeRecentComboBox.setEnabled(true);
    }
    
    private static boolean isChildOf( TreeNode parent, TreeNode child ) {
        while ( child!=null ) {
            if ( child==parent ) {
                return true;
            } else {
                child= child.getParent();
            }
        } 
        return false;
    }
    
    private void doDrop( String data, TreePath tp ) {
        doDrop( data, tp, true );
    }
    
    /**
     * print the path to a string, comma delimited.
     * @param newPath
     * @return 
     */
    public static String printPath(Object [] newPath ) {
        StringBuilder bb= new StringBuilder();
        for (Object newPath1 : newPath) {
            bb.append(",");
            bb.append(newPath1);
        }
        return bb.length()==0 ? "" : bb.substring(1);
    }
    
    /**
     * insert element into array at index, as long as index is within the array
     * or at the length of the array.
     * @param array array of elements
     * @param index index for insertion, which may be out of bounds for the array.
     * @param node the object to insert.
     * @return 
     * @see 
     */
    public static Object[] insertElement( Object[] array, int index, Object node ) {
        if ( array.length>=index ) {
            Object[] result= new Object[array.length+1];
            System.arraycopy( array, 0, result, 0, index );
            result[index]= node;
            System.arraycopy( array, index, result, index+1, array.length-index );
            return result;
        } else {
            return array;
        }
    }
    
    /**
     * 
     * @param data the expression to incorporate into the tree
     * @param tp the path where the expression is to be inserted
     * @param moveOldNodeDown if true, then make the drop target the first child.
     */
    private void doDrop( final String data, final TreePath tp, boolean moveOldNodeDown ) {
    
        DefaultTreeModel model= (DefaultTreeModel) expressionTree.getModel();

        MutableTreeNode oldBranch= (MutableTreeNode)tp.getLastPathComponent();
        final Enumeration<TreePath> ppp= expressionTree.getExpandedDescendants(tp);
        final List<TreePath> expandedDescendants= new ArrayList<>();
        if ( ppp!=null ) {
            while ( ppp.hasMoreElements() ) {
                expandedDescendants.add(ppp.nextElement());
            }
        }
        
        MutableTreeNode parent= (MutableTreeNode)oldBranch.getParent();
        
        final MutableTreeNode newBranch= getTreeNode(data, namedURIListTool1.ids, new ArrayList<String>() );
        
        int index= -1;
        String arg0= null;
        if ( parent!=null ) {
            index= parent.getIndex(oldBranch);
            String vv= oldBranch.toString();
            if ( Ops.isSafeName(vv) && oldBranch.getChildCount()==0 ) {
                arg0=vv;
            }
            model.removeNodeFromParent(oldBranch);
        } 
        
        if ( moveOldNodeDown && newBranch.getChildCount()>0 ) {
            // replace the first argument with what we are replacing
            newBranch.remove(0);
            newBranch.insert( oldBranch, 0 );
        }
        
        if ( parent==null ) {
            model.setRoot(newBranch);
        } else {
            model.insertNodeInto( newBranch, parent, index );
        }

        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run() {
                TreePath newTreePath= getPath(newBranch);
                expressionTree.expandPath( newTreePath );
                for ( TreePath tp1 : expandedDescendants ) {
                    Object[] path= tp1.getPath();
                    Object[] newPath= insertElement( path, tp.getPathCount()-1, newBranch );

                    TreePath mtp1= new TreePath(newPath);
                    expressionTree.expandPath(mtp1);
                }
                imaged.clear();
                resolved.clear();
                expressionTree.treeDidChange();
            }
        });        
    }
    
    private static TreePath getPath(TreeNode treeNode) {
        List<Object> nodes = new ArrayList<>();
        if (treeNode != null) {
            nodes.add(treeNode);
            treeNode = treeNode.getParent();
            while (treeNode != null) {
                nodes.add(0, treeNode);
                treeNode = treeNode.getParent();
            }
        }
        return nodes.isEmpty() ? null : new TreePath(nodes.toArray());
    }
    
    /**
     * return true if the script conforms to the jython dashup requirements.
     * @param jython script.
     * @return true if the script conforms to the jython dashup requirements.
     */
    public static boolean isDataMashupJythonInline( String jython ) {
        try {
            DataMashUp dmu= new DataMashUp();
            dmu.setAsJythonInline(jython);
            return !"vap+inline:ds".equals(dmu.getAsJythonInline());
        } catch ( Exception ex ) {
            logger.log( Level.FINER, null, ex );
            return false;
        }
    }
        
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this
     * code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        palettePopupMenu = new javax.swing.JPopupMenu();
        addItemMenuItem = new javax.swing.JMenuItem();
        deleteItemsMenuItem = new javax.swing.JMenuItem();
        expressionPopupMenu = new javax.swing.JPopupMenu();
        editMenuItem = new javax.swing.JMenuItem();
        plotMenuItem = new javax.swing.JMenuItem();
        jSplitPane1 = new javax.swing.JSplitPane();
        jSplitPane2 = new javax.swing.JSplitPane();
        jPanel4 = new javax.swing.JPanel();
        directionsLabel = new javax.swing.JLabel();
        jScrollPane6 = new javax.swing.JScrollPane();
        expressionTree = new javax.swing.JTree();
        jPanel7 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        mathematicsList = new javax.swing.JList();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        datasetList = new javax.swing.JList();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        filtersList = new javax.swing.JList();
        myFunctionsPanel = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        scratchList = new javax.swing.JList();
        jPanel6 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        allList = new javax.swing.JList<>();
        jPanel2 = new javax.swing.JPanel();
        synchronizeCB = new javax.swing.JCheckBox();
        jScrollPane7 = new javax.swing.JScrollPane();
        namedURIListTool1 = new org.autoplot.jythonsupport.ui.NamedURIListTool();
        jLabel1 = new javax.swing.JLabel();
        timeRangeLabel = new javax.swing.JLabel();
        timeRangeRecentComboBox = new org.autoplot.datasource.RecentComboBox();
        helpButton = new javax.swing.JButton();
        calendarButton = new javax.swing.JButton();

        addItemMenuItem.setText("Add function...");
        addItemMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addItemMenuItemActionPerformed(evt);
            }
        });
        palettePopupMenu.add(addItemMenuItem);

        deleteItemsMenuItem.setText("Delete Items");
        deleteItemsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteItemsMenuItemActionPerformed(evt);
            }
        });
        palettePopupMenu.add(deleteItemsMenuItem);

        editMenuItem.setText("Edit");
        editMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editMenuItemActionPerformed(evt);
            }
        });
        expressionPopupMenu.add(editMenuItem);

        plotMenuItem.setText("Plot");
        plotMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotMenuItemActionPerformed(evt);
            }
        });
        expressionPopupMenu.add(plotMenuItem);

        jSplitPane1.setDividerLocation(140);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(0.5);

        jSplitPane2.setDividerLocation(420);

        directionsLabel.setText("<html>Double-click on the name to set the variable or constant argument, or to replace the branch.");
        directionsLabel.setAlignmentY(0.0F);
        directionsLabel.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        expressionTree.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                expressionTreeMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                expressionTreeMouseReleased(evt);
            }
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                expressionTreeMouseClicked(evt);
            }
        });
        jScrollPane6.setViewportView(expressionTree);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(directionsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 540, Short.MAX_VALUE)
            .addComponent(jScrollPane6)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(directionsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE))
        );

        jSplitPane2.setRightComponent(jPanel4);

        jLabel2.setText("Drag functions onto the palette to the right.");

        mathematicsList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "add(x,y)", "subtract(x,y)", "multiply(x,y)", "divide(x,y)", "pow(x,y)", "log10(x)", "sqrt(x)", "abs(x): the absolute value of the data", "magnitude(x): the lengths of the vectors", "toRadians(x)", "toDegrees(x)", "sin(x)", "cos(x)", "tan(x)", "asin(x)", "acos(x)", "atan2(y,x)", "atan(x)", " " };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        mathematicsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane3.setViewportView(mathematicsList);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 419, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 291, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("mathematics", jPanel1);

        datasetList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "link(x,y): create data set where y is a function of x", "link(x,y,z): create data set where z is a function of x and y", "slice1(ds,0): slice ds(x,y) to create a new ds(x)", "smooth(ds,5): run boxcar average over the dataset", "putProperty(ds,QDataSet.UNITS,'s'): attach properties to the data", "getProperty(ds,QDataSet.DEPEND_0): get properties, like timetags.", "unbundle(ds,0): remove the 0th dataset from the bundle", "bundle(t,ds1,ds2): bundle the three datasets together", "collapse1(ds): average measurements along the dimension", "total(ds,1): sum measurements along the dimension", "trim1(ds,st,en): trim the indices in the the dimension" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        datasetList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane4.setViewportView(datasetList);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 419, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("dataset", jPanel3);

        filtersList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "putValues(ds,w,v)", "removeValues(ds,w)", "removeValuesGreaterThan(ds,v)", "removeValuesLessThan(ds,v)", "where(c)", "lt(ds1,ds2)", "le(ds1,ds2)", "gt(ds1,ds2)", "ge(ds1,ds2)", "eq(ds1,ds2)", "ne(ds1,ds2)", "ds1.or(ds2)", "ds1.and(ds2)" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        filtersList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(filtersList);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 419, Short.MAX_VALUE)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
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
            .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 419, Short.MAX_VALUE)
        );
        myFunctionsPanelLayout.setVerticalGroup(
            myFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("my functions", myFunctionsPanel);

        allList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(allList);

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(3, 3, 3)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE)
                .addGap(3, 3, 3))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(3, 3, 3)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE)
                .addGap(3, 3, 3))
        );

        jTabbedPane1.addTab("all", jPanel6);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jTabbedPane1))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(jLabel2)
                .addGap(0, 328, Short.MAX_VALUE))
            .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                    .addGap(23, 23, 23)
                    .addComponent(jTabbedPane1)))
        );

        jSplitPane2.setLeftComponent(jPanel7);

        jSplitPane1.setBottomComponent(jSplitPane2);

        synchronizeCB.setSelected(true);
        synchronizeCB.setText("synchronize data by time tags, interpolating data to the first dataset's time tags");
        synchronizeCB.setToolTipText("Nearest Neighbor synchronization is used to line up the data, so that they can be combined.");

        namedURIListTool1.setMinimumSize(new java.awt.Dimension(100, 100));
        namedURIListTool1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                namedURIListTool1FocusLost(evt);
            }
        });
        jScrollPane7.setViewportView(namedURIListTool1);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane7, javax.swing.GroupLayout.Alignment.TRAILING)
            .addComponent(synchronizeCB, javax.swing.GroupLayout.DEFAULT_SIZE, 971, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 97, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(synchronizeCB, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jSplitPane1.setLeftComponent(jPanel2);

        jLabel1.setText("Load these Data Sets into variable names:");

        timeRangeLabel.setText("Time Range:");
        timeRangeLabel.setEnabled(false);
        timeRangeLabel.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeRangeTextFieldFocusLost(evt);
            }
        });

        timeRangeRecentComboBox.setEnabled(false);
        timeRangeRecentComboBox.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeRangeRecentComboBoxFocusLost(evt);
            }
        });
        timeRangeRecentComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeRecentComboBoxActionPerformed(evt);
            }
        });

        helpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/help.png"))); // NOI18N
        helpButton.setText("Help");
        helpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpButtonActionPerformed(evt);
            }
        });

        calendarButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/calendar.png"))); // NOI18N

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, timeRangeRecentComboBox, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), calendarButton, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        calendarButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                calendarButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeRangeLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeRangeRecentComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(calendarButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(helpButton, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addComponent(jSplitPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(timeRangeLabel)
                    .addComponent(timeRangeRecentComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(helpButton)
                    .addComponent(calendarButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1))
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {calendarButton, helpButton});

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void timeRangeTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeRangeTextFieldFocusLost

    }//GEN-LAST:event_timeRangeTextFieldFocusLost

    /**
     * use the resolver to get the QDataSet, then plot it.
     */
    private void plotExpr( ) {
        TreePath tp= expressionTree.getSelectionPath();
        if ( tp==null ) return;
        QDataSet showMe= resolved.get( getAsJythonInline( (TreeNode)tp.getLastPathComponent() ));
        if ( showMe!=null ) {
            resolver.interactivePlot( showMe );
        } else {
            if ( resolver!=null ) {
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        TreePath tp= expressionTree.getSelectionPath();
                        if ( tp==null ) return;
                        QDataSet showMe= resolver.getDataSet( getAsJythonInline( (TreeNode)tp.getLastPathComponent() ) );
                        resolver.interactivePlot( showMe );
                    }
                };
                new Thread(run).start();
            } else {
                logger.info("resolver is not set.");
            }
        }
    }
    
    private String getSelectedFunction() {
        Component c= this.jTabbedPane1.getSelectedComponent();
        if ( c instanceof JPanel ) {
            c= ((JPanel)c).getComponent(0);
        }
        if ( c instanceof JScrollPane ) {
            c= ((javax.swing.JScrollPane)c).getViewport().getComponent(0);
        }
        if ( c instanceof JList ) {
            Object o= ((JList)c).getSelectedValue();
            if ( o instanceof String ) {
                String s= ((String)o);
                int i= s.indexOf(":");
                if ( i>1 && s.charAt(i-1)==')' ) {
                    s= s.substring(0,i);
                }
                return s;
            }
        }
        return "";
    }
    
    private void expressionTreeMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_expressionTreeMouseClicked
        if ( evt.isShiftDown() ) {
            TreePath tp= expressionTree.getClosestPathForLocation( evt.getX(), evt.getY() );
            expressionTree.setSelectionPath(tp);
            plotExpr();

        } else if ( evt.getClickCount()==2 ) {
            TreePath tp= expressionTree.getClosestPathForLocation( evt.getX(), evt.getY() );
            if ( !expressionTree.getModel().isLeaf(tp.getLastPathComponent()) ) {
                return;
            }
            expressionTree.setSelectionPath(tp);
            String currentId= tp.getLastPathComponent().toString();
            namedURIListTool1.setExpression(getSelectedFunction());
            String s= namedURIListTool1.selectDataId(currentId);
            if ( s!=null ) {
                doDrop(s,tp);
            }
        }
    }//GEN-LAST:event_expressionTreeMouseClicked

    private void addItemMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addItemMenuItemActionPerformed
        String s= JOptionPane.showInputDialog( this, "Add function" );
        if ( s!=null && !( s.trim().length()==0 ) ) {
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

    private void expressionTreeMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_expressionTreeMousePressed
        if ( evt.isPopupTrigger() ) {
            plotMenuItem.setEnabled( resolver!=null );
            expressionPopupMenu.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_expressionTreeMousePressed

    private void editMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editMenuItemActionPerformed
        TreePath tp= expressionTree.getSelectionPath();
        if ( tp==null ) {
            JOptionPane.showMessageDialog( this, "A node must be selected", "Node must be selected", JOptionPane.PLAIN_MESSAGE );
            return;
        }
        expressionTree.setSelectionPath(tp);
        if ( !expressionTree.getModel().isLeaf(tp.getLastPathComponent()) ) {
            String s= getAsJythonInline( (TreeNode)tp.getLastPathComponent() );
            int i= s.lastIndexOf("&");
            if ( i>-1 ) {
                s= s.substring(i+1);
            } else {
                i= s.lastIndexOf(":");
                s= s.substring(i+1);
            }
            s= namedURIListTool1.selectDataId(s);
            if ( s!=null ) {
                doDrop(s,tp,false);
            }
        } else {
            String currentId= tp.getLastPathComponent().toString();
            String s= namedURIListTool1.selectDataId(currentId);
            namedURIListTool1.setExpression(getSelectedFunction());
            if ( s!=null ) {
                doDrop(s,tp,false);
            }
        }
    }//GEN-LAST:event_editMenuItemActionPerformed

    private void expressionTreeMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_expressionTreeMouseReleased
        if ( evt.isPopupTrigger() ) {
            expressionPopupMenu.show( evt.getComponent(), evt.getX(), evt.getY() );   
        }
    }//GEN-LAST:event_expressionTreeMouseReleased

    private void plotMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotMenuItemActionPerformed
        TreePath tp= expressionTree.getSelectionPath();
        if ( tp==null ) {
            JOptionPane.showMessageDialog( this, "A node must be selected", "Node must be selected", JOptionPane.PLAIN_MESSAGE );
            return;
        }
        expressionTree.setSelectionPath(tp);
        plotExpr();
    }//GEN-LAST:event_plotMenuItemActionPerformed

    private void timeRangeRecentComboBoxFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeRangeRecentComboBoxFocusLost
        String s= timeRangeRecentComboBox.getText().trim();
        if ( s.length()>0 ) {
            try {
                namedURIListTool1.setTimeRange( DatumRangeUtil.parseTimeRange(timeRangeRecentComboBox.getText()));
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_timeRangeRecentComboBoxFocusLost

    private void timeRangeRecentComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeRecentComboBoxActionPerformed
        String s= timeRangeRecentComboBox.getText().trim();
        if ( s.length()>0 ) {
            try {
                namedURIListTool1.setTimeRange( DatumRangeUtil.parseTimeRange(timeRangeRecentComboBox.getText()));
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_timeRangeRecentComboBoxActionPerformed

    private void scratchListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scratchListMouseClicked
        if ( jTabbedPane1.getSelectedComponent()==myFunctionsPanel ) {
            if ( evt.isPopupTrigger() ) {
                palettePopupMenu.show( evt.getComponent(), evt.getX(), evt.getY() );
            }
        }
    }//GEN-LAST:event_scratchListMouseClicked

    private void scratchListMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scratchListMouseReleased
        if ( jTabbedPane1.getSelectedComponent()==myFunctionsPanel ) {
            if ( evt.isPopupTrigger() ) {
                palettePopupMenu.show( evt.getComponent(), evt.getX(), evt.getY() );
            }
        }
    }//GEN-LAST:event_scratchListMouseReleased

    private void scratchListMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scratchListMousePressed
        if ( jTabbedPane1.getSelectedComponent()==myFunctionsPanel ) {
            if ( evt.isPopupTrigger() ) {
                palettePopupMenu.show( evt.getComponent(), evt.getX(), evt.getY() );
            }
        }
    }//GEN-LAST:event_scratchListMousePressed

    private void helpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpButtonActionPerformed
        DataSourceUtil.openBrowser("http://autoplot.org/help.mashup");
    }//GEN-LAST:event_helpButtonActionPerformed

    private void calendarButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calendarButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        TimeRangeTool tt= new TimeRangeTool();
        String s= timeRangeRecentComboBox.getText();
        if ( s!=null ) tt.setSelectedRange(s);
        int r= JOptionPane.showConfirmDialog( this, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
        if ( r==JOptionPane.OK_OPTION) {
            timeRangeRecentComboBox.setText(tt.getSelectedRange());
        }
    }//GEN-LAST:event_calendarButtonActionPerformed

    /**
     * this should be called from the event thread, but will start a new thread
     * to check for a TSB capability.  
     */
    private void checkForTSB() {        
        if ( SwingUtilities.isEventDispatchThread() ) {
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    checkForTSBImmediately();
                }
            };
            new Thread(run,"checkForTSB").start();
        } else {
            checkForTSBImmediately();
        }
    }
    
    private void checkForTSBImmediately() {
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
        final String ftimerange= timerange;
        Runnable run= new Runnable() {
            public void run() {
                if ( ftimerange!=null ) {
                    timeRangeRecentComboBox.setEnabled(true);
                    timeRangeLabel.setEnabled(true);
                    timeRangeRecentComboBox.setText( ftimerange );
                } else {
                    timeRangeRecentComboBox.setEnabled(false);
                    timeRangeLabel.setEnabled(false);
                }
            }
        };
        SwingUtilities.invokeLater(run);
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
        final String text0= directionsLabel.getText();
        directionsLabel.setText("Replaced expression is added to my functions.");
        Timer t= new Timer( 1500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                directionsLabel.setText(text0);
            }
        } );
        t.setRepeats(false);
        t.start();
        
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
    
    /**
     * load the scientist's custom functions off the event thread.
     */
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
                TreePath tp= expressionTree.getClosestPathForLocation( dtde.getLocation().x, dtde.getLocation().y );
                expressionTree.setSelectionPath(tp);
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

                    TreePath tp= expressionTree.getClosestPathForLocation( dtde.getLocation().x, dtde.getLocation().y );

                    DefaultMutableTreeNode n= (DefaultMutableTreeNode)tp.getLastPathComponent();
                    String old= getJython( (DefaultTreeModel)expressionTree.getModel(), n );
                    if ( old.contains("(") ) {
                        addToScratch( old );
                    }
                    if ( data.endsWith(REPLACEARGSFLAG) ) {
                        data= data.substring(0,data.length()-17);
                        doDrop(data,tp,true);
                    } else {
                        doDrop(data,tp,false);
                    }
                    
                    
                } catch (UnsupportedFlavorException | IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            }
        };
    }
    
    /**
     * when the drop target ends with this string (kludge), don't clobber
     * what's in the tree, instead make it the first argument.
     */
    private static final String REPLACEARGSFLAG = "(REPLACEARGSFLAG)";
    
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
                    if ( data.endsWith(REPLACEARGSFLAG) ) {
                        data= data.substring(0,data.length()-17);
                    }
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
                
                boolean replaceArgs= false;
                if ( dge.getComponent() instanceof JList ) {
                    s= (String)((JList)dge.getComponent()).getSelectedValue();
                    replaceArgs= true;
                } else if  ( dge.getComponent()==expressionTree ) {
                    if ( expressionTree.getSelectionCount()==1 ) {
                        TreePath tp= expressionTree.getSelectionPath();
                        if ( tp==null ) return;
                        DefaultMutableTreeNode n= (DefaultMutableTreeNode)tp.getLastPathComponent();
                        s= getJython( (DefaultTreeModel)expressionTree.getModel(), n );
                    }
                } else if  ( dge.getComponent()==namedURIListTool1 ) {
                    logger.fine("here where dge.getComponent()==namedURIListTool1");
                }
                if ( s!=null ) {
                    if ( s.contains(": ") ) {
                        int i= s.lastIndexOf(": ");
                        s= s.substring(0,i).trim();
                    }
                    if ( replaceArgs ) s= s + REPLACEARGSFLAG;
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
                BufferedImage result= new BufferedImage(128,32,BufferedImage.TYPE_4BYTE_ABGR);
                Graphics2D g= (Graphics2D)result.getGraphics();
                g.setColor( Color.DARK_GRAY );
                g.drawString( eu.createDatum(qds.value()).toString(), 2, 10 );
                return result;
            }
            @Override
            public void interactivePlot(QDataSet qds) {
                System.err.println( qds );
            }

        });
        dmu.fillTree("add(a,b)", Collections.singletonList("z"), new ArrayList<String>() );
        JOptionPane.showConfirmDialog( null, dmu );
        System.err.println( dmu.getAsJythonInline() );
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem addItemMenuItem;
    private javax.swing.JList<String> allList;
    private javax.swing.JButton calendarButton;
    private javax.swing.JList datasetList;
    private javax.swing.JMenuItem deleteItemsMenuItem;
    private javax.swing.JLabel directionsLabel;
    private javax.swing.JMenuItem editMenuItem;
    private javax.swing.JPopupMenu expressionPopupMenu;
    private javax.swing.JTree expressionTree;
    private javax.swing.JList filtersList;
    private javax.swing.JButton helpButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JList mathematicsList;
    private javax.swing.JPanel myFunctionsPanel;
    private org.autoplot.jythonsupport.ui.NamedURIListTool namedURIListTool1;
    private javax.swing.JPopupMenu palettePopupMenu;
    private javax.swing.JMenuItem plotMenuItem;
    private javax.swing.JList scratchList;
    private javax.swing.JCheckBox synchronizeCB;
    private javax.swing.JLabel timeRangeLabel;
    private org.autoplot.datasource.RecentComboBox timeRangeRecentComboBox;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}
