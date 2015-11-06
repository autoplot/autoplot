/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;
 
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ColorPickerRobot extends JFrame
    implements MouseListener, MouseMotionListener {

    JPanel image_panel;
    Dimension screen_size;
    JComponent comp = null;
    Image background_image = null;
    Robot robot;
    JLabel label;

    public ColorPickerRobot(JComponent comp) {
        // get the screen dimensions
        screen_size = Toolkit.getDefaultToolkit().getScreenSize();

        // setup the frame (this)
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.comp = comp;
        this.setUndecorated(true);
        this.setSize(screen_size.width, screen_size.height);

        // set up the panel that holds the screenshot
        image_panel = new JPanel() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(background_image,0,0,null);
            }
        };
        image_panel.setPreferredSize(screen_size);
        image_panel.setLayout(null);
        this.getContentPane().add(image_panel);

        // setup the display label
        label = new JLabel("Selected Color");
        label.setOpaque(true);
        label.setSize(100,100);
        image_panel.add(label);
        label.setLocation((int)screen_size.getWidth()/2 - 50,
            (int)screen_size.getHeight()/2 - 50);
    }

    public void show() {
        try {
            // make the screenshot before showing the frame
            Rectangle rect = new Rectangle(0,0,
                 (int)screen_size.getWidth(),
                 (int)screen_size.getHeight());
            this.robot = new Robot();
            background_image = robot.createScreenCapture(rect);
            super.show();
        } catch (AWTException ex) {
            System.out.println("exception creating screenshot:");
            ex.printStackTrace();
        }
    }


    // update both the display label and the component that was passed in
    public void setSelectedColor(Color color) {
        comp.setBackground(color);
        label.setBackground(color);
    }
    // update the selected color on mouse press, dragged, and release
    public void mousePressed(MouseEvent evt) {
        setSelectedColor(robot.getPixelColor(evt.getX(), evt.getY()));
    }
    public void mouseDragged(MouseEvent evt) {
        setSelectedColor(robot.getPixelColor(evt.getX(), evt.getY()));
    }
    // for released we want to hide the frame as well
    public void mouseReleased(MouseEvent evt) {
        setSelectedColor(robot.getPixelColor(evt.getX(),evt.getY()));
        this.setVisible(false);
    }


    // no-ops for the rest of the mouse event listener
    public void mouseClicked(MouseEvent evt) { }
    public void mouseEntered(MouseEvent evt) { }
    public void mouseExited(MouseEvent evt) { }
    public void mouseMoved(MouseEvent evt) { }



    public static void main(String[] args) {
        JFrame frame = new JFrame("Color Chooser Hack");
        final JButton button = new JButton("Click to choose a color");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                JFrame frame = new ColorPickerRobot(button);
                frame.show();
            }
        });

        frame.getContentPane().add(button);
        frame.pack();
        frame.setVisible(true);
    }

}



/*   Swing Hacks
 *   Tips and Tools for Killer GUIs
 * By Joshua Marinacci, Chris Adamson
 *   First Edition June 2005
 *   Series: Hacks
 *   ISBN: 0-596-00907-0
 *   http://www.oreilly.com/catalog/swinghks/
 */

 