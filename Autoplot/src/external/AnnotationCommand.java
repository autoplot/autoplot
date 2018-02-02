/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package external;

import java.awt.Color;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.util.ClassMap;
import org.python.core.Py;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.autoplot.RenderType;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Annotation;
import org.autoplot.dom.Application;
import org.autoplot.dom.CanvasUtil;
import org.autoplot.dom.Column;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.Row;
import org.das2.qds.QDataSet;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.PyQDataSet;
import org.autoplot.jythonsupport.PyQDataSetAdapter;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
import org.das2.graph.BorderType;
import org.das2.graph.FillStyle;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.TickVDescriptor;
import org.das2.qds.DataSetUtil;
import org.das2.qds.ops.Ops;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyMethod;

/**
 * new implementation of the plot command allows for keywords in the
 * Jython environment.
 *<blockquote><pre><small>{@code
 * plot( 0, ripples(20) )
 * plot( 1, ripples(20), color=Color.BLUE )
 * plot( 2, ripples(20), renderType='series>color=blue' )
 *}</small></pre></blockquote>
 * @see http://autoplot.org/help.plotCommand
 * @author jbf
 */
public class AnnotationCommand extends PyObject {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>annotation([index],[named parameters])</H2>"
            + "annotation puts an annotation on the canvas.\n"
            + "See http://autoplot.org/help.annotationCommand<br>\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>"
            + "<tr><td>text</td><td>The message, allowing granny codes</td></tr>\n"
            + " <tr><td> textColor      </td><td> text color\n</td></tr>"
            + " <tr><td> background     </td><td> background color\n</td></tr>"
            + " <tr><td> foreground     </td><td> foreground color\n</td></tr>"
            + "</table></html>");

    private static AnchorPosition anchorPosition( PyObject val ) {
        AnchorPosition c=null;
        if (val.__tojava__(AnchorPosition.class) != Py.NoConversion) {
            c = (AnchorPosition) val.__tojava__(AnchorPosition.class);
        } else if (val instanceof PyString) {
            String sval = (String) val.__str__().__tojava__(String.class);
            c = AnchorPosition.valueOf(sval);
        } else {
            throw new IllegalArgumentException("anchorPosition must be a string or AnchorPosition");
        }
        return c;
    }
    
    private static AnchorType anchorType( PyObject val ) {
        AnchorType c=null;
        if (val.__tojava__(AnchorType.class) != Py.NoConversion) {
            c = (AnchorType) val.__tojava__(AnchorType.class);
        } else if (val instanceof PyString) {
            String sval = (String) val.__str__().__tojava__(String.class);
            c = AnchorType.valueOf(sval);
        } else {
            throw new IllegalArgumentException("anchorType must be a string or AnchorType");
        }
        return c;
    }

    private static BorderType borderType( PyObject val ) {
        BorderType c=null;
        if (val.__tojava__(BorderType.class) != Py.NoConversion) {
            c = (BorderType) val.__tojava__(BorderType.class);
        } else if (val instanceof PyString) {
            String sval = (String) val.__str__().__tojava__(String.class);
            c = BorderType.valueOf(sval);
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
                "anchorPosition", "anchorOffset", "anchorType", "anchorBorderType",
                "fontSize",
                "pointAtX", "pointAtY", "pointAt", 
                "xrange", "yrange",
        },
        new PyObject[] { new PyInteger(0), 
            Py.None, Py.None, Py.None, Py.None,
            Py.None, Py.None, Py.None, Py.None,
            Py.None,
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
        
        Application dom= ScriptContext.getDocumentModel();
        
        Annotation annotation;
        while ( index>=dom.getAnnotations().length ) {
            dom.getController().addAnnotation( new Annotation() );
        }
        annotation= dom.getAnnotations(index);

        dom.getController().registerPendingChange( this, this );  
        dom.getController().performingChange(this,this);
        
        try {

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
                        String[] ss= sval.split(",",-2);
                        annotation.setPointAtX(Ops.datum(ss[0]));
                        annotation.setPointAtY(Ops.datum(ss[1]));
                        annotation.setShowArrow(true);
                        break;
                    case "xrange":
                        annotation.setXrange(Ops.datumRange(sval));
                        annotation.setAnchorOffset("");
                        annotation.setAnchorType( AnchorType.DATA );                        
                        break;
                    case "yrange":
                        annotation.setYrange(Ops.datumRange(sval));
                        annotation.setAnchorOffset("");
                        annotation.setAnchorType( AnchorType.DATA );                        
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
