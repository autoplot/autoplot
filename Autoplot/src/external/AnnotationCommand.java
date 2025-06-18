
package external;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.python.core.Py;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.autoplot.dom.Annotation;
import org.autoplot.dom.Application;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;
import org.autoplot.jythonsupport.JythonOps;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
import org.das2.graph.BorderType;
import org.das2.qds.ops.Ops;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;

/**
 * new implementation of the plot command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * annotation( 0, 'Anno1' )
 * annotation( 1, 'Anno2', textColor='darkBlue', anchorPosition='NW'  )
 * plot( 'vap+cdaweb:ds=OMNI2_H0_MRG1HR&id=DST1800&timerange=Oct+2016' )
 * annotation( 2, 'Anno3', anchorType='DATA',pointAt='2016-10-14T07:51Z,-100', xrange='2016-10-20T00:00/PT30S', yrange='-150 to -100',
 *     anchorPosition='OutsideNE', anchorOffset='' )
 *}</small></pre></blockquote>
 * @see https://autoplot.org/help.annotationCommand
 * @author jbf
 */
public class AnnotationCommand extends PyObject {
    
    private Application dom;
    
    public AnnotationCommand( Application dom ) {
        this.dom= dom;
    }
    
    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>annotation([index],[named parameters])</H2>"
            + "annotation puts an annotation on the canvas.\n"
            + "See <a href='https://autoplot.org/help.annotationCommand'>https://autoplot.org/help.annotationCommand</a><br>\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>"
            + "<tr><td>text</td><td>The message, allowing Granny codes</td></tr>"
            + " <tr><td> textColor      </td><td> text color\n</td></tr>"
            + " <tr><td> background     </td><td> background color\n</td></tr>"
            + " <tr><td> foreground     </td><td> foreground color\n</td></tr>"
            + " <tr><td> fontSize     </td><td> size relative to parent (1.2em) or in pts (8pt)\n</td></tr>"
            + " <tr><td> borderType     </td><td> draw a border around the annotation text<br>none,rectangle,rounded_rectangle<br>.</td></tr>"
            + " <tr><td> anchorBorderType </td><td> draw a border around the anchor box.</td></tr>"
            + " <tr><td> anchorPosition </td><td>One of NE,NW,SE,SW,<br>N,E,W,S,<br>outsideN,outsideNNW</td></tr>"
            + " <tr><td> anchorOffset </td><td>position relative to the anchor, like '1em,1em'</td></tr>"
            + " <tr><td> anchorType </td><td>PLOT means relative to the plot.<br>DATA means relative to xrange and yrange</td></tr>"
            + " <tr><td> xrange, yrange </td><td> anchor box when using data anchor</td></tr>"
            + " <tr><td> plotId </td><td> ID of the plot containing axes.  This will set the anchorType to CANVAS, unless xrange or yrange is set.</td></tr>"    
            + " <tr><td> pointAt </td><td>comma separated X and Y to point the annotation arrow at.</td></tr>"
            + " <tr><td> pointAtX </td><td>X value to point the arrow at or to anchor the annotation.</td></tr>"
            + " <tr><td> pointAtX </td><td>Y value to point the arrow at or to anchor the annotation.</td></tr>"
            + " <tr><td> rowId </td><td>ID of the row containing for positioning this annotation, sets anchorType=CANVAS<br>(See dom.plots[0].rowId)</td></tr>"
            + " <tr><td> columnId </td><td>ID of the column containing for positioning this annotation, sets anchorType=CANVAS</td></tr>"
            + "</table>" 
            + "See <a href='https://github.com/autoplot/documentation/blob/master/docs/annotations.md'>https://github.com/autoplot/documentation/blob/master/docs/annotations.md</a>"
            + "</html>");

    private static AnchorPosition anchorPosition( PyObject val ) {
        AnchorPosition c=null;
        if (val.__tojava__(AnchorPosition.class) != Py.NoConversion) {
            c = (AnchorPosition) val.__tojava__(AnchorPosition.class);
        } else if (val instanceof PyString) {
            String sval = (String) val.__str__().__tojava__(String.class);
            c = (AnchorPosition) lookupEnum( AnchorPosition.values(), sval );
        } else {
            throw new IllegalArgumentException("anchorPosition must be a string or AnchorPosition");
        }
        return c;
    }
    
    private static Object lookupEnum( Object[] vs, String s ) {
        s= s.toLowerCase();
        for ( Object v: vs ) {
            if ( v.toString().toLowerCase().equals(s) ) return v;
        }
        throw new IllegalArgumentException("unable to find enumerated value for "+s);
    }
    
    private static AnchorType anchorType( PyObject val ) {
        AnchorType c=null;
        if (val.__tojava__(AnchorType.class) != Py.NoConversion) {
            c = (AnchorType) val.__tojava__(AnchorType.class);
        } else if (val instanceof PyString) {
            String sval = (String) val.__str__().__tojava__(String.class);
            c = (AnchorType) lookupEnum( AnchorType.values(), sval );
        } else {
            throw new IllegalArgumentException("anchorType must be a string or AnchorType");
        }
        return c;
    }

    private static BorderType borderType( PyObject val ) {
        BorderType c=null;
        if ( val==Py.None ) {
            return BorderType.NONE;
        } else if (val.__tojava__(BorderType.class) != Py.NoConversion) {
            c = (BorderType) val.__tojava__(BorderType.class);
        } else if (val instanceof PyString) {
            String sval = (String) val.__str__().__tojava__(String.class);
            c = (BorderType) lookupEnum( BorderType.values(), sval );
        } else {
            throw new IllegalArgumentException("borderType must be a string or BorderType");
        }
        return c;
    }
        
    private static boolean booleanValue( PyObject arg0 ) {
        if ( arg0.isNumberType() ) {
            return arg0.__nonzero__();
        } else {
            String s= String.valueOf(arg0);
            return s.equals("True") || s.equals("T") || s.equals("1");
        }
    }
    
    /**
     * implement the python call.
     * @param args the "rightmost" elements are the keyword values.
     * @param keywords the names for the keywords.
     * @return the annotation
     */
    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {

        FunctionSupport fs= new FunctionSupport( "annotation", 
            new String[] { "index", 
                "text", "textColor", "background", "foreground",
                "anchorPosition", "anchorOffset", "anchorType", "borderType", "anchorBorderType",
                "fontSize",
                "pointAtX", "pointAtY", "pointAt", "pointAtOffset",
                "xrange", "yrange", "plotId",
                "rowId", "columnId"
        },
        new PyObject[] { new PyInteger(0), 
            Py.None, Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None, Py.None, Py.None,
            Py.None,
            Py.None, Py.None, Py.None, Py.None, 
            Py.None, Py.None, Py.None,
            Py.None, Py.None,
        } );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;

        int index=0;
        int nargs= nparm;

        // If the first (zeroth) argument is an int, than this is the data source where the value should be inserted.  Additional
        // data sources and plots will be added until there are enough.
        // this is an alias for the index argument.
        if ( args.length>0 ) {
            PyObject po0= args[0];
            if ( po0 instanceof PyInteger ) {
                index= ((PyInteger)po0).getValue();
                PyObject[] newArgs= new PyObject[args.length-1];
                for ( int i=0; i<args.length-1; i++ ) {
                    newArgs[i]= args[i+1];
                }
                args= newArgs;
                nargs= nargs-1;
                nparm= args.length - keywords.length;
                po0= args[0];
            }
        } else {
            index= 0;
        }
        
        Application dom= this.dom;
        
        dom.getController().registerPendingChange( this, this );  
        dom.getController().performingChange(this,this);
        
        while ( index>=dom.getAnnotations().length ) {
            dom.getController().addAnnotation( new Annotation() );
        }
        Annotation annotation= dom.getAnnotations(index);
        
        // reset the annotation.
        annotation.syncTo( new Annotation(), Arrays.asList( DomNode.PROP_ID, Annotation.PROP_PLOTID, Annotation.PROP_ROWID, Annotation.PROP_COLUMNID ) );
                
        try {
            List<String> keywordsList= Arrays.asList(keywords);
            
            for ( int i=nparm; i<args.length; i++ ) { //HERE nargs
                String kw= keywords[i-nparm];
                PyObject val= args[i];

                String sval= (String) val.__str__().__tojava__(String.class);
                switch (kw) {
                    case "text":
                        annotation.setText(sval);
                        break;
                    case "textColor":
                        annotation.setTextColor(JythonOps.color(val));
                        annotation.setOverrideColors(true);
                        break;
                    case "background":
                        annotation.setBackground(JythonOps.color(val)); 
                        annotation.setOverrideColors(true);
                        break;
                    case "foreground":
                        annotation.setForeground(JythonOps.color(val));
                        annotation.setOverrideColors(true);
                        break;
                    case "anchorPosition":
                        annotation.setAnchorPosition( anchorPosition( val ));
                        break;
                    case "anchorOffset":
                        annotation.setAnchorOffset( sval );
                        break;                        
                    case "anchorType":
                        annotation.setAnchorType( anchorType(val) );
                        break;           
                    case "anchorBorderType":
                        annotation.setAnchorBorderType( borderType(val) );
                        break;           
                    case "borderType":
                        annotation.setBorderType( borderType(val) );
                        break;           
                    case "fontSize":
                        annotation.setFontSize(sval);
                        break;
                    case "pointAtX":
                        annotation.setPointAtX(JythonOps.datum(val));
                        annotation.setShowArrow(true);
                        break;
                    case "pointAtY":
                        annotation.setPointAtY(JythonOps.datum(val));
                        annotation.setShowArrow(true);
                        break;
                    case "pointAt":
                        if ( val instanceof PyList ) {
                            annotation.setPointAtX(Ops.datum(((PyList)val).get(0)));
                            annotation.setPointAtY(Ops.datum(((PyList)val).get(1)));
                            annotation.setShowArrow(true);
                        } else {
                            String[] ss= sval.split(",",-2);
                            annotation.setPointAtX(Ops.datum(ss[0]));
                            annotation.setPointAtY(Ops.datum(ss[1]));
                            annotation.setShowArrow(true);
                        }
                        break;
                    case "pointAtOffset":
                        annotation.setPointAtOffset(sval);
                        break;
                    case "plotId":
                        annotation.setPlotId(sval);
                        if ( keywordsList.contains("xrange") || keywordsList.contains("yrange" ) ) { // check other keywords so that order doesn't matter
                            annotation.setAnchorType( AnchorType.DATA );
                        } else {
                            annotation.setAnchorType( AnchorType.CANVAS ); //TODO: review AnchorType.PLOT
                        }
                        DomNode p= DomUtil.getElementById(dom,sval);
                        if ( p instanceof Plot ) {
                            annotation.setRowId(((Plot)p).getRowId());
                            annotation.setColumnId(((Plot)p).getColumnId());
                        }
                        break;
                    case "xrange":
                        annotation.setXrange(JythonOps.datumRange(val));
                        annotation.setAnchorOffset("");
                        annotation.setAnchorType( AnchorType.DATA );                        
                        break;
                    case "yrange":
                        annotation.setYrange(JythonOps.datumRange(val));
                        annotation.setAnchorOffset("");
                        annotation.setAnchorType( AnchorType.DATA );                        
                        break;
                    case "rowId":
                        annotation.setRowId(sval);
                        annotation.setAnchorType( AnchorType.CANVAS );
                        break;
                    case "columnId":
                        annotation.setColumnId(sval);
                        annotation.setAnchorType( AnchorType.CANVAS );
                        break;
                    default:
                        break;
                }
            }

        } finally {
            dom.getController().changePerformed(this,this);
        }

        return new PyJavaInstance(annotation);
    }

}
