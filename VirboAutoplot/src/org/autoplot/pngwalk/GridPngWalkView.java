package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.Scrollable;

/**
 *
 * @author Ed Jackson
 */
public class GridPngWalkView extends PngWalkView implements Scrollable {

    private int thumbSize = 100;
    private int nCols = 1;
    private static final int MIN_THUMB_SIZE = 20;
    private static final int MAX_THUMB_SIZE = 300;

    public GridPngWalkView(WalkImageSequence seq) {
        super(seq);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectCellAt(e.getX(), e.getY());
            }
        });

        addComponentListener(new ComponentAdapter() {
           @Override
           public void componentResized(ComponentEvent e) {
               nCols = getWidth() / thumbSize;
               if (nCols == 0) nCols = 1;
               updateLayout();
           }
        });
    }

    private void updateLayout() {
        if (seq != null) setPreferredSize(new Dimension(thumbSize*nCols, thumbSize*(seq.size()/nCols + 1)));
        else setPreferredSize(new Dimension(100,100));
        revalidate();
    }

    @Override
    protected void sequenceChanged() {
        updateLayout();
    }
//    @Override
//    public Dimension getPreferredSize() {
//        if (seq==null ) {
//            return new Dimension(200,200);
//        }
//        int w = getParent().getWidth();
//        int h = thumbSize * (1 + (seq.size() + 1) / Math.max(1, w / thumbSize));
//        //System.err.printf("w=%d, h=%d%n",w ,h);
//        return new Dimension(w, h);
//    }

    private void selectCellAt(int x, int y) {
        if (x > nCols * thumbSize) {
            return;
        }
        int col = x / thumbSize;
        int row = y / thumbSize;

        int n = row * nCols + col;
        if (n >= seq.size()) {
            return;
        }

        seq.setIndex(n);
    }

    public int getThumbSize() {
        return thumbSize;
    }

    public void setThumbSize(int size) {
        if (size < MIN_THUMB_SIZE) {
            size = MIN_THUMB_SIZE;
        }
        if (size > MAX_THUMB_SIZE) {
            size = MAX_THUMB_SIZE;
        }
        thumbSize = size;
    }

    @Override
    public void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        // Fit as many thumbnails as possible horizontally (at least one), then go to a new row
        //int cellsPerRow = Math.max(1, this.getWidth() / thumbSize);

        if ( seq==null ) return;
        
        for (int row = 0; row < (seq.size() / nCols + 1); row++) {
            for (int col = 0; col < nCols; col++) {
                int n = (row * nCols) + col;
                if (n >= seq.size()) {
                    break;
                }
                if (seq.getIndex() == n) {
                    Color oldColor = g2.getColor();
                    g2.setColor(Color.orange);
                    g2.fillRect(col * thumbSize + 2, row * thumbSize + 2, thumbSize - 4, thumbSize - 4);
                    g2.setColor(oldColor);
                }
                //g2.draw(new Ellipse2D.Double(col * thumbSize + 2, row * thumbSize + 2, thumbSize - 4, thumbSize - 4));
                BufferedImage thumb = seq.imageAt(n).getThumbnail(thumbSize - 4);
                g2.drawImage(thumb, col * thumbSize + (thumbSize - thumb.getWidth()) / 2, row * thumbSize + (thumbSize - thumb.getHeight()) / 2, null);

            }
        }
    }

    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return thumbSize;
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return (this.getHeight()/thumbSize) * thumbSize;
    }

    public boolean getScrollableTracksViewportWidth() {
        return true;  // Never scroll horizontally
    }

    public boolean getScrollableTracksViewportHeight() {
        return false;
    }


}
