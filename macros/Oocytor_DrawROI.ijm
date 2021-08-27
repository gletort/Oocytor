// Macro to open a stack and draw the cortex and ZP ROIs calculated by Oocytor plugin on a given slice or all images
// author: Gaëlle Letort, Terret/Verlhac team, CIRB, Collège de France

drawCortex = 0; // put to 0 not to draw it
drawZP = 1;     // put to 0 not to draw it
slice = 10;     // slice on which to draw. Put -1 to draw on all slices
saveImg = 0;    // save the resulting image or stack in a new folder

doFolder = 0;  // put to 0 to do only one file, 1 to process a whole folder

run("Line Width...", "line=3");  // width of the drawing line

if ( doFolder==0)
{
	filename = File.openDialog("Choose file to open");
	drawOneFile(filename);
}else {
	dir = getDirectory("Choose folder to process");
	filelist = getFileList(dir);
	for(i = 0; i < filelist.length; i++) {
		if (endsWith(filelist[i], ".tif") ) {
			file = dir + filelist[i];
			drawOneFile(file);
		}
	}	
}

// Process one file
function drawOneFile(filename)
{
	rootname = File.getNameWithoutExtension(filename);
	path = File.getDirectory(filename);
	if (!endsWith(filename, ".tif")) { print("Not a tif file"); exit; } 
	// create output directory
	outDir = path + "drawROI"+ File.separator();
	if (!File.isDirectory(outDir)) {File.makeDirectory(outDir); }


	open(filename);
	run("RGB Color");
	run("ROI Manager...");
	if ( roiManager("count")>0 ) roiManager("reset");
	// Open ROIs
	nzp = 0;
	if (drawCortex>0) { 
		roiManager("Open", path+"contours"+File.separator()+rootname+"_UnetCortex.zip");
		nzp = roiManager("count");
	}
	if (drawZP>0) roiManager("Open", path+"contours"+File.separator()+rootname+"_ZP.zip");

	// one slice or all stack ?
	dep = slice;
	if (slice == -1) dep = 1;
	end = slice;
	if (slice == -1) end = nSlices;

	// DRAW the ROIs
	for (i=dep; i<=end; i++)
	{
		setSlice(i);
		if (drawCortex>0)
		{
			roiManager("select", i-1);
			setForegroundColor(255, 15, 10);  // Cortex color in RGB
			run("Draw", "slice");
			roiManager("Deselect");
		}
		if (drawZP>0)
		{
			roiManager("select", nzp+2*(i-1));
			setForegroundColor(255, 150, 30);   // iner ZP color in RGB
			run("Draw", "slice");
			roiManager("select", nzp+2*(i-1)+1);  // outer ZP color in RGB
			run("Draw", "slice");
			roiManager("Deselect");
		}
	}
	roiManager("Show None");
	run("Select None");

	// Save results
	if (saveImg)
	{
		if ( slice==-1) { saveAs(".tif", outDir+rootname+"_rois.tif"); }
		else {
			setSlice(slice);
			saveAs(".png", outDir+rootname+"_roisSlice_"+slice+".png");
		}
		close();
	}
}