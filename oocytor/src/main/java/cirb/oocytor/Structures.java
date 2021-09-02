package cirb.oocytor;

import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import java.util.*;
import ij.plugin.filter.ParticleAnalyzer;
import ij.measure.Measurements;

public class Structures
{
	ImagePlus ip;

	public void distanceStructuresToEdge( ImagePlus ip, Roi roi, Roi only, ResultsTable myrt )
	{
		ip.setTitle("cur");
		IJ.run(ip, "FeatureJ Structure", "smallest smoothing=1.0 integration=3.0");
		ImagePlus small = WindowManager.getImage("cur smallest structure eigenvalues");

		only.setImage(small);
		Prefs.blackBackground = true;
		small.setRoi(only);
		IJ.setAutoThreshold(small, "Mean");
		small.setRoi(only);
		IJ.run(small, "Convert to Mask", "");
                
                //small.show();
                //new WaitForUserDialog("dist").show();
                
		roi.setImage(small);
		small.setRoi(roi);

		double[] cent = roi.getContourCentroid();
		double rad = (roi.getFeretsDiameter()*0.85);
		getDistanceToEdge( cent, rad, roi, only, small, myrt );	

		small.changes = false;
		small.close();
	}

        /** \brief Get normalised distance to Roi edge of positive points in the binary image */
	public void getDistanceToEdge( double[] cent, double rad, Roi roi, Roi only, ImagePlus imp, ResultsTable myrt)
	{
		int xmin = (int) (cent[0] - 1.1*rad);
		if ( xmin < 0 ) xmin = 0;
		int ymin = (int) (cent[1] - 1.1*rad);
		if ( ymin < 0 ) ymin = 0;
		int xmax = (int) (cent[0] + 1.1*rad);
		if ( xmax > imp.getWidth() ) xmax = imp.getWidth();
		int ymax = (int) (cent[1] + 1.1*rad);
		if ( ymax > imp.getHeight() ) ymax = imp.getHeight();
		
		Vector dist = new Vector();
		double dmean = 0;
		int n = 0;
		for ( int px = xmin; px < xmax; px=px+3 )
		{
			for ( int py = ymin; py < ymax; py=py+3 )
			{
				// is inside cortex
				if ( roi.contains(px, py) && (only.contains(px, py) ) )
				{
					// >0: is a structure
					if ( imp.getPixel(px, py)[0] > 0 )
					{
						double d = getNormedDistance( cent, roi, only, px, py );
						if ( d > 0 ) 
						{
							dist.add(d);
							//System.out.println(px+" "+py+" "+d);
							dmean += d;
							n++;
						}
					}
				}
			}
		}
		
                double std = 0;
		if (n==0) dmean = 0;
                else 
                {
                    dmean /= n;
                    std = vecstd(dist,dmean);
                }
		myrt.addValue("OoParticleDistanceMean", dmean);
		myrt.addValue("OoParticleDistanceStd", std);
                myrt.addValue("OoParticleDistanceCoefVar", std/dmean);
	}
	
        /** \brief Return the distance to center of the point (x,y) normalised by the radius of the Roi in the same radius */
	public double getNormedDistance( double[] cent, Roi roi, Roi only, int x, int y )
	{
		double dx = (double) x - cent[0];
		double dy = (double) y - cent[1];
		double norm = Math.sqrt(dx*dx+dy*dy);
		dx /= norm;
		dy /= norm;
		double angle = Math.atan2( dy, dx );
		
		// find local radius	
		double radius = norm;
		int xcur = (int) (cent[0] + radius*Math.cos(angle));
		int ycur = (int) (cent[1] + radius*Math.sin(angle));
		while ( roi.contains(xcur, ycur) )
		{
			if ( !only.contains(xcur, ycur) ) return -1;
			radius += 0.25;
			xcur = (int) (cent[0] + radius*Math.cos(angle));
			ycur = (int) (cent[1] + radius*Math.sin(angle));
		}
		radius -= 0.25; // found cortex

		//System.out.println(dist+" "+norm+" "+radius);
		return (norm/radius);
	}


