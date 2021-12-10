package cirb.oocytor;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.*;
import java.io.*;
import java.awt.*;
import javax.swing.ImageIcon;


/**
 * \brief Measures different features, ask user to choose which ones
 * 
 * Oocyte, ZP, structures, PIV, texture...
 * @author gaelle Letort, Collège de France
*
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
*/
public class MeasureFeatures implements PlugIn 
{
	ImagePlus imp;
	Calibration cal;
	RoiManager rm;
	Utils util;
	String dir;
	String resdir = "";
        String purname;
        String inname;

        // Handles black edge by looking at Rois of erased areas
	Roi[] erased;
	boolean erasing;
	Roi outside;
		
	// Which actions to do
	boolean oocyte = true;
	boolean zp = true;
        boolean periv = true;
	boolean fluct = true;
	boolean texture = true;
	boolean lbp = true;
	boolean piv = true;
	boolean spatial = true;
	boolean zpstruc = true;
	
	// parameters
	double scalexy = 0.5; // one pixel in um
	double timeoff = 0; // start time point
	double dtime = 0.05; // time between frames in h
	double sizexy = 0.25; // size of image in pixel for texture
	double pivsize = 0.227; // size of image in pixel for piv
	int maxslice = -1; // don't do all slices
        
        int precision = 5; // number of digits

