
package external;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.autoplot.dom.DomOps;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;

/**
 * Jython command for fixLayout.
 * @author jbf
 */
public class FixLayoutCommand extends PyObject  {
        
    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    
    public static final PyString __doc__ =
        new PyString("<html><H2>fixLayout([named parameters])</H2>"
            + "fixLayout cleans up the layout of the canvas.\n"
            + "See <a href='https://autoplot.org/fixlayout'>https://autoplot.org/fixlayout</a><br>\n"
            + "<br><b>named parameters:</b>\n"
            + "<table>"
            + " <tr><td> horizontalSpacing=1em </td><td>Spacing between plots, such as 1em</td></tr>\n"
            + " <tr><td> verticalSpacing=1em      </td><td>Spacing between plots, such as 1em</td></tr>\n"
            + " <tr><td> hideTitles=True     </td><td> turn off all but the top title\n</td></tr>"
            + " <tr><td> hideTimeAxes=True     </td><td> turn off all but the bottom axis\n</td></tr>"
            + " <tr><td> hideYAxes=True     </td><td> turn off y-axes between plots</td></tr>"
            + " <tr><td> moveLegendsToOutsideNE=True     </td><td> move legends from default inside position to outside, when there is no colorbar.</td></tr>"
            + "</table>" 
            + "</html>");
    
    public static final PyString __completions__;
    
    static {
        String text = new BufferedReader(
            new InputStreamReader( FixLayoutCommand.class.getResourceAsStream("FixLayoutCommand.json"), StandardCharsets.UTF_8) )
            .lines().collect(Collectors.joining("\n"));
        __completions__= new PyString( text );
    }
    
    /**
     * implement the python call.
     * @param args the "rightmost" elements are the keyword values.
     * @param keywords the names for the keywords.
     * @return None
     */
    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {

        FunctionSupport fs= new FunctionSupport( "annotation", 
            new String[] { 
                "horizontalSpacing", "verticalSpacing",
                "hideTitles", "hideTimeAxes", "hideYAxes"
        },
        new PyObject[] { 
            Py.None, Py.None,
            Py.None, Py.None, Py.None
        } );
        
        fs.args( args, keywords );
        
        int nparm= args.length - keywords.length;
                
        HashMap controls= new HashMap();
                                    
        for ( int i=nparm; i<args.length; i++ ) {
            String kw= keywords[i-nparm];
            PyObject val= args[i];

            String sval= (String) val.__str__().__tojava__(String.class);
            switch (kw) {
                case "horizontalSpacing":
                    controls.put( "horizontalSpacing", sval );
                    break;
                case "verticalSpacing":
                    controls.put( "verticalSpacing", sval );
                    break;
                case "hideTitles":
                    controls.put("hideTitles",true);
                    break;
                case "hideTimeAxes":
                    controls.put("hideTimeAxes",true);
                    break;
                case "hideYAxes":
                    controls.put("hideYAxes",true);
                    break;
                default:
                    break;
            }
        }
        
        Application dom= ScriptContext.getDocumentModel();
        
        DomOps.fixLayout( dom, controls );

        return Py.None;
    }

}
