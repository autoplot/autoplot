function prepimage(id) {
	
	PLOTINFO[id] = {};
	PLOTINFO[id].src = $("#"+id).attr("src"); 
	
	$('#'+id)
		.load(function () {
			id = $(this).attr("id"); // ID of loaded image.

			// Extract the data.
			ImageInfo.loadInfo(PLOTINFO[id].src, mycallback);

			// Callback function for when metadata extracted
			function mycallback() {
				splotInfo = ImageInfo.getField(PLOTINFO[id].src, "data")['plotInfo'];
				PLOTINFO[id] = $.parseJSON(splotInfo);
				// When image is loaded and metadata extracted, set onclick attribute.
				//$("#"+id).attr("onclick","cross(event)");
				//$("#"+id).click(cross);
			}
		})
}