        // decoration
        final ImageIcon icon = new ImageIcon(this.getClass().getResource("/oo_logo.png"));
       
        
	/** \brief Dialog window */
	public boolean getParameters()
	{

		GenericDialog gd = new GenericDialog("Options", IJ.getInstance() );
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
                gd.setFont(boldy);
        	
                gd.addMessage("Parameters", boldy);
		gd.addNumericField("scale_xy", scalexy, 4);
		gd.addNumericField("time_tonebd", timeoff, 4);
		gd.addNumericField("dt", dtime, 4);
		gd.addNumericField("texture_size_xy", sizexy, 4);
		gd.addNumericField("piv_size_xy", pivsize, 4);
		gd.addNumericField("max_slice", maxslice, 1);
		gd.addMessage("---------------------------------------------------------- ");
		gd.addMessage("Measure oocyte features", boldy);
		gd.addCheckbox("oocyte_feature", true);
		gd.addCheckbox("zp_feature", true);
                gd.addCheckbox("periv_feature", true);
		gd.addCheckbox("cortex_fluctuation", true);
		gd.addCheckbox("image_texture", true);
		gd.addCheckbox("local_binary_pattern", true);
		gd.addCheckbox("get_piv", true);
		gd.addCheckbox("spatial", true);
		gd.addCheckbox("zp_structure", true);

                //gd.setBackground(new Color(75,75,91));
                //gd.setForeground(new Color(255,255,255));
                gd.setBackground(new Color(100,140,170));
                gd.setInsets​(-120, 250, 0);
                ImagePlus iconimg = new ImagePlus();
                iconimg.setImage(icon.getImage());
                gd.addImage(iconimg);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		scalexy = gd.getNextNumber();
		timeoff = gd.getNextNumber();
		dtime = gd.getNextNumber();
		sizexy = gd.getNextNumber();
		pivsize = gd.getNextNumber();
		maxslice = (int) gd.getNextNumber();
		
                oocyte = gd.getNextBoolean();
		zp = gd.getNextBoolean();
                periv = gd.getNextBoolean();
		fluct = gd.getNextBoolean();
		texture = gd.getNextBoolean();
		lbp = gd.getNextBoolean();
		piv = gd.getNextBoolean();
		spatial = gd.getNextBoolean();
		zpstruc = gd.getNextBoolean();

                dir = IJ.getDirectory("Choose images directory:");	
		return true;
	}

	
	/** \brief Shape and intensity inside oocyte */
	public void measureOocyte()
	{
		openImageRois(true, true, -1, true);	
		int nrois = rm.getCount();
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		// calculate mean motion
		for ( int i=0; i < nrois; i++ )
		{
                    rm.select(i);
                    Roi cur = rm.getRoi(i);
                    int slice = cur.getPosition();

                    // translate if is on the side
                    Roi cort = (Roi) cur.clone();
                    int goOut = util.goingOutRoi(imp, cort);
                    if ( goOut < 0 ) cort = util.translateRoi(imp, cort, goOut );
                    imp.setSlice(slice);
                    imp.setRoi(cort);	
                    ImageStatistics mystat = imp.getAllStatistics();

                    myrt.incrementCounter();
                    myrt.addValue("Time", timeoff+(slice-1)*dtime);
                    myrt.addValue("OoArea", mystat.area*scalexy*scalexy);
                    myrt.addValue("OoMajorAxisLength", mystat.major*scalexy);
                    myrt.addValue("OoMinorAxisLength", mystat.minor*scalexy);
                    myrt.addValue("OoEllAspectRatio", mystat.major/mystat.minor);
                    myrt.addValue("OoFeretDiam", cort.getFeretsDiameter()*scalexy);
                    double perim = cort.getLength();
                    myrt.addValue("OoPerimeter", perim*scalexy);
                    myrt.addValue("OoCircularity", 4*Math.PI*mystat.area/(perim*perim));
                    myrt.addValue("OoRoundness", 4*mystat.area/(Math.PI*mystat.major*mystat.major));
                    Polygon hull = cort.getConvexHull();
                    double ahull = util.polyarea(hull);
                    myrt.addValue("OoConvexity", mystat.area/ahull);
                    RoiCurvature rc = new RoiCurvature(util);
                    double meanRad = perim/(2*Math.PI);
                    rc.getCurvature(cort, myrt, scalexy, "Oo", meanRad);
                    //LocoEfa loco = new LocoEfa();
                    //loco.getLocoEFA( cort, myrt );

                    //normalise intensities to outside mean
                    imp.setRoi(outside);
                    ImageStatistics exstat = imp.getStatistics();

                    // get clean Roi
                    rm.runCommand(imp,"Deselect");
                    Roi cleaned; 
                    if ( erasing ) cleaned = util.getCleanedRoi( rm, imp, cur, erased, slice );
                    else cleaned = cur;
                    imp.setSlice(slice);
                    cleaned.setImage(imp);
                    imp.setRoi(cleaned);
                    mystat = imp.getAllStatistics();

                    myrt.addValue("OoKurtosis", mystat.kurtosis);
                    myrt.addValue("OoNormMean", mystat.mean/exstat.mean);
                    myrt.addValue("OoNormStd", mystat.stdDev/exstat.mean);
                    myrt.addValue("OoNormCoefVar", mystat.stdDev/mystat.mean);
                    myrt.addResults();
               }
		myrt.save(resdir+File.separator+purname+"_oocyteFeatures.csv");
        	imp.changes = false;
		imp.close();
	}