	public double[] getMeanRadialIntensity( double[] cent, double radius, Roi roi, Roi only, ImagePlus imp )
	{
		int nang = 360;	
		double dang = 2*Math.PI/nang;
		Vector ints = new Vector();
		Vector npts = new Vector();

		double ang = 0;
		for ( int a = 0; a < nang; a++ )
		{
			Line myline = new Line( cent[0], cent[1], cent[0]+radius*Math.cos(ang), cent[1]+radius*Math.sin(ang) );
			myline.setStrokeWidth(10);
			imp.setRoi(myline);
			double[] vals = myline.getPixels();
			//vals = smooth(vals);
		
			// find Roi edge and if should analyze this ang or not (only==roi on that side)
			int i = 1;
			int xcur = getPosX( cent, radius, ang, i, vals.length );
			int ycur = getPosY( cent, radius, ang, i, vals.length );
			int breaking = 0;
			while ( roi.contains( xcur, ycur ) && (breaking==0) && (i < vals.length) )
			{
				if ( !only.contains( xcur, ycur ) ) breaking = 1;
				i++;
				xcur = getPosX( cent, radius, ang, i, vals.length );
				ycur = getPosY( cent, radius, ang, i, vals.length );
			}
			i = i-1;
		
			// rempli seulement si a atteint le cortex
			if ( breaking == 0 )
			{
				int j = 0; // distance to cortex
				while (i >= 0 )
				{
					if ( ints.size() <= j )
					{
						ints.add( vals[i] );
						npts.add( 1 );
					}
					else
					{
						ints.set(j, (double)ints.get(j)+ vals[i]);
						npts.set(j, (int) npts.get(j) + 1 );
					}
					i--;
					j++;
				}
			}
			ang = ang + dang;
		}
		
		double[] mint = new double[ints.size()];
		double max = 0;
		double imax = 0;
		for ( int k = 0; k < ints.size(); k++ )
		{
			mint[k] = ((double)ints.get(k))/((int)npts.get(k));
			if ( mint[k] > max )
			{
				max = mint[k];
				imax = k;
			}
		}
		for ( int k = 0; k < ints.size(); k++ )
		{
			mint[k] = mint[k] / max;
			System.out.print(mint[k] + " \t "); 
		}
		return mint;
	}
	
	public double[] meanRadialStructIntensity( ImagePlus ip, Roi roi, Roi only )
	{
		ip.setTitle("cur");
		IJ.run(ip, "FeatureJ Structure", "largest smallest smoothing=1.0 integration=3.0");
		ImagePlus larg = WindowManager.getImage("cur largest structure eigenvalues");
		ImagePlus small = WindowManager.getImage("cur smallest structure eigenvalues");

		ImageCalculator ic = new ImageCalculator();
		ImagePlus tog = ic.run("Add create", larg, small);
		larg.changes = false;
		larg.close();
		small.changes = false;
		small.close();

		tog.setRoi(only);
		only.setImage(tog);
		roi.setImage(tog);
		tog.setRoi(roi);

		double[] cent = roi.getContourCentroid();
		double rad = (roi.getFeretsDiameter()*0.85);
	
		double[] profile = getMeanRadialIntensity( cent, rad, roi, only, tog );	

		tog.changes = false;
		tog.close();

	//	double avg = mean( widths );
	//	double var = std( widths, avg );
		return new double[]{0};
	}

	
	public double mean( double[] arr )
	{
		double res = 0;
		int n = 0;
		for (double val:arr )
		{
			if ( val > 0 )
			{
				res += val;
				n++;
			}
		}
		if ( n == 0 )  return -1;
		return res/n;
	}
	
	public double vecmean( Vector vec )
	{
		double res = 0;
		int n = 0;
		for (int i = 0; i < vec.size(); i++)
		{
			res += (double) vec.get(i);
			n++;
		}
		if ( n == 0 )  return -1;
		return res/n;
	}
	
	public double vecstd( Vector vec, double mean )
	{
		double res = 0;
		int n = 0;
		for (int i = 0; i < vec.size(); i++)
		{
			res += ((double)vec.get(i) -mean)*((double)vec.get(i)-mean);
			n++;
		}
		if ( n == 0 )  return -1;
		//return Math.sqrt(res)/n;
		return res/n;
	}
	
