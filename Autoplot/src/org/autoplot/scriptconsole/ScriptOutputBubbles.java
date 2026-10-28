
package org.autoplot.scriptconsole;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

/**
 * Transparent overlay for source-line-associated execution output.
 */
public final class ScriptOutputBubbles extends JComponent {

    private final JTextComponent editor;
    private final JViewport viewport;
    private final Map<Integer, StringBuilder> output = new LinkedHashMap<>();
    private final Color fill = new Color(255, 249, 215, 235);

    public ScriptOutputBubbles(JTextComponent editor, JViewport viewport) {
        this.editor = editor;
        this.viewport = viewport;
        setOpaque(false);
        setFocusable(false);
        viewport.addChangeListener(e -> repaint());
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                repaint();
            }

            public void removeUpdate(DocumentEvent e) {
                repaint();
            }

            public void changedUpdate(DocumentEvent e) {
                repaint();
            }
        });
    }

    /**
     * 1-based source line. May be called from a script execution thread.
     */
    public void setOutput(int line, String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setOutput(line, text));
            return;
        }
        if ( line < 1 ) return;
        if ( text==null ) {
            output.remove(line);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        String ttext= text.trim();
        if (text.isEmpty()) {
            return;
        }
        StringBuilder b= new StringBuilder();
        output.put(line,b);
        b.append(ttext);
        if (b.length() > 2000) {
            b.delete(0, b.length() - 2000);
        }
        repaint();
    }
    
    /**
     * 1-based source line. May be called from a script execution thread.
     */
    public void appendOutput(int line, String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendOutput(line, text));
            return;
        }
        if (line < 1 || text == null || text.isEmpty()) {
            return;
        }
        StringBuilder b = output.get(line);
        if (b == null) {
            b = new StringBuilder();
            output.put(line, b);
        }
        b.append(text);
        if (b.length() > 2000) {
            b.delete(0, b.length() - 2000);
        }
        repaint();
    }

    public void clear() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clear);
            return;
        }
        output.clear();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Element root = editor.getDocument().getDefaultRootElement();
            FontMetrics fm = g.getFontMetrics(editor.getFont());
            int bubbleWidth = Math.min(280, Math.max(100, getWidth() / 2));
            for (Map.Entry<Integer, StringBuilder> entry : output.entrySet()) {
                int index = entry.getKey() - 1;
                if (index >= root.getElementCount()) {
                    continue;
                }
                try {
                    Rectangle r = editor.modelToView(root.getElement(index).getStartOffset());
                    if (r == null) {
                        continue;
                    }
                    Point p = SwingUtilities.convertPoint(editor, r.x, r.y, viewport);
                    int x = Math.max(4, getWidth() - bubbleWidth - 8);
                    int y = p.y + Math.max(0, (r.height - fm.getHeight()) / 2);
                    String s = entry.getValue().toString().replace('\r', ' ');
                    String[] lines = s.split("\n", -1);
                    int count = Math.min(5, lines.length);
                    int h = count * fm.getHeight() + 12;
                    if (y + h < 0 || y > getHeight()) {
                        continue;
                    }
                    g.setColor(fill);
                    g.fillRoundRect(x, y, bubbleWidth, h, 12, 12);
                    g.setColor(new Color(166, 135, 67));
                    g.drawRoundRect(x, y, bubbleWidth, h, 12, 12);
                    Color textColor= new Color(45, 45, 45);
                    g.setColor(textColor);
                    for (int j = 0; j < count; j++) {
                        String text = lines[j];
                        int len= fm.stringWidth(text);
                        while ( len > ( bubbleWidth - 16 ) && text.length() > 1) {
                            text = text.substring(0, text.length() - 1);
                            len= fm.stringWidth(text);
                        }
                        g.drawString(text, x + 8, y + 6 + fm.getAscent() + j * fm.getHeight());
                        if ( text.length()<lines[j].length() ) {
                            g.setColor( Color.LIGHT_GRAY );
                            g.drawString("...", x + len, y + 6 + fm.getAscent() + j * fm.getHeight());
                            g.setColor( textColor );
                        }
                    }
                } catch (BadLocationException ignored) {
                    // Document may have changed since output was captured.
                }
            }
        } finally {
            g.dispose();
        }
    }

}
