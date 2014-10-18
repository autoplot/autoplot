/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.filters;

/**
 * Interface for adding small GUIs to control each of the filters.  For example
 * "|divide(5)" is controlled with a GUI that accepts the float parameter that 
 * might check that the operand is not zero.  These should each implement get
 * and setFilter, and fire off a property change event when the value is changed,
 * so the GUI can be interactively.
 * @author mmclouth
 */
public interface FilterEditorPanel {
    String getFilter();
    void setFilter( String filter );
}
