package test;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.FileOutputStream;

import javax.swing.JComponent;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.das2.util.Entities;

public class PdfUnicodeTextImageTest
{
    public static final String FLAT_SYMBOL  = "\u266D";
    public static final String SHARP_SYMBOL = "\u266F";

    public static final String RHO= Entities.decodeEntities("&rho;");

    public static final String DOUBLE_FLAT_SYMBOL = "\uD834\uDD2B";
    public static final String DOUBLE_SHARP_SYMBOL = "\uD834\uDD2A";




    public static void main(String[] args) throws InterruptedException
    {
        new PdfUnicodeTextImageTest();
        Thread.sleep(2000);
        System.exit(0);
    }


    public PdfUnicodeTextImageTest()
    {
        try
        {
            final FileOutputStream fileOutputStream = new FileOutputStream("/tmp/unicode_test.pdf");
            final Document document = new Document();
            final PdfWriter pdf = PdfWriter.getInstance(document, fileOutputStream);


            try
            {
                document.open();
                document.setPageSize(new com.lowagie.text.Rectangle(500,800));
                document.newPage();


                printPage(document, pdf);
            }
            finally
            {
                document.close();
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void printPage(Document document, PdfWriter pdf) throws DocumentException
    {
        document.add(new Chunk(" "));
        final PdfContentByte contentbytes = pdf.getDirectContent();
        final PdfTemplate template = contentbytes.createTemplate(500, 800);
        final Graphics2D graphics2d = template.createGraphicsShapes(500, 800);

        StringPainter painter = new StringPainter(new Font("Arial Unicode MS", Font.PLAIN, 16));
        painter.setBounds(50,50,200,100);
        painter.paint(graphics2d);


        graphics2d.dispose();
        contentbytes.addTemplate(template, 0, 0);
    }

    class StringPainter extends JComponent
    {
        private Font font;


        public StringPainter(Font font)
        {
            this.font = font;
        }


        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);


            Graphics2D g2 = (Graphics2D) g;


            g2.setFont(font);


            g2.drawString("TEST: " + FLAT_SYMBOL + "  " + DOUBLE_FLAT_SYMBOL + "  " + Entities.decodeEntities("&rho;"), 10, 50);
            g2.drawString("TEST: " + SHARP_SYMBOL + "  " + DOUBLE_SHARP_SYMBOL + "  " + Entities.decodeEntities("&Omega;"), 10, 80);
        }
    }
}