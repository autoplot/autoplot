/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import java.util.LinkedHashMap;
import java.util.Map;
import org.virbo.cefdatasource.CefReaderHeader.GlobalStruct;
import org.virbo.cefdatasource.CefReaderHeader.ParamStruct;

/**
 *
 * @author jbf
 */
public class Cef {

    int error = 0;
    int nglobal = 0;
    int nparam = 0;
    int nrec = 0;
    byte eor = 10;
    String dataUntil;
    String fileName;
    String fileFormatVersion;
    Map<String, ParamStruct> parameters = new LinkedHashMap<String, ParamStruct>();
    Map<String, GlobalStruct> globals = new LinkedHashMap<String, GlobalStruct>();
}
