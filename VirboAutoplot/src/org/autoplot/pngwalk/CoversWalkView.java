/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
 * @author jbf
 */
public class CoversWalkView extends PngWalkView  {

    private static final int HEIGHT_WIDTH_RATIO = 10;
    int cellSize = 100;
    int cellWidth= cellSize / HEIGHT_WIDTH_RATIO;
    int MINIMUM_CELL_SIZE = 50;
    int DEFAULT_CELL_SIZE = 100;
    private JScrollPane scrollPane;

    Canvas canvas;
    
    public CoversWalkView(final WalkImageSequence sequence) {
        super(sequence);
        setLayout(new java.awt.BorderLayout());
        canvas= new Canvas();

        scrollPane = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        MouseWheelListener[] ll= scrollPane.getMouseWheelListeners();
        for ( MouseWheelListener l : ll ) scrollPane.removeMouseWheelListener( l );
        scrollPane.addMouseWheelListener( getMouseWheelListener() );

        canvas.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (seq == null) return;
                int clickCell = (int) Math.floor((double) e.getX() / (double) cellWidth);
                //System.err.printf("Click at %d, %d (cell %d)%n", e.getX(), e.getY(), clickCell);
                selectCell(clickCell);
            }
        });

        canvas.addComponentListener(new ComponentAdapter() {  // jbf has no idea what this is!
            @Override
            public void componentResized(ComponentEvent e) {
                //cellSize = getHeight();
                cellSize = getThumbnailSize();
                //System.err.printf("Set cell size to %d.%n", cellSize);
                updateLayout();
            }
        });

        //I'm not sure that this actually does anything.  Changed verticalScrollBar to horiz with no effect.
        scrollPane.getHorizontalScrollBar().getModel().addChangeListener(new ChangeListener() {
            Timer repaintTimer = new Timer("CoversViewRepaintDelay", true);
            TimerTask task;

            public void stateChanged(ChangeEvent e) {
                // Cancel any pending timer events
                if (task != null) task.cancel();
                if (sequence == null) return;
                if ( !canvas.isShowing() ) return;
                
                // Schedule a new one
                task = new TimerTask() {

                    public void run() {
                        Rectangle bounds = scrollPane.getViewport().getViewRect();
                        int first = bounds.x / cellWidth;
                        int last = Math.min(sequence.size(), (bounds.x + bounds.width) / cellWidth + 1);
                        for(int i=first; i<last; i++) {
                            sequence.imageAt(i).getThumbnail(true);
                        }
                    }
                };
                repaintTimer.schedule(task, 200L);
            }
        });

        add(scrollPane);
    }

    protected boolean perspective = true;
    public static final String PROP_PERSPECTIVE = "perspective";

    public boolean isPerspective() {
        return perspective;
    }

    public void setPerspective(boolean perspective) {
        boolean oldPerspective = this.perspective;
        this.perspective = perspective;
        canvas.repaint();
        firePropertyChange(PROP_PERSPECTIVE, oldPerspective, perspective);
    }


    @Override
    protected void sequenceChanged() {
        updateLayout();
        if (scrollPane!=null) scrollPane.getVerticalScrollBar().setValue(0);
    }

    @Override
    protected void thumbnailSizeChanged() {
        cellSize= getThumbnailSize();
        cellWidth= cellSize / HEIGHT_WIDTH_RATIO;
        updateLayout();
        super.thumbnailSizeChanged();
    }


    @Override
    public JComponent getMouseTarget() {
        return canvas;
    }

    //why?
    private void updateLayout() {
        if (canvas==null) return;  // super constructor causes this to be called before canvas init
        if (seq != null) {
            canvas.setPreferredSize(new Dimension(cellWidth * seq.size(), cellSize ));
        } else {
            canvas.setPreferredSize(new Dimension(640,MINIMUM_CELL_SIZE));
        }
        scrollPane.revalidate(); //force scrollpane to re-do layout
        canvas.revalidate();
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
            int x = (i % seq.size()) * (cellWidth);
            canvas.repaint(new Rectangle(x, 0, cellWidth, cellSize));
            i = (Integer) e.getNewValue();
            x = (i % seq.size()) * (cellWidth);
            canvas.repaint(new Rectangle(x, 0, cellWidth, cellSize));
            // If the new index isn't completely visible, scroll to center it as nearly as possible
            // Note that the BoundedRangeModel correctly handles out-of-bounds values so we don't check
            int scrollMin = scrollPane.getHorizontalScrollBar().getValue();
            int scrollMax = scrollMin + scrollPane.getHorizontalScrollBar().getVisibleAmount();
            int pos = (i * cellWidth) - getWidth() / 2 + cellWidth / 2;
            if (scrollMin > i * cellWidth || scrollMax < (i + 1) * cellWidth) {
                scrollPane.getHorizontalScrollBar().setValue(pos);
            }
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_THUMB_LOADED)) {
            int i = (Integer) e.getNewValue();
            int x = (i % seq.size()) * (cellWidth);
            canvas.repaint(new Rectangle(x, 0, cellWidth, cellSize));
            canvas.repaintSoon();
        } else if (e.getPropertyName().equals(WalkImageSequence.PROP_SEQUENCE_CHANGED)) {
            sequenceChanged();
        }
    }

    private class Canvas extends JPanel implements Scrollable {

        Canvas() {
            repaintTimer = new javax.swing.Timer( 300, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    repaint();
                }
            });
            repaintTimer.setRepeats(false);
        }

        // kludge to work around repainting problem
        javax.swing.Timer repaintTimer;

        private void repaintSoon(  ) {
            repaintTimer.restart();
        }
        
        @Override
        public void paintComponent(Graphics g1) {
            boolean useSquished= true;

            super.paintComponent(g1);
            Graphics2D g2 = (Graphics2D) g1;
            g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );

            Rectangle bounds = g2.getClipBounds();
            //cellSize = this.getHeight();

            if (seq == null) {
                return;
            }

            int cellWidth = (cellSize / HEIGHT_WIDTH_RATIO);
            int i = bounds.x / cellWidth;
            int imax = Math.min(seq.size() - 1, (bounds.x + bounds.width) / cellWidth);

            //double pp= perspective ? 0.05 : 0.0;
            double pp= perspective ? ( useSquished ? 0.50 : 0.05 ) : 0.0;
            double sh= useSquished ? 1.0 : HEIGHT_WIDTH_RATIO;  // scale horizontal

            //System.out.printf("First: %d, Last: %d%n", i, imax);
            for (; i <= imax; i++) {
                if (i == seq.getIndex()) {
                    g2.setColor(java.awt.Color.orange);
                    g2.fillRect(i * cellWidth, 0, cellWidth, cellSize);
                }

                if ( seq.imageAt(i).getStatus()==WalkImage.Status.MISSING ) {
                    continue;
                }

                //g2.draw(new Ellipse2D.Double(i*cellSize+2, 2, cellSize-4, cellSize-4));
                BufferedImage thumb = useSquished ? seq.imageAt(i).getSquishedThumbnail(!scrollPane.getVerticalScrollBar().getValueIsAdjusting()) :  seq.imageAt(i).getThumbnail(!scrollPane.getVerticalScrollBar().getValueIsAdjusting());
                if (thumb != null) {
                    double s = Math.min((double) (cellSize - 4) / thumb.getWidth(), (double) (cellSize - 4) / thumb.getHeight());
                    if (s < 1.0) {
                        int w = (int) (s * thumb.getWidth()/sh );
                        int h = (int) (s * thumb.getHeight());
                        BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, w, h, 0, 1, 1, pp, true);
                        thumb = resizeOp.filter(thumb, null);
                    } else {
                        int w = (int) (  thumb.getWidth()/sh );
                        int h = (int) (  thumb.getHeight());
                        BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, w, h, 0, 1, 1, pp, true);
                        thumb = resizeOp.filter(thumb, null);
                    }
                } else {
                    thumb = loadingImage;
                    double s = Math.min((double) (cellSize - 4) / thumb.getWidth(), (double) (cellSize - 4) / thumb.getHeight());
                    if (s > 1.0) s = 1.0;
                    int w = (int) (s * thumb.getWidth() / HEIGHT_WIDTH_RATIO);
                    int h = (int) (s * thumb.getHeight());

                    BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, w, h, 0, 1, 1, pp, true);
                    thumb = resizeOp.filter(thumb, null);
                }
                int imgX= i * cellWidth + (cellWidth - thumb.getWidth()) / 2;
                int imgY= (cellSize - thumb.getHeight()) / 2;
                g2.drawImage(thumb, imgX, imgY, null);
                if (PngWalkTool1.isQualityControlEnabled() && seq.getQualityControlSequence()!=null ) {
                    paintQualityControlIcon( i, g2, imgX, imgY, cellSize>300 );
                }
            }
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle arg0, int arg1, int arg2) {
            return cellSize / HEIGHT_WIDTH_RATIO;
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