	/** \brief Measure ZP size, shape, intensities */
	public void measureZPFeatures()
	{
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		openImageRois(true, true, -1, true);
                Roi[] cortex = rm.getRoisAsArray();
                int nrois = cortex.length;
		rm.reset();
		IJ.run(imp, "Select None", "");
		// get ZP Rois and load cortex Roi in RoiManager
		rm.runCommand("Open", dir+"contours"+File.separator+purname+"_ZP.zip");
		Roi[] zps = rm.getRoisAsArray();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		IJ.run(imp, "Select None", "");

		double iarea, ell, oarea;	
		// Measure features in time
		for ( int i = 0; i < nrois; i++ )
		{
			Roi cur = cortex[i];
			int slice = cur.getPosition();
			imp.setSlice(slice);
			myrt.incrementCounter();
			myrt.addValue("Time", timeoff+(slice-1)*dtime);
			
			// get inside outside zp Roi
			Roi trcur = (Roi) cur.clone();
			Roi in = zps[i*2];
			Roi out = zps[i*2+1];
			
			Roi zpin = (Roi) in.clone();
			Roi zpout = (Roi) out.clone();
			// translate if is on the side
			int goOut = util.goingOutRoi(imp, zpout);
			if ( goOut < 0 )
			{
				zpin = util.translateRoi(imp, zpin, goOut );
				zpout = util.translateRoi(imp, zpout, goOut );
				trcur = util.translateRoi(imp, trcur, goOut );
			}

			// measure in ZP
			imp.setRoi(zpin);
			iarea = (zpin.getStatistics()).area;
			ell = (zpin.getStatistics()).major/(zpin.getStatistics()).minor;
			myrt.addValue("ZPPerimIn", zpin.getLength()*scalexy);
                        double meanRadius = zpin.getLength()/(2*Math.PI);
			myrt.addValue("ZPEllAspectRatioIn", ell);
			RoiCurvature rc = new RoiCurvature(util);
                        rc.getCurvature(zpin, myrt, scalexy, "ZPIn", meanRadius);
			
			// get outside zp Roi
			imp.setRoi(zpout);
			oarea = zpout.getStatistics().area;
			ell = (zpout.getStatistics()).major/(zpout.getStatistics()).minor;
			double orad = Math.sqrt(oarea/Math.PI);
			myrt.addValue("ZPPerimOut", zpout.getLength()*scalexy);
			myrt.addValue("ZPEllAspectRatioOut", ell);
                        meanRadius = zpout.getLength()/(2*Math.PI);	
			rc.getCurvature(zpout, myrt, scalexy, "ZPOut", meanRadius);
                        
			// thickness measures
			double[] thick = util.roisThickness( zpin, zpout );
			myrt.addValue("ZPArea", (oarea-iarea)*scalexy*scalexy);
			myrt.addValue("ZPThicknessMean", thick[2]*scalexy);
			myrt.addValue("ZPThicknessStd", thick[3]*scalexy);
                        myrt.addValue("ZPThicknessCoefVar", thick[3]/thick[2]);
			myrt.addValue("ZPThicknessMin", thick[0]*scalexy);
			myrt.addValue("ZPThicknessMax", thick[1]*scalexy);
		
			// ZP together
			rm.reset();
			rm.addRoi(in);
			rm.addRoi(out);
			IJ.run(imp, "Select None", "");
			rm.runCommand(imp,"Deselect");
			rm.runCommand(imp,"XOR");
			Roi tzp = imp.getRoi();
                        // pb in the tzp contours, take whole image
                        if ( tzp == null ) tzp = new Roi(0, 0, imp.getWidth(), imp.getHeight());

			// intensities
			imp.setRoi(outside);
			ImageStatistics is = outside.getStatistics();
			double omean = is.mean;
                        
			// get clean Roi
			rm.runCommand(imp,"Deselect");
			//System.out.println("so far so good 2");
			Roi cleaned; 
			if ( erasing )
			{
				cleaned = util.getCleanedRoi( rm, imp, tzp, erased, slice );
			}
			else cleaned = tzp;
			imp.setSlice(slice);
			cleaned.setImage(imp);
			imp.setRoi(cleaned);
			
			is = cleaned.getStatistics();
			myrt.addValue("ZPKurtosis", is.kurtosis);
			myrt.addValue("ZPNormMean", is.mean/omean);
			myrt.addValue("ZPNormStd", is.stdDev/omean);
                        myrt.addValue("ZPNormCoefVar", is.stdDev/is.mean);
			
			double carea = (trcur.getStatistics()).area;
			double rad = Math.sqrt(carea/Math.PI);
			myrt.addValue("ZPOoRadiusRatio", orad/rad);
			
		}

		myrt.addResults();
		myrt.save(resdir+File.separator+purname+"_zpFeatures.csv");
      
                util.close(imp);
	}
        
        
	/** \brief Measure Perivitelin space size, shape */
	public void measurePerivFeatures()
	{
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		
		openImageRois(true, true, -1, true);
		Roi[] cortex = rm.getRoisAsArray();
		int nrois = cortex.length;
		rm.reset();
		IJ.run(imp, "Select None", "");
		// get ZP Rois and load cortex Roi in RoiManager
		rm.runCommand("Open", dir+"contours"+File.separator+purname+"_ZP.zip");
		Roi[] zps = rm.getRoisAsArray();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		IJ.run(imp, "Select None", "");

		double iarea;	
		// Measure features in time
		for ( int i = 0; i < nrois; i++ )
		{
			Roi cur = cortex[i];
			int slice = cur.getPosition();
			imp.setSlice(slice);
			myrt.incrementCounter();
			myrt.addValue("Time", timeoff+(slice-1)*dtime);
			
			// get inside outside zp Roi
			Roi trcur = (Roi) cur.clone();
			Roi in = zps[i*2];
			
			Roi zpin = (Roi) in.clone();
			// translate if is on the side
			int goOut = util.goingOutRoi(imp, zpin);
			if ( goOut < 0 )
			{
				zpin = util.translateRoi(imp, zpin, goOut );
				trcur = util.translateRoi(imp, trcur, goOut );
			}

			// measure in ZP
			imp.setRoi(zpin);
			iarea = (zpin.getStatistics()).area;        
			// Oo area
			IJ.run(imp, "Select None", "");
			double carea = (trcur.getStatistics()).area;
			
			// perivitelline space
			double parea = iarea-carea;
			if ( parea < 0 )
				parea = 0;
			myrt.addValue("PerivArea", parea*scalexy*scalexy);
			double prad = Math.sqrt(parea/Math.PI);
			myrt.addValue("PerivOoAreaRatio", parea/carea);
			double[] thickPeriv = util.roisThickness( trcur, zpin );
			myrt.addValue("PerivThicknessMean", thickPeriv[2]*scalexy);
			myrt.addValue("PerivThicknessMin", thickPeriv[0]*scalexy);
			myrt.addValue("PerivThicknessMax", thickPeriv[1]*scalexy);
			myrt.addValue("PerivThicknessStd", thickPeriv[3]*scalexy);
                        myrt.addValue("PerivThicknessCoefVar", thickPeriv[3]/thickPeriv[2]);
		}

		myrt.addResults();
		myrt.save(resdir+File.separator+purname+"_perivFeatures.csv");
              util.close(imp);
	}

