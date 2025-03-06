/** 
 * @brief Plugin to get zona pellucida (ZP) contours Rois from TRANS images
 *
 * @details Segment ZP with neural network trained on oocytes, TRANS images
 * Use CSBDeep "run your network" plugin to call the trained networks.
 * Detect the ZP
 * 
 *
 * @author G. Letort, College de France
 * @date created on 2021/04/01
 *
 * License information
 * Copyright (C) <2021>  <Gaelle Letort>

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
 * */

package cirb.oocytor;

import fiji.util.gui.GenericDialogPlus;
import ij.*;
import ij.gui.*;
import ij.io.LogStream;
import ij.plugin.*;
import ij.Prefs;
import ij.plugin.frame.*;
import ij.measure.*;
import ij.process.ImageStatistics;
import java.awt.Color;
import java.awt.Font;
import java.io.*;
import javax.swing.ImageIcon;

public class GetZP implements PlugIn 
{
	ImagePlus imp;
	Calibration cal;
	RoiManager rm;
	String dir;
	String modeldir;
	Utils util;
	int nnet = 2;
	Network net;
	final ImageIcon icon = new ImageIcon(this.getClass().getResource("/oo_logo.png"));
	boolean visible = false;
	boolean locate = false;   // Locate the location of the oocyte+zp structure, and run the network on cropped around it
	int threshold = 100;      // threshold to locate oocyte
	String contours = "Both"; // "Both", "Outer only", "Inner only"

	/** Initialisation of an image */
	public void openResetImage(String imgname) 
	{
		String ext = imgname.substring(imgname.lastIndexOf('.'));
		if (ext.equals(".tif"))
			imp = IJ.openVirtual(imgname);
		else
			imp = IJ.openImage(imgname);
		//imp = IJ.getImage();
		cal = util.initCalibration(imp);
		if (visible) imp.show();
		rm.runCommand(imp, "Deselect");
		rm.reset();
		util.unselectImage(imp);
	}

	/** \brief For radii with values very different from the others, most likely errors, replace them by local mean */
	public float[] smoothExtremeRadius(float cx, float cy, double ang, float[] rads, int ind, boolean remExtreme, boolean fit) 
	{
		float[] res = new float[2];
		res[0] = (float) 0.0;
		res[1] = (float) 0.0;

		float mean = util.meanPositiveTab(rads);
		float stdPos = util.stdPositiveTab(rads, mean);
		float std = (float) 4 * stdPos;
		float stdm = (float) 4 * stdPos;

		// repace extreme radii by threshold radius
		if (remExtreme) {
			for (int i = 0; i < rads.length; i++) {
				if (rads[i] < 0) {
					rads[i] = mean;
				}
				if (rads[i] > (mean + std)) {
					rads[i] = mean + std;
				}
				if (rads[i] < (mean - stdm)) {
					rads[i] = mean - stdm;
				}
			}
		}

		int size = 15;
		if (fit) {
			size = 10;
		}
		double crad = util.meanCircularWindow(rads, ind, size, true);
		res[0] = ((float) cx + (float) (crad * Math.cos(ang)));
		res[1] = ((float) cy + (float) (crad * Math.sin(ang)));
		return res;
	}

