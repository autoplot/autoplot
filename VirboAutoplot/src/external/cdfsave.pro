;+
; cdfsave/cdfrestore, replacements for proprietary IDL savesets.  attempts
; to mimic the save command in IDL, but stores the data in a CDF file
; rather than the proprietary IDLsave file.
;
; Jeremy Faden
; created on Web Oct 1, 2014.
;
;-

function getvarname, var
   varname= scope_varname( var, level=-2 )
   return, varname
end

pro cdfsave, var0, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10check, description=description, $
   filename=filename

   on_error, 2

   if ( n_elements( filename ) eq 0 ) then begin
      filename= 'idlsave.cdf'
   endif

   if ( n_params() ge 10 ) then begin
      message, 'Only 10 parameters can be exported at once.'
   endif

   if ( FILE_TEST( filename, /read ) ) then begin
      FILE_DELETE, filename
   endif
   
   fid= cdf_create( filename )

   for i=0,n_params()-1 do begin

      r= execute( 'var= var'+strtrim(i,2) )
      
      case i of
        0: varname= getvarname( var0 )
        1: varname= getvarname( var1 )
        2: varname= getvarname( var2 )
        3: varname= getvarname( var3 )
        4: varname= getvarname( var4 )
        5: varname= getvarname( var5 )
        6: varname= getvarname( var6 )
        7: varname= getvarname( var7 )
        8: varname= getvarname( var8 )
        9: varname= getvarname( var9 )
      endcase
      

      if ( varname eq '' ) then begin
          help, var, output=o
          message, 'Expression must be named variable in this context: '+ o[0]
      endif
      s= size( var )
      if ( s[0] eq 0 ) then begin      
           var_id = cdf_varcreate( fid, varname )
      endif else if ( s[0] eq 1 ) then begin
           var_id = cdf_varcreate( fid, varname )
      endif else if ( s[0] eq 2 ) then begin
           var_id = cdf_varcreate( fid, varname, [1], dim=[s[2]], /zvar, /rec_vary )
      endif
      if ( s[0] eq 2 ) then begin
         nv= n_elements(var[*,0])
         for i=0,nv-1 do begin ; STUPID, Why can't I figure out how to do this in one call!
            cdf_varput, fid, varname, reform(var[i,*]), /zvar, rec_start=i
         endfor
      endif else begin
         CDF_VARPUT, fid, varname, var, /zvariable
      endelse

   endfor
   cdf_close, fid

end

