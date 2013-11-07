/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.awt.BorderLayout;
import java.io.IOException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.GraphUtil;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.util.AboutUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import test.graph.QFunctionLarry;

/**
 * Test of Das2 aspects used as APL
 * @author jbf
 */
public class Test039 {
    
    private static void testTCA() throws ParseException, IOException {
        int width = 500;
        int height = 400;

        JPanel panel= new JPanel();

        panel.setLayout(new BorderLayout());

        DasCanvas canvas = new DasCanvas(width, height);
        canvas.setAntiAlias(true);

        panel.add(canvas, BorderLayout.CENTER );

        // read data
        QDataSet yds = Ops.sin( Ops.linspace(0,10,1000) );
        QDataSet tds= Ops.timegen( "2010-01-01T00:00", "1 s", 1000 );

        QDataSet ds= Ops.link( tds, yds );

        // here's some old das2 autoranging, works for this case
        DasAxis xaxis = GraphUtil.guessXAxis(ds);
        DasAxis yaxis = GraphUtil.guessYAxis(ds);

        DasPlot plot = new DasPlot( xaxis, yaxis );

        // here's autoplot as of 2005
        Renderer r= GraphUtil.guessRenderer(ds);
        plot.addRenderer( r );

        // ugh.  I need to make antialiased the default.  Right now it reads the property from $HOME/.dasrc
        if ( r instanceof SeriesRenderer ) {
            ((SeriesRenderer)r).setAntiAliased(true);
        }


        xaxis.setTcaFunction( new QFunctionLarry() );

        xaxis.setDrawTca(true);

        canvas.add( plot, DasRow.create( canvas, null, "0%+2em", "100%-5em" ),
                DasColumn.create( canvas, null, "0%+14em", "100%-4em" ) );

        canvas.setPrintingTag("APL $Y"); // this will cause a failure once per year...
        
        canvas.writeToPng("test039_tca.png");
        
    }
    
    public static void main( String[] args ) throws Exception {
        String s= AboutUtil.getAboutHtml();
        String[] ss= s.split("\\<br\\>");
        for ( String sss : ss ) {
            System.err.println( sss );
        }
        testTCA();
        System.exit(0);
    }
}
