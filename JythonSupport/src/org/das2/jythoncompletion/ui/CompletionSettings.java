/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.das2.jythoncompletion.ui;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.text.JTextComponent;

/**
 * Maintenance of the editor settings related to the code completion.
 *
 * @author Miloslav Metelka
 * @version 1.00
 */

public final class CompletionSettings  {
    
    public static final CompletionSettings INSTANCE = new CompletionSettings();
    
    private CompletionSettings() {
        //Settings.addSettingsChangeListener(this);
    }
    
    public boolean completionAutoPopup() {
        return true;
    }
    
    public int completionAutoPopupDelay() {
        return 300;
    }
    
    public boolean documentationAutoPopup() {
        return true;
    }
    
    public int documentationAutoPopupDelay() {
        return 600;
    }
    
    public Dimension completionPopupMaximumSize() {
        String s= org.das2.jythoncompletion.JythonCompletionProvider.getInstance().settings().getDocumentationPaneSize();
        int i= s.indexOf("x");
        if ( i==-1 ) return new Dimension(640,480);
        try {
            return new Dimension( Integer.parseInt(s.substring(0,i)), Integer.parseInt(s.substring(i+1) ) );
        } catch ( NumberFormatException ex ) {
            return new Dimension(640,480);
        }
    }
    
    public Dimension documentationPopupPreferredSize() {
        return completionPopupMaximumSize();
    }
    
    public Color documentationBackgroundColor() {
        return Color.LIGHT_GRAY;
    }

    public boolean completionInstantSubstitution() {
        return true;
    }
    
    public void notifyEditorComponentChange(JTextComponent newEditorComponent) {
        //this.editorComponentRef = new WeakReference<JTextComponent>(newEditorComponent);
        //clearSettingValues();
    }
    
  /*  public Object getValue(String settingName) {
        Object value;
        synchronized (this) {
            value = settingName2value.get(settingName);
        }
        
        if (value == null) {
            JTextComponent c = editorComponentRef.get();
            if (c != null) {
                Class kitClass = Utilities.getKitClass(c);
                if (kitClass != null) {
                    value = Settings.getValue(kitClass, settingName);
                    if (value == null) {
                        value = NULL_VALUE;
                    }
                }
            }
            
            if (value != null) {
                synchronized (this) {
                    settingName2value.put(settingName, value);
                }
            }
        }
        
        if (value == NULL_VALUE) {
            value = null;
        }
        return value;
    }
    
    public Object getValue(String settingName, Object defaultValue) {
        Object value = getValue(settingName);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }
    
    public void settingsChange(SettingsChangeEvent evt) {
        clearSettingValues();
    }
    
    private synchronized void clearSettingValues() {
        settingName2value.clear();
    }*/
}
