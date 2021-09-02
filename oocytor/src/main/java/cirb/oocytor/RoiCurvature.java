/** 
 * \brief Plugin to compute shape curvature 
 *
 * \details For a given Roi, calculate the curvature and curvature radius along it
 *
 * \author G. Letort, College de France
 * \date created on 2020/05/22
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
import ij.process.*;
import ij.gui.*;
import ij.measure.ResultsTable;

public class RoiCurvature
{
    Utils util;
    
	public RoiCurvature(Utils ut)
	{
            util = ut;
	}

        /** \brief First derivative, central derivation */
	public float firstd( float[] z, int ind, int n)
	{
		int pind = (ind-1+n)%n;
		int nind = (ind+1)%n;
		return (float) ((z[nind]-z[pind])/2.0);
	}
	
        /** \brief Second derivative */
	public float secondd( float[] z, int ind, int n)
	{
		int pind = (ind-1+n)%n;
		int nind = (ind+1)%n;
		return (float) ((z[nind]-2.0*z[ind]+z[pind])/1.0);
	}
	
	
	public void curvature( FloatPolygon fp, ResultsTable rt, double scale, String which, double meanRad )
	{
		int n = fp.npoints;
		float[] xp = new float[n];
		float[] yp = new float[n];
		float[] xpp = new float[n];
		float[] ypp = new float[n];
		for ( int i = 0; i < n; i++ )
		{
			xp[i] = firstd( fp.xpoints, i, n); 
			yp[i] = firstd( fp.ypoints, i, n); 
			xpp[i] = secondd( fp.xpoints, i, n); 
			ypp[i] = secondd( fp.ypoints, i, n); 
		}
		/** smooth
		xp = util.smoothPts(xp,1);
		yp = util.smoothPts(yp,1);
		xpp = util.smoothPts(xpp,1);
		ypp = util.smoothPts(ypp,1);
                */
		double[] curv = new double[n];
		double maxc = 0;
		double minc = 10000;
		double flat = 0;  // proportion of nearly flat curv
		double perim = 0;
		double be = 0; // Bending energy = 1/n*sum(k(s)^2)
                double mean = 0.0;
		for ( int i = 0; i < n; i++ )
		{
			// | (x' y'' - y' x'')/(x'x' + y'y')^3/2 |  
                        // Positive curvature radius 
			curv[i] = Math.abs( xp[i]*ypp[i] - yp[i]*xpp[i] );
			curv[i] /= Math.pow( xp[i]*xp[i] + yp[i]*yp[i], 1.5); 
                        if ( curv[i] > maxc )
				maxc = curv[i];
			if ( curv[i] < minc )
				minc = curv[i];
			double dist = distance( fp.xpoints, fp.ypoints, i);
                        perim += dist;	
                        // nearly flat, high curvature radius, 4*meanRadius µm
			if ( curv[i] <= (1.0/(4*meanRad)) ) flat += dist;
			//sum += curv[i];
			be += curv[i]*curv[i];
		        mean += curv[i];
               }
                flat /= perim;	
                mean /= n;
                be /= n;
                double std = util.stdTab(curv, mean);
                rt.addValue(which+"CurvatureMean", mean/scale);
                rt.addValue(which+"CurvatureStd", std/scale); 
                rt.addValue(which+"CurvatureCoefVar", std/mean);  
                rt.addValue(which+"CurvatureMax",maxc/scale);
                rt.addValue(which+"CurvatureMin",minc/scale);
                rt.addValue(which+"CurvatureFlatProp",flat);
                rt.addValue(which+"CurvatureBendingEnergy",be/(scale*scale));
                rt.addValue(which+"CurvatureNormalisedMean", mean*meanRad);
                rt.addValue(which+"CurvatureNormalisedMax",maxc*meanRad);
                rt.addValue(which+"CurvatureNormalisedMin",minc*meanRad);
                rt.addValue(which+"CurvatureNormalisedStd",std*meanRad);
	}

	
	public double distance( float[] x, float[] y, int i)
	{
		int n = x.length;
		double res = Math.pow((double)(x[(i+1)%n]-x[i]), 2);
		res += Math.pow( (double) (y[(i+1)%n]-y[i]), 2);
		double resp = Math.pow((double)(x[(i-1+n)%n]-x[i]), 2);
		resp += Math.pow( (double) (y[(i-1+n)%n]-y[i]), 2);
		return (Math.sqrt(res)+Math.sqrt(resp))/2.0;
	}

        /** \brief Get the curvature of the Roi and write the results in the ResultsTable */
	public void getCurvature(Roi roi, ResultsTable myrt, double scale, String which, double meanRad)
	{
		ImagePlus imp = IJ.getImage();
		imp.setRoi(roi);
		IJ.run(imp, "Interpolate", "interval=2 smooth adjust");
		IJ.run(imp, "Fit Spline", "");
		IJ.run(imp, "Interpolate", "interval=4 smooth adjust");
		Roi splined = imp.getRoi();
		FloatPolygon poly = splined.getFloatPolygon();
		curvature(poly, myrt, scale, which, meanRad);        
	}
}
