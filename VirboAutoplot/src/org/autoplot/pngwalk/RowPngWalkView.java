package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeEvent;
import java.util.List;
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
public class RowPngWalkView extends PngWalkView {

    public static final int DEFAULT_CELL_SIZE = 100;
    public static final int MINIMUM_CELL_SIZE = 20;
    private int cellSize = DEFAULT_CELL_SIZE;
    protected JScrollPane scrollPane;
    private Canvas canvas;
    private double restoreScrollPct = -1;

    public RowPngWalkView(final WalkImageSequence sequence) {
        super(sequence);
        setShowCaptions(true);
        setLayout(new java.awt.BorderLayout());
        canvas = new Canvas();
        scrollPane = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        MouseWheelListener[] ll= scrollPane.getMouseWheelListeners();
        for ( MouseWheelListener l : ll ) scrollPane.removeMouseWheelListener( l );

        scrollPane.addMouseWheelListener( getMouseWheelListener() );
        
        canvas.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (seq == null) return;
                int clickCell = (int) Math.floor((double) e.getX() / (double) cellSize);
                //System.err.printf("Click at %d, %d (cell %d)%n", e.getX(), e.getY(), clickCell);
                selectCell(clickCell);
            }
        });

        canvas.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                // Thumbnail set restoreScroll, indicating we need to adjust scroll
                if (restoreScrollPct >= 0) {
                    //int newScroll = restoreScrollPct * cellSize - (scrollPane.getWidth()-cellSize)/2;
                    javax.swing.JScrollBar sb = scrollPane.getHorizontalScrollBar();
                    int newScroll = (int)(restoreScrollPct * (sb.getMaximum()-sb.getVisibleAmount()));
                    if (newScroll < 0) newScroll = 0;
                    sb.setValue(newScroll);
                    restoreScrollPct = -1;
                }
            }
        });

        scrollPane.getHorizontalScrollBar().getModel().addChangeListener(new ChangeListener() {
            Timer repaintTimer = new Timer("RowViewRepaintDelay", true);
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
                        int first = Math.max( 0, ( bounds.x - bounds.width ) / cellSize );
                        int last = Math.min(seq.size(), (bounds.x +  2 * bounds.width) / cellSize + 1);
                        for(int i=first; i<last; i++) {
                            seq.imageAt(i).getThumbnail(true);
                        }
                    }
                };
                repaintTimer.schedule(task, 200L);
            }
        });

        add(scrollPane);
    }

    @Override
    protected void sequenceChanged() {
        updateLayout();
        if (scrollPane!=null) scrollPane.getHorizontalScrollBar().setValue(0);
    }

    @Override
    protected void thumbnailSizeChanged() {
        // Store scroll bar position as percentage
        // This will be restored by component event handler
        javax.swing.JScrollBar sb = scrollPane.getHorizontalScrollBar();
        restoreScrollPct = ((double)sb.getValue() / (sb.getMaximum()-sb.getVisibleAmount()));
        //System.err.printf("Will restore scroll at %.2f%%%n", 100*restoreScrollPct);
        //Update thumbnail size
        cellSize= getThumbnailSize();
        updateLayout();
        super.thumbnailSizeChanged();
    }


    @Override
    public JComponent getMouseTarget() {
        return canvas;
    }
    
    private void updateLayout() {
        if (canvas==null) return;  // super constructor causes this to be called before canvas init
        if (seq != null) {
            canvas.setPreferredSize(new Dimension(cellSize * seq.size(), cellSize));
        } else {
            canvas.setPreferredSize(new Dimension(0, 0));
        }
        canvas.revalidate(); //force scrollpane to re-do layout
        canvas.repaint();
    }

    private void selectCell(int n) {
        // This will fire a property change and cause the view to repaint
        seq.setIndex(n);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals(WalkImageSequence.PROP_INDEX)) {
            int i = (Integer) e.getOldValue();
            int x = i * cellSize;
            canvas.repaint(new Rectangle(x, 0, cellSize, cellSize));
            i = (Integer) e.getNewValue();
            x = i * cellSize;
            canvas.repaint(new Rectangle(x, 0, cellSize, cellSize));
            // If the new index isn't completely visible, scroll to center it as nearly as possible
            // Note that the BoundedRangeModel correctly handles out-of-bounds values so we don't check
            int scrollMin = scrollPane.getHorizontalScrollBar().getValue();
            int scrollMax = scrollMin + scrollPane.getHorizontalScrollBar().getVisibleAmount();
            int pos = (i * cellSize) - getWidth() / 2 + cellSize / 2;
            if ( scrollMin > i*cellSize || scrollMax < (i+1)*cellSize ) {
                scrollPane.getHorizontalScrollBar().setValue(pos);
            }
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_THUMB_LOADED) ||
                e.getPropertyName().equals(WalkImageSequence.PROP_IMAGE_LOADED) ||
                e.getPropertyName().equals(WalkImageSequence.PROP_BADGE_CHANGE)) {
            int i = (Integer) e.getNewValue();
            int x = i * cellSize;
            canvas.repaint(new Rectangle(x, 0, cellSize, cellSize));
            //canvas.repaintSoon();
            //System.err.println("repaint soon...");
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_SEQUENCE_CHANGED)) {
            sequenceChanged();
        }
    }


    private class Canvas extends JPanel implements Scrollable {
        private Font smallFont = new Font("Dialog", Font.PLAIN, 6);  //for use with small thumbnails
        private Font normalFont = new Font("Dialog", Font.PLAIN, 12); // this is the Java default

        public Canvas() {
            this.setBorder(javax.swing.BorderFactory.createEmptyBorder());

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
        public synchronized void paintComponent(Graphics g1) {
            super.paintComponent(g1);
            Graphics2D g2 = (Graphics2D) g1;

            g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );

            if (cellSize < 100)
                g2.setFont(smallFont);
            else
                g2.setFont(normalFont);
            
            Rectangle bounds = g2.getClipBounds();
            FontMetrics fm= g2.getFontMetrics();

//            Color cc= new Color( 220 + (int)(Math.random()*25), 220 + (int)(Math.random()*25), 220 + (int)(Math.random()*25) );
//            Color c0= g2.getColor();
//            g2.setColor( cc );
//            g2.fillRect( bounds.x, bounds.y, bounds.width, bounds.height );
//            g2.setColor( c0 );
//            psn++;
//
//            g2.drawString(""+psn, bounds.x, fm.getHeight() );
//            g2.drawString(""+psn, bounds.x+bounds.width-fm.stringWidth(""+psn), fm.getHeight() );

            if (seq == null) {
                return;
            }

            int i = bounds.x / cellSize;
            int imax = Math.min(seq.size() - 1, (bounds.x + bounds.width) / cellSize);

            List<DatumRange> drs= seq.getActiveSubrange();

            //System.out.printf("First: %d, Last: %d%n", i, imax);
            for (; i <= imax; i++) {
                if (i == seq.getIndex()) {
                    g2.setColor(java.awt.Color.orange);
                    g2.fillRect(i * cellSize, 0, cellSize, cellSize);
                }
                //g2.draw(new Ellipse2D.Double(i*cellSize+2, 2, cellSize-4, cellSize-4));
                WalkImage wimage= seq.imageAt(i);
                BufferedImage thumb = wimage.getThumbnail(!scrollPane.getHorizontalScrollBar().getValueIsAdjusting());
                if (thumb != null) {
                    double s = Math.min((double) (cellSize - 4) / thumb.getWidth(), (double) (cellSize - 4 - fm.getHeight()) / thumb.getHeight());
                    if (s < 1.0) {
                        int w = (int) (s * thumb.getWidth());
                        int h = (int) (s * thumb.getHeight());
                        //BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, w, h, 0, 1, 1, 0, false);
                        //thumb= ImageResize.getScaledInstance( thumb, w, h, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR, true );
                        thumb= wimage.getThumbnail(w,h,true);
                        if ( thumb==loadingImage ) {
                            this.repaintSoon();
                        }
                        //thumb = resizeOp.filter(thumb, null);
                    }
                } else {
                    thumb = loadingImage;
                }
                int imgX = i * cellSize + (cellSize - thumb.getWidth()) / 2;
                int imgY = (cellSize - thumb.getHeight() - fm.getHeight()) / 2;
                g2.drawImage(thumb, imgX , imgY, null);
                if (PngWalkTool1.isQualityControlEnabled() && seq.getQualityControlSequence()!=null ) {
                    paintQualityControlIcon( i, g2, imgX, imgY, true );
                }
                int ds=6;
                if ( drs!=null && i<seq.size()-1 && wimage.getDatumRange()!=null && seq.imageAt(i+1).getDatumRange().min().subtract(wimage.getDatumRange().max()).doubleValue(Units.seconds)>0 ) {
                    g2.setColor(Color.GRAY);
                    int cx = i*cellSize + ( cellSize ) - ds;
                    int cy = ( cellSize ) - fm.getHeight() - 3;
                    Shape oldClip = g2.getClip();
                    //g2.clip(new Rectangle(cx, cellSize, cellSize, cellSize));
                    g2.fillPolygon( new int[] { cx, cx+ds, cx+ds, cx }, new int[] { cy, cy-ds, cy, cy }, 4 );
                    g2.setClip(oldClip);
                }
                if ( drs!=null && i>0 && wimage.getDatumRange()!=null  && seq.imageAt(i).getDatumRange().min().subtract(seq.imageAt(i-1).getDatumRange().max()).doubleValue(Units.seconds)>0 ) {
                    g2.setColor(Color.GRAY);
                    int cx = i*cellSize;
                    int cy = ( cellSize ) - fm.getHeight() - 3;
                    Shape oldClip = g2.getClip();
                    //g2.clip(new Rectangle(cx, cellSize, cellSize, cellSize));
                    g2.fillPolygon( new int[] { cx, cx, cx+ds, cx }, new int[] { cy, cy-ds, cy, cy }, 4 );
                    g2.setClip(oldClip);
                }
                
                if (showCaptions && wimage.getCaption()!=null) {
                    //The following two lines center the caption under the image
                    //int cx = i*cellSize + (cellSize - fm.stringWidth(wimage.getCaption())) / 2;
                    //cx = Math.max(cx, i*cellSize + 2);
                    //Instead, align with the left edge of the image.
                    int cx = i * cellSize + ((cellSize - thumb.getWidth()) / 2);
                    int cy = (cellSize + thumb.getHeight() + fm.getHeight())/2;
                    g2.setColor(Color.BLACK);
                    Shape oldClip = g2.getClip();
                    g2.clip(new Rectangle(cx, 0, (cellSize+thumb.getWidth())/2, getHeight()));
                    String s= wimage.getCaption();
                    if ( s.startsWith("orbit:") ) {
                        s= s.substring(6);
                    }
                    g2.drawString( s, cx, cy);
                    g2.setClip(oldClip);
                }
            }
        }

        public Dimension getPreferredScrollableViewportSize() {
            //System.err.println("getPreferredScrollableViewportSize called: preferredSize=" + getPreferredSize());
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle arg0, int arg1, int arg2) {
            return cellSize;
        }

        public int getScrollableBlockIncrement(Rectangle arg0, int arg1, int arg2) {
            // There is integer division here, so not as redundant as it looks
            int x= (scrollPane.getHorizontalScrollBar().getVisibleAmount() / cellSize ) * cellSize;
            return x;
        }

        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }
}
