package org.autoplot.pngwalk;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;

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
    private double restoreScrollPct = -1;

    /**
     * time limit where we stop getting new thumbnails, so that the event queue doesn't get blocked.
     */
    public static final int PAINT_THUMB_TIMEOUT_MS = 200;

    public GridPngWalkView(WalkImageSequence sequence) {
        super(sequence);

        setShowCaptions(true);
        setLayout(new java.awt.BorderLayout());
        canvas = new GridViewCanvas();
        scrollPane = new JScrollPane(canvas);
        scrollPane.setPreferredSize( new Dimension(640,640) );

        MouseWheelListener[] ll= scrollPane.getMouseWheelListeners();
        for ( MouseWheelListener l : ll ) scrollPane.removeMouseWheelListener( l );

        scrollPane.addMouseWheelListener( getMouseWheelListener() );

        MouseAdapter ma= new MouseAdapter() {
            Point p0;
            @Override
            public void mouseClicked(MouseEvent e) {
                p0= null;
                if (seq == null) return;
                selectCellAt(e.getX(), e.getY());
            }
            @Override
            public void mousePressed(MouseEvent e) {
                p0= e.getPoint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if ( p0!=null ) {
                    Point p2= e.getPoint();
                    selectCellsWithin(p0,p2);
                }
            }
        };
        canvas.addMouseListener(ma);
        canvas.addMouseMotionListener(ma);

        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
               nCols = getWidth() / thumbSize;
               if (nCols == 0) nCols = 1;
               // Thumbnail set restoreScroll, indicating we need to adjust scroll
               if (restoreScrollPct >= 0) {
                    javax.swing.JScrollBar sb = scrollPane.getVerticalScrollBar();
                    int newScroll = (int)(restoreScrollPct * (sb.getMaximum()-sb.getVisibleAmount()));
                    sb.setValue(newScroll);
                    restoreScrollPct = -1;
               }
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
        else canvas.setPreferredSize(new Dimension(0,0));
        canvas.revalidate();
        canvas.repaint();
    }

    @Override
    protected void sequenceChanged() {
        updateLayout();
        if (scrollPane!=null) scrollPane.getVerticalScrollBar().setValue(0);
    }

    @Override
    protected void thumbnailSizeChanged() {
        // before resizing, figure out the (roughly) central image
//        int curScrollPos = scrollPane.getVerticalScrollBar().getValue() + scrollPane.getVerticalScrollBar().getVisibleAmount() / 2;
//        restoreScrollPct = curScrollPos/thumbSize * nCols + (nCols/2);
        javax.swing.JScrollBar sb = scrollPane.getVerticalScrollBar();
        restoreScrollPct = ((double)sb.getValue() / (sb.getMaximum()-sb.getVisibleAmount()));

        // do the resize
        thumbSize= getThumbnailSize();
        nCols = getWidth() / thumbSize;
        if (nCols == 0) nCols = 1;
        updateLayout();

        // now scroll to place old central image in center (within scroll limits)
//        int newScroll = middleIndex/nCols * thumbSize - (scrollPane.getVerticalScrollBar().getVisibleAmount()-thumbSize)/2;
//        if (newScroll < 0) newScroll = 0;
//        scrollPane.getVerticalScrollBar().setValue(newScroll);
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

    private void selectCellsWithin( Point p1, Point p2 ) {
        if (p2.x > nCols * thumbSize) {
            return;
        }
        int col1 = p1.x / thumbSize;
        int row1 = p1.y / thumbSize;
        int col2 = p2.x / thumbSize;
        int row2 = p2.y / thumbSize;
        if ( row2<row1 ) {
            int t= row1;
            row1= row2;
            row2= t;
        }
        if ( col2<col1 ) {
            int t= col1;
            col1= col2;
            col2= t;
        }
        List<Integer> sel= new ArrayList();
        for ( int j=row1; j<=row2; j++ ) {
            for ( int i=col1; i<=col2; i++ ) {
                int n = j * nCols + i;
                if ( n<seq.size() ) {
                    sel.add(n);
                }
            }
        }
        seq.setSelectedIndeces(sel);
        repaint();
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
       } else if (e.getPropertyName().equals(WalkImageSequence.PROP_THUMB_LOADED) ||
                e.getPropertyName().equals(WalkImageSequence.PROP_IMAGE_LOADED) ||
                e.getPropertyName().equals(WalkImageSequence.PROP_BADGE_CHANGE) ) {
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
        private Font smallFont = new Font("Dialog", Font.PLAIN, 6);  //for use with small thumbnails
        private Font normalFont = new Font("Dialog", Font.PLAIN, 12); // this is the Java default

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

            long t0= System.currentTimeMillis();

            super.paintComponent(g1);
            Graphics2D g2 = (Graphics2D) g1;

            if (thumbSize < 100)
                g2.setFont(smallFont);
            else
                g2.setFont(normalFont);

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

            List<DatumRange> drs= seq.getActiveSubrange();

            List<Integer> sel= seq.getSelectedIndeces();

            boolean outOfTime= false;
            for (int row = rowMin; row < rowMax; row++) {
                for (int col = colMin; col < colMax; col++) {
                    int i = (row * nCols) + col;
                    if (i >= seq.size()) {
                        break;
                    }
                    if (sel.contains(i)) {
                        Color oldColor = g2.getColor();
                        g2.setColor(Color.orange);
                        g2.drawRect(col * thumbSize, row * thumbSize, thumbSize-1, thumbSize-1);
                        g2.setColor(oldColor);
                    }
                    if (seq.getIndex() == i) {
                        Color oldColor = g2.getColor();
                        g2.setColor(Color.orange);
                        g2.fillRect(col * thumbSize, row * thumbSize, thumbSize, thumbSize);
                        g2.setColor(oldColor);
                    }
                    //g2.draw(new Ellipse2D.Double(col * thumbSize + 2, row * thumbSize + 2, thumbSize - 4, thumbSize - 4));
                    WalkImage wimage = seq.imageAt(i);
                    BufferedImage thumb = wimage.getThumbnail( false );
                    if ( thumb==null && wimage.getStatus()!=WalkImage.Status.SIZE_THUMB_LOADED ) {
                        thumb = wimage.getThumbnail( !scrollPane.getVerticalScrollBar().getValueIsAdjusting() );
                    }
                    Dimension thumbd= wimage.getThumbnailDimension(false);
                    if (thumbd != null) {
                        double s = Math.min((double) (thumbSize - 4) / thumbd.getWidth(), (double) (thumbSize - 4 - fm.getHeight()) / thumbd.getHeight());
                        if (s < 1.0) {
                            int w = (int) (s * thumbd.getWidth());
                            int h = (int) (s * thumbd.getHeight());
                            outOfTime= outOfTime || System.currentTimeMillis()-t0 > PAINT_THUMB_TIMEOUT_MS;
                            BufferedImage whthumb= wimage.getThumbnail(w,h,!outOfTime);
                            thumb= whthumb;
                            if ( thumb==loadingImage ) {
                                this.repaintSoon();
                            }
                        } else {
                            thumb = loadingImage;
                        }
                    } else {
                        thumb = loadingImage;
                    }

                    int imgX= col * thumbSize + (thumbSize - thumb.getWidth()) / 2;
                    int imgY= row * thumbSize + (thumbSize - thumb.getHeight() - fm.getHeight()) / 2;
                    g2.drawImage(thumb, imgX, imgY, null);

                    if (PngWalkTool1.isQualityControlEnabled() && seq.getQualityControlSequence()!=null ) {
                        paintQualityControlIcon( i, g2, imgX, imgY, true );
                    }

                    DatumRange dr= seq.getTimeSpan();
                    if ( dr!=null ) {
                        try {
                        int ds=6;
                        if ( drs!=null && i<seq.size()-1 && seq.imageAt(i+1).getDatumRange().min().subtract(wimage.getDatumRange().max()).doubleValue(Units.seconds)>0 ) {
                            g2.setColor(Color.GRAY);
                            int cx = col*thumbSize + (thumbSize ) - ds;
                            int cy = row*thumbSize + (thumbSize ) - fm.getHeight() - 3;
                            Shape oldClip = g2.getClip();
                            g2.clip(new Rectangle(cx, row*thumbSize, thumbSize, thumbSize));
                            g2.fillPolygon( new int[] { cx, cx+ds, cx+ds, cx }, new int[] { cy, cy-ds, cy, cy }, 4 );
                            g2.setClip(oldClip);
                        }
                        if ( drs!=null && i>0 && seq.imageAt(i).getDatumRange().min().subtract(seq.imageAt(i-1).getDatumRange().max()).doubleValue(Units.seconds)>0 ) {
                            g2.setColor(Color.GRAY);
                            int cx = col*thumbSize;
                            int cy = row*thumbSize + (thumbSize ) - fm.getHeight() - 3;
                            Shape oldClip = g2.getClip();
                            g2.clip(new Rectangle(cx, row*thumbSize, thumbSize, thumbSize));
                            g2.fillPolygon( new int[] { cx, cx, cx+ds, cx }, new int[] { cy, cy-ds, cy, cy }, 4 );
                            g2.setClip(oldClip);
                        }
                        } catch ( NullPointerException ex ) {
                            ex.printStackTrace();;
                        }
                    }

                    if (showCaptions && wimage.getCaption()!=null) {
                        //These 2 lines center caption below image
                        //int cx = col*thumbSize + (thumbSize - fm.stringWidth(wimage.getCaption())) / 2;
                        //cx = Math.max(cx,col*thumbSize + 2);
                        //Instead, align to left edge of thumbnail:
                        int cx = col*thumbSize + (thumbSize - thumb.getWidth())/2;
                        int cy = row*thumbSize + (thumbSize + thumb.getHeight() + fm.getHeight())/2;
                        g2.setColor(Color.BLACK);
                        Shape oldClip = g2.getClip();
                        g2.clip(new Rectangle(cx, row*thumbSize, (thumbSize+thumb.getWidth())/2, thumbSize));
                        String s= wimage.getCaption();
                        if ( s.startsWith("orbit:") ) {
                            s= s.substring(6);
                        }
                        g2.drawString( s, cx, cy);
                        g2.setClip(oldClip);
                    }

                }
            }

            //System.err.printf("repaint gridPngWalkView (ms): %d\n", System.currentTimeMillis()-t0 ); // on 20120418, this was okay.
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return thumbSize;
        }

        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int x= (scrollPane.getVerticalScrollBar().getVisibleAmount() / thumbSize ) * thumbSize;
            return x;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;  // Never scroll horizontally
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

}
