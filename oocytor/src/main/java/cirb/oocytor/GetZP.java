
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

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.*;
import java.io.*;

public class GetZP implements PlugIn 
{
	ImagePlus imp;
	Calibration cal;
	RoiManager rm;
	String dir = "/home/gaelle/Proj/Miv/Embryo/imgs100";
	String modeldir;
	Utils util;
        int nnet = 2;


	/** Initialisation of an image */
	public void openResetImage(String imgname)
	{
		String ext = imgname.substring(imgname.lastIndexOf('.'));
		if ( ext.equals(".tif") )
			imp = IJ.openVirtual(imgname);
		else
			imp = IJ.openImage(imgname);
		//imp = IJ.getImage();
		cal = util.initCalibration(imp);
		imp.show();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		util.unselectImage(imp);
	}

        /** \brief For radii with values very different from the others, most likely errors, replace them by local mean */
	public float[] smoothExtremeRadius( float cx, float cy, double ang, float[] rads, int ind, boolean remExtreme, boolean fit )
	{
		float[] res = new float[2];
		res[0] = (float) 0.0;
		res[1] = (float) 0.0;

		float mean = util.meanPositiveTab( rads );
                float stdPos = util.stdPositiveTab(rads, mean);
		float std = (float) 4*stdPos;
		float stdm = (float) 4*stdPos;

                // repace extreme radii by threshold radius
		if ( remExtreme )
		{
                    for ( int i = 0; i < rads.length; i++ )
                    {
			if ( rads[i] < 0 )
			{
				rads[i] = mean;
			}	
			if ( rads[i] > (mean+std) )
			{
				rads[i] = mean+std;
			}
			if ( rads[i] < (mean-stdm) )
			{
				rads[i] = mean-stdm;
			}
                    }
		}

		int size = 15;
		if ( fit )
		{
			size = 10;
		}
		double crad = util.meanCircularWindow( rads, ind, size, true);
		res[0] =  ((float) cx + (float) (crad*Math.cos(ang)));	
		res[1] =  ((float) cy + (float) (crad*Math.sin(ang)));
		return res;
	}
		
	
        /** \brief At given angle, find inner and outer limits of the positive area */
	public float[] findFirstLast( float cx, float cy, float radius, double ang, ImagePlus img )
	{
		float[] res = new float[4];
		res[0] = (float) 0.0;
		res[1] = (float) 0.0;
		res[2] = (float) 0.0;
		res[3] = (float) 0.0;
		Line myline = new Line(cx, cy, cx+radius*Math.cos(ang), cy+radius*Math.sin(ang));
		myline.setStrokeWidth(15);  // prev 30
		img.setRoi(myline);
		double[] vals = myline.getPixels();
		
		// find last
		int i = vals.length;
		while ( (vals[i-1] <= 200) && (i>1) )
			i--;
		if ( i<=2 )
			i = -10000;
		else
			i += 1;

		// find first
		int j = 0;
		while ( (vals[j+1]<=200) && (j<(vals.length-2)) )
			j++;
		if ( j>= (vals.length-4) )
			j = -10000;
		else
			j -= 1;

		res[0] = (float) cx + (float) (radius*Math.cos(ang) * j/vals.length);	
		res[1] = (float) cy + (float) (radius*Math.sin(ang) * j/vals.length);	
		res[2] = (float) cx + (float) (radius*Math.cos(ang) * i/vals.length);	
		res[3] = (float) cy + (float) (radius*Math.sin(ang) * i/vals.length);	

		// is out or against image limits
		if ( res[0] >= imp.getWidth()*0.99 || res[0] <= 3 )
			res[0] = -10000;
		if ( res[1] >= imp.getHeight()*0.99 || res[1] <= 3 )
			res[1] = -10000;
		if ( res[2] >= imp.getWidth()*0.99 || res[2] <= 3 )
			res[2] = -10000;
		if ( res[3] >= imp.getHeight()*0.99 || res[3] <= 3 )
			res[3] = -10000;
		
		return res;
	}

