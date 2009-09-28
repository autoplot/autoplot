package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 *
 * @author ed
 */
public class PngWalkThumbPanel extends javax.swing.JPanel {

    private static Border plainBorder = BorderFactory.createEmptyBorder(1, 4, 1, 4);
    private TitledBorder plainBorderCap = BorderFactory.createTitledBorder(plainBorder, "", TitledBorder.CENTER, TitledBorder.ABOVE_BOTTOM);
    private static Border selectedBorder = BorderFactory.createLineBorder(Color.ORANGE, 1);
    private TitledBorder selectedBorderCap = BorderFactory.createTitledBorder(selectedBorder, "", TitledBorder.CENTER, TitledBorder.ABOVE_BOTTOM);
    private boolean showCaption = false;
    private boolean selected = true;
    private String captionString = "";
    private WalkImage walkImage;
    private BufferedImage cacheImage;
    private boolean cacheValid = false;

    /** Creates new form PngWalkThumbPanel */
    public PngWalkThumbPanel(WalkImage im) {
        walkImage = im;
        setPreferredSize(new Dimension(200, 200)); //TODO: thumbnail size should be adjustable
        
    }

    @Override
    protected  void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        Insets insets = getBorder().getBorderInsets(this);
        double w = (double) (getWidth() - insets.left - insets.right);
        double h = (double) getHeight();


        if (!cacheValid) {
            BufferedImage i = walkImage.getThumbnail();
            double xfactor = (double) w / (double) i.getWidth();
            double yfactor = (double) h / (double) i.getHeight();
            double s = Math.min(xfactor, yfactor);
            s = Math.min(1.0, s);

            int xs = (int) (i.getWidth() * s);
            int ys = (int) (i.getHeight() * s);

            BufferedImageOp resizeOp = new ScalePerspectiveImageOp(i.getWidth(), i.getHeight(), 0, 0, xs, ys, 0, 0, false);
            cacheImage = resizeOp.filter(i, null);
            cacheValid = true;
        }

        int xpos = (int) (w - cacheImage.getWidth()) / 2 + insets.left;
        int ypos = (int) (h - cacheImage.getHeight()) / 2;

        g2.drawImage(cacheImage, xpos, ypos, null);

    }

    public boolean isShowCaption() {
        return showCaption;
    }

    public void setShowCaption(boolean showCaption) {
        this.showCaption = showCaption;
        setBorderType();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        setBorderType();
    }

    public String getCaptionString() {
        return captionString;
    }

    public void setCaptionString(String captionString) {
        this.captionString = captionString;
        selectedBorderCap.setTitle(captionString);
        plainBorderCap.setTitle(captionString);
    }

    private void setBorderType() {
        if (selected) {
            if (showCaption) {
                setBorder(selectedBorderCap);
            } else {
                setBorder(selectedBorder);
            }
        } else {
            if (showCaption) {
                setBorder(plainBorderCap);
            } else {
                setBorder(plainBorder);
            }
        }
    }
}
