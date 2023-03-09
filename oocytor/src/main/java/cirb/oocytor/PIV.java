/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
package cirb.oocytor;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;

/**
 *
 * @author Gaelle Letort, Collège de France
 */
public class PIV 
{
    double[] prev;
    double[] val;
    
    public PIV()
    {
        prev = new double[12];
        val = new double[12];
    }
    
    public void runOneTimeDiff(ImagePlus img, double[] cent, double rad, String tmpfile)
    {
        IJ.run(img, "iterative PIV(Basic)...", "piv1=32 sw1=64 piv2=16 sw2=32 piv3=0 sw3=0 correlation=0.60 what=[Accept this PIV and output] noise=0.20 threshold=5 c1=3 c2=1 save="+tmpfile+" batch");
	for ( int j=0; j<3; j++ )
	{
		ImagePlus tmp = IJ.getImage();
		tmp.changes = false;
		tmp.close();
	}

        // read result and get mean
        readPIVFile(tmpfile, cent, rad);
    }
    
    public void writePIVResults(int i, ResultsTable myrt, double scale)
    {
        if ( i > 0 )
        {
            double tmp = 0.0;
            for ( int k=0; k <12; k++)
            {
                tmp = prev[k];
                prev[k] = val[k];
                val[k] += tmp;
                val[k] /= 2.0;
            }
        }
        if ( i == 0 )
        {
            for ( int k=0; k <12; k++) val[k] = prev[k];
        }
      
        myrt.addValue("OoPIVMean", val[0]*scale);
        myrt.addValue("OoPIVStd", val[1]*scale);
        myrt.addValue("OoPIVCoefVar", val[1]/val[0]);
        myrt.addValue("OoPIVMeanCenter", val[2]*scale);
        myrt.addValue("OoPIVMeanEdge", val[3]*scale);
        myrt.addValue("OoPIVAngleCenter", val[4]);
        myrt.addValue("OoPIVAngleEdge", val[5]);
        myrt.addValue("OoPIVAverageDirection", val[6]);
        myrt.addValue("OoPIVStrengthDirection", val[7]);
        myrt.addValue("OoPIVAverageDirectionCenter", val[8]);
        myrt.addValue("OoPIVAverageDirectionEdge", val[9]);
        myrt.addValue("OoPIVStrengthDirectionCenter", val[10]);
        myrt.addValue("OoPIVStrengthDirectionEdge", val[11]);      
    }
    
    // center of Roi and radius for local analysis
	public void readPIVFile(String infile, double[] center, double radius)
	{
		Vector norms = new Vector();
		double normCent = 0;
		double normEdge = 0;
		double angCent = 0;
		double angEdge = 0;
                double meanVx = 0;
                double meanVy = 0;
                double sumNorm = 0;
                double meanVxEdge = 0;
                double meanVyEdge = 0;
                double meanVxCent = 0;
                double meanVyCent = 0;
                double x,y, vx, vy, norm, dist, cost;
		int nCent = 0;
		int nEdge = 0;
		try {
			/* open the file */
			BufferedReader r = new BufferedReader(new FileReader(infile));
			int row = 0;
			String line;
			while ((line = r.readLine()) != null) 
			{
				line = line.trim();
				String[] cur = line.split("\\s+");
				norm = Double.parseDouble(cur[4]) ;
				norms.add( norm );
				x = Double.parseDouble(cur[0]);
				y = Double.parseDouble(cur[1]);
				vx = Double.parseDouble(cur[2]);
				vy = Double.parseDouble(cur[3]);
				row++;

				if ( (norm != 0) )
				{ 
					// position in oocyte
					dist = Math.sqrt( Math.pow(x-center[0],2) + Math.pow(y-center[1],2) );
					cost = ( (x-center[0])*vx + (y-center[1])*vy )/(dist*norm);
                                        meanVx += vx;
                                        meanVy += vy;
                                        sumNorm += norm;
                                        
					if ( !Double.isNaN(cost) )
					{	
					// close to center, 1/2 of radius
					if ( dist <= (radius * 0.5) )
					{
						normCent += norm;
						angCent += Math.abs(cost);
						nCent ++;
                                                meanVxCent += vx;
                                                meanVyCent += vy;
					}
					// close to edge
					if ( dist >= (radius * 0.75) )
					{
						normEdge += norm;
						angEdge += Math.abs( cost );
						nEdge ++;
                                                meanVxEdge += vx;
                                                meanVyEdge += vy;
					}
					}
				}
			}
			r.close();
		}
		catch (Exception e) 
		{
			IJ.error(e.getMessage());
		}

		int nres = norms.size();
		val[0] = 0;
		for ( int j = 0; j < nres; j++ )
		{
			val[0] += (double) norms.get(j);
		}
		val[0] /= nres;

		double std = 0;
		for ( int j = 0; j < nres; j++ )
		{
			std += Math.pow( ((double) norms.get(j))-val[0],2);
		}
		std /= nres;
				
		if (nCent==0) nCent = 1;
		if (nEdge==0) nEdge = 1;
		
		val[1] = std;  // total amplitude sd
		val[2] = normCent/nCent; // mean amplitude around center
		val[3] = normEdge/nEdge; // mean amplitude close to the edge
		val[4] = angCent/nCent; // tangentiel close to center ?
		val[5] = angEdge/nEdge; // tangentiel close to edge ?
                // mean angle from sum of displacements
                double alpha = Math.atan2(meanVy, meanVx) * 180/Math.PI;
                double dirStrength = Math.sqrt(meanVy*meanVy+meanVx*meanVx) / sumNorm;
                val[6] = alpha;
                val[7] = dirStrength;
                double alphaCent = Math.atan2(meanVyCent, meanVxCent) * 180/Math.PI;
                double dirStrengthCent = Math.sqrt(meanVyCent*meanVyCent+meanVxCent*meanVxCent) / normCent;
                val[8] = alphaCent;
                val[10] = dirStrengthCent;
                double alphaEdge = Math.atan2(meanVyEdge, meanVxEdge) * 180/Math.PI;
                double dirStrengthEdge = Math.sqrt(meanVyEdge*meanVyEdge+meanVxEdge*meanVxEdge) / normEdge;
                val[9] = alphaEdge;
                val[11] = dirStrengthEdge;
                
	}
}
