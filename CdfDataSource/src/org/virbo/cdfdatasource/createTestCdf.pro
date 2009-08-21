;+
; __NAME__
; created on __DATE__
;-

pro createTestCdf
    cd, curr=pwd
    print, pwd

    cdf= cdf_create( '/home/jbf/temp/testCdfRowMajor', /row_major, /clobber, /single_file )

    var= cdf_varcreate( cdf, 'rank3float', [1,1], dimensions=[2,5], /cdf_float, /zvar )

    array= findgen( 2,5,7 )

    cdf_varput, cdf, var, /zvar, array

    cdf_close, cdf


    cdf= cdf_create( '/home/jbf/temp/testCdfColMajor', /col_major, /clobber, /single_file )

    var= cdf_varcreate( cdf, 'rank3float', [1,1], dimensions=[5,2], /cdf_float, /zvar )

    ;array= findgen( 2,5,7 )
    array= findgen( 5,2,7 )

    cdf_varput, cdf, var, /zvar, array

    cdf_close, cdf


end