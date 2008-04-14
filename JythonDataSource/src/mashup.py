ds1= getDataSet( 'http://cdaweb.gsfc.nasa.gov/istp_public/data/ace/swe/2007/ac_k0_swe_20070105_v01.cdf?He_ratio' )
ds2= getDataSet( 'http://cdaweb.gsfc.nasa.gov/istp_public/data/ace/swe/2007/ac_k0_swe_20070105_v01.cdf?Np' )
He= ds2 * ds1  


