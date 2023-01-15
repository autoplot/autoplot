/**
 * We don't know the size of the image until it is loaded, but we also wish to allocate
 * space where it will go.  This will remove the approximate width and height used to
 * allocate space.
 * @param {type} img
 * @returns {undefined}
 */
function clearImageSizeWhenLoaded(img) {
    img.onload = function () {
        img.removeAttribute("width");
        img.removeAttribute("height");
    };
}

