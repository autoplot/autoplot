var ret;
var oReq = new XMLHttpRequest();
oReq.open("GET", "http://autoplot.org/images/extractpngmetadata.png", true);
//oReq.open("GET", "extractpngmetadata.png", true);
oReq.responseType = "arraybuffer";
oReq.onload = function (oEvent) {
    var arrayBuffer = oReq.response; // Note: not oReq.responseText
    if (arrayBuffer) {
        var byteArray = new Uint8Array(arrayBuffer);
        ret = String.fromCharCode.apply(String,byteArray).match(/{[\x00-\x7f]+}/);
        // Better to use this (see email from Wei)?
        //ret = String.fromCharCode.apply(String,byteArray).match(/{\[[\x00-\x7f]+\]}/)
    }
};
oReq.send(null);
console.log(ret);