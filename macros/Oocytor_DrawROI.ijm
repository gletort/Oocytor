// Macro to open a stack and draw the cortex and ZP ROIs calculated by Oocytor plugin on a given slice or all images
// author: Gaëlle Letort, Terret/Verlhac team, CIRB, Collège de France

drawCortex = 1; // put to 0 not to draw it
drawZP = 0;     // put to 0 not to draw it
slice = -1;     // slice on which to draw. Put -1 to draw on all slices
saveImg = 1;    // save the resulting image or stack in a new folder

doFolder = 1;  // put to 0 to do only one file, 1 to process a whole folder

run("Line Width...", "line=3");  // width of the drawing line
run("ROI Manager...");

if ( doFolder==0)
{
	filename = File.openDialog("Choose file to open");
	drawOneFile(filename);
}else {
	dir = getDirectory("Choose folder to process");
	filelist = getFileList(dir);
	for(i = 0; i < filelist.length; i++) {
		if (endsWith(filelist[i], ".tif") || endsWith(filelist[i], ".png") ) {
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
	if (!endsWith(filename, ".tif") & !endsWith(filename, ".png") ) { print("Not a tif or png file"); exit; } 
	// create output directory
	outDir = path + "drawROI"+ File.separator();
	if (!File.isDirectory(outDir)) {File.makeDirectory(outDir); }

	open(filename);
	run("RGB Color");
	
	if ( roiManager("count")>0 ) roiManager("reset");
	// Open ROIs
	nzp = 0;
	doCortex = drawCortex;
	doZP = drawZP;
	if (drawCortex>0) { 
		cortpath = path+"contours"+File.separator()+rootname+"_UnetCortex.zip";
		if (! File.exists(cortpath) ) { 
			print("No cortex segmentation associated with file "+rootname);  
			doCortex = 0;
		} else {
			roiManager("Open", cortpath);
			nzp = roiManager("count");
		}
	}
	if (drawZP>0) {
		zppath = path+"contours"+File.separator()+rootname+"_ZP.zip";
		if (! File.exists(zppath) ) { 
			print("No ZP segmentation associated with file "+rootname);  
			doZP = 0;
		} else {
		roiManager("Open", zppath);
		}
	}

	// one slice or all stack ?
	dep = slice;
	if (slice == -1) dep = 1;
	end = slice;
	if (slice == -1) end = nSlices;

	// DRAW the ROIs
	for (i=dep; i<=end; i++)
	{
		setSlice(i);
		
		if ((drawZP>0) & (doZP>0))
		{
			roiManager("select", nzp+2*(i-1));
			setForegroundColor(100, 210, 80);   // iner ZP color in RGB
			run("Draw", "slice");
			roiManager("select", nzp+2*(i-1)+1);  // outer ZP 
			run("Draw", "slice");
			roiManager("Deselect");
		}
		if ((drawCortex>0) & (doCortex>0))
		{
			roiManager("select", i-1);
			setForegroundColor(180, 80, 255);  // Cortex color in RGB
			run("Draw", "slice");
			roiManager("Deselect");
		}
	}
	roiManager("Show None");
	run("Select None");

	// Save results
	if (saveImg>0)
	{
		if ( slice==-1) { saveAs(".tif", outDir+rootname+"_rois.tif"); }
		else {
			setSlice(slice);
			saveAs(".png", outDir+rootname+"_roisSlice_"+slice+".png");
		}
		close();
	}
}
