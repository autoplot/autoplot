package org.autoplot.pngwalk;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 *
 * @author Ed Jackson
 */
public class RowPngWalkView extends PngWalkView {

    private int cellSize = 0;
    //private List<PngWalkThumbPanel> thumbs = new ArrayList();

    /** Creates new form RowPngWalkView */
    public RowPngWalkView(WalkImageSequence seq) {
        super(seq);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int clickCell = (int)Math.floor( (double)e.getX()/(double)cellSize);
                //System.err.printf("Click at %d, %d (cell %d)%n", e.getX(), e.getY(), clickCell);
                selectCell(clickCell);
            }
        });

    }

    private void selectCell(int n) {
        // This will fire a property change and cause the view to repaint
        seq.setIndex(n);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getParent().getHeight() * seq.size(), getParent().getHeight() );
    }

    @Override
    public  void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        Rectangle bounds = g2.getClipBounds();
        cellSize = this.getHeight();

        int i = (int)Math.floor(bounds.x / cellSize);
        int imax =Math.min(seq.size()-1,(int)Math.ceil((bounds.x + bounds.width) / cellSize) );
        
        System.out.printf("First: %d, Last: %d%n", i, imax);
        for(; i<=imax; i++) {          
            if (i == seq.getIndex()) {
                g2.setColor(java.awt.Color.orange);
                g2.fillRect(i*cellSize+2, 2, cellSize-4, cellSize-4);
            }
            //g2.draw(new Ellipse2D.Double(i*cellSize+2, 2, cellSize-4, cellSize-4));
            BufferedImage thumb = seq.imageAt(i).getThumbnail(cellSize-4);
            g2.drawImage(thumb, i*cellSize+(cellSize-thumb.getWidth())/2, (cellSize-thumb.getHeight())/2 , null);
        }
    }

}
