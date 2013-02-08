package org.virbo.autoplot.transferrable;
        
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.TransferHandler;

/**
 * Transferable for Images.
 * <code>
 * ImageSelection imageSelection = new ImageSelection();
 * DasCanvas c = parent.applicationModel.canvas;
 * Image i = c.getImage(c.getWidth(), c.getHeight());
 * imageSelection.setImage(i);
 * Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
 * clipboard.setContents(imageSelection, ImageSelection.getNullClipboardOwner() )
 * </code>
 * @author jbf
 */
public class ImageSelection implements Transferable {
    
    private static final DataFlavor defaultFlavor = DataFlavor.imageFlavor;
    
    private Image image;
    
    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY;
    }
    
    public void setImage( Image i ) {
        this.image= i;
    }
    
    public boolean canImport(JComponent comp, DataFlavor flavors[]) {
        if (!(comp instanceof JLabel) || (comp instanceof AbstractButton)) {
            return false;
        }
        for (int i=0, n=flavors.length; i<n; i++) {
            if (flavors[i].equals(defaultFlavor)) {
                return true;
            }
        }
        return false;
    }

    public static ClipboardOwner getNullClipboardOwner() {
        return new ClipboardOwner() {
            public void lostOwnership(Clipboard clipboard, Transferable contents) {
                // do nothing
            }
        };
    }

    public Transferable createTransferable(JComponent  comp) {
// Clear
        image = null;
        Icon icon = null;
        
        if (comp instanceof JLabel) {
            JLabel label = (JLabel)comp;
            icon = label.getIcon();
        } else if (comp instanceof AbstractButton) {
            AbstractButton button = (AbstractButton)comp;
            icon = button.getIcon();
        }
        if (icon instanceof ImageIcon) {
            image = ((ImageIcon)icon).getImage();
            return this;
        }
        return null;
    }
    
    public boolean importData(JComponent comp, Transferable t) {
        ImageIcon icon = null;
        try {
            if (t.isDataFlavorSupported(defaultFlavor)) {
                image = (Image)t.getTransferData(defaultFlavor);
                icon = new ImageIcon(image);
            }
            if (comp instanceof JLabel) {
                JLabel label = (JLabel)comp;
                label.setIcon(icon);
                return true;
            } else if (comp instanceof AbstractButton) {
                AbstractButton button = (AbstractButton)comp;
                button.setIcon(icon);
                return true;
            }
            
        } catch (UnsupportedFlavorException ignored) {
            ignored.printStackTrace();
        } catch (IOException ignored) {
            ignored.printStackTrace();;
        }
        return false;
    }
    
// Transferable
    public Object getTransferData(DataFlavor flavor) {
        if (isDataFlavorSupported(flavor)) {
            return image;
        }
        return null;
    }
    
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { defaultFlavor };
    }
    
    public boolean isDataFlavorSupported( DataFlavor flavor ) {
        return flavor.equals(defaultFlavor);
    }
}
