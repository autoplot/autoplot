package test;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JFrame;
import static javax.swing.JFrame.EXIT_ON_CLOSE;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;

/**
 * Taken from www.21doc.net, with minor tweaks to clarify.
 * @author jbf
 */
public class PrimesSwingWorker {

    static class PrimeCalculator extends SwingWorker {

        private final JTextArea intermediateJTextArea; // displays found primes
        private final JButton getPrimesJButton;
        private final JButton cancelJButton;
        private final JLabel statusJLabel; // displays status of calculation
        private final boolean[] isPrime; // boolean array for finding primes

        // constructor//from   ww w. 2 1d o c . n et
        public PrimeCalculator(int max, JTextArea intermediateJTextArea,
                JLabel statusJLabel, JButton getPrimesJButton, JButton cancelJButton) {
            this.intermediateJTextArea = intermediateJTextArea;
            this.statusJLabel = statusJLabel;
            this.getPrimesJButton = getPrimesJButton;
            this.cancelJButton = cancelJButton;
            isPrime = new boolean[max];

            Arrays.fill(isPrime, true); // initialize all primes elements to true
        }

        @Override
        public Integer doInBackground() {
            int count = 0; // the number of primes found
            for (int i = 2; i < isPrime.length; i++) {
                if (isCancelled()) {
                    return count;
                } else {
                    setProgress(100 * (i + 1) / isPrime.length);

                    try {
                       Thread.sleep(5); // slow it down a little
                    } catch (InterruptedException ex) {
                        statusJLabel.setText("Worker thread interrupted");
                        return count;
                    }

                    if (isPrime[i]) {
                        publish(i); // make i available for display in prime list
                        ++count;

                        for (int j = 2*i; j < isPrime.length; j += i) { // any multiple of this is not a prime.
                            isPrime[j] = false; 
                        }
                    }
                }
            }
            return count;
        }

        @Override
        protected void process(List publishedVals) {
            for (int i = 0; i < publishedVals.size(); i++) {
                intermediateJTextArea.append(publishedVals.get(i) + "\n");
            }
        }

        @Override
        protected void done() {
            getPrimesJButton.setEnabled(true); // enable Get Primes button
            cancelJButton.setEnabled(false); // disable Cancel button

            try {
                statusJLabel.setText("Found " + get() + " primes.");
            } catch (InterruptedException | ExecutionException | CancellationException ex) {
                statusJLabel.setText(ex.getMessage());
            }
        }
    }

    public final static class Main extends JFrame {

        private final JTextField highestPrimeJTextField = new JTextField();
        private final JButton getPrimesJButton = new JButton("Get Primes");
        private final JTextArea displayPrimesJTextArea = new JTextArea();
        private final JButton cancelJButton = new JButton("Cancel");
        private final JProgressBar progressJProgressBar = new JProgressBar();
        private final JLabel statusJLabel = new JLabel();
        private PrimeCalculator calculator;

        public Main() {
            super("Finding Primes with SwingWorker");
            setLayout(new BorderLayout());
            JPanel northJPanel = new JPanel();
            northJPanel.add(new JLabel("Find primes less than: "));
            highestPrimeJTextField.setColumns(5);
            northJPanel.add(highestPrimeJTextField);
            getPrimesJButton.addActionListener(e -> {
                progressJProgressBar.setValue(0); // reset JProgressBar
                displayPrimesJTextArea.setText(""); // clear JTextArea
                statusJLabel.setText(""); // clear JLabel

                int number; // search for primes up through this value

                try {
                    // get user input
                    number = Integer.parseInt(highestPrimeJTextField.getText());
                } catch (NumberFormatException ex) {
                    statusJLabel.setText("Enter an integer.");
                    return;
                }
                calculator = new PrimeCalculator(number, displayPrimesJTextArea,
                        statusJLabel, getPrimesJButton, cancelJButton);
                calculator.addPropertyChangeListener( e1 -> {
                    if (e1.getPropertyName().equals("progress")) {
                        int newValue = (Integer) e1.getNewValue();
                        progressJProgressBar.setValue(newValue);
                    }
                } );

                getPrimesJButton.setEnabled(false);
                cancelJButton.setEnabled(true);

                calculator.execute();
            } );
            northJPanel.add(getPrimesJButton);
            displayPrimesJTextArea.setEditable(false);
            add(new JScrollPane(displayPrimesJTextArea,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));

            JPanel southJPanel = new JPanel(new GridLayout(1, 3, 10, 10));
            cancelJButton.setEnabled(false);
            cancelJButton.addActionListener(e -> calculator.cancel(true));
            southJPanel.add(cancelJButton);
            progressJProgressBar.setStringPainted(true);
            southJPanel.add(progressJProgressBar);
            southJPanel.add(statusJLabel);

            add(northJPanel, BorderLayout.NORTH);
            add(southJPanel, BorderLayout.SOUTH);
            setSize(350, 300);
            setVisible(true);
        }
        
    }

    public static void main(String[] args) {
        Main application = new Main();
        application.setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

}
