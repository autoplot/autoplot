;+
; demonstrate how Autoplot's IDL interface could be used to read data into the TDAS data system
;
; tdas_store_data, 'protonvel', 'http://www.autoplot.org/data/proton_velocity_rtn.qds'
; tplot, 'protonvel'
;-
pro tdas_store_data, name, uri
   qds= OBJ_NEW('IDLjavaObject$APDataSet', 'org.virbo.idlsupport.APDataSet')
   qds->setDataSetUri, uri
   qds->doGetDataSet
   qds->setPreferredUnits, 'seconds since 1970-001T00:00'
   qds->setFillValue, !values.d_nan

   dep0Name= qds->depend(0)

   print, qds->toString()

   if ( qds->rank() eq 1 ) then begin
      store_data, name, data= { x:qds->values(dep0Name), y:qds->values() }
   endif else if ( qds->rank() gt 2 ) then begin
      message, 'not supported'
   endif else begin
      dep1Name= qds->depend(1)
      yunits= (qds->property( dep1Name,'UNITS'))
      isSpectrogram= obj_valid( yunits )
      if ( isSpectrogram ) then begin
         if ( strpos( yunits->toString(), 'ordinal' ) gt -1 ) then begin  ; nasty...
             isSpectrogram= 0
         endif
      endif
      if ( isSpectrogram ) then begin
        store_data, name, data= { x:qds->values(dep0Name), v:qds->values(dep1Name), y:qds->values() }
      endif else begin
        store_data, name, data= { x:qds->values(dep0Name), y:qds->values() }
      endelse
   end

end
