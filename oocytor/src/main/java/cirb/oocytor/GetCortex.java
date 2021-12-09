
/** 
 * @brief Plugin to get cortex Rois from TRANS images
 *
 * @details Segment oocyte with neural network trained on oocytes, TRANS images
 * Use CSBDeep "run your network" plugin to call the trained networks.
 * Detect the cortex
 * 
 * Parameters: smooth_contour choose how much to smooth the results
 *             nb networks: number of neural networks to average the results
 *
 * @author G. Letort, College de France
 * @date created on 2021/02/24
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
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * */


package cirb.oocytor;

import ij.IJ;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.*;
import java.io.*;
import java.awt.*;
import javax.swing.ImageIcon;



public class GetCortex implements PlugIn
{
	ImagePlus imp;
	Calibration cal;
	RoiManager rm;
	String dir = "";
	String modeldir = "";
	Utils util;
        Network net;

	int threshold = 100;
	int smoothRes = 5;
	double preach = 0.005;
	int nnet = 2;
	boolean visible = true;
	final ImageIcon icon = new ImageIcon(this.getClass().getResource("/oo_logo.png"));


	/** \brief Dialog window 
	  @return true if no pb, false else
	  */
	public boolean getParameters()
	{
		GenericDialog gd = new GenericDialog("Options", IJ.getInstance() );
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
		gd.setFont(boldy);
		//gd.addMessage("----------------------------------------------------------------------------------------------- ");
		gd.addNumericField("smooth_contour :", smoothRes);
		gd.addNumericField("reach_proportion :", preach);
		gd.addNumericField("nb_networks :", nnet);
		gd.addCheckbox("visible_mode", visible);
		//gd.setBackground(new Color(140,160,185));
		gd.setBackground(new Color(100,140,170));

		//gd.setForeground(new Color(255,255,255));
		gd.setInsets​(-100, 240, 0);
		ImagePlus iconimg = new ImagePlus();
		iconimg.setImage(icon.getImage());
		gd.addImage(iconimg);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		smoothRes = (int) gd.getNextNumber();
		preach = (double) gd.getNextNumber();
		nnet = (int) gd.getNextNumber();
		visible = gd.getNextBoolean();

		dir = IJ.getDirectory("Choose images directory:");	
		return true;
	}


	/** Initialisation of an image 
	  @param imgname: name of the image to treat
	  */
	public void openResetImage(String imgname)
	{
		String ext = imgname.substring(imgname.lastIndexOf('.'));
		if ( ext.equals(".tif") )
			imp = IJ.openVirtual(imgname);
		else
			imp = IJ.openImage(imgname);
		//imp = IJ.getImage();
		cal = util.initCalibration(imp);
		if (visible) imp.show();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		util.unselectImage(imp);
	}


	// find current radius
	public float[] getAnglePosition( float cx, float cy, float radius, double ang, FloatPolygon fp, ImagePlus img )
	{
		float[] res = new float[2];
		res[0] = (float) 0.0;
		res[1] = (float) 0.0;
		Line myline = new Line(cx, cy, cx+radius*Math.cos(ang), cy+radius*Math.sin(ang));
		myline.setStrokeWidth(30);  // prev 30
		img.setRoi(myline);
		double[] vals = myline.getPixels();

		int xcur;
		int ycur;

		// detect position of the border (segmentation from the network)
		int i = 0;
		xcur = (int) ((float) cx + (float) (radius*Math.cos(ang) * i/vals.length));	
		ycur = (int) ((float) cy + (float) (radius*Math.sin(ang) * i/vals.length));
		while ( fp.contains(xcur, ycur ))
		{
			i++;
			xcur = (int) ((float) cx + (float) (radius*Math.cos(ang) * i/vals.length));	
			ycur = (int) ((float) cy + (float) (radius*Math.sin(ang) * i/vals.length));
		}

		// found the border, look for local maxima whithin reach
		int mpos = i;
		int size = (int) (radius*preach); //reachLim; // before: 15;
		int deb = i-size;
		if ( deb<0) deb = 0;
		int end = i+size;
		if (end >vals.length) end = vals.length;
		double max = 0;
		for ( int j=deb; j<end; j++ )
		{
			if ( vals[j] > max )
			{
				max = vals[j];
				mpos = j;
			}
		}	

		res[0] = (float) cx + (float) (radius*Math.cos(ang) * mpos/vals.length);	
		res[1] = (float) cy + (float) (radius*Math.sin(ang) * mpos/vals.length);	
		return res;
	}

