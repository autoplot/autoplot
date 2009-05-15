/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.DasExceptionHandler;

/**
 *
 * @author jbf
 */
public class PngWalkCanvas extends JPanel {

    public PngWalkCanvas() {
        super();
        addMouseListener(mlistener);
        addMouseWheelListener(wlistener);
    }
    public static final int NOMINAL_HEIGHT = 600;
    public static final int NOMINAL_WIDTH = 800;

    private static Dimension resize(int width, int height, int target) {
        double aspect = 1. * width / height;
        double sqrtAspect = Math.sqrt(aspect);
        return new Dimension((int) (target * sqrtAspect), (int) (target / sqrtAspect));
    }

    private static Point position(Dimension size, int xpos, int ypos) {
        return new Point(xpos - size.width / 2, ypos - size.height / 2);
    }

    private static Rectangle bounds(int xpos, int ypos, int width, int height, int targetWidth, int targetHeight, double aspectFactor,boolean debug) {
        double aspect = 1. * width / height;
        int target = 1. * targetWidth / targetHeight > aspect ? targetHeight : targetWidth;

        if ( debug ) System.err.printf( "tw=%d th=%d rat=%5.2f asp=%5.2f  target=%d\n", targetWidth, targetHeight, 1. * targetWidth / targetHeight, aspect, target );
        double sqrtAspect = Math.sqrt(aspect * aspectFactor);
        int w = (int) (target * sqrtAspect);
        int h = (int) (target / sqrtAspect);
        int x = xpos - w / 2;
        int y = ypos - h / 2;
        return new Rectangle(x, y, w, h);
    }

    MouseWheelListener wlistener= new MouseWheelListener() {
        public void mouseWheelMoved(MouseWheelEvent e) {
            int current = getCurrentIndex();
            current += e.getWheelRotation();
            if (current < 0) current = 0;
            if (current >= images.size()) current = images.size();
            setCurrentIndex(current);
        }
    };

    MouseAdapter mlistener = new MouseAdapter() {

        public void mouseClicked(MouseEvent e) {
            int selected = -1;
            for (Entry<Rectangle, Integer> entry : imagebounds.entrySet()) {
                if (entry.getKey().contains(e.getPoint())) {
                    selected = entry.getValue();
                }
            }
            if (selected != -1) {
                if (mode == DisplayMode.day) {
                    setMode(lastMode);
                } else {
                    if ( getCurrentIndex()==selected || e.getClickCount()>1 ) {
                        setMode(DisplayMode.day);
                    }
                    setCurrentIndex(selected);
                }
            }
        }

    };

    // keep track of the last drawn image to avoid flicker
    Image lastImage;

    @Override
    protected synchronized void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g = (Graphics2D) g1;

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int CELL_PAD = 30;

        imagebounds = new HashMap<Rectangle, Integer>();

