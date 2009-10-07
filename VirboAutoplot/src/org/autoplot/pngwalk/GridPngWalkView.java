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
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeEvent;
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

        if ( seq==null ) return;

        Rectangle bounds = g2.getClipBounds();
        int rowMin = bounds.y / thumbSize;
        int rowMax = Math.min((bounds.y+bounds.height)/thumbSize+1, seq.size() / nCols + 1);
        int colMin = Math.min(bounds.x / thumbSize, nCols);
        int colMax = Math.min((bounds.x+bounds.width)/thumbSize+1, nCols);

        for (int row = rowMin; row < rowMax; row++) {
            for (int col = colMin; col < colMax; col++) {
                int n = (row * nCols) + col;
                if (n >= seq.size()) {
                    break;
                }
                if (seq.getIndex() == n) {
                    Color oldColor = g2.getColor();
                    g2.setColor(Color.orange);
                    g2.fillRect(col * thumbSize, row * thumbSize, thumbSize, thumbSize);
                    g2.setColor(oldColor);
                }
                //g2.draw(new Ellipse2D.Double(col * thumbSize + 2, row * thumbSize + 2, thumbSize - 4, thumbSize - 4));
                BufferedImage thumb = seq.imageAt(n).getThumbnail();
                if (thumb != null) {  //TODO: placeholder for loading image
                    double s = Math.min((double) (thumbSize - 4) / thumb.getWidth(), (double) (thumbSize - 4) / thumb.getHeight());
                    if (s < 1.0) {
                        int w = (int) (s * thumb.getWidth());
                        int h = (int) (s * thumb.getHeight());
                        BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, w, h, 0, 1, 1, 0, false);
                        thumb = resizeOp.filter(thumb, null);
                    }
                } else {
                    thumb = loadingImage;
                }
                g2.drawImage(thumb, col * thumbSize + (thumbSize - thumb.getWidth()) / 2, row * thumbSize + (thumbSize - thumb.getHeight()) / 2, null);

            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals(WalkImageSequence.PROP_INDEX)) {
            //System.err.printf("Index changed from %d to %d%n", e.getOldValue(), e.getNewValue());
            int i = (Integer)e.getOldValue();
            int x = (i%nCols) * thumbSize;
            int y = (i/nCols) * thumbSize;
            repaint(new Rectangle(x, y, thumbSize, thumbSize));
            i = (Integer)e.getNewValue();
            x = (i%nCols) * thumbSize;
            y = (i/nCols) * thumbSize;
            repaint(new Rectangle(x, y, thumbSize, thumbSize));
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_IMAGE_LOADED)) {
            int i = (Integer)e.getNewValue();
            //System.err.printf("Image number %d finished loading%n", i);
            int y = (i/nCols) * thumbSize;
            int x = (i%nCols) * thumbSize;
            repaint(new Rectangle(x, y, thumbSize, thumbSize));
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
