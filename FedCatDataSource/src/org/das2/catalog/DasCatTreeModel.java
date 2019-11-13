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

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 *
 * @author cwp
 */
public class DasCatTreeModel extends DefaultTreeModel
{
	
	private static class DasCatTreeNode extends DefaultMutableTreeNode
	{
		DasNode node;
		DasCatTreeNode(DasNode node){
			this.node = node;
		}
		
		@Override
		public String toString(){
			
			String s2 = "doggy";
			
			String s = (String) node.property("title", node.name(), String.class);
			return s;
		}
	}
	
	public DasCatTreeModel(DasNode dasRoot)
	{
		super(new DasCatTreeNode(dasRoot), true);  // true = not all nodes can have children
		
	}
	
	@Override
	public boolean isLeaf(Object treenode){
		DasNode node = ((DasCatTreeNode)treenode).node;
		return node.isDir();
	}
	
	@Override
	public int getChildCount(Object treenode){
		DasNode node = ((DasCatTreeNode)treenode).node;
		if(node.isDir()){
			DasDirNode dir = (DasDirNode)node;
			String aIds[] = dir.list();
			return aIds.length;
		}
		return 0;
	}
	
	
	
}
