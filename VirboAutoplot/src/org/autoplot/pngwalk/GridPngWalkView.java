package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeEvent;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Ed Jackson
 */
public class GridPngWalkView extends PngWalkView {

    private int thumbSize = 100;
    private int nCols = 1;
    private static final int MIN_THUMB_SIZE = 20;
    private static final int MAX_THUMB_SIZE = 300;

    private JScrollPane scrollPane;
    private GridViewCanvas canvas;

    public GridPngWalkView(WalkImageSequence sequence) {
        super(sequence);

        setShowCaptions(true);
        setLayout(new java.awt.BorderLayout());
        canvas = new GridViewCanvas();
        scrollPane = new JScrollPane(canvas);
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectCellAt(e.getX(), e.getY());
            }
        });

        canvas.addComponentListener(new ComponentAdapter() {
           @Override
           public void componentResized(ComponentEvent e) {
               nCols = getWidth() / thumbSize;
               if (nCols == 0) nCols = 1;
               updateLayout();
           }
        });

        scrollPane.getVerticalScrollBar().getModel().addChangeListener(new ChangeListener() {
            Timer repaintTimer = new Timer("GridViewRepaintDelay", true);
            TimerTask task;

            public void stateChanged(ChangeEvent e) {
                // Cancel any pending timer events
                if (task != null) task.cancel();
                if (seq == null) return;
                if ( !canvas.isShowing() ) return;
                // Schedule a new one
                task = new TimerTask() {

                    public void run() {
                        Rectangle bounds = scrollPane.getViewport().getViewRect();
                        int rowMin = bounds.y / thumbSize;
                        int rowMax = Math.min((bounds.y + bounds.height) / thumbSize + 1, seq.size() / nCols + 1);
                        int colMin = Math.min(bounds.x / thumbSize, nCols);
                        int colMax = Math.min((bounds.x + bounds.width) / thumbSize + 1, nCols);

                        for (int row = rowMin; row < rowMax; row++) {
                            for (int col = colMin; col < colMax; col++) {
                                int n = (row * nCols) + col;
                                if (n >= seq.size()) {
                                    break;
                                }
                                seq.imageAt(n).getThumbnail(true);
                            }
                        }
                    }
                };
                repaintTimer.schedule(task, 200L);
            }
        });

        add(scrollPane);
    }

    private void updateLayout() {
        if (canvas == null) return;
        if (seq != null) canvas.setPreferredSize(new Dimension(thumbSize*nCols, thumbSize*(seq.size()/nCols + 1)));
        else canvas.setPreferredSize(new Dimension(100,100));
        canvas.repaint();
    }

    @Override
    protected void sequenceChanged() {
        updateLayout();
        if (scrollPane!=null) scrollPane.getVerticalScrollBar().setValue(0);
    }

    @Override
    protected void thumbnailSizeChanged() {
        thumbSize= getThumbnailSize();
        nCols = getWidth() / thumbSize;
        if (nCols == 0) nCols = 1;
        updateLayout();

        super.thumbnailSizeChanged();
    }

    @Override
    public JComponent getMouseTarget() {
        return canvas;
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


    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals(WalkImageSequence.PROP_INDEX)) {
            //System.err.printf("Index changed from %d to %d%n", e.getOldValue(), e.getNewValue());
            int i = (Integer) e.getOldValue();
            int x = (i % nCols) * thumbSize;
            int y = (i / nCols) * thumbSize;
            canvas.repaint(new Rectangle(x, y, thumbSize, thumbSize));
            i = (Integer) e.getNewValue();
            x = (i % nCols) * thumbSize;
            y = (i / nCols) * thumbSize;
            canvas.repaint(new Rectangle(x, y, thumbSize, thumbSize));

            int scrollMin = scrollPane.getVerticalScrollBar().getValue();
            int scrollMax = scrollMin + scrollPane.getVerticalScrollBar().getVisibleAmount();
            int pos = y - getHeight()/2 - thumbSize/2;
            if (scrollMin > y || scrollMax < y+thumbSize) {
                scrollPane.getVerticalScrollBar().setValue(pos);
            }
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_THUMB_LOADED)) {
            int i = (Integer) e.getNewValue();
            //System.err.printf("Image number %d finished loading%n", i);
            int y = (i / nCols) * thumbSize;
            int x = (i % nCols) * thumbSize;
            canvas.repaint(new Rectangle(x, y, thumbSize, thumbSize));
            canvas.repaintSoon();
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_SEQUENCE_CHANGED)) {
            sequenceChanged();
        }
    }


    private class GridViewCanvas extends JPanel implements Scrollable {


        GridViewCanvas() {
            repaintTimer = new javax.swing.Timer( 300, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    repaint();
                }
            });
            repaintTimer.setRepeats(false);
        }

        // kludge to work around repainting problem
        javax.swing.Timer repaintTimer;

        //int psn= 0; // paint sequence number

        private void repaintSoon(  ) {
            repaintTimer.restart();
        }


        @Override
        public void paintComponent(Graphics g1) {
            super.paintComponent(g1);
            Graphics2D g2 = (Graphics2D) g1;

            g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
            if (seq == null) {
                return;
            }

            Rectangle bounds = g2.getClipBounds();
            int rowMin = bounds.y / thumbSize;
            int rowMax = Math.min((bounds.y + bounds.height) / thumbSize + 1, seq.size() / nCols + 1);
            int colMin = Math.min(bounds.x / thumbSize, nCols);
            int colMax = Math.min((bounds.x + bounds.width) / thumbSize + 1, nCols);
            FontMetrics fm = g2.getFontMetrics();

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
                BufferedImage thumb = seq.imageAt(n).getThumbnail(!scrollPane.getVerticalScrollBar().getValueIsAdjusting());
                    if (thumb != null) {
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
                    if (showCaptions && seq.imageAt(n).getCaption()!=null) {
                        int cx = col*thumbSize + 5;
                        int cy = (row+1)*thumbSize - fm.getDescent();
                        g2.setColor(Color.BLACK);
                        Shape oldClip = g2.getClip();
                        g2.clip(new Rectangle(col*thumbSize, row*thumbSize, thumbSize-10, thumbSize));
                        g2.drawString(seq.imageAt(n).getCaption(), cx, cy);
                        g2.setClip(oldClip);
                    }

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
            return (this.getHeight() / thumbSize) * thumbSize;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;  // Never scroll horizontally
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

}
