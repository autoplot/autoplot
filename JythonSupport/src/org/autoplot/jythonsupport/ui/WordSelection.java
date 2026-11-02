
package org.autoplot.jythonsupport.ui;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

public class WordSelection {

    public static void install(JTextComponent area) {

        area.getActionMap().put("select-word", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int pos = area.getCaretPosition();
                Document doc = area.getDocument();

                try {
                    String text = doc.getText(0, doc.getLength());
                    if (text.isEmpty()) return;

                    pos = Math.min(pos, text.length() - 1);

                    // Find identifier boundaries.
                    int start = pos;
                    int end = pos;

                    while (start > 0 &&
                            Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                        start--;
                    }

                    while (end < text.length() &&
                            Character.isJavaIdentifierPart(text.charAt(end))) {
                        end++;
                    }

                    // If preceded by "identifier.", include that prefix.
                    while (start > 1 && text.charAt(start - 1) == '.' && Character.isJavaIdentifierPart(
                                        text.charAt(start - 2) ) ) {
                        int prefixEnd = start - 1;
                        int prefixStart = prefixEnd;
                        
                        while (prefixStart > 0 &&
                                Character.isJavaIdentifierPart(
                                        text.charAt(prefixStart - 1))) {
                            prefixStart--;
                        }

                        if (prefixStart < prefixEnd) {
                            start = prefixStart;
                        }

                        // Include empty method-call parentheses.
                        if (end + 1 < text.length()
                                && text.charAt(end) == '(' ) {
                            int i= end;
                            int nparen=1;
                            while ( end + 1 < text.length() && nparen>0 && text.charAt(i) != ')' ) {
                                i= i+1;
                                if ( text.charAt(i)=='(' ) {
                                    nparen++;
                                } else if ( text.charAt(i)==')') {
                                    nparen--;
                                } else if ( text.charAt(i)==10 || text.charAt(i)==13 ) {
                                    break;
                                }
                                if ( nparen>2 ) {
                                    break;
                                }
                            }
                            if ( nparen==0 ) {
                                end= i+1;
                            }
                        }
                    }
                    
                                            // Include empty method-call parentheses.
                    if (end + 1 < text.length()
                            && text.charAt(end) == '(' ) {
                        int i= end;
                        int nparen=1;
                        while ( end + 1 < text.length() && nparen>0 ) {
                            i= i+1;
                            if ( text.charAt(i)=='(' ) {
                                nparen++;
                            } else if ( text.charAt(i)==')') {
                                nparen--;
                            } else if ( text.charAt(i)==10 || text.charAt(i)==13 ) {
                                break;
                            }
                            if ( nparen>2 ) {
                                break;
                            }
                        }
                        if ( nparen==0 ) {
                            end= i+1;
                        }
                    }
                   // text.substring(start, end);
                    area.select(start, end);

                } catch (BadLocationException ex) {
                    // Ignore invalid positions.
                }
            }
        });
    }
}