;+
; __NAME__
; created on __DATE__
;-

pro createTestCdf
    cd, curr=pwd
    print, pwd

    out= '/home/jbf/ct/temp/'
    cdf= cdf_create( out + 'testCdfRowMajor', /row_major, /clobber, /single_file )

    var= cdf_varcreate( cdf, 'rank4float', [1,1,1], dimensions=[2,5,7], /cdf_float, /zvar )

    array= findgen( 7,5,2,9 )
    array= transpose( array, [ 2,1,0,3 ] )
    help, array
    print, array[1,2,3,0]

    cdf_varput, cdf, var, /zvar, array

    cdf_close, cdf


    cdf= cdf_create( out + 'testCdfColMajor', /col_major, /clobber, /single_file )

    var= cdf_varcreate( cdf, 'rank4float', [1,1,1], dimensions=[7,5,2], /cdf_float, /zvar )

    array= findgen( 7,5,2,9 )
    help, array
    print, array[3,2,1,0]

    print, out

    cdf_varput, cdf, var, /zvar, array

    cdf_close, cdf


end