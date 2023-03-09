
/** 
 * @brief Plugin to get correct cortex and ZP Rois from TRANS images
 *
 * @details Open already calculated segmentation, and correct parts that are inside the "erased" ROIs
 * 
 *
 * @author G. Letort, Institut Pasteur, DSCB
 * @date created on 2022/11/29
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
import java.util.ArrayList;



public class CorrecErasedPart implements PlugIn
{
	ImagePlus imp;
	Calibration cal;
	RoiManager rm;
	String dir = "";
	Utils util;
        int smoothRes = 5;
        double preach = 0.005;
	

        
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


	
	/** \brief Treat one image: correct cortex and save it as Rois 
	 *
	 * @param inname image file name
	 * */
	public void corrigeCortex(String inname)
	{
		IJ.log("Doing "+dir+inname);
		IJ.run("Close All", "");
		rm.reset();

		String imgname = dir+inname;
		String ext = inname.substring(inname.lastIndexOf('.'));
		openResetImage(imgname);
		util.reOrder(imp);
                String purinname = inname.substring(0, inname.lastIndexOf('.'));
                rm.runCommand("Open", dir+"contours"+File.separator+purinname+"_UnetCortex.zip");
                Roi[] cortex = rm.getRoisAsArray();
                rm.reset();
                rm.runCommand("Open", dir+"contours"+File.separator+purinname+"_erased.zip");
                Roi[] erased = rm.getRoisAsArray();
                rm.reset();
                
		IJ.showStatus("Correcting oocyte membrane contours...");
		if ( visible ) imp.show();
                
                FloatPolygon[] smoothcortex = new FloatPolygon[cortex.length];
		
                for (int i=0; i<cortex.length; i++)
                {
                    FloatPolygon fp = cortex[i].getFloatPolygon();
                    int slice = cortex[i].getPosition();
                    imp.setSlice(slice);
                    // remove points that are inside erased part
                    ArrayList keepx = new ArrayList<Double>();
                    ArrayList keepy = new ArrayList<Double>();
                    ArrayList tofix = new ArrayList<Integer>();
                
                    for (int j=0; j<fp.xpoints.length; j++)
                    {
                      if (!util.isCleaned( erased, slice, fp.xpoints[j], fp.ypoints[j] ) )
                      {
                          keepx.add((double)(fp.xpoints[j]));
                          keepy.add((double)(fp.ypoints[j]));
                      }
                      else 
                      {
                          tofix.add(j);
                      }
                    }
                    
                    double[] circle = fitCircle(keepx, keepy);
                    
                    int indfix = 0;
                    for (int f=0; f<fp.xpoints.length; f++ )
                    {   
                        if ( (indfix < tofix.size()) && ((int)(tofix.get(indfix)) == f) )
                        {
                            double dcentx = fp.xpoints[f]-circle[0];
                            double dcenty = fp.ypoints[f]-circle[1];
                            double ang = Math.atan2(dcenty, dcentx);
                            //System.out.println(ang+" "+circle[0]+" "+circle[1]+" "+circle[2]);
                            fp.xpoints[f] = (float)(circle[0]) + (float)(circle[2]*Math.cos(ang));
                            fp.ypoints[f] = (float)(circle[1]) + (float)(circle[2]*Math.sin(ang));
                            indfix = indfix + 1;
                        }
                    }
                    
                    // refine ROI: smooth
                    // find contour+local maxima position at each angle
                    int nang = 300; //200	
                    double ang = 0;
                    double dang = 2*Math.PI/nang;
                    float[] xpts = new float[nang];
                    float[] ypts = new float[nang];
                    float rad = (float) (circle[2]*1.4);
                    float cx = (float) circle[0];
                    float cy = (float) circle[1];
                    for (int a=0; a<nang; a++)
                    {       
			ang = ang + dang;
			float[] res = getAnglePosition(cx, cy, rad, ang, fp, imp);
			xpts[a] = res[0];
			ypts[a] = res[1];
                    }

			float[] rads = getRadiusInside(xpts, ypts, cx, cy);
			ang = 0;
			for (int a=0; a<nang; a++)
			{	
				ang = ang + dang;
				float[] res = smoothRadius(cx, cy, rad, ang, rads, a);
				xpts[a] = res[0];
				ypts[a] = res[1];
			}

			smoothcortex[i] = new FloatPolygon(xpts, ypts);
                }
		createRois(smoothcortex);

                IJ.run(imp, "Select None", "");
		rm.runCommand(imp,"Deselect");
		rm.runCommand("Save", dir+"contours"+File.separator+purinname+"_UnetCortex.zip");
		util.close(imp);	
	}
        
        /* Adapted from Fiji code:
	if selection is closed shape, create a circle with the same area and centroid, otherwise use<br>
	the Pratt method to fit a circle to the points that define the line or multi-point selection.<br>
	Reference: Pratt V., Direct least-squares fitting of algebraic surfaces", Computer Graphics, Vol. 21, pages 145-152 (1987).<br>
	Original code: Nikolai Chernov's MATLAB script for Newton-based Pratt fit.<br>
	(http://www.math.uab.edu/~chernov/cl/MATLABcircle.html)<br>
	Java version: https://github.com/mdoube/BoneJ/blob/master/src/org/doube/geometry/FitCircle.java<br>
	Authors: Nikolai Chernov, Michael Doube, Ved Sharma
	*/
	double[] fitCircle(ArrayList xpts, ArrayList ypts) 
        {
		int n=xpts.size();
		// calculate point centroid
		double sumx = 0, sumy = 0;
		for (int i=0; i<n; i++) {
			sumx = sumx + (double)(xpts.get(i));
			sumy = sumy + (double)(ypts.get(i));
		}
		double meanx = sumx/n;
		double meany = sumy/n;
		
		// calculate moments
		double[] X = new double[n], Y = new double[n];
		double Mxx=0, Myy=0, Mxy=0, Mxz=0, Myz=0, Mzz=0;
		for (int i=0; i<n; i++) {
			X[i] = (double)(xpts.get(i)) - meanx;
			Y[i] = (double)(ypts.get(i)) - meany;
			double Zi = X[i]*X[i] + Y[i]*Y[i];
			Mxy = Mxy + X[i]*Y[i];
			Mxx = Mxx + X[i]*X[i];
			Myy = Myy + Y[i]*Y[i];
			Mxz = Mxz + X[i]*Zi;
			Myz = Myz + Y[i]*Zi;
			Mzz = Mzz + Zi*Zi;
		}
		Mxx = Mxx/n;
		Myy = Myy/n;
		Mxy = Mxy/n;
		Mxz = Mxz/n;
		Myz = Myz/n;
		Mzz = Mzz/n;
		
		// calculate the coefficients of the characteristic polynomial
		double Mz = Mxx + Myy;
		double Cov_xy = Mxx*Myy - Mxy*Mxy;
		double Mxz2 = Mxz*Mxz;
		double Myz2 = Myz*Myz;
		double A2 = 4*Cov_xy - 3*Mz*Mz - Mzz;
		double A1 = Mzz*Mz + 4*Cov_xy*Mz - Mxz2 - Myz2 - Mz*Mz*Mz;
		double A0 = Mxz2*Myy + Myz2*Mxx - Mzz*Cov_xy - 2*Mxz*Myz*Mxy + Mz*Mz*Cov_xy;
		double A22 = A2 + A2;
		double epsilon = 1e-12; 
		double ynew = 1e+20;
		int IterMax= 20;
		double xnew = 0;
		int iterations = 0;
		
		// Newton's method starting at x=0
		for (int iter=1; iter<=IterMax; iter++) {
			iterations = iter;
			double yold = ynew;
			ynew = A0 + xnew*(A1 + xnew*(A2 + 4.*xnew*xnew));
			if (Math.abs(ynew)>Math.abs(yold)) {
				if (IJ.debugMode) IJ.log("Fit Circle: wrong direction: |ynew| > |yold|");
				xnew = 0;
				break;
			}
			double Dy = A1 + xnew*(A22 + 16*xnew*xnew);
			double xold = xnew;
			xnew = xold - ynew/Dy;
			if (Math.abs((xnew-xold)/xnew) < epsilon)
				break;
			if (iter >= IterMax) {
				if (IJ.debugMode) IJ.log("Fit Circle: will not converge");
				xnew = 0;
			}
			if (xnew<0) {
				if (IJ.debugMode) IJ.log("Fit Circle: negative root:  x = "+xnew);
				xnew = 0;
			}
		}
		if (IJ.debugMode) IJ.log("Fit Circle: n="+n+", xnew="+IJ.d2s(xnew,2)+", iterations="+iterations);
		
		// calculate the circle parameters
		double DET = xnew*xnew - xnew*Mz + Cov_xy;
		double CenterX = (Mxz*(Myy-xnew)-Myz*Mxy)/(2*DET);
		double CenterY = (Myz*(Mxx-xnew)-Mxz*Mxy)/(2*DET);
		double radius = Math.sqrt(CenterX*CenterX + CenterY*CenterY + Mz + 2*xnew);
		if (Double.isNaN(radius)) {
                    radius = 100000;
                }
		CenterX = CenterX + meanx;
		CenterY = CenterY + meany;
                double[] res = {CenterX, CenterY, radius};
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

        /** \brief Treat one image: correct ZP and save it as Rois 
	 *
	 * @param inname image file name
	 * */
	public void corrigeZP(String inname)
	{
		IJ.log("Not implemented yet");
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
                                            if (arg.equals("corcortex"))
                                            {
                                                corrigeCortex( inname );
                                            }
                                            if (arg.equals("corzp"))
                                            {
                                                corrigeZP( inname );
                                            }
					}   
                                        
				}
				System.gc(); // garbage collector
			}
		}
                System.gc(); // garbage collector
	}


}
