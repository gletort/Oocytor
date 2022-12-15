// Macro to open a stack and display the cortex and ZP ROIs calculated by Oocytor plugin
// author: Gaëlle Letort, Terret/Verlhac team, CIRB, Collège de France

showCortex = 1; // put to 0 not to show it
showZP = 0;     // put to 0 not to show it

filename = File.openDialog("Choose file to open");
rootname = File.getNameWithoutExtension(filename);
path = File.getDirectory(filename);
if (!endsWith(filename, ".tif")) { print("Not a tif file"); exit; } 

open(filename);
run("ROI Manager...");
if ( roiManager("count")>0 ) roiManager("reset");
if (showCortex>0) roiManager("Open", path+"contours/"+rootname+"_UnetCortex.zip");
if (showZP>0) roiManager("Open", path+"contours/"+rootname+"_ZP.zip");

roiManager("Associate", "true");
roiManager("Centered", "false");
roiManager("UseNames", "false");
roiManager("Show All");
