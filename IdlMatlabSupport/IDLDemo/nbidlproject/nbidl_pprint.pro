;*******************************************************************************
;* NAME: PPRINT
;* DESCRIPTION: return a human-comsumable string that is a concise representation of the
;*    IDL value.  This is for debugging only, and not for machine consumption!!!  For
;*    example a indgen(50) is printed like "fltarr(50)[0,1,2,3,4,...]".  papco_objects
;*    toString function is called, etc.
;* INPUTS:
;*    VAR, expr, any IDL expression. 
;* OUTPUT KEYWORDS:
;*    out, string, the formated value.
;* SIDE EFFECTS:
;* EXCEPTIONS:
;* EXAMPLES:
;* UNIT TEST: 
;* CVSTAG: 
;*   $Name:  $
;*   $Revision: 1.1 $
;* CURATOR: NAME
;* HISTORY:  
;*   DATE, REV, written by Jeremy Faden
;*******************************************************************************
pro nbidl_pprint, var, out=out
   on_error, 2
   if n_elements( var ) eq 0 then begin
      out=  '<undefined>'
      print, out
      return
   endif
   if ( size( var, /type ) eq 8 ) then begin
;      if ( papco_ds_isDs(var) ) then begin
;        help, /str, papco_ds_strip_metadata(var), out=out
;        out= [ out, '(metadata omitted)' ]
;      endif else begin
        help, /str, var, out=out
;      endelse
      print, transpose( out )
      return
   endif

   s= size( var )

   if s[0] eq 0 then begin
     out= nbidl_varprt(var, /debug )
     print, out

   endif else begin ;; array
     n= n_elements(var)
     s= size(var)
     if n gt 5 then begin
        types= [ '', 'byt', 'int', 'lon', 'flt', 'dbl', 'complex', 'str', 'struct', 'dcomplex', 'ptr', 'obj', '', '', '', '' ] 
        stype= size(var, /type )
        type= types[stype] + 'arr'
        
        result= type+'('+strjoin(strtrim(s[1:s[0]],2),',')+')[ '
        if ( s[0] eq 2 ) then begin ; two-dim array
            print, result
            spaces= string( replicate( byte(' '), strlen(result) ) )
            result= spaces
        endif
     endif else begin
        result= '[ '
     endelse
     if ( s[0] eq 2 ) then begin
          n1= s[1]  <4
          n2= s[2]  <4
          for i=0, n2-2 do begin
               result= result+ '[ '
               for j=0,n1-2 do begin
                    result= result + nbidl_varprt( var[j,i], /debug ) + ', '
               endfor
               if  s[1] gt n1 then begin
                  result= result + nbidl_varprt( var[n1-1,i], /debug ) + ', ... ]'
                  print, result
                  result= spaces
              endif else begin
                  result= result + nbidl_varprt( var[n1-1,i], /debug ) + ' ]'
                  print, result
                  result= spaces
              endelse
          endfor
          if (  s[2] gt n2 ) then begin
            result= result +  '... ]'
          endif else begin
              result= result + ']'
          endelse
     endif else begin
          n= n_elements(var)  <5
          for i=0, n-2 do begin
               result= result + nbidl_varprt( var[i], /debug ) + ', '
          endfor
          if n_elements( var ) gt n then begin
              result= result + nbidl_varprt( var[n-1], /debug ) + ', ... ]'
          endif else begin
              result= result + nbidl_varprt( var[n-1], /debug ) + ' ]'
          endelse
      endelse
      out= result
      print, out
  endelse
end
