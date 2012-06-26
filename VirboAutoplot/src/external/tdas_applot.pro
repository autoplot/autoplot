pro tdas_applot, name
  common tplot_com1, data_quants, tplot_vars , tplot_configs, current_config , foo1,foo2

  if ( size( name, /type ) ne 7 ) then begin
     iname= name
  endif else begin
     r= where( data_quants[*].name eq name )
     iname= r[0]
  endelse

  if ( iname eq -1 ) then begin
     print, 'name '+name+' not found in:'
     for i=0,n_elements(data_quants)-1 do begin
        print, i, ' ', data_quants[i].name
     endfor
     message, ''
  endif

  data= *data_quants[iname].dh

  tn = tag_names(data)
  index = where( tn eq 'V', nmatch)

  if ( nmatch eq 1 ) then begin
     xx= *data.x
     yy= *data.v
     zz= *data.y
     help, xx, yy, zz
     applot, xx, yy, zz, xunits='seconds since 1970-001T00:00'
  endif else begin
     yy= *data.y
     xx= *data.x
     help, xx , yy
     if ( size( yy, /n_dimensions ) eq 2 ) then begin
        applot, xx ,yy, xunits='seconds since 1970-001T00:00', $
          renderType='series'
     endif else begin
        applot, xx ,yy, xunits='seconds since 1970-001T00:00', $
          renderType='series'
     endelse
  endelse
end
