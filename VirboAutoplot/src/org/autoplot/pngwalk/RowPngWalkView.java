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
public class RowPngWalkView extends PngWalkView {

    public static final int DEFAULT_CELL_SIZE = 100;
    public static final int MINIMUM_CELL_SIZE = 20;
    private int cellSize = DEFAULT_CELL_SIZE;
    protected JScrollPane scrollPane;
    private RowViewCanvas canvas;

    public RowPngWalkView(final WalkImageSequence sequence) {
        super(sequence);
        setShowCaptions(true);
        setLayout(new java.awt.BorderLayout());
        canvas = new RowViewCanvas();
        scrollPane = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        canvas.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                int clickCell = (int) Math.floor((double) e.getX() / (double) cellSize);
                //System.err.printf("Click at %d, %d (cell %d)%n", e.getX(), e.getY(), clickCell);
                selectCell(clickCell);
            }
        });

        canvas.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                //cellSize = getHeight();
                cellSize = getThumbnailSize();
                //System.err.printf("Set cell size to %d.%n", cellSize);
                updateLayout();
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
                        int first = (int) Math.floor(bounds.x / cellSize);
                        int last = Math.min(seq.size(), (int) Math.ceil((bounds.x + bounds.width) / cellSize + 1));
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
            canvas.setPreferredSize(new Dimension(DEFAULT_CELL_SIZE, DEFAULT_CELL_SIZE));
        }
        canvas.revalidate();
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
                e.getPropertyName().equals(WalkImageSequence.PROP_IMAGE_LOADED)) {
            int i = (Integer) e.getNewValue();
            int x = i * cellSize;
            canvas.repaint(new Rectangle(x, 0, cellSize, cellSize));
            canvas.repaintSoon();
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_SEQUENCE_CHANGED)) {
            sequenceChanged();
        }
    }


    private class RowViewCanvas extends JPanel implements Scrollable {

        public RowViewCanvas() {
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

            int i = (int) Math.floor(bounds.x / cellSize);
            int imax = Math.min(seq.size() - 1, (int) Math.ceil((bounds.x + bounds.width) / cellSize));

            //System.out.printf("First: %d, Last: %d%n", i, imax);
            for (; i <= imax; i++) {
                if (i == seq.getIndex()) {
                    g2.setColor(java.awt.Color.orange);
                    g2.fillRect(i * cellSize, 0, cellSize, cellSize);
                }
                //g2.draw(new Ellipse2D.Double(i*cellSize+2, 2, cellSize-4, cellSize-4));
                BufferedImage thumb = seq.imageAt(i).getThumbnail(!scrollPane.getHorizontalScrollBar().getValueIsAdjusting());
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
                g2.drawImage(thumb, i * cellSize + (cellSize - thumb.getWidth()) / 2, (cellSize - thumb.getHeight()) / 2, null);
                if (showCaptions && seq.imageAt(i).getCaption()!=null) {
                    int cx = i*cellSize + 5;
                    int cy = getHeight() - fm.getHeight();
                    g2.setColor(Color.BLACK);
                    Shape oldClip = g2.getClip();
                    g2.clip(new Rectangle(i*cellSize, 0, cellSize-10, getHeight()));
                    g2.drawString(seq.imageAt(i).getCaption(), cx, cy);
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
            return (this.getWidth() / cellSize) * cellSize;
        }

        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }
}
