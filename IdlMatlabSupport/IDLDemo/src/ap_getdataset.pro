;+
; ap_getdataset 
; returns a structure qdataset.  The goal of this is to return an IDL structure
; that is as much like a QDataSet as possible.
; created on __DATE__
;-

;+
; returns an IDL structure of a Java Map, or 0.
; keycount is the number of keys in the map. 
;-
function ap_mapToStruct, map, qds, keycount=keycount
   it= (map->keySet())->iterator()
   util= OBJ_NEW( 'IDLjavaObject$Static$org_virbo_ittsupport_idlsupport', 'org.virbo.idlsupport.Util' )
   keycount=0
   if ( it->hasNext() ) then begin
      key= it->next()
      val= map->get(key)
      key= key->toString()
      if ( obj_class( val ) eq 'IDLJAVAOBJECT$JAVA_LANG_STRING' ) then begin
         val= val->toString()
      endif else if ( obj_class( val ) eq 'IDLJAVAOBJECT$JAVA_LANG_DOUBLE' ) then begin
         val= val->doubleValue()
      endif else if ( util->isQDataSet( val ) ) then begin
         val= qds->nameFor( val )
      endif else if ( util->isMap( val ) ) then begin
         val= ap_mapToStruct( val, qds )
      endif
      if ( size( val, /type ) ne 11 ) then begin
        keycount= keycount+1
        result1= create_struct( key, val )
        if ( n_elements( result ) eq 0 ) then result= result1 else result= create_struct( result, result1 )
      endif
   endif
   if ( keycount gt 0 ) then begin
      return, result
   endif else begin
      return, 0
   endelse
end

function ap_getdataset_getProps, qds, n, keycount=keycount
    map= qds->properties(n)
    return, ap_mapToStruct( map, qds, keycount=keycount )
;    for i=0, qds->rank(n)-1 do begin
;       propname= 'DEPEND_'+strtrim(i,2)
;       if ( qds->hasProperty( n, propname ) ) then begin 
;          val= qds->property( n, propname )
;          if ( obj_class( val ) eq 'IDLJAVAOBJECT$JAVA_LANG_STRING' ) then begin
;             val= val->toString()
;          endif else if ( obj_class( val ) eq 'IDLJAVAOBJECT$JAVA_LANG_DOUBLE' ) then begin
;             val= val->doubleValue()
;          endif else if ( qds->isMap( val ) ) then begin
;             val= mapToStruct( val )
;          endif
;          if ( n_elements( result ) eq 0 ) then begin
;             result= create_struct( propname, val )
;          endif else begin
;             result= create_struct( result, create_struct( propname, val ) )  
;          endelse
;       endif
;    endfor
;    moreprops= [ 'UNITS', 'TITLE' 
;    return, result
end


function ap_getdataset, url

    if ( n_elements( url ) eq 0 ) then url= 'file:///media/mini/data.backup/examples/dat/asciiTab2.dat'

    catch, err

    if ( err eq 0 ) then begin

        util= OBJ_NEW( 'IDLjavaObject$Static$org_virbo_ittsupport_idlsupport', 'org.virbo.idlsupport.Util' )

        qds= OBJ_NEW('IDLjavaObject$GetDataSet', 'org.virbo.idlsupport.APDataSet')  

        qds->setDataSetUrl, url 
        qds->doGetDataSet

        n= qds->name()

        nele= long(product( qds->lengths(n) ))
        if ( nele eq 0 ) then begin ; qdatasets can be zero length.  This is a problem for IDL.
            sizes= [ n_elements( qds->lengths(n) ), qds->lengths(n), 4, nele ]
            result= { sizes:sizes }
        endif else begin
            data= make_array( /double, nele )
            qds->valuesAlias, n, data
            data= reform( data, qds->lengths(n) )

            result= { data:data }
        endelse
        props= ap_getdataset_getProps( qds, n, keycount=keycount )
        if ( keycount gt 0 ) then result= create_struct( result, props )

        theresult= create_struct( n, result )

        names= qds->names()

        for i=0,n_elements(names)-1 do begin
           n= names[i]
           if ( n ne qds->name() ) then begin
               props= ap_getdataset_getProps( qds, n, keycount=keycount )
               doLabels= 0
               if ( qds->hasProperty( n, 'UNITS' ) ) then begin
                  units= qds->property( n, 'UNITS' )
                  units= units->toString()
                  if ( strpos( units, 'ordinal' )ne -1 ) then doLabels=1
               endif
               if ( doLabels ) then begin
                   data= make_array( /string, product( qds->lengths( n ) ) )    
                   qds->labelsAlias, n, data 
                   data= reform( data, qds->lengths(n) )
                   result= { data:data }
               endif else begin
                   data= make_array( /double, product( qds->lengths( n ) ) )    
                   qds->valuesAlias, n, data 
                   data= reform( data, qds->lengths(n) )
                   result= { data:data }
               endelse
               if ( keycount gt 0 ) then result= create_struct( result, props )
               theresult= create_struct( theresult, create_struct( n, result ) )
           endif
        endfor
        return, theresult

    endif else begin
       catch, /cancel
       oJSession = OBJ_NEW('IDLJavaObject$IDLJAVABRIDGESESSION')  
       oJExc= oJSession->GetException()  
       if ( obj_valid(oJExc) ) then oJExc->PrintStackTrace  
       message, !error_state.msg

    endelse

    
end
    