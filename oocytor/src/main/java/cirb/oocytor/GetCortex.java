
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

import fiji.util.gui.GenericDialogPlus;
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
        //boolean twoPass = false;
	boolean locate = false;
        final ImageIcon icon = new ImageIcon(this.getClass().getResource("/oo_logo.png"));


	/** \brief Dialog window 
	  @return true if no pb, false else
	  */
	public boolean getParameters()
	{
		GenericDialogPlus gd = new GenericDialogPlus("Options", IJ.getInstance() );
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
		gd.setFont(boldy);
		//gd.addMessage("----------------------------------------------------------------------------------------------- ");
		gd.addNumericField("smooth_contour :", smoothRes);
		gd.addNumericField("reach_proportion :", preach);
		gd.addNumericField("nb_networks :", nnet);
		gd.addCheckbox("visible_mode", visible);
                //gd.addCheckbox("two_pass", twoPass);
                gd.addCheckbox("locate", locate);
				
//gd.setBackground(new Color(140,160,185));
		gd.setBackground(new Color(100,140,170));

		//gd.setForeground(new Color(255,255,255));
		gd.setInsets​(-100, 240, 0);
		ImagePlus iconimg = new ImagePlus();
		iconimg.setImage(icon.getImage());
		gd.addImage(iconimg);
                
                gd.addDirectoryField("model_path:", modeldir);
                gd.addDirectoryField("images_directory:", dir);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		smoothRes = (int) gd.getNextNumber();
		preach = (double) gd.getNextNumber();
		nnet = (int) gd.getNextNumber();
		visible = gd.getNextBoolean();
                //twoPass = gd.getNextBoolean();
                locate = gd.getNextBoolean();
                
                modeldir = gd.getNextString();
		dir = gd.getNextString();	
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
	public Roi[] createRois(FloatPolygon[] fp, int[] zpos)
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
			contour.setPosition(zpos[j]);
			contour.setName("cortex_"+zpos[j]);
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
        }
        
        /** Find approximate location and size of oocyte */
        public void localizeAndRunOocyte(String inname)
        {
            ImagePlus dup = imp.duplicate();
            IJ.run(dup, "Gaussian Blur...", "sigma=2 stack");
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
		IJ.run(bin, "Convert to Mask", "method=Default background=Light black");
		IJ.run(bin, "Analyze Particles...", "size=100-Infinity clear include add stack");
		util.keepRois(0, bin);
		IJ.run("Select None");
		util.close(bin); 
                imp.hide();
        }
        
        /** \brief Second pass: from ROI, crop and resegment */
	public void getCortexAgain(String inname)
	{
            int nrois = rm.getCount();
            int[] debx = new int[nrois];
            int[] deby = new int[nrois];
            int[] orig_size = new int[nrois];
            int[] zpos = new int[nrois];
            
            int cropsize = 350;
            ImageStack cropstack = new ImageStack(cropsize, cropsize);
            for ( int i = 0; i < nrois; i++ )
            {
                Roi cur = rm.getRoi(i);
                imp.setSlice( cur.getPosition() );
                zpos[i] = cur.getPosition();
                
                imp.setRoi(cur);
                cur = imp.getRoi();
                double[] cent = cur.getContourCentroid();
                double fer = cur.getFeretsDiameter();
                fer = fer * 0.5 * 2;
                orig_size[i] = (int)Math.floor(fer*2);
                
                debx[i] = (int)Math.floor(cent[0]- fer);
                deby[i] = (int)Math.floor(cent[1]- fer);
                
                imp.setRoi(debx[i], deby[i], orig_size[i], orig_size[i]);
                ImagePlus cropped = imp.crop();
                cropped = cropped.resize(cropsize, cropsize, "bilinear");
                cropstack.addSlice(cropped.getProcessor());
            }
            rm.reset();
            
            ImagePlus impcrop = new ImagePlus("cropped", cropstack);               
            //impcrop.show();
            //IJ.run("stop");
                            
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
            for ( int i = 0; i < nrois; i++ )
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
		IJ.run(bin, "Convert to Mask", "method=Default background=Light black");
		IJ.run(bin, "Analyze Particles...", "size=100-Infinity clear include add stack");
		util.keepRois(0, bin);
		IJ.run("Select None");
		util.close(bin); 
                imp.hide();
        }
        
        
        /**
         * \brief Refine the ROI for a better match of cortex
         */
        public void refineCortex()
	{
                    IJ.run(imp, "Select None", "");
                    IJ.run(imp, "Invert", "stack");

                    // Enhance structures
                    ImagePlus vert = new Duplicator().run(imp);
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
                    FloatPolygon[] smoothcortex = new FloatPolygon[rm.getCount()];
                    int[] zpos = new int[rm.getCount()];
                    for ( int i = 0; i < rm.getCount(); i++ )
                    {
                            IJ.showStatus("Refining Rois... "+i+"/"+rm.getCount());
                            Roi cur = rm.getRoi(i);
                            imp.setSlice( cur.getPosition() );
                            zpos[i] = cur.getPosition();
                            imp.setRoi(cur);
                            cur = imp.getRoi();

                            // find contour+local maxima position at each angle
                            int nang = 360; //200	
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
                    createRois(smoothcortex, zpos);
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

		String imgname = dir+inname;
		String ext = inname.substring(inname.lastIndexOf('.'));
		openResetImage(imgname);
		util.reOrder(imp);
                
                IJ.showStatus("Segment oocyte with neural networks...");    
                if (locate)
                {
                    localizeAndRunOocyte(inname);
                }
                else 
                {
                    ImagePlus unet = net.runUnet(imp, dir+inname, nnet, modeldir, 800, visible);
                    if ( visible ) unet.show();
                    // extract contours from the binary image
                    getCortexFromUnet(unet);
                
                    // if two pass option, do the second passage
                    /**if (twoPass)
                    {
                        getCortexAgain(inname);  
                    }*/
                }
                
                // smooth a little and finer match to cortex
		refineCortex();

                IJ.run(imp, "Select None", "");
		rm.runCommand(imp,"Deselect");
		String purinname = inname.substring(0, inname.lastIndexOf('.'));
		rm.runCommand("Save", dir+"contours"+File.separator+purinname+"_UnetCortex.zip");
		util.close(imp);	
	}
        
  

	public void run(String arg)
	{
            
                modeldir = IJ.getDirectory("imagej")+File.separator+"models"+File.separator+arg+File.separator;
		// get parameters, initialize
		if ( !getParameters() ) { return; }
		IJ.run("Close All");
		rm = RoiManager.getInstance();
		if ( rm == null )
			rm = new RoiManager();
		rm.reset();
		util = new Utils();

                if (! dir.endsWith(File.separator))
                {
                    dir = dir + File.separator;
                }
                if (! modeldir.endsWith(File.separator))
                {
                    modeldir = modeldir + File.separator;
                }
                net = new Network();
                net.init();
                
		// Performs on all images in chosen directory
		File thedir = new File(dir); 
		File[] fileList = thedir.listFiles(); 
		File directory = new File(dir+"contours");
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