	/** \brief Smooth the radii with local mean and get list of points from it */
	public float[] smoothRadius( float cx, float cy, float radius, double ang, float[] rads, int ind )
	{
		float[] res = new float[2];
		res[0] = (float) 0.0;
		res[1] = (float) 0.0;

		int size = smoothRes;
		double crad = util.meanCircularWindow( rads, ind, size, true);

		res[0] =  ((float) cx + (float) (crad*Math.cos(ang)));	
		res[1] =  ((float) cy + (float) (crad*Math.sin(ang)));
		return res;
	}

	/** \brief Convert floatPolygons to Rois, set image/position/name */
	public Roi[] createRois(FloatPolygon[] fp)
	{
		rm.reset();
		IJ.run(imp, "Select None", "");

		Roi[] rcortex = new Roi[fp.length];
		for ( int j = 0; j < fp.length; j++ )
		{
			float[] xpts = (fp[j]).xpoints;
			float[] ypts = (fp[j]).ypoints;
			Roi contour = new PolygonRoi(xpts, ypts, Roi.POLYGON);
			imp.setSlice((j+1));
			contour.setImage(imp);
			contour.setPosition((j+1));
			contour.setName("cortex_"+(j+1));
			rm.addRoi(contour);
			rcortex[j] = contour;
		}

		return rcortex;
	}


	/** \brief Put coordinates to radius
	  and handle points outside image */
	public float[] getRadiusInside( float[] xs, float[] ys, float cx, float cy )
	{
		float[] rads = new float[xs.length];
		for ( int m = 0; m < xs.length; m++ )
		{
			// is out or againts image limits
			if ( (xs[m] >= imp.getWidth()*0.96 || xs[m] <= imp.getWidth()*0.04)
					|| (ys[m] >= imp.getHeight()*0.96 ||ys[m] <= imp.getHeight()*0.04)  )
				rads[m] = -10000;
			else
				rads[m] = (float) Math.sqrt( (double) ((xs[m]-cx)*(xs[m]-cx) + (ys[m]-cy)*(ys[m]-cy)));
		}

		// replace negatives (outside img) by local average
		for ( int m = 0; m < xs.length; m++ )
		{
			if ( rads[m] <= 0 )
				rads[m] = util.meanCircularRadius( rads, m, 20 );
		}

		return rads;
	}

