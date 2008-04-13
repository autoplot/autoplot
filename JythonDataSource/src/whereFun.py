data= sin( findgen( 10000 ) / 10000 * 10 * PI * 2 )
x= ( data < -0.5 ) | ( data > 0.5 )
r= where( x )
data= data[r]
data.putProperty( 'DEPEND_0', r )
