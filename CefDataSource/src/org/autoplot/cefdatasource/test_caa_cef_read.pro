pro test_caa_cef_read
   file= 'C1_CP_EDI_EGD__20050212_V03.cef'
   file4= 'C1_CP_PEA_CP3DXPH_DNFlux__20020811_140000_20020811_150000_V061018.cef'
   t0= systime(1)
   x= cef_read( 'N:/data/cef/' + file4, /ep_flag )
   print, 'read in '+(systime(1)-t0)+' sec'
   help, x
end