	/** \brief At given angle, find inner and outer limits of the positive area */
	public float[] findFirstLast(float cx, float cy, float radius, double ang, ImagePlus img) 
	{
		float[] res = new float[4];
		res[0] = (float) 0.0;
		res[1] = (float) 0.0;
		res[2] = (float) 0.0;
		res[3] = (float) 0.0;
		Line myline = new Line(cx, cy, cx + radius * Math.cos(ang), cy + radius * Math.sin(ang));
		myline.setStrokeWidth(15);  // prev 30
		img.setRoi(myline);
		double[] vals = myline.getPixels();

		// find last
		int i = vals.length;
		while ((vals[i - 1] <= 200) && (i > 1))
			i--;
		if (i <= 2)
			i = -10000;
		else
			i += 1;

		// find first
		int j = 0;
		while ((vals[j + 1] <= 200) && (j < (vals.length - 2)))
			j++;
		if (j >= (vals.length - 4))
			j = -10000;
		else
			j -= 1;

		res[0] = (float) cx + (float) (radius * Math.cos(ang) * j / vals.length);
		res[1] = (float) cy + (float) (radius * Math.sin(ang) * j / vals.length);
		res[2] = (float) cx + (float) (radius * Math.cos(ang) * i / vals.length);
		res[3] = (float) cy + (float) (radius * Math.sin(ang) * i / vals.length);

		// is out or against image limits
		if (res[0] >= imp.getWidth() * 0.99 || res[0] <= 3)
			res[0] = -10000;
		if (res[1] >= imp.getHeight() * 0.99 || res[1] <= 3)
			res[1] = -10000;
		if (res[2] >= imp.getWidth() * 0.99 || res[2] <= 3)
			res[2] = -10000;
		if (res[3] >= imp.getHeight() * 0.99 || res[3] <= 3)
			res[3] = -10000;

		return res;
	}

	/** \brief Remove small Rois from the binary image */
	public void cleanSmallRois(ImagePlus img) {
		IJ.run(img, "Select None", "");
		IJ.setRawThreshold(img, 1, 255, null);
		IJ.run(img, "Analyze Particles...", "clear add");
		util.keepRois(0, img);  // keep only biggest Roi 
		for (int i = 0; i < rm.getCount(); i++) {
			Roi cur = rm.getRoi(i);
			img.setSlice(cur.getPosition());
			img.setRoi(cur);
			IJ.run(img, "Clear Outside", "slice");
		}
		IJ.run(img, "Select None", "");
		rm.runCommand(img, "Deselect");
		rm.reset();
	}

