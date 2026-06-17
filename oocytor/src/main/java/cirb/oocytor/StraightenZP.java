/*-
 * #%L
 * Plugins to segment different oocytes structures, and to extract numerous features to describe them
 * %%
 * Copyright (C) 2021 - 2026 Gaelle Letort
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the CIRB nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package cirb.oocytor;
import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.*;
import java.io.*;
import java.awt.*;

public class StraightenZP implements PlugIn 
{

	ImagePlus imp;
	ImagePlus rgb;
	Calibration cal;
	RoiManager rm;
	String dir = "/home/gaelle/Miv/Mouse/imseq/movo01";
	String resdir = "";
	String inname = "movo_01.tif";
	String purname;
	Utils util;

	Roi[] erased;
	boolean erasing;
	Roi outside;

	
	// parameters
	double scalexy = 0.5; // one pixel in um
	double timeoff = 0; // start time point
	double dtime = 0.05; // time between frames in h

	int slice = 1;

	/** \brief Dialog window */
	public boolean getParameters()
	{

		GenericDialog gd = new GenericDialog("Options", IJ.getInstance() );
		Font boldy = new Font("SansSerif", Font.BOLD, 12);
		gd.addStringField("path_to_main_directory:", dir);
		gd.addStringField("name_image:", inname);
		gd.addNumericField("scale_xy", scalexy, 4);
		gd.addNumericField("time_offset", timeoff, 4);
		gd.addNumericField("dt", dtime, 4);
		gd.addMessage("---------------------------------------------------------- ");
		gd.addNumericField("slice", slice, 0);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		dir = gd.getNextString();
		inname = gd.getNextString();
		purname = inname.substring(0, inname.lastIndexOf('.'));
		scalexy = gd.getNextNumber();
		timeoff = gd.getNextNumber();
		dtime = gd.getNextNumber();
		slice = (int)gd.getNextNumber();

		return true;
	}

	/** Be sure there s no calibration */
	public void initCalibration()
	{
		// Measure everything in pixels
		cal = imp.getCalibration();
		if (cal == null ) 
		{
			cal = new Calibration(imp);
		}
		cal.pixelWidth = 1;
		cal.pixelHeight = 1;
		cal.pixelDepth = 1;
	}


	/** \brief create image of straighten ZP */
	public void straightenZP()
	{
		openImageRois(false, true, -1, false);
		rm.reset();
		IJ.run(imp, "Select None", "");
		// get ZP Rois and load cortex Roi in RoiManager
		rm.runCommand("Open", dir+"/cortex/"+purname+"_ZP.zip");
		Roi[] zps = rm.getRoisAsArray();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		IJ.run(imp, "Select None", "");

		double epaisseur = 9 / scalexy;
		StraightZP straighter = new StraightZP(epaisseur, (int)(0.5/scalexy), (int)(1.2/scalexy));
		
		// Measure features in time
		Roi in = zps[(slice-1)*2];
		Roi out = zps[(slice-1)*2+1];
		ImagePlus ip = imp.crop(slice+"-"+slice);
		ip.show();
		
		Roi mean = util.meanRoi( in, out );
		straighter.createStraight(ip, mean);
		straighter.clearZone(rm);
		ImagePlus straight = straighter.getStraight();
		straight.show();
		util.close(ip);
		util.close(imp);

	}


	public void openImageRois(boolean openCortex,  boolean openErased, double fact, boolean getOut)
	{
		// open, reset
		String imgname = dir+"/"+inname;
		
		String ext = inname.substring(inname.lastIndexOf('.'));
		if ( ext.equals(".png") )
		{
			IJ.open(imgname);
			imp = IJ.getImage();
		}
		else
		{
			imp = IJ.openVirtual(imgname);
		}
		initCalibration();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		imp.show();
		IJ.run(imp, "Select None", "");

		//get outside Roi
                int enl = (int) Math.floor(1.2/scalexy);
		if ( getOut )
			outside = util.getOutsideRoi(imp, fact, dir+"/cortex/"+purname+"_ZP.zip", rm, enl);
		
		// rescale if needed
		if ( fact > 0 )
		{
			// put all images to same size in microns for comparable texture 
			int newwidth = (int) Math.round(imp.getWidth()*fact);
			int newheight = (int) Math.round(imp.getHeight()*fact);
			IJ.run(imp, "Size...", "width="+newwidth+" height="+newheight+" depth="+imp.getNSlices()+" constrain average interpolation=Bilinear");
		}
	
		if ( openErased )
		{	
			// get erased zones	
			File erasedZIP = new File(dir+"/cortex/"+purname+"_erased.zip");
			erasing = false;
			erased = null;
			if ( erasedZIP.exists() )
			{
				rm.runCommand("Open", dir+"/cortex/"+purname+"_erased.zip");
				erasing = true;
				if ( fact > 0 )
					util.rescaleRois(rm, imp, fact);
				erased = rm.getRoisAsArray();
				rm.reset();
			}
		}

		if ( openCortex )
		{
			rm.runCommand("Open", dir+"/cortex/"+purname+"_UnetCortex.zip");
			if ( fact > 0 )
				util.rescaleRois(rm, imp, fact);
		}
	}

	public void run(String arg)
	{
		if ( !getParameters() ) { return; }
		IJ.run("Close All");

		rm = RoiManager.getInstance();
		if ( rm == null )
			rm = new RoiManager();
		
		util = new Utils();

		resdir = dir+"/measures";
		File directory = new File(resdir);
		if (! directory.exists())
			directory.mkdir();
		
		String ext = inname.substring(inname.lastIndexOf('.'));
		if ( ext.equals(".png") )
		{
			inname = "/input/"+inname;
		}

		IJ.run("Close All");
		straightenZP();
	}
}

