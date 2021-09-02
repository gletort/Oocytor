
/** 
 * \brief Plugin to calculte LOCO-EFA (Sanchez-Corrales et al 2018) shape description.
 *
 *
 * \author G. Letort, College de France
 * \date created on 2020/05/18
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
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

public class LocoEfa
{
	public LocoEfa(){}
	
	public double distancePointToPol( FloatPolygon fp, double x, double y)
	{
		double dist = 10000000;
		for ( int i = 0; i < fp.npoints; i++ )
		{
			double d = Math.sqrt( (fp.xpoints[i]-x)*(fp.xpoints[i]-x) + (fp.ypoints[i]-y)*(fp.ypoints[i]-y) );
			if ( d < dist )
				dist = d;
		}
		return dist;
	}

	public double distanceBetweenShapes( Roi shape, Roi efares )
	{
		FloatPolygon sha = shape.getInterpolatedPolygon(1, true);
		FloatPolygon efa = efares.getInterpolatedPolygon(1, true);

		double dist = 0;
		for ( int i = 0; i < sha.npoints; i++ )
		{
			dist += distancePointToPol( efa, sha.xpoints[i], sha.ypoints[i] );
		}
		dist /= sha.npoints;
	return dist;	
	}


	public void getLocoEFA( Roi shape, ResultsTable rt)
	{

		EFALocoCoef efa = new EFALocoCoef( shape.getFloatPolygon(), 100);
		efa.calcEFACoefficients();
		efa.calcLocoCoefficients();
		double rad = shape.getFeretsDiameter()/2.0;
		double[] cent = shape.getContourCentroid();
		Roi ref = new OvalRoi(cent[0]-rad, cent[1]-rad, 2*rad, 2*rad); 
		
		double dref = distanceBetweenShapes( shape, ref );
		double cumuldist = 0;
		for (int j = 2; j < 50; j+=1)
		{
			Roi res = efa.reconstruct(j);
			//rm.addRoi(res);
			double dist = distanceBetweenShapes( shape, res );
			//IJ.log("Relative distance mode "+j+": "+dist/dref);
			cumuldist += dist/dref;
		}
		rt.addValue("OoLocoEFACumDist", cumuldist);
		rt.addValue("OoLocoEFAEntropy", (efa.entropy(50)));
		rt.addValue("OoLocoEFAMaxLnMode", (efa.maxContribPos()));
		//double locmean = efa.meanLn(50);
		//rt.addValue("OoLocoEFALnMean", locmean);
		//rt.addValue("OoLocoEFALnSd", (efa.locoStd(50, locmean)));
	}
}