	/** \brief At each slice, calculate the iner and outer Rois from the binary image */
	public void getRoisSlice(int slice, ImagePlus bin, double wratio, double hratio, float rad) {
		imp.setSlice(slice);
		bin.setSlice(slice);

		int nang = 360;
		double ang = 0;
		double dang = 2 * Math.PI / nang;
		float[] fxpts = new float[nang];
		float[] fypts = new float[nang];
		float[] lxpts = new float[nang];
		float[] lypts = new float[nang];
		float cx = (float) (bin.getWidth() / 2.0);
		float cy = (float) (bin.getHeight() / 2.0);
		int nfpos = 0;
		int nlpos = 0;

		/// Find each first and last point at each angle
		// get local radii. Put negative values when missing
		float[] radtabf = new float[nang];
		float[] radtabl = new float[nang];
		for (int j = 0; j < nang; j++) {
			ang = ang + dang;
			float[] res = findFirstLast(cx, cy, rad, ang, bin);
			fxpts[j] = res[0];
			fypts[j] = res[1];
			lxpts[j] = res[2];
			lypts[j] = res[3];
			if (fxpts[j] > 0 && fypts[j] > 0)
				radtabf[j] = (float) Math.sqrt((double) ((fxpts[j] - cx) * (fxpts[j] - cx) + (fypts[j] - cy) * (fypts[j] - cy)));
			else
				radtabf[j] = -100;
			if (lxpts[j] > 0 && lypts[j] > 0)
				radtabl[j] = (float) Math.sqrt((double) ((lxpts[j] - cx) * (lxpts[j] - cx) + (lypts[j] - cy) * (lypts[j] - cy)));
			else
				radtabl[j] = -100;
		}

		/// choose if get both or only one contours
		int get_contours = 2;
		if (contours.equals("Outer only"))
			get_contours = 1;
		if (contours.equals("Inner only"))
			get_contours = 0;

		/// replace negative (missing) values by local mean
		int size = 30;
		ang = 0;
		for (int j = 0; j < nang; j++) {
			ang = ang + dang;
			if ( (get_contours != 1) && (radtabf[j] <= 0) ) 
			{
				float mrad = util.meanCircularRadius(radtabf, j, size);
				if (mrad < 0) mrad = Math.max(bin.getWidth(), bin.getHeight());
				radtabf[j] = mrad;
				fxpts[j] = cx + (float) (mrad * Math.cos(ang));
				fypts[j] = cy + (float) (mrad * Math.sin(ang));
			}
			if ((get_contours != 0 ) && (radtabl[j] <= 0)) {
				float mrad = util.meanCircularRadius(radtabl, j, size);
				if (mrad < 0) mrad = Math.max(bin.getWidth(), bin.getHeight());
				radtabl[j] = mrad;
				lxpts[j] = cx + (float) (mrad * Math.cos(ang));
				lypts[j] = cy + (float) (mrad * Math.sin(ang));
			}
		}

		/// get center position
		float fcx = 0;
		float fcy = 0;
		float lcx = 0;
		float lcy = 0;
		if (get_contours != 1)
		{
			fcx = util.meanPositiveTab(fxpts);
			fcy = util.meanPositiveTab(fypts);
			/// scale to imp image size
			fcx = (float) (fcx * wratio);
			fcy = (float) (fcy * hratio);
		}
		if (get_contours != 0)
		{
			lcx = util.meanPositiveTab(lxpts);
			lcy = util.meanPositiveTab(lypts);
			/// scale to imp image size
			lcx = (float) (lcx * wratio);
			lcy = (float) (lcy * hratio);
		}

		/// scale the radius to image size
		float[] frads = new float[fxpts.length];
		float[] lrads = new float[lxpts.length];
		for (int m = 0; m < fxpts.length; m++) {
			if ( get_contours != 1 )
				frads[m] = (float) Math.sqrt((double) ((fxpts[m] * wratio - fcx) * (fxpts[m] * wratio - fcx) + (fypts[m] * hratio - fcy) * (fypts[m] * hratio - fcy)));
			if ( get_contours != 0 )
				lrads[m] = (float) Math.sqrt((double) ((lxpts[m] * wratio - lcx) * (lxpts[m] * wratio - lcx) + (lypts[m] * hratio - lcy) * (lypts[m] * hratio - lcy)));
		}

		/// smooth, remove extreme rads and replace by neighbors
		ang = 0;
		float[] res = new float[2];
		for (int j = 0; j < nang; j++) {
			ang = ang + dang;
			if ( get_contours != 1 )
			{
				res = smoothExtremeRadius(fcx, fcy, ang, frads, j, true, true);
				fxpts[j] = res[0];
				fypts[j] = res[1];
			}
			if ( get_contours != 0 )
			{
				res = smoothExtremeRadius(lcx, lcy, ang, lrads, j, true, true);
				lxpts[j] = res[0];
				lypts[j] = res[1];
			}
		}
		res = null;

		/// Construct and add the two Roi
		if (get_contours != 1)
		{
			Roi froi = new PolygonRoi(fxpts, fypts, Roi.POLYGON);
			froi.setImage(imp);
			froi.setPosition(slice);
			imp.setRoi(froi);
			froi.setName("zp_" + (slice) + "-in");
			rm.addRoi(froi);
		}
		if (get_contours != 0)
		{
			Roi lroi = new PolygonRoi(lxpts, lypts, Roi.POLYGON);
			lroi.setImage(imp);
			lroi.setPosition(slice);
			imp.setRoi(lroi);
			lroi.setName("zp_" + (slice) + "-out");
			rm.addRoi(lroi);
		}
	}

	public void getZPFromUnet(ImagePlus bin) 
	{
		if (visible) bin.show();

		// get ratio of sizes
		double wratio = ((double) (imp.getWidth())) / bin.getWidth();
		double hratio = ((double) (imp.getHeight())) / bin.getHeight();
		float rad = (float) (Math.min(bin.getWidth(), bin.getHeight()));

		// clean small areas
		cleanSmallRois(bin);
		if (visible) {
			bin.hide();
			imp.hide();
		}
		for (int i = 1; i <= imp.getNSlices(); i++) {
			IJ.showStatus("Refine ZP Rois... " + i + "/" + imp.getNSlices());
			getRoisSlice(i, bin, wratio, hratio, rad);
		}
		util.close(bin);
	}


