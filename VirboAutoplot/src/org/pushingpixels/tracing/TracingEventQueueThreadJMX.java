/**
 * 
 */
package org.pushingpixels.tracing;

import java.awt.AWTEvent;
import java.awt.event.InvocationEvent;
import java.lang.management.*;
import java.util.*;

import javax.management.MBeanServer;
import javax.management.ObjectName;

class TracingEventQueueThreadJMX extends Thread {
	private long thresholdDelay;
        private long initTime;

	private Map<AWTEvent, Long> eventTimeMap;

	private ThreadMXBean threadBean;

	public TracingEventQueueThreadJMX(long thresholdDelay) {
                this.initTime= System.currentTimeMillis();

		this.thresholdDelay = thresholdDelay;
		this.eventTimeMap = new HashMap<AWTEvent, Long>();

		try {
			MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
			ObjectName objName = new ObjectName(
					ManagementFactory.THREAD_MXBEAN_NAME);
			Set<ObjectName> mbeans = mbeanServer.queryNames(objName, null);
			for (ObjectName name : mbeans) {
				this.threadBean = ManagementFactory.newPlatformMXBeanProxy(
						mbeanServer, name.toString(), ThreadMXBean.class);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void eventDispatched(AWTEvent event) {
		this.eventTimeMap.put(event, System.currentTimeMillis());
	}

	public synchronized void eventProcessed(AWTEvent event) {
		this.checkEventTime(event, System.currentTimeMillis(),
				this.eventTimeMap.get(event),"B");
		this.eventTimeMap.put(event,null);
	}

	private void checkEventTime(AWTEvent event, long currTime, long startTime, String callSite ) {
                if ( callSite.equals("A") ) {
                    return;
                }
                
		long currProcessingTime = currTime - startTime;
		if (currProcessingTime >= this.thresholdDelay) {
                        long deltaTime= currTime - initTime;
                        String msg= " is taking too much time on EDT (";
                        if ( callSite.equals("B") ) {
                            msg= " took too much time on EDT (";
                        }
			System.err.println( String.format( "@%8.1fs: ", deltaTime/1000. ) +
                                "Event [" + event.hashCode() + "] "
					+ event.getClass().getName()
					+ msg + currProcessingTime
					+ " ms)");
                        if ( event instanceof InvocationEvent ) {
                            System.err.println( "InvocationEvent.paramString: "+((InvocationEvent)event).paramString() );
                        }

                        boolean verbose= true;
			if (this.threadBean != null) {
				long threadIds[] = threadBean.getAllThreadIds();
				for (long threadId : threadIds) {
					ThreadInfo threadInfo = threadBean.getThreadInfo(threadId,
							Integer.MAX_VALUE);
					if (threadInfo.getThreadName().startsWith("AWT-EventQueue")) {
						if ( verbose ) System.err.println(threadInfo.getThreadName() + " / "
								+ threadInfo.getThreadState());
						StackTraceElement[] stack = threadInfo.getStackTrace();
						for (StackTraceElement stackEntry : stack) {
                                                    if ( verbose ) {
							System.err.println("\t" + stackEntry.getClassName()
									+ "." + stackEntry.getMethodName() + " ["
									+ stackEntry.getLineNumber() + "]");
                                                    } else {
                                                        if ( stackEntry.getClassName().contains("autoplot") || stackEntry.getClassName().contains("das2") ) {
                                                            System.err.println("\t" + stackEntry.getClassName()
									+ "." + stackEntry.getMethodName() + " ["
									+ stackEntry.getLineNumber() + "]");
                                                            break;
                                                        }
                                                    }
						}
					}
				}

				long[] deadlockedThreads = threadBean.findDeadlockedThreads();
				if ((deadlockedThreads != null)
						&& (deadlockedThreads.length > 0)) {
					System.err.println("Deadlocked threads:");
					for (long threadId : deadlockedThreads) {
						ThreadInfo threadInfo = threadBean.getThreadInfo(
								threadId, Integer.MAX_VALUE);
						System.err.println(threadInfo.getThreadName() + " / "
								+ threadInfo.getThreadState());
						StackTraceElement[] stack = threadInfo.getStackTrace();
						for (StackTraceElement stackEntry : stack) {
							System.err.println("\t" + stackEntry.getClassName()
									+ "." + stackEntry.getMethodName() + " ["
									+ stackEntry.getLineNumber() + "]");
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
				for (Map.Entry<AWTEvent, Long> entry : this.eventTimeMap
						.entrySet()) {
					AWTEvent event = entry.getKey();
					if (entry.getValue() == null)
						continue;
					long startTime = entry.getValue();
					this.checkEventTime(event, currTime, startTime,"A");
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
			}
		}
	}
}