	public int getPosXRad( double[] cent, double angle, double rad )
	{
		return ( (int) (cent[0] + rad*Math.cos(angle)) );
	}
	public int getPosYRad( double[] cent, double angle, double rad )
	{
		return ( (int) (cent[1] + rad*Math.sin(angle)) );
	}

	public int getPosX( double[] cent, double radius, double angle, int index, int length )
	{
		return ( (int) (cent[0] + radius*Math.cos(angle) * (double)(index)/length) );
	}
	
	public int getPosY( double[] cent, double radius, double angle, int index, int length )
	{
		return ( (int) (cent[1] + radius*Math.sin(angle) * (double)(index)/length) );
	}

	public double getWidthOneAngle( double[] cent, double radius, double angle, Roi roi, Roi only, ImagePlus imp )
	{
		Line myline = new Line( cent[0], cent[1], cent[0]+radius*Math.cos(angle), cent[1]+radius*Math.sin(angle) );
		myline.setStrokeWidth(30);
		imp.setRoi(myline);
		double[] vals = myline.getPixels();
		vals = smooth(vals);
		
		// find Roi edge and if should analyze this angle or not (only==roi on that side)
		int i = 1;
		int xcur = getPosX( cent, radius, angle, i, vals.length );
		int ycur = getPosY( cent, radius, angle, i, vals.length );
		while ( roi.contains( xcur, ycur ) )
		{
			if ( !only.contains( xcur, ycur ) ) return -1;
			i++;
			xcur = getPosX( cent, radius, angle, i, vals.length );
			ycur = getPosY( cent, radius, angle, i, vals.length );
		}
		i = i-1;
		
		//get first min
	    int minpos = i;
		while ( (vals[minpos] > vals[minpos-1]) )
		{
			minpos--;
			if ( minpos <= 0 ) return -1;
		}

		// get next max
		int maxpos = minpos;
		while ( vals[maxpos] < vals[maxpos-1] )
		{
			maxpos --;
			if ( maxpos <= 0 ) return -1;
		}

		return (i - maxpos);
	}

	public double[] smooth( double[] val )
	{
		double[] smoo = new double[val.length];
		int wind = 20;
		for ( int i = 0; i < val.length; i++ )
		{
			smoo[i] = meanWindow( val, i, wind );
		}
		return smoo;
	}
	
	public double meanWindow( double[] tab, int mid, int size)
	{
		double res = 0;
		int nb = 0;

		int deb = mid - size;
		if ( deb < 0 ) deb = 0;
		int lim = mid + size;
		if ( lim > tab.length )
			lim = tab.length;
		for ( int k = deb; k < lim; k++ )
		{
			res += tab[k];
			nb++;
		}
		res /= nb;
		return res;
	}
	
	

	public double[] particleSizes( ImagePlus ip, Roi roi, Roi only )
	{
		ip.setTitle("cur");
		IJ.run(ip, "FeatureJ Structure", "smallest smoothing=1.0 integration=3.0");
		ImagePlus small = WindowManager.getImage("cur smallest structure eigenvalues");

		only.setImage(small);
		Prefs.blackBackground = true;
		small.setRoi(only);
		IJ.setAutoThreshold(small, "Moments dark");
		small.setRoi(only);
		IJ.run(small, "Convert to Mask", "");
		small.setRoi(only);
		IJ.setBackgroundColor(0, 0, 0);
		IJ.run(small, "Options...", "iterations=1 count=1 black do=Nothing");
		small.setRoi(only);
		IJ.run(small, "Clear Outside", "");
		IJ.run(small, "Open", "");

                //small.show();
                //new WaitForUserDialog("structures").show();
                
		int opts = ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES | ParticleAnalyzer.DISPLAY_SUMMARY ;
		int meas = Measurements.AREA | Measurements.MEAN ;
		ResultsTable sum = new ResultsTable();
		ResultsTable respa = new ResultsTable();
		ParticleAnalyzer pa = new ParticleAnalyzer(opts, meas, respa, 1, 1000000000);
		pa.setSummaryTable(sum);
		pa.analyze(small);
		//IJ.run(small, "Analyze Particles...", "size=1-Infinity summarize");
		int last = sum.getCounter()-1;
		double npart = sum.getValue("Count", last);
		double sizepart = sum.getValue("Average Size", last);
		small.changes = false;
		small.close();

		return new double[]{npart, sizepart};
	}

}
