package org.autoplot.pngwalk;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import javax.swing.Scrollable;

/**
 *
 * @author Ed Jackson
 */
public class RowPngWalkView extends PngWalkView implements Scrollable {

    private int cellSize = 0;
    //private List<PngWalkThumbPanel> thumbs = new ArrayList();

    /** Creates new form RowPngWalkView */
    public RowPngWalkView(final WalkImageSequence seq) {
        super(seq);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int clickCell = (int)Math.floor( (double)e.getX()/(double)cellSize);
                //System.err.printf("Click at %d, %d (cell %d)%n", e.getX(), e.getY(), clickCell);
                selectCell(clickCell);
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                cellSize = getHeight();
                //System.err.printf("Set cell size to %d.%n", cellSize);
                updateLayout();
            }
        });
        
    }
    
    @Override
    protected void sequenceChanged() {
        updateLayout();
    }

    private void updateLayout() {
        if (seq != null) setPreferredSize(new Dimension(cellSize*(seq.size()), cellSize));
        else setPreferredSize(new Dimension(100,100));
        revalidate();
    }

    private void selectCell(int n) {
        // This will fire a property change and cause the view to repaint
        seq.setIndex(n);
    }

    @Override
    public void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        Rectangle bounds = g2.getClipBounds();
        //cellSize = this.getHeight();

        int i = (int)Math.floor(bounds.x / cellSize);
        int imax =Math.min(seq.size()-1,(int)Math.ceil((bounds.x + bounds.width) / cellSize) );
        
        //System.out.printf("First: %d, Last: %d%n", i, imax);
        for(; i<=imax; i++) {          
            if (i == seq.getIndex()) {
                g2.setColor(java.awt.Color.orange);
                g2.fillRect(i*cellSize, 0, cellSize, cellSize);
            }
            //g2.draw(new Ellipse2D.Double(i*cellSize+2, 2, cellSize-4, cellSize-4));
            BufferedImage thumb = seq.imageAt(i).getThumbnail();
            if (thumb != null) {
                double s = Math.min((double) (cellSize - 4) / thumb.getWidth(), (double) (cellSize - 4) / thumb.getHeight());
                if (s < 1.0) {
                    int w = (int) (s * thumb.getWidth());
                    int h = (int) (s * thumb.getHeight());
                    BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, w, h, 0, 1, 1, 0, false);
                    thumb = resizeOp.filter(thumb, null);
                }
            } else {
                thumb = loadingImage;
            }
            g2.drawImage(thumb, i*cellSize+(cellSize-thumb.getWidth())/2, (cellSize-thumb.getHeight())/2 , null);
        }
    }

    public Dimension getPreferredScrollableViewportSize() {
        System.err.println("getPreferredScrollableViewportSize called");
        return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle arg0, int arg1, int arg2) {
        return cellSize;
    }

    public int getScrollableBlockIncrement(Rectangle arg0, int arg1, int arg2) {
        // There is integer division here, so not as redundant as it looks
        return (this.getWidth()/cellSize) * cellSize;
    }

    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

}
