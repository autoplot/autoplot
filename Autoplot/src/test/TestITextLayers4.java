/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import com.itextpdf.text.Document;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import javax.imageio.ImageIO;

/**
 * This shows a problem with itext, that a child graphics context is always drawn on top of its
 * parent, even if there is drawing code after the child is disposed and gone.  I realized this
 * wasn't a problem though were I was seeing a bug (the legend), but we need to be aware of this
 * problem.
 *
 * @author jbf
 */
public class TestITextLayers4 {

    public static void main(String[] args) throws Exception {
        int width = 300;
        int height = 300;

        OutputStream out;
        Document doc;
        PdfWriter writer;
        PdfContentByte cb;

        out = new FileOutputStream("/tmp/foo_0001.pdf");

        Rectangle rect = new Rectangle(width, height);
        doc = new Document(rect, 0f, 0f, 0f, 0f);
        writer = PdfWriter.getInstance(doc, out);
        doc.open();
        cb = writer.getDirectContent();
        cb.saveState();
        
        Graphics2D g0= cb.createGraphics(width, height);

        g0.setColor( Color.LIGHT_GRAY );
        g0.fillRoundRect( 10, 10, 280, 280, 20, 20 );

        g0.setColor( Color.BLACK );
        //g0.drawLine( 0, 0, 100, 100 );
        g0.drawString("First Text", 30, 40 );

        Graphics2D g1= (Graphics2D) g0.create();

        g1.drawImage( ImageIO.read(new URL( "http://www-pw.physics.uiowa.edu/~jbf/itext/colors.png") ), 35, 60, null );
        
        g1.setColor( Color.BLACK );
        g1.drawLine( 0, 0, 100, 100 );
        g1.drawString("Second Text", 40, 70 );

        g1.dispose();

        Graphics2D g2= (Graphics2D) g0.create();

        g2.drawImage( ImageIO.read(new URL( "http://www-pw.physics.uiowa.edu/~jbf/itext/colors.png") ), 45, 50, null );

        g2.setColor( Color.BLACK );
        g2.drawLine( 0, 20, 100, 120 );
        g2.drawString("Third Text", 40, 90 );

        g2.dispose();

        g0.setColor( new Color( 255, 200, 200, 200 ) );  // PINK
        g0.fillRoundRect( 15, 15, 100, 100, 15, 15  );

        g0.setColor( Color.DARK_GRAY );
        g0.drawString("I'd expect the pink to be on top", 20, 100 );

        g0.drawImage( ImageIO.read(new URL( "http://www-pw.physics.uiowa.edu/~jbf/itext/colors.png") ), 50, 35, null );

        g0.dispose();

        cb.restoreState();
        doc.close();
    }
}
