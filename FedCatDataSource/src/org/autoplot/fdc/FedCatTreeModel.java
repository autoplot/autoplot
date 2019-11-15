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

package org.autoplot.fdc;

import java.time.ZonedDateTime;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.das2.catalog.DasDirNode;
import org.das2.catalog.DasNode;
import org.das2.util.LoggerManager;

/**
 *
 * @author cwp
 */
public class FedCatTreeModel extends DefaultTreeModel
{
	private static final Logger LOGGER = LoggerManager.getLogger("apdss.dc");
	
	private static class DasCatTreeNode extends DefaultMutableTreeNode
	{
		DasNode node;
		DasCatTreeNode(DasNode node){
			this.node = node;
		}
		
		@Override
		public String toString(){
			String sRet = "";
			if(node.name() != null) sRet = "<html><b>"+node.name()+"</b>";
			String s = node.prop("title").str();  //<-- FIXME: triggers network activity
			if(s != null) sRet += " "+s;
			return sRet;
		}
	}
	
	public FedCatTreeModel(DasNode dasRoot)
	{
		super(new DasCatTreeNode(dasRoot), true);  // true = not all nodes can have children
	}
	
	@Override
	public boolean isLeaf(Object treenode){
		DasNode node = ((DasCatTreeNode)treenode).node;
		return ! node.isDir();
	}
	
	@Override
	public int getChildCount(Object parent){
		DasNode node = ((DasCatTreeNode)parent).node;
		if(node.isDir()){
			DasDirNode dir = (DasDirNode)node;
			String aIds[] = dir.list();
			return aIds.length;
		}
		return 0;
	}
	
	@Override
	public Object getChild(Object parent, int index) {
		DasNode node = ((DasCatTreeNode)parent).node;
		if(!node.isDir()) return null;
		DasDirNode dir = (DasDirNode)node;
		String aIds[] = dir.list();
		if(index >= aIds.length) return null;
		
		return new DasCatTreeNode(dir.get(aIds[index]));
    }
	
	@Override
	public int getIndexOfChild(Object parent, Object child)
	{
		DasNode dnTmp = ((DasCatTreeNode)parent).node;
		if(!dnTmp.isDir()) return -1;
		DasDirNode dir = (DasDirNode)dnTmp;
		
		DasNode node = ((DasCatTreeNode)child).node;
		String aIds[] = dir.list();
		
		for(int i = 0; i < aIds.length; ++i){
			if(dir.get(aIds[i]) == node)
				return i;
		}
		
		return -1;
	}
}
