;+
; save_cdf/restore_cdf, replacements for proprietary IDL savesets.  attempts
; to mimic the save command in IDL, but stores the data in a CDF file
; rather than the proprietary IDLsave file.
;
; Jeremy Faden  faden@cottagesystems.com
; created on Web Oct 1, 2014.
;
; This uses SCOPE_VARFETCH and SCOPE_VARNAME to get information from other stack frames.
;       
;-

;+
; NAME:
;     RESTORE_CDF
; PURPOSE:
;     Mimic the restore command, but use "open" CDF format useful outside of IDL.  This assumes
;     the file was written by save_cdf and reads each parameter into an IDL variable with the
;     same (or modified to make a valid) name.
; CALLING SEQUENCE:
;     restore_cdf, 'myfile.cdf'
;-
pro restore_cdf, filename, verbose=verbose
   if ( n_elements( filename ) eq 0 ) then begin
       filename= 'idlsave.cdf'
   endif
   
   cdf= CDF_OPEN( filename )
   inq= CDF_INQUIRE( cdf )
   for i=0,inq.nzvars-1 do begin
       x= CDF_VARINQ( cdf, i, /zvar )
       varname= x.name
       CDF_CONTROL, cdf, variable=i, get_var_info=vinfo, /zvar
       cdf_varget, cdf, i, var, /zvar, rec_count=vinfo.MAXRECS+1  
       ( SCOPE_VARFETCH( varname, /enter, level=1 ) ) = var
       if ( keyword_set( verbose ) ) then  message, 'Restored variable: '+ varname, /cont
   endfor 
end

;+
; NAME:
;     SAVE_CDF
; PURPOSE:
;     Mimic the restore command, but use "open" CDF format useful outside of IDL.  This assumes
;     the file was written by save_cdf and reads each parameter into an IDL variable with the
;     same (or modified to make a valid) name.
; CALLING SEQUENCE:
;     save_cdf, filename='myfile.cdf', a, [b,...]
; PARAMS:
;     var0 a named variable to save.  
;     var/i/ up to 9 parameters can be saved at once.
; KEYWORD_PARAMS:
;     filename - If specified use this filename instead of the default "idlsave.cdf"
;     verbose - If true print verbose information
;     
; EXAMPLE:
;   A= indgen(6)+100
;   B= indgen(7)+200
;   C= indgen(3,4)+300
;   save_cdf, A, B, C, /verbose
;   restore_cdf, /verbose
;   help, A, B, C
;-
pro save_cdf, var0, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10check, $ 
   description=description, $
   filename=filename, verbose=verbose

   on_error, 2

   if ( n_elements( filename ) eq 0 ) then begin
      filename= 'idlsave.cdf'
   endif

   if ( n_params() ge 10 ) then begin
      MESSAGE, 'Only 10 parameters can be exported at once.'
   endif

   if ( FILE_TEST( filename, /read ) ) then begin
      FILE_DELETE, filename
   endif
   
   cdf= CDF_CREATE( filename )

   for i=0,N_PARAMS()-1 do begin

      var= SCOPE_VARFETCH( 'var'+STRTRIM(i,2), /enter, level=0 )
      
      case i of
        0: varname= SCOPE_VARNAME( var0, level=-1 )
        1: varname= SCOPE_VARNAME( var1, level=-1 )
        2: varname= SCOPE_VARNAME( var2, level=-1 )
        3: varname= SCOPE_VARNAME( var3, level=-1 )
        4: varname= SCOPE_VARNAME( var4, level=-1 )
        5: varname= SCOPE_VARNAME( var5, level=-1 )
        6: varname= SCOPE_VARNAME( var6, level=-1 )
        7: varname= SCOPE_VARNAME( var7, level=-1 )
        8: varname= SCOPE_VARNAME( var8, level=-1 )
        9: varname= SCOPE_VARNAME( var9, level=-1 )
      endcase
      
      if ( varname eq '' ) then begin
          HELP, var, output=o 
          if n_elements(o) gt 1 then o=o[0]
          j= STRPOS(o,'=')
          o= STRMID(o,j+1)
          MESSAGE, 'Expression must be named variable in this context: var'+strtrim(i,2)+'='+o
      endif
      s= SIZE( var )
      type= SIZE( var, /type )
      
      if ( type ne 14 and ( type lt 2 or type gt 5 ) ) then begin
         HELP, var, output=o 
         if n_elements(o) gt 1 then o=o[0]
         j= STRPOS(o,'=')
         o= STRMID(o,j+1)
         message, 'unable to write this type: var'+strtrim(i,2)+'='+o, /continue
         continue
      endif
      
      types= { $
         cdf_int2: type eq 2,  $
         cdf_int4: type eq 3,  $
         cdf_real4: type eq 4,  $
         cdf_real8: type eq 5,  $
         cdf_time_tt2000: type eq 14  $
      }
      
      if ( s[0] eq 0 ) then begin      
           var_id = CDF_VARCREATE( cdf, varname, /zvar, _extra=types )
      endif else if ( s[0] eq 1 ) then begin
           var_id = CDF_VARCREATE( cdf, varname, /zvar, _extra=types )
      endif else if ( s[0] eq 2 ) then begin
           var_id = CDF_VARCREATE( cdf, varname, [1], dim=[s[1]], /zvar, /rec_vary, _extra=types )
      endif else if ( s[0] ge 3 ) then begin
           var_id = CDF_VARCREATE( cdf, varname, replicate(1,s[0]-1), dim=[s[1:s[0]-1]], /zvar, /rec_vary, _extra=types )
      endif
      CDF_CONTROL, cdf, variable=var_id, /zvar, SET_PADVALUE=0 ; not used, this is just to avoid warning.
      CDF_VARPUT, cdf, varname, var, /zvar     

      if ( KEYWORD_SET( verbose ) ) then MESSAGE, 'Saved variable: '+ varname + '.', /cont
      
   endfor
   cdf_close, cdf

end

pro test_suite
   A= indgen(6)+100
   B= indgen(7)+200
   C= indgen(3,4)+300
   D= dindgen(2,3,4)+400
   GS = [ '2005-12-04T20:19:18.176321123',  '2005-12-04T20:20:18.176321123', $
     '2005-12-04T20:21:18.176321123',  '2005-12-04T20:23:18.176321123',  '2005-12-04T20:24:18.176321123' ]
   G64 = CDF_PARSE_TT2000( GS )
   ;G= [ 186999622360321123,    186999682360321123,    186999742360321123 ]

   ;E= { X:2, Y:3, Z:6 }
   ;F= replicate( { X:2, Y:3, Z:6 }, 10 )
   save_cdf, A, B, C, D, G64, /verbose
   restore_cdf, /verbose
   help, A, B, C, D, G64
end   
