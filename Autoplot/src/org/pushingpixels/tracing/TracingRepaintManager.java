package org.pushingpixels.tracing;

import javax.swing.JComponent;
import javax.swing.RepaintManager;

public class TracingRepaintManager extends RepaintManager {
	@Override
	public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
		try {
			throw new Exception();
		} catch (Exception exc) {
			StringBuffer sb = new StringBuffer();
			StackTraceElement[] stack = exc.getStackTrace();
			int count = 0;
			for (StackTraceElement stackEntry : stack) {
				if (count++ > 8)
					break;
				sb.append("\t");
				sb.append(stackEntry.getClassName() + ".");
				sb.append(stackEntry.getMethodName() + " [");
				sb.append(stackEntry.getLineNumber() + "]");
				sb.append("\n");
			}
			System.out.println("**** Repaint stack ****");
			System.out.println(sb.toString());
		}

		super.addDirtyRegion(c, x, y, w, h);
	}
}
