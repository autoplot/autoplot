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
    private final Map<Integer, Color> outputColors = new LinkedHashMap<>();
    private final Map<Integer, Boolean> gotEoln = new LinkedHashMap<>();

    private final Color fill = new Color(255, 249, 215, 235);
    private final Color textColor = new Color(45, 45, 45);

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
    public void setOutput(int line, String text, Color color) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setOutput(line, text, color));
            return;
        }
        if (line < 1) {
            return;
        }
        if (text == null) {
            output.remove(line);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        String ttext = text.trim();
        if (text.isEmpty()) {
            return;
        }
        StringBuilder b = new StringBuilder();
        output.put(line, b);
        outputColors.put(line, color);
        b.append(ttext);
        if (b.length() > 2000) {
            b.delete(0, b.length() - 2000);
        }
        repaint();
    }

    /**
     * 1-based source line. May be called from a script execution thread.
     */
    public void setOutput(int line, String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setOutput(line, text));
            return;
        }
        if (line < 1) {
            return;
        }
        if (text == null) {
            output.remove(line);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        String ttext = text.trim();
        if (text.isEmpty()) {
            return;
        }
        StringBuilder b = new StringBuilder();
        output.put(line, b);
        outputColors.put(line, textColor);
        b.append(ttext);
        if (b.length() > 2000) {
            b.delete(0, b.length() - 2000);
        }
        repaint();
    }
    
    public void appendOutput(int line, String text) {
        appendOutput(line,text,this.textColor);
    }
    
    /**
     * 1-based source line. May be called from a script execution thread.
     */
    public void appendOutput(int line, String text, Color textColor) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendOutput(line, text));
            return;
        }
        if (line < 1 || text == null || text.isEmpty()) {
            return;
        }
        
        StringBuilder b = output.get(line);
        if ( Boolean.TRUE.equals(gotEoln.get(line)) ) {
            b= null;
            gotEoln.put(line,Boolean.TRUE);
        }
        
        if (b == null) {
            b = new StringBuilder();
            output.put(line, b);
        }
        b.append(text);
        if (text.endsWith("\n") ) {
            gotEoln.put(line,Boolean.TRUE);
        }
        
        if (b.length() > 2000) {
            b.delete(0, b.length() - 2000);
        }
        outputColors.put(line, textColor);
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

    private void drawString(Graphics2D g, String text, String originalText, int x, int y) {
        Color tc = g.getColor();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + 8, y + 6 + fm.getAscent());
        if (text.length() < originalText.length()) {
            int len = fm.stringWidth(text);
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("...", x + 8 + len, y + 6 + fm.getAscent());
            g.setColor(tc);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Element root = editor.getDocument().getDefaultRootElement();
            FontMetrics editorFm = g.getFontMetrics(editor.getFont());
            FontMetrics fm = g.getFontMetrics(g.getFont());
            int bubbleWidth = Math.min(280, Math.max(100, getWidth() / 2));
            
            int lastLine=-1;
            
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
                    String s = entry.getValue().toString().replace('\r', ' ').trim();
                    String[] lines = s.split("\n", -1);
                    int count = Math.min(5, lines.length);
                    int h = count * fm.getHeight() + 12;
                    if (y + h < 0 || y > getHeight()) {
                        continue;
                    }
                    if ( index==lastLine+1 ) {
                        g.setClip(new Rectangle(x,y+fm.getHeight()-(int)(fm.getAscent()*0.5), bubbleWidth+1, h+1) );
                    } else {
                        g.setClip( null );
                    }
                    lastLine= index;
                    
                    g.setColor(fill);
                    g.fillRoundRect(x, y, bubbleWidth, h, 12, 12);
                    g.setColor(new Color(166, 135, 67));
                    g.drawRoundRect(x, y, bubbleWidth, h, 12, 12);
                    Color tc = outputColors.get(entry.getKey());
                    g.setColor(tc);
                    int ellipsesLen = fm.stringWidth("...");
                    for (int j = 0; j < count; j++) {
                        String text = lines[j];
                        int len = fm.stringWidth(text);
                        if (len > (bubbleWidth - 16) && text.length() > 1) {
                            while (len > (bubbleWidth - 16 - ellipsesLen) && text.length() > 1) {
                                text = text.substring(0, text.length() - 1);
                                len = fm.stringWidth(text);
                            }
                        }
                        drawString(g, text, lines[j], x + 8, y + j * editorFm.getHeight());

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
