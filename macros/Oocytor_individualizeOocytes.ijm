// Macro that read a stack containing several oocytes, find them and save individual stack for each oocyte
// author: Gaëlle Letort, Terret/Verlhac team, CIRB, Collège de France

outputInd = 50;  // to number the new oocytes
outputName = "fmn"; // name of the oocytes
read = 1; // read .nd files (0) or look for .tif files (1)

run("Bio-Formats Macro Extensions");
run("Close All");
dir = getDirectory("Choose folder to process");
// where to output movies
outputDir = dir + "Out"+ File.separator();
if (!File.isDirectory(outputDir)) {
	File.makeDirectory(outputDir);
}
filelist = getFileList(dir);
// Read a .nd file and import each serie
for(i = 0; i < filelist.length; i++) {
	if (read==0 & endsWith(filelist[i], ".nd") ) {
		file = dir + filelist[i];
		outputInd = findOocytesNd(file, outputInd);
	}
	if (read==1 & endsWith(filelist[i], ".tif") ) {
		file = dir + filelist[i];
		outputInd = findOocytesTif(file, outputInd);
	}
}

function findOocytesNd(filename, ind){
	Ext.setId(filename);
	seriesCount = 0;
	Ext.getSeriesCount(seriesCount);	// get number of series
	print(seriesCount);
	for (s = 0; s < seriesCount; s++) {
			Ext.setSeries(s);
			run("Bio-Formats Importer", "open=[&filename] autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
			ind = doOneImage(ind);	
	}
	return ind;
}

function findOocytesTif(filename, ind){
	open(filename);
	return doOneImage(ind);	
}

function doOneImage(ind) {
	run("8-bit");
			// Find each oocyte by threshold of standard deviation projection
			run("Z Project...", "projection=[Standard Deviation]");	
			setAutoThreshold("Mean dark");
			setOption("BlackBackground", true);
			run("Convert to Mask");
			run("Fill Holes");
			// erode and dilate to separate neighbors
			for ( i = 0; i < 80; i++ ) { run("Erode"); }
			for ( i = 0; i < 75; i++ ) { run("Dilate"); }
			run("Analyze Particles...", "display exclude clear add");
			close();

			for ( i=0; i < roiManager("Count"); i++) {
				roiManager("Select", i);

				//run("Enlarge...", "enlarge=140");  // 20x
				run("Enlarge...", "enlarge=170");    // 40x
				run("Duplicate...", "duplicate");
				run("Select None");
				saveAs("Tiff", outputDir+outputName+"_"+(ind)+".tif");
				ind = ind + 1;
				run("Select None");
				close();
			}
		close();
		return ind;
	}