        // don't consider that part of oocyte can be outside image
	public void calcShapeFluctuations()
	{
		// open, reset
		String imgname = dir+inname;
		imp = IJ.openVirtual(imgname);
                purname = inname.substring(0, inname.lastIndexOf('.'));
		//imp = IJ.getImage();
		util.initCalibration(imp);
		rm.runCommand(imp,"Deselect");
		rm.reset();
		IJ.run(imp, "Select None", "");
		rm.runCommand("Open", dir+"contours"+File.separator+purname+"_UnetCortex.zip");

		// do by angles
		int nang = 300;
		double ang = 0;
		double dang = 2*Math.PI/nang;
		int x, y;
		double rad = 0;
		int nt = rm.getCount();
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		// get radius
		for (int i=0; i < nt; i++)
		{
			Roi cur = rm.getRoi(i);
			double[] cent = cur.getContourCentroid();
			myrt.incrementCounter();
			ang = 0;
                            
			for ( int a = 0; a < nang; a++ )
			{
				x = (int) cent[0];
				y = (int) cent[1];
				rad = rad/4.0;  // begins closer to previous radius
				while ( cur.contains(x,y) )
				{
					x = (int) (cent[0] + rad*Math.cos(ang));
					y = (int) (cent[1] + rad*Math.sin(ang));
					rad = rad + 0.05;
				}
				myrt.addValue("Time", timeoff+(cur.getPosition()-1)*dtime);
				myrt.addValue("Ang"+a, rad*scalexy);
				ang = ang + dang;
			}

			myrt.addResults();
		}

		myrt.save(resdir+File.separator+purname+"_fluctuationResults.csv");

		imp.changes = false;
		imp.close();
	}



