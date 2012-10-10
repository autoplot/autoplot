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

pro hdfsave1, var0, filename=filename, append=append, id=id
   outsideName= scope_varname( var0, level=-2 )
   if ( append ) then begin
      h5d_write, id, var0
   endif else begin
      h5_create, filename, { _data:var0, _name:outsideName, _type:'Dataset' }
   endelse
end


pro hdfrestore, filename

end

pro hdfsave, var0, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10check, description=description, $
   filename=filename

   on_error, 2

   if ( n_elements( filename ) eq 0 ) then begin
      filename= 'idlsave.h5'
   endif

   if ( n_params() gt 10 ) then begin
      message, 'Only 10 parameters can be exported at once.'
   endif

   if ( FILE_TEST( filename, /read ) ) then begin
      FILE_DELETE, filename
   endif
   
   fid= h5f_create( filename )
   ds = H5S_CREATE_SIMPLE(n_params())

   for i=1,n_params() do begin
      varname= scope_varname( var0, level=-1 )
      var= var0
      if ( varname eq '' ) then begin
          help, var0, output=o
          message, 'Expression must be named variable in this context: '+ o[0]
      endif
      datatype_id = H5T_IDL_CREATE(var)
      s= size( var, /dimensions )
      dataspace_id = H5S_CREATE_SIMPLE( s,max_dimensions=s)
      dataType = H5T_MEMTYPE( size( var, /type ) )
      dataset_id = H5D_CREATE( fid, varname, dataType, dataspace_id )
      H5D_EXTEND,dataset_id,size(var,/dimensions)
      H5D_WRITE, dataset_id, var

   endfor
   h5_close

end

