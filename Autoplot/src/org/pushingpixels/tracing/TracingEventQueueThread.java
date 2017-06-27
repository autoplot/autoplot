/**
 * 
 */
package org.pushingpixels.tracing;

import java.awt.AWTEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

class TracingEventQueueThread extends Thread {

    private long thresholdDelay;
    private Map<AWTEvent, Long> eventTimeMap;

    public TracingEventQueueThread(long thresholdDelay) {
        this.thresholdDelay = thresholdDelay;
        this.eventTimeMap = new HashMap<AWTEvent, Long>();
    }

    public synchronized void eventDispatched(AWTEvent event) {
        this.eventTimeMap.put(event, System.currentTimeMillis());
    }

    public synchronized void eventProcessed(AWTEvent event) {
        this.checkEventTime(event, System.currentTimeMillis(),
                this.eventTimeMap.get(event));
        this.eventTimeMap.put(event, null);
    }

    private void checkEventTime(AWTEvent event, long currTime, long startTime) {
        boolean verbose = true;
        long currProcessingTime = currTime - startTime;
        if (currProcessingTime >= this.thresholdDelay) {
            System.err.println("Event [" + event.hashCode() + "] "
                    + event.getClass().getName()
                    + " is taking too much time on EDT (" + currProcessingTime
                    + ")");
            Map<Thread, StackTraceElement[]> thrs = Thread.getAllStackTraces();
            for ( Entry<Thread, StackTraceElement[]> e : thrs.entrySet() ) {
                if (e.getKey().getName().startsWith("AWT-EventQueue")) {
                    StackTraceElement[] stack = e.getValue();
                    for (StackTraceElement stackEntry : stack) {
                        if (verbose) {
                            System.err.println("\t" + stackEntry.getClassName()
                                    + "." + stackEntry.getMethodName() + " ["
                                    + stackEntry.getLineNumber() + "]");
                        } else {
                            if (stackEntry.getClassName().contains("autoplot") || stackEntry.getClassName().contains("das2")) {
                                System.err.println("\t" + stackEntry.getClassName()
                                        + "." + stackEntry.getMethodName() + " ["
                                        + stackEntry.getLineNumber() + "]");
                                break;
                            }
                        }
                    }
                }
            }

        }
    }

    @Override
    public void run() {
        while (true) {
            long currTime = System.currentTimeMillis();
            synchronized (this) {
                for (Map.Entry<AWTEvent, Long> entry : this.eventTimeMap.entrySet()) {
                    AWTEvent event = entry.getKey();
                    if (entry.getValue() == null) {
                        continue;
                    }
                    long startTime = entry.getValue();
                    this.checkEventTime(event, currTime, startTime);
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
            }
        }
    }
}