	public void measureImageTexture()
	{
		double factxy = scalexy/sizexy;
		openImageRois( true, true, factxy, false);
		Roi[] cortex = rm.getRoisAsArray();
		rm.reset();
		rm.runCommand(imp,"Deselect");

		// get tzp Roi
		rm.runCommand("Open", dir+"contours"+File.separator+purname+"_ZP.zip");
		rm.runCommand(imp,"Deselect");
		util.rescaleRois(rm, imp,factxy);
		Roi[] zps = rm.getRoisAsArray();
		IJ.run(imp, "Select None", "");
		rm.reset();

		GLCMTexture glcm = new GLCMTexture();
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		

		IJ.run(imp, "8-bit", "");
		MoranIndex mi = new MoranIndex();
		int radmoran = 50;
		
		for ( int i=0; i < cortex.length; i++ )
		{
			IJ.showProgress(i, cortex.length );
			// texture inside cortex
			Roi cur = cortex[i];
			int slice = cur.getPosition();
			imp.setSlice(slice);
			imp.setRoi(cur);	
			// get clean Roi
			rm.runCommand(imp,"Deselect");
			Roi cleaned; 
			if ( erasing )
			{
				cleaned = util.getCleanedRoi( rm, imp, cur, erased, slice );
			}
			else cleaned = cur;
			imp.setSlice(slice);
			cleaned.setImage(imp);
			cleaned.setPosition(slice);
			imp.setRoi(cleaned);
			
			myrt.incrementCounter();
			myrt.addValue("Time", timeoff+(slice-1)*dtime);
			ImageProcessor ip = imp.getProcessor();
			glcm.calcTexture(ip, cleaned, myrt, "Oo");
		
			// create smaller ROI	
			rm.runCommand(imp,"Deselect");
			double[] cent = cur.getContourCentroid();
			Roi mor = new OvalRoi((cent[0]-radmoran),(cent[1]-radmoran),2*radmoran,2*radmoran);
			imp.setRoi(mor);
			ImageProcessor ipm = imp.getProcessor();
			double res = mi.run(5, ipm, mor);
			myrt.addValue("OoMoranIndexK5", res);
			res = mi.run(20, ipm, mor);
			myrt.addValue("OoMoranIndexK20", res);

			// texture inside foll zone only
			rm.reset();
			imp.setSlice(slice);
			imp.setRoi(zps[i*2+1]);
			rm.runCommand(imp,"Add");
			imp.setRoi(zps[i*2]);
			rm.runCommand(imp,"Add");
			rm.runCommand(imp,"Deselect");
			rm.runCommand(imp,"XOR");
			Roi tzp = imp.getRoi();
			// get clean Roi
			rm.runCommand(imp,"Deselect");
			cleaned = null; 
			if ( erasing )
			{
				cleaned = util.getCleanedRoi( rm, imp, tzp, erased, slice );
			}
			else cleaned = tzp;
			imp.setSlice(slice);
			cleaned.setImage(imp);
			cleaned.setPosition(slice);
			imp.setRoi(cleaned);
			IJ.run(imp, "Clear Outside", "slice");	
			ip = imp.getProcessor();
			glcm.calcTexture(ip, cleaned, myrt, "ZP");
			
			myrt.addResults();
		}
		myrt.save(resdir+File.separator+purname+"_textureFeatures.csv");
        	imp.changes = false;
		imp.close();
	}