    /** Find approximate location and size of ZP to run netork only locally */
    public ImagePlus localizeAndRunZP(String inname)
    {
        ImagePlus dup = imp.duplicate();
        IJ.run(dup, "Gaussian Blur...", "sigma=2 stack");
		IJ.run(dup, "8-bit", "");
        IJ.run(dup, "Variance...", "radius=10 stack"); 
        IJ.setAutoThreshold(dup, "Mean dark");
        Prefs.blackBackground = true;
        IJ.run(dup, "Convert to Mask", "method=Mean background=Dark calculate black");
        IJ.run(dup, "Fill Holes", "stack");
            
        int nslices = imp.getNSlices();
        int[] debx = new int[nslices];
        int[] deby = new int[nslices];
        int[] orig_size = new int[nslices];
        int[] zpos = new int[nslices];
        int cropsize = 350;
        ImageStack cropstack = new ImageStack(cropsize, cropsize);
            
        // localize oocyte and copy to image to analyse
        for (int i=1; i<= nslices; i++)
        {
                dup.setSlice(i);
                IJ.run(dup, "Analyze Particles...", "size=100-Infinity clear include add");
                double maxlength = 0;
                int indmax = 0;
                for (int j=0; j<rm.getCount(); j++)
                {
                    Roi cur = rm.getRoi(j);
                    double curl = cur.getFeretsDiameter();
                    if (curl > maxlength)
                    {
                        maxlength = curl;
                        indmax = j;
                    }
                }
                Roi best = rm.getRoi(indmax);
                maxlength = maxlength*0.75;
                orig_size[i-1] = (int)Math.floor(maxlength*2);
                double[] cent = best.getContourCentroid();
                debx[i-1] = (int)Math.floor(cent[0]- maxlength);
                deby[i-1] = (int)Math.floor(cent[1]- maxlength);
                zpos[i-1] = best.getZPosition();
                
                imp.setSlice(i);
                imp.setRoi(debx[i-1], deby[i-1], orig_size[i-1], orig_size[i-1]);
                ImagePlus cropped = imp.crop();
                cropped = cropped.resize(cropsize, cropsize, "bilinear");
                cropstack.addSlice(cropped.getProcessor());
        }
        dup.close();
        rm.reset();
            
        // Segment the cropped images
        ImagePlus impcrop = new ImagePlus("cropped", cropstack);               
        ImagePlus unet = net.runUnet(impcrop, dir+inname, nnet, modeldir, 800, visible);
        if ( visible ) unet.show();
                
        IJ.showStatus("Binary to Rois...");
        IJ.run(unet, "Select None", "");
            
        ImagePlus dupbin = unet.duplicate();
        IJ.setRawThreshold(unet, threshold, 255, null);
        Prefs.blackBackground = true;

        IJ.run(unet, "Convert to Mask", "method=Default background=Light black");
        ImageStatistics binstats = unet.getStatistics(Measurements.MEAN);
        if ( binstats.mean <= 10 )
        {
			util.close(unet);
			unet = new Duplicator().run(dupbin);
			IJ.setRawThreshold(unet, 1, 255, null);
			Prefs.blackBackground = true;
			IJ.run(unet, "Convert to Mask", "method=Default background=Light black");
        }
        util.close(dupbin);
            
        // Copy of results to original image sized binary
        ImagePlus bin = IJ.createImage("HyperStack", "8-bit grayscale-mode", imp.getWidth(), imp.getHeight(), 1, imp.getNSlices(), 1);
        for ( int i = 0; i < nslices; i++ )
        {
               unet.setSlice(zpos[i]);
               IJ.run(unet, "Select None", "");
               ImagePlus crop = unet.crop();
               crop = crop.resize(orig_size[i], orig_size[i], "bilinear");
               crop.copy();
               bin.setSlice(zpos[i]);
               bin.setRoi(debx[i], deby[i], orig_size[i], orig_size[i]);
               bin.paste();
               IJ.run(bin, "Select None", "");
               util.close(crop);
        }
        util.close(unet);
                
        IJ.setRawThreshold(bin, 1, 255, null);
        Prefs.blackBackground = true;
        if ( bin.isInvertedLut() )
        {
			IJ.run(bin, "Invert LUT", "stack");
        }

        ImageStatistics stats = bin.getStatistics(Measurements.MEAN);
		if ( stats.mean > 175 )
		{
			IJ.run(bin, "Invert", "stack");
		}
		IJ.run("Select None");
		return bin;
    }
        