	/** \brief from binary images to Rois 
	  @param bin: input binary image
	  */
	public void getCortexFromUnet(ImagePlus bin)
	{
		IJ.showStatus("Binary to Rois...");

		IJ.run(bin, "Select None", "");
		ImagePlus dupbin = bin.duplicate();

		IJ.setRawThreshold(bin, threshold, 255, null);
		Prefs.blackBackground = true;

		IJ.run(bin, "Convert to Mask", "method=Default background=Light black");
		ImageStatistics binstats = bin.getStatistics(Measurements.MEAN);
		if ( binstats.mean <= 10 )
		{
			util.close(bin);
			bin = new Duplicator().run(dupbin);
			IJ.setRawThreshold(bin, 1, 255, null);
			Prefs.blackBackground = true;
			IJ.run(bin, "Convert to Mask", "method=Default background=Light black");
		}
		util.close(dupbin);

		IJ.run(bin, "Size...", "width="+imp.getWidth()+" height="+imp.getHeight()+" depth="+(imp.getNSlices())+" average interpolation=Bilinear");
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
		IJ.run(bin, "Convert to Mask", "method=Default background=Light black");
		IJ.run(bin, "Analyze Particles...", "size=100-Infinity clear include add stack");
		util.keepRois(0, bin);
		IJ.run("Select None");
		util.close(bin);

		imp.hide();
		IJ.run(imp, "Select None", "");
		IJ.run(imp, "Invert", "stack");

		// Enhance structures
		ImagePlus vert = new Duplicator().run(imp);
		//vert.show();
		IJ.run(vert, "Convolve...", "text1=[-1 0 1\n-1 0 1\n-1 0 1] normalize stack");
		ImagePlus hor = new Duplicator().run(imp);
		//hor.show();
		IJ.run(hor, "Convolve...", "text1=[-1 -1 -1 \n0 0 0\n1 1 1 ] normalize stack");
		ImageCalculator calc = new ImageCalculator();
		calc.run("Add stack", vert, hor);
		util.close(hor);
		calc.run("Average stack", imp, vert);
		calc.run("Average stack", imp, vert);
		util.close(vert);

		IJ.showStatus("Refining Rois...");
		// Get cortex contours
		FloatPolygon[] smoothcortex = new FloatPolygon[imp.getNSlices()];
		for ( int i = 0; i < rm.getCount(); i++ )
		{
			IJ.showStatus("Refining Rois... "+i+"/"+rm.getCount());
			Roi cur = rm.getRoi(i);
			imp.setSlice( cur.getPosition() );
			imp.setRoi(cur);
			cur = imp.getRoi();

			// find contour+local maxima position at each angle
			int nang = 300; //200	
			double ang = 0;
			double dang = 2*Math.PI/nang;
			float[] xpts = new float[nang];
			float[] ypts = new float[nang];
			float rad = (float) (cur.getFeretsDiameter()*0.7);
			double[] cent = cur.getContourCentroid();
			float cx = (float) cent[0];
			float cy = (float) cent[1];
			for (int j=0; j<nang; j++)
			{	
				ang = ang + dang;
				float[] res = getAnglePosition(cx, cy, rad, ang, cur.getFloatPolygon(), imp);
				xpts[j] = res[0];
				ypts[j] = res[1];
			}

			float[] rads = getRadiusInside(xpts, ypts, cx, cy);
			ang = 0;
			for (int j=0; j<nang; j++)
			{	
				ang = ang + dang;
				float[] res = smoothRadius(cx, cy, rad, ang, rads, j);
				xpts[j] = res[0];
				ypts[j] = res[1];
			}

			smoothcortex[i] = new FloatPolygon(xpts, ypts);
		}
		createRois(smoothcortex);
	}


	/** \brief Treat one image: find cortex and save it as Rois 
	 *
	 * @param inname image file name
	 * */
	public void getCortexImage(String inname)
	{
		IJ.log("Doing "+dir+inname);
		IJ.run("Close All", "");
		rm.reset();

		String imgname = dir+"/"+inname;
		String ext = inname.substring(inname.lastIndexOf('.'));
		openResetImage(imgname);
		util.reOrder(imp);

		IJ.showStatus("Segment oocyte with neural networks...");
		//Network net = new Network();
		ImagePlus unet = net.runUnet(imp, dir+inname, nnet, modeldir, 800, visible);
		if ( visible ) unet.show();
                //net = null;
                
		// extract contours from the binary image, smooth a little
		getCortexFromUnet(unet);
		IJ.run(imp, "Select None", "");
		rm.runCommand(imp,"Deselect");
		String purinname = inname.substring(0, inname.lastIndexOf('.'));
		rm.runCommand("Save", dir+"/contours/"+purinname+"_UnetCortex.zip");
		util.close(imp);	
	}
        
  

	public void run(String arg)
	{
		// get parameters, initialize
		if ( !getParameters() ) { return; }
		IJ.run("Close All");
		rm = RoiManager.getInstance();
		if ( rm == null )
			rm = new RoiManager();
		rm.reset();
		util = new Utils();

                modeldir = IJ.getDirectory("imagej")+"/models/"+arg+"/";
                net = new Network();
                net.init();
                
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
						getCortexImage( inname );
					}   
                                        
				}
				System.gc(); // garbage collector
			}
		}
                net.end();
                net = null;
                System.gc(); // garbage collector
	}


}