	public void measureImageLBP()
	{
		double factxy = scalexy/sizexy;
		openImageRois( true, true, factxy, false);
		// get cortex Rois
		rm.runCommand(imp,"Deselect");
		Roi[] cortex = rm.getRoisAsArray();
		rm.reset();
		
		// get tzp Roi
		rm.runCommand("Open", dir+"contours"+File.separator+purname+"_ZP.zip");
		rm.runCommand(imp,"Deselect");
		util.rescaleRois(rm, imp,factxy);
		Roi[] zps = rm.getRoisAsArray();
		rm.runCommand(imp,"Deselect");
		rm.reset();

		LBP locbp = new LBP();
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		

		IJ.run(imp, "8-bit", "");
		for ( int i=0; i < cortex.length; i++ )
		{
			// texture inside cortex
			Roi cur = cortex[i];
			int slice = cur.getPosition();
			imp.setSlice(slice);
			imp.setRoi(cur);	
			myrt.incrementCounter();
			//myrt.addValue("Slice", slice);
			myrt.addValue("Time", timeoff+(slice-1)*dtime);
			
			// get clean Roi
			rm.runCommand(imp,"Deselect");
			Roi cleaned; 
			if ( erasing )
			{
				cleaned = util.getCleanedRoi( rm, imp, cur, erased, slice );
			}
			else cleaned = cur;
			imp.setSlice(slice);
			cleaned.setImage(imp);
			cleaned.setPosition(slice);
			imp.setRoi(cleaned);
			ImageProcessor ip = imp.getProcessor();
			locbp.calcLBP(ip, cleaned, myrt, "Oo");

			// texture inside foll zone only
			rm.reset();
			imp.setSlice(slice);
			rm.addRoi(zps[i*2+1]);
			rm.addRoi(zps[i*2]);
			rm.runCommand(imp,"XOR");
			Roi tzp = imp.getRoi();
			imp.setRoi(tzp);
			// get clean Roi
			rm.runCommand(imp,"Deselect");
			cleaned = null; 
			if ( erasing )
			{
				cleaned = util.getCleanedRoi( rm, imp, tzp, erased, slice );
			}
			else cleaned = tzp;
			imp.setSlice(slice);
			cleaned.setImage(imp);
			cleaned.setPosition(slice);
			imp.setRoi(cleaned);
			ip = imp.getProcessor();
			locbp.calcLBP(ip, cleaned, myrt, "ZP");

			myrt.addResults();
		}
		myrt.save(resdir+File.separator+purname+"_lbpFeatures.csv");

		imp.changes = false;
		imp.close();
	}

        /** \brief Try to look at spatial organisation */
	public void measureSpatialFeatures()
	{
		openImageRois( true, true, -1, false);
		
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		for ( int i=0; i < rm.getCount(); i++ )
		{
			rm.select(i);
			Roi cur = rm.getRoi(i);
                        int slice = cur.getPosition();
			myrt.incrementCounter();
			//myrt.addValue("Slice", slice);
			myrt.addValue("Time", timeoff+(slice-1)*dtime);
			structureNearCortex( imp, i, myrt );
			myrt.addResults();
		}

		myrt.save(resdir+File.separator+purname+"_spatialFeatures.csv");
	
		imp.changes = false;
		imp.close();
	}
	
	public void structureNearCortex(ImagePlus img, int i, ResultsTable myrt)
	{
		rm.select(i);
		Roi cur = rm.getRoi(i);
		int slice = cur.getPosition();
		img.setSlice(slice);
		IJ.run(img, "Select None", "");
		ImagePlus imslice = img.crop( slice+"-"+slice); 
		img.setRoi(cur);	
		
		// if some pixels inside ROI have been erased
		Roi cleaned; 
		if ( erasing )
		{
			cleaned = util.getCleanedRoi( rm, imp, cur, erased, slice );
		}
		else cleaned = cur;

		Structures struc = new Structures();
		struc.distanceStructuresToEdge(imslice, cur, cleaned, myrt);			
		
		IJ.run(imslice, "Select None", "");
		double[] part = struc.particleSizes(imslice, cur, cleaned);			
		myrt.addValue("OoParticleNumber", part[0]);
		myrt.addValue("OoParticleAverageSize", part[1]*scalexy*scalexy);
	
		imslice.changes = false;
		imslice.close();
	}