        if ( exception!=null ) {
            g.drawString( exception.toString(), 100, 100 );
            return;
        }
        if ( images.size()==0 ) {
            g.drawString( "no matching images found", getWidth()/2 - 100, getHeight()/2 );
            return;
        }
        if (mode == DisplayMode.day) {
            boolean usedLastImage = false;
            Image image = images.get(currentIndex);
            int height = image.getHeight(this);
            int width = image.getWidth(this);
            if (height == -1 || width == -1) {
                image = lastImage == null ? image : lastImage;
                height = image.getHeight(this);
                width = image.getWidth(this);
                usedLastImage = true;
            }
            if (height == -1) height = NOMINAL_HEIGHT;
            if (width == -1) width = NOMINAL_WIDTH;

            Rectangle bounds = bounds(getWidth() / 2, getHeight() / 2, width, height, getWidth() * 80 / 100, getHeight() * 80 / 100, 1.0,false);
            if (g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                lastImage = image;
            }
            g.draw(bounds);
            maybeTimeStamp( g, bounds, ranges.get(currentIndex) );

            if (usedLastImage) {
                drawMomentStr(g,bounds);
            }

            imagebounds.put(bounds, currentIndex);

        } else if (mode == DisplayMode.week) {
            int minIndex = currentIndex - 3;
            int maxIndex = currentIndex + 3;

            int columns = 7;
            int targetSize = (getWidth() - CELL_PAD) / columns - CELL_PAD;

            int ylow=0;
            for (int index = minIndex; index <= maxIndex; index++) {
                if (index < 0) continue;
                if (index >= images.size()) continue;

                Image image = images.get(index);
                int height = image.getHeight(this);
                if (height == -1) height = NOMINAL_HEIGHT;
                int width = image.getWidth(this);
                if (width == -1) width = NOMINAL_WIDTH;

                int irow = 0;
                int icol = (index - minIndex) % columns;

                int x = CELL_PAD + targetSize / 2 + (CELL_PAD + targetSize) * icol;
                int y = CELL_PAD + targetSize / 2;

                Rectangle bounds = bounds(x, y, width, height, targetSize, targetSize, 1.0,false);
                if (g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                }
                g.draw(bounds);
                ylow= Math.max( bounds.y+bounds.height, ylow );

                maybeTimeStamp(g, bounds, ranges.get(index) );

                imagebounds.put(bounds, index);

            }

            boolean usedLastImage= false;
            Image image = images.get(currentIndex);
            int height = image.getHeight(this);
            int width = image.getWidth(this);
            if (height == -1 || width == -1) {
                image = lastImage == null ? image : lastImage;
                height = image.getHeight(this);
                width = image.getWidth(this);
                usedLastImage = true;
            }
            if (height == -1) height = NOMINAL_HEIGHT;
            if (width == -1) width = NOMINAL_WIDTH;

            //int widthLimit= getWidth()-CELL_PAD*2;
            int widthLimit= 100000;
            int heightLimit = getHeight() - ylow - CELL_PAD;
            Rectangle size =  bounds( 0, 0, width, height, widthLimit,heightLimit, 1.0 ,false);

            Rectangle bounds = bounds( getWidth() / 2, ylow + Math.max( size.height, getHeight() - ylow ) / 2,
                    width, height,
                    widthLimit, heightLimit, 1.0, false );
            if (g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                lastImage = image;
            }
            g.draw(bounds);

            maybeTimeStamp( g, bounds, ranges.get(currentIndex) );
            if (usedLastImage) {
                drawMomentStr(g,bounds);
            }
            imagebounds.put(bounds, currentIndex);


        } else if (mode == DisplayMode.coverFlow) {

            int xm = getWidth() / 2;
            int y = Math.max(200, getHeight() / 2);

            int targetSize = 100;
            int columns = (xm - 200) * 4 / targetSize;

            int minIndex = currentIndex - columns;
            int maxIndex = currentIndex + columns;

            for ( int i = 0; i< columns*2 + 1; i++ ) {

                int index;
                int d= columns - i/2;
                if ( i%2==0 ) {
                    index= currentIndex - d;
                } else {
                    index= currentIndex + d;
                }
                if (index < 0) continue;
                if (index >= images.size()) continue;

                boolean usedLastImage= false;
                Image image = images.get(index);
                int height = image.getHeight(this);
                int width = image.getWidth(this);
                if (height == -1 || width == -1) {
                    image = lastImage == null ? image : lastImage;
                    height = image.getHeight(this);
                    width = image.getWidth(this);
                    usedLastImage = true;
                }
                if (height == -1) height = NOMINAL_HEIGHT;
                if (width == -1) width = NOMINAL_WIDTH;

                Rectangle bounds;

                if (index < currentIndex) {
                    int x = xm - 200 + (index - currentIndex) * targetSize / 4;
                    bounds = bounds(x, y, width, height, targetSize, targetSize, 0.1,false);
                    if (!g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                    }
                    g.draw(bounds);
                    if ( usedLastImage ) drawMomentStr( g, bounds);

                } else if (index == currentIndex) {
                    int x = xm;
                    bounds = bounds(x, y, width, height, 360, height, 1.0,false);
                    if ( g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                        lastImage= image;
                    }
                    g.draw(bounds);
                    maybeTimeStamp( g, bounds, ranges.get(index) );
                    if ( usedLastImage ) drawMomentStr( g, bounds);
                } else {
                    int x = xm + 200 + (index - currentIndex) * targetSize / 4;
                    bounds = bounds(x, y, width, height, targetSize, targetSize, 0.1,false);
                    if ( g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                    }
                    g.draw(bounds);
                    if ( usedLastImage ) drawMomentStr( g, bounds);
                }

                imagebounds.put(bounds, index);

            }

        } else if (mode == DisplayMode.month) {
            if (currentIndex < minIndex) {
                minIndex -= 35;
                maxIndex = minIndex + 35;
            }
            if (currentIndex >= maxIndex) {
                maxIndex += 35;
                minIndex = maxIndex - 35;
            }

            int columns = 7;
            int rows = (maxIndex - minIndex) / columns;

            int targetWidth = (getWidth() - CELL_PAD) / columns - CELL_PAD;
            int targetHeight = (getHeight() - CELL_PAD) / rows - CELL_PAD;
            double aspMe = (1. * getWidth() / getHeight());
            double aspTarget = ((1. * (targetWidth + CELL_PAD) * columns) / ((targetHeight + CELL_PAD) * rows));
            int yHeight = aspMe > aspTarget ? targetWidth : targetHeight;

            int ylow = 0;
            int nextYLow = 0;

            for (int index = minIndex; index <= maxIndex; index++) {
                if (index < 0) continue;
                if (index >= images.size()) continue;

                Image image = images.get(index);
                int height = image.getHeight(this);
                if (height == -1) height = NOMINAL_HEIGHT;
                int width = image.getWidth(this);
                if (width == -1) width = NOMINAL_WIDTH;

                int irow = (index - minIndex) / columns;
                int icol = (index - minIndex) % columns;

                ylow = ( targetHeight + CELL_PAD ) * irow;

                Rectangle bounds = bounds(0, 0, width, height, targetWidth, targetHeight, 1.0,false);

                int x = CELL_PAD + targetWidth / 2 + (CELL_PAD + targetWidth) * icol;
                int y = ylow + CELL_PAD + targetHeight / 2;

                bounds = bounds(x, y, width, height, targetWidth, targetHeight, 1.0,false);
                nextYLow = Math.max(nextYLow, bounds.y + bounds.height);

                if (index == currentIndex) {
                    g.setColor(Color.darkGray);
                    g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawRect(bounds.x - 5, bounds.y - 5, bounds.width + 10, bounds.height + 10);
                    g.setStroke(new BasicStroke());
                    g.setColor(Color.black);
                }

                if (!g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                }
                g.draw(bounds);
                maybeTimeStamp( g, bounds, ranges.get(index) );

                imagebounds.put(bounds, index);

            }

        }
    }
    List<Image> images = new ArrayList<Image>();
    List<URL> urls = new ArrayList<URL>();
    List<DatumRange> ranges = new ArrayList<DatumRange>();
    Map<Rectangle, Integer> imagebounds;

    private void drawMomentStr(Graphics2D g,Rectangle bounds) {
        g.drawString("moment...", bounds.x + bounds.width / 2 - 30, bounds.y + bounds.height / 2 - 30);
        g.setColor(Color.white);
        g.drawString("moment...", bounds.x + bounds.width / 2 - 32, bounds.y + bounds.height / 2 - 32);
        g.setColor(Color.black);
    }

    private synchronized void maybeTimeStamp(Graphics2D g, Rectangle bounds, DatumRange dr ) {
        int fmh= 14; // font metrics height
        if (dr != null) {
            String rangestr = dr.toString();
            g.drawString(rangestr, bounds.x, bounds.y + bounds.height + fmh );
        }
    }

    private synchronized void updateImages() {
        try {
            ranges = new ArrayList<DatumRange>();
            urls = WalkUtil.getFilesFor(template, timeRange, ranges);
            images = new ArrayList<Image>();
            for (int i = 0; i < urls.size(); i++) {
                images.add(i, getToolkit().createImage(urls.get(i)));
            }
            exception= null;
        } catch ( Exception ex ) {
            DasExceptionHandler.handle(ex);
            DasExceptionHandler.handle(ex);
            exception= ex;
        }
        repaint();
    }

    public enum DisplayMode {

        day, week, month, coverFlow
    }
    protected DisplayMode mode = DisplayMode.week;
    /**
     * when click on image in 1-up, this is the mode to return to.
     */
    private DisplayMode lastMode = DisplayMode.week;
    public static final String PROP_MODE = "mode";

    public DisplayMode getMode() {
        return mode;
    }

    public void setMode(DisplayMode mode) {
        DisplayMode oldMode = this.mode;
        this.mode = mode;
        firePropertyChange(PROP_MODE, oldMode, mode);
        switch (mode) {
            case day:
                lastMode = oldMode;
                setCount(1);
                break;
            case week:
                setCount(7);
                break;
            case coverFlow:
                setCount(7);
                break;
            case month:
                setCount(35);
                break;
            default:
                throw new IllegalArgumentException("bad index");

        }
    }
    protected int count = 7;
    /**
     * number of images displayed.  This is not used, but may be used to
     * control the number of weeks displayed.
     */
    public static final String PROP_COUNT = "count";

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        int oldCount = this.count;
        this.count = count;
        repaint();
        firePropertyChange(PROP_COUNT, oldCount, count);
    }
    protected int currentIndex = 0;
    /**
     * index of first image
     */
    public static final String PROP_CURRENTINDEX = "currentIndex";

    public int getCurrentIndex() {
        return currentIndex;
    }

    public synchronized void setCurrentIndex(int currentIndex) {
        int oldCurrentIndex = this.currentIndex;
        String oldItem= getCurrentItem();
        if (currentIndex >= 0 && currentIndex < this.images.size()) {
            this.currentIndex = currentIndex;
            repaint();
            firePropertyChange(PROP_CURRENTINDEX, oldCurrentIndex, currentIndex);
            firePropertyChange(PROP_CURRENTITEM, oldItem, getCurrentItem() );
        }
    }

    // for 35-up;
    private int minIndex = 0;
    private int maxIndex = 0;


    private Exception exception=null;

    protected String template = null;
    /**
     * template string identifying the images
     * /home/jbf/pngs/$Y$m$d.png
     */
    public static final String PROP_TEMPLATE = "template";

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        template= template.trim();
        if ( template.startsWith("file:///") ) {
            template= "file:/" + template.substring(8);
        }
        String oldTemplate = this.template;
        this.template = template;
        firePropertyChange(PROP_TEMPLATE, oldTemplate, template);
        updateImages();
    }

    public static final String PROP_CURRENTITEM = "currentItem";

    public String getCurrentItem() {
        if (urls != null) {
            return urls.get(currentIndex).toString();
        } else {
            return null;
        }
    }

    public synchronized void setCurrentItem(String currentItem) {
        try {
            String oldCurrentItem = getCurrentItem();
            int idx = this.urls.indexOf(new URL(currentItem));
            if (idx != -1) {
                setCurrentIndex(idx);
                firePropertyChange(PROP_CURRENTITEM, oldCurrentItem, currentItem);
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(PngWalkCanvas.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    protected String timeRange = "";
    public static final String PROP_TIMERANGE = "timeRange";

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        String oldTimeRange = this.timeRange;
        this.timeRange = timeRange;
        if ( timeRange.trim().length()>0 ) {
            try {
                DatumRangeUtil.parseTimeRange(timeRange);
            } catch (ParseException ex) {
                this.timeRange= oldTimeRange;
            }
        }
        firePropertyChange(PROP_TIMERANGE, oldTimeRange, timeRange);
        updateImages();
    }

}