	/** Get ZP contours: run the neural networks and refine the detection */
	public void getZP(String inname) 
	{
		IJ.log("Doing " + dir + inname);
		IJ.run("Close All", "");
		rm.reset();

		String imgname = dir + inname;
		openResetImage(imgname);
		util.reOrder(imp);

        IJ.showStatus("Segment ZP with neural networks...");    
		ImagePlus unet = null;
    	if (locate)
    	{
            unet = localizeAndRunZP(inname);
        }
		else
		{
			// run neural network for segmentation
			unet = net.runUnet(imp, dir + inname, nnet, modeldir, 800, visible);
		}
		// extract contours from the binary image, smooth a little
		IJ.showStatus("Refine ZP Rois...");
		getZPFromUnet(unet);
		IJ.run(imp, "Select None", "");
		rm.runCommand(imp, "Deselect");
		String purinname = inname.substring(0, inname.lastIndexOf('.'));
		rm.runCommand("Save", dir + "contours" + File.separator + purinname + "_ZP.zip");
		util.close(imp);
	}

	/** \brief Dialog window 
	  @return true if no pb, false else
	  */
	public boolean getParameters() {
		GenericDialogPlus gd = new GenericDialogPlus("Get Zona Pellucida - Options", IJ.getInstance());
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
		gd.setFont(boldy);
		//gd.addMessage("----------------------------------------------------------------------------------------------- ");

		gd.addChoice( "ZP contours:", new String[] { "Both", "Outer only", "Inner only" }, "Both");
		gd.addNumericField("nb_networks :", nnet);
		gd.addCheckbox("visible_mode", visible);
        gd.addCheckbox("locate", locate);
		gd.setBackground(new Color(100, 140, 170));
		gd.setInsets​(-100, 240, 0);
		ImagePlus iconimg = new ImagePlus();
		iconimg.setImage(icon.getImage());
		gd.addImage(iconimg);

		gd.addDirectoryField("model_path:", modeldir);
		gd.addDirectoryField("images_directory:", dir);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		contours = gd.getNextChoice();
		nnet = (int) gd.getNextNumber();
		visible = gd.getNextBoolean();
		locate = gd.getNextBoolean();
		modeldir = gd.getNextString();
		dir = gd.getNextString();

		return true;
	}

	public void run(String arg) {
		modeldir = IJ.getDirectory("imagej") + File.separator + "models" + File.separator + arg + File.separator;

		// get parameters, initialize
		if (!getParameters()) return;
		IJ.run("Close All");
		rm = RoiManager.getInstance();
		if (rm == null)
			rm = new RoiManager();
		rm.reset();
		util = new Utils();

		if (!dir.endsWith(File.separator)) {
			dir = dir + File.separator;
		}
		if (!modeldir.endsWith(File.separator)) {
			modeldir = modeldir + File.separator;
		}
		net = new Network();
		net.init();

		// Performs on all images in chosen directory
		File thedir = new File(dir);
		File[] fileList = thedir.listFiles();
		File directory = new File(dir + "contours");
		if (!directory.exists())
			directory.mkdir();

		for (File fily : fileList) {
			if (fily.isFile()) {
				String inname = fily.getName();
				int j = inname.lastIndexOf('.');
				if (j > 0) {
					String extension = inname.substring(j);
					if (extension.equals(".tif") | extension.equals(".TIF") | extension.equals(".png") | extension.equals(".jpg") | extension.equals(".JPG")) {
						getZP(inname);
					}
					System.gc(); // garbage collector
				}
			}
		}
		net.end();
		net = null;
		System.gc();
	}
}
