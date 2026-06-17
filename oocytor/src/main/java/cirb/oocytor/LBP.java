/*-
 * #%L
 * Plugins to segment different oocytes structures, and to extract numerous features to describe them
 * %%
 * Copyright (C) 2021 - 2026 Gaelle Letort
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the CIRB nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