        /** \brief Remove small Rois from the binary image */
	public void cleanSmallRois(ImagePlus img)
	{
		IJ.run(img, "Select None", "");
		IJ.setRawThreshold(img, 1, 255, null);
		IJ.run(img, "Analyze Particles...", "clear add");
		util.keepRois(0, img);  // keep only biggest Roi 
		for ( int i = 0; i < rm.getCount(); i++ )
		{
			Roi cur = rm.getRoi(i);
			img.setSlice( cur.getPosition() );
			img.setRoi(cur);
			IJ.run(img, "Clear Outside", "slice");
		}
		IJ.run(img, "Select None", "");
		rm.runCommand(img,"Deselect");
		rm.reset();
	}

        /** \brief At each slice, calculate the iner and outer Rois from the binary image */
	public void getRoisSlice( int slice, ImagePlus bin, double wratio, double hratio, float rad )
	{
		imp.setSlice(slice);
		bin.setSlice(slice);
		
		int nang = 360;	
		double ang = 0;
		double dang = 2*Math.PI/nang;
		float[] fxpts = new float[nang];
		float[] fypts = new float[nang];
		float[] lxpts = new float[nang];
		float[] lypts = new float[nang];
		float cx = (float) (bin.getWidth()/2.0);
		float cy = (float) (bin.getHeight()/2.0);
		int nfpos = 0;
		int nlpos = 0;

		/// Find each first and last point at each angle
		// get local radii. Put negative values when missing
		float[] radtabf = new float[nang];
		float[] radtabl = new float[nang];
		for (int j=0; j<nang; j++)
		{	
				ang = ang + dang;
				float[] res = findFirstLast(cx, cy, rad, ang, bin);
				fxpts[j] = res[0];
				fypts[j] = res[1];
				lxpts[j] = res[2];
				lypts[j] = res[3];
				if ( fxpts[j]>0 && fypts[j]>0 )
					radtabf[j] = (float) Math.sqrt( (double) ((fxpts[j]-cx)*(fxpts[j]-cx) + (fypts[j]-cy)*(fypts[j]-cy)));
				else
					radtabf[j] = -100;
				if ( lxpts[j]>0 && lypts[j]>0 )
					radtabl[j] = (float) Math.sqrt( (double) ((lxpts[j]-cx)*(lxpts[j]-cx) + (lypts[j]-cy)*(lypts[j]-cy)));
				else
					radtabl[j] = -100;
		}
		
		/// replace negative (missing) values by local mean
		int size = 30;
		ang = 0;
		for (int j=0; j<nang; j++)
		{
			ang = ang + dang;
			if ( (radtabf[j]<=0) )
			{
				float mrad = util.meanCircularRadius( radtabf, j, size);
				if ( mrad<0 ) mrad = Math.max(bin.getWidth(), bin.getHeight());
                                radtabf[j] = mrad;
				fxpts[j] = cx + (float) (mrad * Math.cos(ang));
				fypts[j] = cy + (float) (mrad * Math.sin(ang));
			}		
			if ( (radtabl[j]<=0) )
			{
				float mrad = util.meanCircularRadius( radtabl, j, size);
                                if ( mrad<0 ) mrad = Math.max(bin.getWidth(), bin.getHeight());
				radtabl[j] = mrad;
				lxpts[j] = cx + (float) (mrad * Math.cos(ang));
				lypts[j] = cy + (float) (mrad * Math.sin(ang));
			}
		}

		/// get center position
		float fcx = util.meanPositiveTab( fxpts );
		float fcy = util.meanPositiveTab( fypts );
		float lcx = util.meanPositiveTab( lxpts );
		float lcy = util.meanPositiveTab( lypts );
		/// scale to imp image size
		fcx = (float) (fcx*wratio);
		fcy = (float) (fcy*hratio);
		lcx = (float) (lcx*wratio);
		lcy = (float) (lcy*hratio);

		/// scale the radius to image size
		float[] frads = new float[fxpts.length];
		float[] lrads = new float[lxpts.length];
		for ( int m = 0; m < fxpts.length; m++ )
		{
			frads[m] = (float) Math.sqrt( (double) ((fxpts[m]*wratio-fcx)*(fxpts[m]*wratio-fcx) + (fypts[m]*hratio-fcy)*(fypts[m]*hratio-fcy)));
			lrads[m] = (float) Math.sqrt( (double) ((lxpts[m]*wratio-lcx)*(lxpts[m]*wratio-lcx) + (lypts[m]*hratio-lcy)*(lypts[m]*hratio-lcy)));
		}
		
		/// smooth, remove extreme rads and replace by neighbors
		ang = 0;
		for (int j=0; j<nang; j++)
		{	
			ang = ang + dang;
			float[] res = smoothExtremeRadius(fcx, fcy, ang, frads, j, true, true);
			fxpts[j] = res[0];
			fypts[j] = res[1];
			float[] lres = smoothExtremeRadius(lcx, lcy, ang, lrads, j, true, true);
			lxpts[j] = lres[0];
			lypts[j] = lres[1];
		}

		/// Construct and add the two Roi
		Roi froi = new PolygonRoi( fxpts, fypts, Roi.POLYGON);
		froi.setImage(imp);
		froi.setPosition(slice);
		imp.setRoi(froi);
                froi.setName("zp_"+(slice)+"-in");
		rm.addRoi(froi);
		
		Roi lroi = new PolygonRoi( lxpts, lypts, Roi.POLYGON);
		lroi.setImage(imp);
		lroi.setPosition(slice);
		imp.setRoi(lroi);
                lroi.setName("zp_"+(slice)+"-out");
		rm.addRoi(lroi);
	}

