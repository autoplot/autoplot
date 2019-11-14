/* This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org/>
 */

package org.das2.catalog;

/** Exception thrown if a path can not be resolved.
 * 
 * These are handled internally by the catalog package if possible, but some 
 * paths just aren't resolvable no matter how many catalog branches are inspected.
 * 
 * @author C. Piker, 2019-11-02
 */
public class DasResolveException extends Exception {
	String path;
	
	// The following constructors are provided by the base class and not repeated here:
	//
	// Exception()
	// Exception(String msg)
	// Exception(Throwable ex)
	// Exception(String msg, Throwable ex)
	/** Construct a das2 catalog resolution exception
	 * @param msg  A general error message
	 * @param sPath The catalog path or sub-path that could not be resolved
	 */
	public DasResolveException(String msg, String sPath){
		super(msg);
		path = sPath;
	}
	
	/** Construct a das2 catalog resolution exception, and attache a cause.
	 * @param msg  A general error message
	 * @param ex   A throwable object that cause the resolution failure
	 * @param sPath The catalog path or sub-path that could not be resolved
	 */
	public DasResolveException(String msg, Throwable ex, String sPath){
		super(msg, ex);
		path = sPath;
	}
}