	public void measurePIV()
	{
		String tmpfile = resdir+File.separator+"PIVFile.txt";

		double factxy = scalexy/pivsize;
		openImageRois(true, false, factxy, false);
		
		// minimumRoi
		rm.runCommand(imp,"Deselect");
		rm.runCommand(imp,"AND");
		Roi cur = imp.getRoi();
		Roi cleaned; 
		if ( erasing )
		{
			cleaned = util.getCleanedRoi( rm, imp, cur, erased, -1);
		}
		else cleaned = cur;
		cleaned.setImage(imp);
		imp.setRoi(cleaned);
		IJ.run(imp, "Clear Outside", "stack");
		IJ.run(imp, "Crop", "");
		rm.runCommand(imp,"Deselect");
		IJ.run(imp, "Select None", "");

		double[] cent = {imp.getWidth()/2.0, imp.getHeight()/2.0};
		double rad = (imp.getWidth()+imp.getHeight())/4.0;  // mean of radii

		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		

		int nslice = (maxslice>0)?maxslice:imp.getNSlices();
                PIV piver = new PIV();
		for ( int i = 1; i < nslice; i++ )
		{	
			// run PIV on two consecutives images
			ImagePlus two = imp.crop(""+i+"-"+(i+1));
			two.show();
			
                        piver.runOneTimeDiff(two, cent, rad, tmpfile);
                        if (i ==1 )
                        {
                             myrt.incrementCounter();
                             myrt.addValue("Time", timeoff);
                             piver.writePIVResults(-1, myrt, pivsize/dtime);
                        }
                        myrt.incrementCounter();
                        myrt.addValue("Time", timeoff+(i)*dtime);
                        piver.writePIVResults(1, myrt, pivsize/dtime);
                        myrt.addResults();
        
			// close all
			two.changes = false;
			two.close();
		}
		myrt.incrementCounter();
		//myrt.addValue("Slice", imp.getNSlices());
		myrt.addValue("Time", timeoff+(imp.getNSlices())*dtime);
                piver.writePIVResults(0, myrt, pivsize/dtime);
		myrt.addResults();

		myrt.save(resdir+File.separator+purname+"_pivFeatures.csv");
	
		imp.changes = false;
		imp.close();
		IJ.run("Close All", "");
	}

	

	/** \brief Measure ZP tube structures, all and vertical */
	public void measureZPStructures()
	{
		ResultsTable myrt = new ResultsTable();
                myrt.setPrecision​(precision);
		
		
		openImageRois(false, true, -1, false);
		rm.reset();
		IJ.run(imp, "Select None", "");
		// get ZP Rois and load cortex Roi in RoiManager
		rm.runCommand("Open", dir+"contours"+File.separator+purname+"_ZP.zip");
		Roi[] zps = rm.getRoisAsArray();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		IJ.run(imp, "Select None", "");
		
		// Measure features in time
		int ntimes = (int) (zps.length/2);
		for ( int i = 0; i < ntimes; i++ )
		{
			Roi in = zps[i*2];
			Roi out = zps[i*2+1];
			
			int slice = in.getPosition();
			myrt.incrementCounter();
			myrt.addValue("Time", timeoff+(slice-1)*dtime);
			//imp.setSlice(slice);
			IJ.run(imp, "Select None", "");
			ImagePlus ip = imp.crop(slice+"-"+slice);
			ip.show();
		
			Roi mean = util.meanRoi( in, out );
                        double meand = util.roiMeanThickness(in, out);
                        StraightZP straighter = new StraightZP(meand, (int)(0.5/scalexy), (int)(1.2/scalexy));
			straighter.createStraight(ip, mean);
			util.close(ip);
			straighter.clearZone(rm);
			straighter.getStructures(myrt);
                        straighter.close();
		}
		
		util.close(imp);
		myrt.addResults();
		myrt.save(resdir+File.separator+purname+"_zpTubeFeatures.csv");
	}

