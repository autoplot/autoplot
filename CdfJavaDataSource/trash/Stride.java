package gov.nasa.gsfc.voyager.cdf;
    public class Stride {
        int[] stride;
        int nv;
        public Stride(int[] stride) {
            this.stride = stride;
        }
        public int getStride(int nv) {
            this.nv = nv;
            return getStride();
        }
        int getStride() {
            int _stride = 1;
            if (stride != null) {
                if (stride[0] > 0) {
                    _stride = stride[0];
                } else {
                    if (nv > stride[1]) {
                        _stride = (nv/stride[1]);
                        if (_stride*stride[1] < nv) _stride++;
                    }
                }
            }
            return _stride;
        }
    }
