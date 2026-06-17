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

/** 
 * \brief Plugin to calculte Moran index (spatial autocorrelation), Moran 1950.
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
import java.awt.*;

public class MoranIndex
{

	double ksize = 10;
	
	public double distance( int x1, int y1, int x2, int y2 )
	{
		return Math.sqrt( Math.pow(x1-x2,2) + Math.pow(y1-y2,2) );
	}

	public double calcIndex(ImageProcessor ip, Roi roi)
	{
		double index = 0;
		double sumw = 0;
		double sumsq = 0;
		int n = 0;

		ip.setRoi(roi);
		ImageStatistics mystat = ip.getStatistics();
		double mu = mystat.mean; // mean value inside ROI

		Rectangle rec = roi.getBounds();
		// For each point inside the roi, xi
		for ( int xi = rec.x; xi < (rec.x+rec.width); xi++ )
		{
			for ( int yi = rec.y; yi < (rec.y+rec.height); yi++ )
			{
				if ( roi.contains(xi,yi) )
				{
					n++;
					double vi = ip.getPixel(xi,yi);
					sumsq += Math.pow(vi-mu,2);
					
					// Look for neighboring pixels inside ROI
					for ( int xj = (int) Math.max(rec.x, xi-ksize-1); xj <= (int) Math.min(rec.x+rec.width, xi+ksize+1); xj++ )
				{	
					if ( xi!=xj )
					{
					for ( int yj = (int) Math.max(rec.y, yi-ksize-1); yj <= (int) Math.min(rec.y+rec.height, yi+ksize+1); yj++ )
				{
					// exclude outside ROI and self pixel
					if ( roi.contains(xj,yj) && ((yi!=yj)))
					{
						// inside neighboring
						if ( distance( xi,yi, xj, yj) <= ksize )
						{
							sumw += 1;
							double vj = ip.getPixel(xj,yj);
							index += (vi-mu)*(vj-mu);
						}				
					}
				}
					}
			}
			}
		}	
	}
		index = n/sumw * index/sumsq;

	return index;	
	}

	public double run(double ks, ImageProcessor ip, Roi roi)
	{
		ksize = ks;
		return calcIndex(ip, roi);
	}
}

