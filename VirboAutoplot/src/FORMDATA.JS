function createRequestObject() {
    FORM_DATA = new Object();
    separator = ',';
    query = '' + this.location;
    qu = query
    query = query.substring((query.indexOf('?')) + 1);
    if (query.length < 1) { return false; }
    keypairs = new Object();
    numKP = 1;
    while (query.indexOf('&') > -1) {
        keypairs[numKP] = query.substring(0,query.indexOf('&'));
        query = query.substring((query.indexOf('&')) + 1);
        numKP++;
    }
    keypairs[numKP] = query;
    for (i in keypairs) {
        keyName = keypairs[i].substring(0,keypairs[i].indexOf('='));
        keyValue = keypairs[i].substring((keypairs[i].indexOf('=')) + 1);
        while (keyValue.indexOf('+') > -1) {
            keyValue =
                keyValue.substring(0,keyValue.indexOf('+'))
                + ' ' + keyValue.substring(keyValue.indexOf('+') + 1);
        }
        keyValue = unescape(keyValue);
        if (FORM_DATA[keyName]) {
            FORM_DATA[keyName] = FORM_DATA[keyName] + separator + keyValue;
        } else {
            FORM_DATA[keyName] = keyValue;
        }
    }
    return FORM_DATA;
}


