;*******************************************************************************
;* NAME: VARPRT
;* DESCRIPTION: format any variable or expression as a string.  Machine consumption okay, unless /DEBUG
;*    keyword is used. 
;* INPUTS:   
;*   VAR, expr, any idl expression.
;* KEYWORDS:
;*   LEN, int, pad to this length with spaces
;*   FIXED, boolean, use format="(g10.4)" to print.
;*   DEBUG, boolean, debug mode for use with pprint.  Indicates that the result is only
;*      for human consumption, so backwards-compatibility is not an
;*      issue.
;*   ADD0, pad string with zeros if a specific LEN is requested
;* RETURNS: 
;*   string, formatted result.
;* SIDE EFFECTS:
;* EXCEPTIONS:
;* EXAMPLES:
;* UNIT TEST: 
;* CVSTAG: 
;*   $Name:  $
;*   $Revision: 1.1 $
;* CURATOR: NAME
;* HISTORY:  
;*   DATE, REV, written by NAME
;*******************************************************************************
FUNCTION nbidl_varprt,var, LEN=LEN, FIXED=FIXED, DEBUG=DEBUG, ADD0 = ADD0

  if ( size( var, /type ) eq 11 ) then begin
      if ( not obj_valid( var ) ) then begin
          return, '<null object>'
      endif else if ( obj_isa(var,'papco_object') ) then begin
          return, var->toString()
      endif else begin
          return, obj_class( var )
      endelse
  end

  if ( size( var, /type ) eq 10 ) then begin
      if ( ptr_valid( var ) ) then begin
        return, '*'+nbidl_varprt( *var )
      endif else begin
        return, '<null pointer>'
      endelse
  endif

  if ( size( var, /type ) eq 8 ) then begin
      return, '<struct>'
  endif

  if keyword_set(FIXED) then $
    out_str=strtrim(string(var, format="(g10.4)"),2) $
  else $
    out_str=strtrim(string(var),2)


  if keyword_set(LEN) then BEGIN 
     out_str=strleft(out_str,LEN)
     IF keyword_set(ADD0) THEN WHILE strlen(out_str) LT len DO out_str = '0'+ out_str
  ENDIF 

  if ( keyword_set( debug ) and size( var, /type ) eq 7 ) then begin
    out_str= "'" + out_str + "'"
  endif

  if ( keyword_set( debug ) and size( var, /type ) eq 1 ) then begin ; byte
    out_str= 'byte('+strtrim( fix(var),2) + ')'
  endif

  return, out_str

END
