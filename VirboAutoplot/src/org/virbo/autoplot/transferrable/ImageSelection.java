package org.virbo.autoplot.transferrable;
        
/*
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
import java.awt.Image;
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

public class ImageSelection implements Transferable {
    
    private static final DataFlavor flavors[] =
    {DataFlavor.imageFlavor};
    
    private Image image;
    
    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY;
    }
    
    public void setImage( Image i ) {
        this.image= i;
    }
    
    public boolean canImport(JComponent comp, DataFlavor
            flavor[]) {
        if (!(comp instanceof JLabel) ||
                (comp instanceof AbstractButton)) {
            return false;
        }
        for (int i=0, n=flavor.length; i<n; i++) {
            if (flavor.equals(flavors[0])) {
                return true;
            }
        }
        return false;
    }
    
    public Transferable createTransferable(JComponent
            comp) {
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
    
    public boolean importData(JComponent comp,
            Transferable t) {
        ImageIcon icon = null;
        try {
            if (t.isDataFlavorSupported(flavors[0])) {
                image = (Image)t.getTransferData(flavors[0]);
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
        } catch (IOException ignored) {
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
        return flavors;
    }
    
    public boolean isDataFlavorSupported(DataFlavor
            flavor) {
        return flavor.equals(flavors[0]);
    }
}
