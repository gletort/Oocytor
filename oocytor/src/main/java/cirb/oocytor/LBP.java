package cirb.oocytor;

import ij.gui.*;
import ij.process.*;
import java.awt.*;
import ij.measure.ResultsTable;

/**
 * \brief Local Binary Pattern calculation
 *
 * @author Gaelle Letort, Collège de France
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
public class LBP
{

	public void calcLBP(ImageProcessor ip, Roi roi, ResultsTable rt, String name )
	{
		double[] hist = buildLBPHist(ip, roi);
		double mu = hmean(hist);
		double[] var = vars(hist, mu);
		
		rt.addValue(name+"LBPMean", mu);
		rt.addValue(name+"LBPVar", var[0]);
		rt.addValue(name+"LBPSkewness", var[1]);
		rt.addValue(name+"LBPKurtosis", var[2]);
	}

	public double[] buildLBPHist(ImageProcessor ip, Roi roi)
	{
		double[] hist = new double[256];
		int i, j;
		int pixelCount = 0;
		int res = 0;

		Rectangle rec = roi.getBounds();
		// fill matrix
		for ( int x = rec.x; x< (rec.x+rec.width); x++ )
		{
			for ( int y = rec.y; y < (rec.y+rec.height); y++ )
			{
				if ( roi.contains(x,y) )
				{
					pixelCount++;
					res = 0;
					for (double theta=0; theta <360; theta = theta+45)
					{
						int dx = (int) Math.round(Math.cos(theta/180*Math.PI));
						int dy = (int) Math.round(Math.sin(theta/180*Math.PI));
						
						res *= 2;
						if ( ip.getPixel(x,y) < ip.getPixel(x+dx, y+dy) )
							res = res + 1;
					}
					//System.out.println("res "+res);
					hist[res] ++;
				}
			}
		}

		for (int k = 0; k<256; k++)
			hist[k] /= pixelCount;

		return hist;
	}
	
	public double hmean( double[] arr )
	{
		double res = 0;
		for (int i = 0; i < arr.length; i++)
		{
			res += i*arr[i];
		}
		return res;
	}

	public double[] vars( double[] arr, double mu )
	{
		double sig = 0; // variance
		double skew = 0; // skewness
		double kur = 0; // kurtosis
		for (int i = 0; i < arr.length; i++)
		{
			sig += (i-mu)*(i-mu)*arr[i];
			skew += Math.pow(i-mu,3) * arr[i];
			kur += Math.pow(i-mu,4) * arr[i];
		}
		skew /= Math.pow(sig,3);
		kur /= Math.pow(sig,4);

		double[] res = new double[3];
		res[0] = sig;
		res[1] = skew;
		res[2] = kur;
		return res;
	}



}
