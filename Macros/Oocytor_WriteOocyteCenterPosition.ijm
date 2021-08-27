// Macro to write the position of the center and perimeter of the cortex ROIs calculated by Oocytor plugin
// Save the results in a new file in the measures folder
// Do all .tif files found in the given folder
// author: Gaëlle Letort, Terret/Verlhac team, CIRB, Collège de France

visible = 0; // if works in batch mode or not

dir = getDirectory("Choose folder to process");
if (visible==0) setBatchMode(true);
filelist = getFileList(dir);
// Read a .nd file and import each serie
for(i = 0; i < filelist.length; i++) {
	if (endsWith(filelist[i], ".tif") ) {
		file = dir + filelist[i];
		rootname = File.getNameWithoutExtension(filelist[i]);
		path = File.getDirectory(file);
		
		open(file);
		run("ROI Manager...");
		if ( roiManager("count")>0 ) roiManager("reset");
		roiManager("Open", path+"contours/"+rootname+"_UnetCortex.zip");
		run("Set Measurements...", "centroid perimeter stack redirect=None decimal=2");
		roiManager("Deselect");
		run("Clear Results");
		roiManager("Measure");
		saveAs("Results", path+"measures/"+rootname+"_oocyteCenterPerimeter_Results.csv");
		close();
	}
}
if (visible==0) setBatchMode(false);