	public void getZPFromUnet(ImagePlus bin)
	{   
            bin.show();
	    
            // get ratio of sizes
            double wratio = ((double)(imp.getWidth()))/bin.getWidth();
            double hratio = ((double)(imp.getHeight()))/bin.getHeight();
            float rad = (float) (Math.min(bin.getWidth(), bin.getHeight()));

            // clean small areas
            cleanSmallRois(bin);	
            bin.hide();
            imp.hide();
            for ( int i = 1; i <= imp.getNSlices(); i++ )
            {
                IJ.showStatus("Refine ZP Rois... "+i+"/"+imp.getNSlices());
		getRoisSlice(i, bin, wratio, hratio, rad);
            }
            util.close(bin);
	}

	public void getZP(String inname)
	{
            IJ.log("Doing "+dir+inname);
            IJ.run("Close All", "");
	    rm.reset();

            String imgname = dir+"/"+inname;
	    openResetImage(imgname);
	    util.reOrder(imp);
         
            // run neural network for segmentation
            Network net = new Network();
	    ImagePlus unet = net.runUnet(imp, dir+inname, nnet, modeldir, 800, true);
		
            // extract contours from the binary image, smooth a little
            IJ.showStatus("Refine ZP Rois...");
            getZPFromUnet(unet);
            IJ.run(imp, "Select None", "");
            rm.runCommand(imp,"Deselect");
            String purinname = inname.substring(0, inname.lastIndexOf('.'));
            rm.runCommand("Save", dir+"/contours/"+purinname+"_ZP.zip");
            util.close(imp);	
	}

        /** \brief Dialog window 
        @return true if no pb, false else
         */
	public boolean getParameters()
	{
            dir = IJ.getDirectory("Choose images directory:");	
            return true;
        }
		
        public void run(String arg)
	{
            // get parameters, initialize
            if (!getParameters()) return;
		IJ.run("Close All");
		rm = RoiManager.getInstance();
		if ( rm == null )
			rm = new RoiManager();
		rm.reset();
		util = new Utils();
		
                modeldir = IJ.getDirectory("imagej")+"/models/"+arg+"/";
                // Performs on all images in chosen directory
		File thedir = new File(dir); 
		File[] fileList = thedir.listFiles(); 
		File directory = new File(dir+"/contours");
		if (! directory.exists())
			directory.mkdir();
			
            for (File fily : fileList) 
            {
                if ( fily.isFile() )
                {
                    String inname = fily.getName();
                    int j = inname.lastIndexOf('.');
                    if (j > 0)
                    {
                        String extension = inname.substring(j);
                        if ( extension.equals(".tif") | extension.equals(".TIF") | extension.equals(".png") | extension.equals(".jpg") | extension.equals(".JPG") )
                        {
                            getZP( inname );
                        }
                        System.gc(); // garbage collector
                    }
                }
            }
	}


}