	public void openImageRois(boolean openCortex,  boolean openErased, double fact, boolean getOut)
	{
		// open, reset
		String imgname = dir+inname;
		
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
		util.initCalibration(imp);
		//rm.runCommand(imp,"Deselect");
               rm.reset();
		imp.show();
		IJ.run(imp, "Select None", "");

                purname = inname.substring(0, inname.lastIndexOf('.'));
		//get outside Roi
		if ( getOut )
                {
                    File roiFile = new File(dir+"contours"+File.separator+purname+"_ZP.zip");
                    int enl = (int) Math.floor(1.2/scalexy);
                    if (roiFile.isFile()) outside = util.getOutsideRoi(imp, fact, dir+"contours"+File.separator+purname+"_ZP.zip", rm, enl);
                    else 
                    {
                        enl = (int) Math.floor(15/scalexy);
                        outside = util.getOutsideRoi(imp, fact, dir+"contours"+File.separator+purname+"_UnetCortex.zip", rm, enl);
                    }
                }
                
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
                    File erasedZIP = new File(dir+"contours"+File.separator+purname+"_erased.zip");
                    erasing = false;
                    erased = null;
                    if ( erasedZIP.exists() )
                    {
                        rm.runCommand("Open", dir+"contours"+File.separator+purname+"_erased.zip");
                        erasing = true;
                        if ( fact > 0 )
                                util.rescaleRois(rm,imp,fact);
                        erased = rm.getRoisAsArray();
                        rm.reset();
                    }
		}

		if ( openCortex )
		{
                    rm.runCommand("Open", dir+"contours"+File.separator+purname+"_UnetCortex.zip");
                    if ( fact > 0 )
                            util.rescaleRois(rm,imp,fact);
                }
	}
        
        /** \brief Choose which measures to do according to selected cases in the dialog */
        public void measure()
        {
                if ( oocyte )
		{	
			IJ.run("Close All");
			measureOocyte();
		}
		if ( zp )
		{
			IJ.run("Close All");
			measureZPFeatures();
		}
                if ( periv )
		{
			IJ.run("Close All");
			measurePerivFeatures();
		}
		if ( fluct )
		{
                      IJ.run("Close All");
                      calcShapeFluctuations();
		}
		
		if ( texture )
		{
			IJ.run("Close All");
			measureImageTexture();
		}
		if ( lbp )
		{
			IJ.run("Close All");
			measureImageLBP();
		}
             
		if ( piv )
		{
			IJ.run("Close All");
			measurePIV();
		}
		
		if ( spatial )
		{
			IJ.run("Close All");
			measureSpatialFeatures();
		}
		if ( zpstruc )
		{
			IJ.run("Close All");
			measureZPStructures();
		}
	
        }

	public void run(String arg)
	{
		if ( !getParameters() ) { return; }
		IJ.run("Close All");

		rm = RoiManager.getInstance();
		if ( rm == null ) rm = new RoiManager();
		util = new Utils();

		resdir = dir+"measures";
		File directory = new File(resdir);
		if (! directory.exists())
			directory.mkdir();
		
                // Performs on all images in chosen directory
		File thedir = new File(dir); 
		File[] fileList = thedir.listFiles(); 
			
                for (File fily : fileList) 
                {
                    if ( fily.isFile() )
                    {
                        inname = fily.getName();
                        int j = inname.lastIndexOf('.');
                        if (j > 0)
                        {
                            String extension = inname.substring(j);
                            if ( extension.equals(".tif") | extension.equals(".TIF") | extension.equals(".png") | extension.equals(".jpg") | extension.equals(".JPG") )
                            {
                                measure();
                            }
                        
                        }
                    }
                   System.gc(); // garbage collector
                }
	}
                
		

}
