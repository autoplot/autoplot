function tests(cb) {
	tests.digitizer =
		[
			["#canvas1","#digitizer1",210,139,"x:210 y:139 np:4 ip:3 datax:9.939833098455859 datay:9.983488372093023"],
			["#canvas1","#digitizer1",440,172,"x:440 y:172 np:4 ip:2 datax:2011-10-24T02:37:22.622Z datay:130.30230774479998"]
		];
	
	tests.digitizer.results = {};
	tests.digitizer.results.values = [];
	tests.digitizer.results.Npass = 0;
	tests.digitizer.results.Nfail = 0;
	runtests(0);

	function runtests(k) {
		if (k == tests.digitizer.length) {
			// Tests are done, send results to callback.
			cb(tests.digitizer.results);
			return;
		}
		
		var e = new jQuery.Event("click");
		e.offsetX = tests.digitizer[k][2];
		e.offsetY = tests.digitizer[k][3];
		$(tests.digitizer[k][0]).trigger(e);
		setTimeout(function () {
			var str = $(tests.digitizer[k][1]).text();
			if (str === tests.digitizer[k][4]) {
				tests.digitizer.results.values[k] = true;
				tests.digitizer.results.Npass  = tests.digitizer.results.Npass + 1;
				console.log("Test " + k + " passed.");
			} else {
				tests.digitizer.results.values[k] = false;
				tests.digitizer.resutls.Nfail  = tests.digitizer.results.Nfail + 1;
				console.log("Test " + k + " failed.");
				console.log("-- Expected: " + tests.digitizer[k][4]);
				console.log("-- Result:   " + str);
			}
			runtests(k+1);
		},tests.digitizer[k][4])
	}

}
