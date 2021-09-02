
package cirb.oocytor;

import ij.*;
import ij.gui.*;
import ij.plugin.frame.*;
import ij.measure.*;
import ij.plugin.RoiScaler;
import ij.plugin.ZProjector;
import ij.process.FloatPolygon;
import ij.process.ImageStatistics;
import java.awt.Polygon;
import java.util.*;

/**
 * \brief Utility functions for Oocytor plugins
 *
 * @author Gaelle Letort, College de France
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

public class Utils
{
    
    	/** Be sure there s no calibration */
	public Calibration initCalibration(ImagePlus imp)
	{
		// Measure everything in pixels
		Calibration cal = imp.getCalibration();
		if (cal == null ) 
		{
			cal = new Calibration(imp);
		}
		cal.pixelWidth = 1;
		cal.pixelHeight = 1;
		cal.pixelDepth = 1;
                return cal;
	}
        
        /** Close without saving */
	public void close(ImagePlus ip)
	{ 
		ip.changes = false;
		ip.close();
	}

        /** \brief Be sure nothing is selected in the image */
        public void unselectImage(ImagePlus ip)
        {
            	IJ.run(ip, "Select None", "");
		IJ.run("Select None");
		IJ.run("Set Measurements...", "area centroid perimeter bounding stack redirect=None decimal=2");
		IJ.run(ip, "Remove Overlay", "");
	
        }
        
        /** \brief Performs a Z projection with meth method */
	public ImagePlus doProjection( ImagePlus ip, String meth)
	{
		return (new ZProjector()).run(ip, meth);
	}

        
	/** \brief Select only one Roi by z-slice
	 *
	 * @param which Criteria to choose a winner Roi to keep by z (e.g. biggest area)*/
	public void keepRois( int which, ImagePlus imp )
	{
		RoiManager rm = RoiManager.getInstance();
		if ( rm == null || rm.getCount() == 0 )
		{
			IJ.error("No Rois in Manager");
			return;
		}

		rm.runCommand( imp, "Sort" );
		Roi[] allRois = rm.getRoisAsArray();
		int curz;
		int	wini = 0;
		int doingz = -1;
		double refmes = 0;
		int mesure = 0;
		switch ( which )
		{
			case 0:
				refmes = 0;
				mesure = Measurements.AREA;
				break;
			case 1:
				refmes = imp.getWidth()*imp.getHeight() + 2;
				mesure = Measurements.AREA;
				break;
			case 2:
				refmes = 0;
				mesure = Measurements.MEAN;
				break;
			default:
				refmes = 0;
				mesure = Measurements.AREA;
				break;
		}
		double mmes = refmes;
		Vector tokeep = new Vector();
		tokeep.clear();
		for ( int i = 0; i < allRois.length; i++ )
		{
			Roi curroi = allRois[i];
			imp.setRoi(curroi);
			if ( imp.getNSlices() > 1 )
			{
				curz = curroi.getPosition();
				if ( doingz == -1 ) doingz = curz;
				if ( curz > doingz )
				{
					tokeep.add( wini );
					mmes = refmes;
					doingz = curz;
				}
			}
			rm.select(i);
			imp.setRoi(curroi);
			
			double tmpmes;
			switch ( which )
			{
				case 0:
					// biggest area wins
					tmpmes	= imp.getStatistics(mesure).area; 
					if ( tmpmes >= mmes )
					{
						wini = i;
						mmes = tmpmes;
					}
					break;
				case 1:
					// smallest area wins
					tmpmes	= imp.getStatistics(mesure).area; 
					if ( tmpmes <= mmes )
					{
						wini = i;
						mmes = tmpmes;
					}
					break;
				case 2:
					tmpmes	= imp.getStatistics(mesure).mean; 
					// best mean wins
					if ( tmpmes >= mmes )
					{
						wini = i;
						mmes = tmpmes;
					}
					break;
				default:
					break;
			}
		}
		// add last z
		tokeep.add( wini );

		int ndel = allRois.length-tokeep.size();
		if ( ndel > 0 )
		{
			int[] dels = new int[ndel];
			int filled = 0;
			for ( int j = 0; j < allRois.length; j++ )
			{
				if ( !tokeep.contains(j) )
				{
					dels[filled] = j;
					filled = filled + 1;
				}
			}
			rm.setSelectedIndexes(dels);
			rm.runCommand("Delete");
		}
	}	
	
	public float meanCircularRadius( float[] radtab, int mid, int size)
	{
            // all tab is -1
            if (size>radtab.length*0.8) return -1;
            
		float res = 0;
		int npt = 0;
		int dep = mid - size/2;
		if ( dep < 0 ) dep = dep + radtab.length;
		for ( int k = 0; k < size; k++ )
		{
                    int ind = (dep+k)%(radtab.length);
                    if ( radtab[ind] >0 )
                    {
			res += radtab[ind];
			npt ++;
                    }
		}
		
		if ( npt > 0)
		{
			res /= npt;
			return res;
		}
                else return meanCircularRadius( radtab, mid, size*2 );
	}
        
        /** \brief Mean value of positives only values of the array */
        public float meanPositiveTab( float[] arr )
	{
            float res = 0;
            int npos = 0;
            for (float val: arr)
            {
                if ( val > 0 )
                {
                        res += val;
                        npos++;
                }
            }
            return (res/npos);
        }


        /** \brief Calculate the std of a given array, only positive values taken */
        public float stdPositiveTab( float[] arr, float mean )
	{
            float sig = 0; // variance
            int npos = 0;
            for (float val: arr)
            {
                if ( val > 0 )
                {
                        sig += (val-mean)*(val-mean);
                        npos++;
                }
            }
            return ((float) Math.sqrt(sig/npos));
        }
        
        public double meanTab( double[] tab )
	{
		double res = 0.0;
		for ( double t:tab ) res += t;
		return res/tab.length;
	}

	public double stdTab( double[] tab, double mean)
	{
		double res = 0.0;
		for (double t:tab )res += (t-mean)*(t-mean);
		return Math.sqrt(res/tab.length);
	}

	
	public float meanCircularWindow( float[] tab, int mid, int size, boolean self)
	{
            float res = 0;
            int nb = 0;
            int deb = 0;
            // middle point counted 2 times (higher coef)
            if ( !self ) deb = 1;
            for ( int k = deb; k < size; k++ )
            {
                    int ind = (mid+k)%(tab.length);
                    res += tab[ind];
                    nb ++;
                    ind = (tab.length+mid-k)%(tab.length);
                    res += tab[ind];
                    nb++;
            }
            res /= nb;
            return res;
	}

	/** smooth points, local mean
	 * size of smoothing: *2 */	
	public float[] smoothPts( float[] tab, int size )
	{
		float[] res = new float[tab.length];
		for ( int k = 0; k < tab.length; k++ )
		{
			res[k] = meanCircularWindow( tab, k, size, true);
		}
		return res;
	}
        
        public void reOrder(ImagePlus ip)
        {
            if ( (ip.getNFrames()>1) && (ip.getNSlices() == 1) )
            {
                 ip.setDimensions(1, ip.getNFrames(), 1);
            }
        }
        
        
        /** \brief Delete part of the Roi if it's in the toer Roi area */
	public Roi cleanRoi( RoiManager rm, ImagePlus imp, Roi in, Roi toer )
	{
		int lim = rm.getCount();
		rm.addRoi(in);
		rm.addRoi(toer);
		rm.setSelectedIndexes(new int[]{lim,lim+1});
		rm.runCommand(imp,"AND");
		// no intersection, no pb
		if ( imp.getRoi() == null )
		{
			rm.setSelectedIndexes(new int[]{lim,lim+1});
			rm.runCommand(imp,"Delete");
			return in;
		}
		rm.runCommand(imp,"Add");
		
		rm.setSelectedIndexes(new int[]{lim,lim+2});
		rm.runCommand(imp,"XOR");
		rm.runCommand(imp,"Add");
		Roi res = rm.getRoi(lim+3);
		
		rm.setSelectedIndexes(new int[]{lim,lim+1, lim+2, lim+3});
		rm.runCommand(imp,"Delete");
		
		return res;
	}
	
        /** \brief For all Rois in er array, clean the parts that overlap with found Rois */
	public Roi cleanRoiAll( RoiManager rm, ImagePlus imp, Roi cortex, Roi[] er )
	{
		int deb = rm.getCount();
		for ( int i = 0; i < er.length; i++ )
		{
			imp.setRoi(er[i]);
			rm.runCommand(imp,"Add");
			if (i > 0 )
			{
				rm.setSelectedIndexes(new int[]{deb,deb+1});
				rm.runCommand(imp,"AND");
				rm.runCommand(imp,"Add");
				rm.setSelectedIndexes(new int[]{deb,deb+1});
				rm.runCommand(imp,"Delete");
			}
		}
		Roi ander = rm.getRoi(deb);
		rm.setSelectedIndexes(new int[]{deb});
		rm.runCommand(imp,"Delete");
		return cleanRoi(rm, imp,  cortex, ander);
	}

        /** \brief Return the Rois after removing the err ones */
	public Roi getCleanedRoi( RoiManager rm, ImagePlus imp, Roi cortex, Roi[] er, int slice)
	{
		if ( slice < 0 )
			return cleanRoiAll(rm, imp, cortex, er);
		//System.out.println("clean "+slice);
		for ( int i = 0; i < er.length; i++ )
		{
                        //System.out.println("pos "+er[i].getPosition());
			if ( er[i].getPosition() == slice )
			{
				return cleanRoi(rm, imp, cortex, er[i]);
			}
		}
		//System.out.println("no clean "+slice);
		return cortex;
	}
	
	/** \brief check if part of Roi is outside of image
	@return which side is out */
	public int goingOutRoi( ImagePlus ip, Roi roi )
	{
		FloatPolygon poly = roi.getContainedFloatPoints();
		for ( int i = 0; i < poly.xpoints.length; i++ )
		{
			int outside = outsidePoint( ip, poly.xpoints[i], poly.ypoints[i] );
			if ( outside != 0 )
				return outside;
		}
		return 0;
	}
	
        /** \brief Translate Roi if part of it is out of the image, to have it inside */
	public Roi translateRoi( ImagePlus ip, Roi roi, int out )
	{
		int shiftx = 0;
		int shifty = 0;
		if ( out == -1 ) shiftx = (int) (ip.getWidth()*0.33);
		if ( out == -3 ) shiftx = (int) (-1*ip.getWidth()*0.33);
		if ( out == -2 ) shifty = (int) (ip.getHeight()*0.33);
		if ( out == -4 ) shifty = (int) (-1*ip.getHeight()*0.33);
		FloatPolygon fp = roi.getFloatPolygon();
		//System.out.println("translate out "+out+" "+shiftx+" "+shifty);
		for ( int i = 0; i < fp.xpoints.length; i++ )
		{
			fp.xpoints[i] += shiftx;
			fp.ypoints[i] += shifty;
		}
		Roi res = (Roi) (new PolygonRoi( fp, Roi.POLYGON ));
		res.setImage(roi.getImage() );
		res.setPosition(roi.getPosition());
		return res;
	}
	
        /** \brief Change the size of the Roi to scale it to new image size */
	public void rescaleRois(RoiManager rm, ImagePlus ip, double factxy)
	{
		Roi[] rois = rm.getRoisAsArray();
		rm.reset();
		RoiScaler scaler = new RoiScaler();
		for ( int i=0; i < rois.length; i++ )
		{
			Roi cur = rois[i];
			Roi scaled = scaler.scale(cur, factxy, factxy, false);
			ip.setSlice(cur.getPosition());
			scaled.setPosition(cur.getPosition());
			ip.setRoi(scaled);
			rm.addRoi(scaled);	
		}
		rm.runCommand(ip,"Deselect");
	}
	
        /** \brief If some ZPs Roi are equal to the image size, remove them */
        public void treatOutsideZPRoi(ImagePlus imp, RoiManager rm)
        {
            int i = 0;
            while (i < rm.getCount())
            {
                rm.select(i);
                imp.setRoi(rm.getRoi(i));
                ImageStatistics is = imp.getStatistics();
                if (is.area >= (imp.getWidth()*imp.getHeight()) ) rm.runCommand(imp,"Delete"); 
                else i++;
            }
        }
        
	/**\brief get outside Roi */
	public Roi getOutsideRoi( ImagePlus ip, double fact, String zpRoi, RoiManager rm, int enl )
	{
		// get ZP limits
		IJ.run(ip, "Select None", "");
		rm.runCommand("Open", zpRoi);
		rm.runCommand(ip,"Deselect");
                treatOutsideZPRoi(ip, rm);
		rm.runCommand(ip,"Deselect");
                rm.runCommand(ip,"Combine");
		IJ.run(ip, "Enlarge...", "enlarge="+enl);
		IJ.run(ip, "Make Inverse", "");
		Roi out = ip.getRoi();
		Roi scaled = ip.getRoi();
		if ( fact > 0 )
		{
			RoiScaler scaler = new RoiScaler();
			scaled = scaler.scale(out, fact, fact, false);
			ip.setSlice(out.getPosition());
			scaled.setPosition(out.getPosition());
			ip.setRoi(scaled);
		}
		rm.runCommand(ip,"Deselect");
		IJ.run(ip, "Select None", "");
		rm.reset();
		return scaled;
	}
	
        /** \brief Average intensity of a polygon contours */
	public double meanPolygon( FloatPolygon fp, ImagePlus ip)
	{
		double res = 0;
		int nb = 0;
		for ( int k = 0; k < fp.npoints; k++ )
		{
			float x = fp.xpoints[k];
			float y = fp.ypoints[k];
			res += (ip.getPixel( (int) x, (int) y))[0];
			nb++;
		}
		res /= nb;

		return res;
	}
	  
        /** \brief Calculate the area of a polygon, using the shoelace formula
	@return signed area of polygon */
	public double polyarea(Polygon p) 
	{
		double area = 0.0;
	    for (int i = 0; i < p.npoints; i++) 
		{
	        area += ( p.xpoints[i] * p.ypoints[(i+1)%p.npoints]) - (p.ypoints[i] * p.xpoints[(i+1)%p.npoints]);
	    }
	    return 0.5 * Math.abs(area);
	}
	
        /** \brief Distance between a point and a polygon */
        public double distanceToPolygon( float x, float y, FloatPolygon poly )
        {
            double res = 10000;
            for ( int i=0; i < poly.npoints; i++)
            {
                double dist = Math.sqrt( Math.pow(x-poly.xpoints[i], 2) + Math.pow(y-poly.ypoints[i], 2) );
                if ( dist < res ) res = dist;
            }
            return res;
        }
        
        /** \brief distance between to float polygon points at index i and j */
	public double distanceBetweenPolPoints( int i, FloatPolygon fpi, int j, FloatPolygon fpj)
	{
		return Math.sqrt( Math.pow(fpi.xpoints[i]-fpj.xpoints[j],2) + Math.pow(fpi.ypoints[i]-fpj.ypoints[j],2) );
	}
	
        /** \brief For an angle a, find the closest angle in the angs array */
	public int findClosestAngle( double a, double[] angs )
	{
		double d = 10000;
		int res = 0;
		for ( int i = 0; i < angs.length; i++ )
		{
			double dis = Math.abs( a - angs[i] );
			if ( dis < d )
			{
				d = dis;
				res = i;
			}
		}
		return res;
	}

	/** \brief calculate point angle compared to center */
	public double getAngle( double x, double y, double[] cent )
	{
		double res = Math.atan2(y-cent[1], x-cent[0]);
		res *= 180/Math.PI;
		if ( res < 0 )
			res += 360;
		return res;
	}

	/** \brief Find distances between two Roi (mean distance, std, min, max), looking by angle */
	public double[] roisThicknessAngle(Roi in, Roi out)
	{
		FloatPolygon ip = in.getFloatPolygon();
		FloatPolygon op = out.getFloatPolygon();

		double[] cent = in.getContourCentroid();
		double[] cento = out.getContourCentroid();
		for ( int i = 0; i < 2; i++ )
		{
			cent[i] = (cent[i] + cento[i])/2.0;
		}	

		// calculate angle of each point of polygon
		double[] iang = new double[ip.npoints];
		for ( int j = 0; j < ip.npoints; j++ )
		{
			iang[j] = getAngle( ip.xpoints[j], ip.ypoints[j], cent );
		}
		double[] oang = new double[op.npoints];
		for ( int j = 0; j < op.npoints; j++ )
		{
			oang[j] = getAngle( op.xpoints[j], op.ypoints[j], cent );
		}

		double[] mdist = new double[ip.npoints];
		double[] thick = new double[4];
		thick[0] = 10000;  // min thickness
		thick[1] = 0;      // max thickness
		thick[2] = 0;      // mean thickness
		for ( int i = 0; i < ip.npoints; i++ )
		{
			double ang = iang[i];
			int oind = findClosestAngle( ang, oang );
			double dist = distanceBetweenPolPoints(i, ip, oind, op);
			mdist[i] = dist;
			if ( dist < thick[0] )
				thick[0] = dist;
			if ( dist > thick[1] )
				thick[1] = dist;
			thick[2] += dist;
		}
		thick[2] /= ip.npoints;

		thick[3] = 0.0; // std thickness
		for ( int j = 0; j < ip.npoints; j++ )
		{
			thick[3] += Math.pow( mdist[j] - thick[2], 2);
		}
		thick[3] = Math.sqrt( thick[3]/ip.npoints );
	
		return thick;
	}
        
        /** \brief Find distances between two Roi (mean distance, std, min, max) */
	public double[] roisThickness(Roi in, Roi out)
	{
		FloatPolygon ip = in.getFloatPolygon();
		FloatPolygon op = out.getFloatPolygon();

		double[] mdist = new double[ip.npoints];
		double[] thick = new double[4];
		thick[0] = 10000;  // min thickness
		thick[1] = 0;      // max thickness
		thick[2] = 0;      // mean thickness
		for ( int i = 0; i < ip.npoints; i++ )
		{
			double dist = distanceToPolygon( ip.xpoints[i], ip.ypoints[i], op );
			mdist[i] = dist;
			if ( dist < thick[0] ) thick[0] = dist;
			if ( dist > thick[1] ) thick[1] = dist;
			thick[2] += dist;
		}
		thick[2] /= ip.npoints;

		thick[3] = 0.0; // std thickness
		for ( double dis: mdist ) thick[3] += Math.pow( dis - thick[2], 2);
		thick[3] = Math.sqrt( thick[3]/ip.npoints );
	
		return thick;
	}
        
           /** \brief Find distances between two Roi (mean distance) */
	public double roiMeanThickness(Roi in, Roi out)
	{
		FloatPolygon ip = in.getFloatPolygon();
		FloatPolygon op = out.getFloatPolygon();

		double thick = 0;
		for ( int i = 0; i < ip.npoints; i++ )
		{
			thick += distanceToPolygon( ip.xpoints[i], ip.ypoints[i], op );
                }
		thick /= ip.npoints;
		return thick;
	}
	
	/** \brief Find mean Roi (middle line of ZP) */
	public Roi meanRoi(Roi in, Roi out)
	{
		FloatPolygon ip = in.getFloatPolygon();
		FloatPolygon op = out.getFloatPolygon();

		double[] cent = in.getContourCentroid();
		double[] cento = out.getContourCentroid();
		for ( int i = 0; i < 2; i++ )
		{
			cent[i] = (cent[i] + cento[i])/2.0;
		}	

		// calculate angle of each point of polygon
		double[] iang = new double[ip.npoints];
		for ( int j = 0; j < ip.npoints; j++ )
		{
			iang[j] = getAngle( ip.xpoints[j], ip.ypoints[j], cent );
		}
		double[] oang = new double[op.npoints];
		for ( int j = 0; j < op.npoints; j++ )
		{
			oang[j] = getAngle( op.xpoints[j], op.ypoints[j], cent );
		}

		float[] xpts = new float[ip.npoints];
		float[] ypts = new float[ip.npoints];
		for ( int i = 0; i < ip.npoints; i++ )
		{
			double ang = iang[i];
			int oind = findClosestAngle( ang, oang );
		
			 xpts[i] = (float) ((ip.xpoints[i]+op.xpoints[oind])/2) ;
			 ypts[i] = (float) ((ip.ypoints[i]+op.ypoints[oind])/2) ;
		}
		Roi mean = new PolygonRoi(xpts, ypts, PolygonRoi.POLYGON );

		return mean;
	}

        /** \brief Test if point (x,y) is in a cleaned area */
	public boolean isCleaned( Roi[] er, int slice, double x, double y )
	{
		for ( int i = 0; i < er.length; i++ )
		{
			if ( er[i].getPosition() == slice )
			{
				if ( er[i].contains((int)x, (int)y) )
					return true;
			}
		}
		return false;
	}

        /** \brief Test if point (x,y) is outside the image limits */
	public int outsidePoint( ImagePlus ip, double x, double y )
	{
		if ( x < 0 ) return -1;
		if ( x > ip.getWidth() ) return -3;
		if ( y < 0 ) return -2;
		if ( y > ip.getHeight() ) return -4;	
		return 0;
	}

      
}
