;+
; hdfsave/hdfrestore, replacements for proprietary IDL savesets.  attempts
; to mimic the save command in IDL, but stores the data in an HDF5 file
; rather than the proprietary IDLsave file.
;
; This is limited to one variable right now, and should be rewritten.
;
; Jeremy Faden
; created on Web Feb 3, 2010.
;
;
;-

pro hdfsave1, var0, filename=filename
   outsideName= scope_varname( var0, level=-2 )
   h5_create, filename, { _data:var0, _name:outsideName, _type:'Dataset' }
end

pro hdfsave, var0, var1, var2, var3, var4, description=description, $
   filename=filename

   on_error, 2

   if ( n_elements( filename ) eq 0 ) then begin
      filename= 'idlsave.h5'
   endif

   if ( n_params() ne 1 ) then begin
      message, 'only one variable supported for now.'
   endif

   if ( n_params() ge 1 ) then begin
      if ( scope_varname( var0, level=-1 ) eq '' ) then begin
         help, var0, output=o
         message, 'Expression must be named variable in this context: '+ o[0]
      endif
      hdfsave1, var0, filename=filename
   endif
end


pro hdfrestore, filename

end
