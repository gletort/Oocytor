package cirb.oocytor;
import ij.gui.*;
import ij.process.*;

/** Calculate the LOCO-EFA coefficients, following Sanchez-Corrales et al. 2018
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
public class EFALocoCoef
{
	double[] x; 
	double[] y;
	double[] t;
	double T;
	double[] alpha;
	double[] beta;
	double[] gama;
	double[] delta;
	double[] alphastar;
	double[] betastar;
	double[] gamastar;
	double[] deltastar;
	double[] aplus;
	double[] bplus;
	double[] cplus;
	double[] dplus;
	double[] aminus;
	double[] bminus;
	double[] cminus;
	double[] dminus;
	double[] locoL;
	int npts;
	int Nmode;
	int posMax; // where is the max contributing mode after 2

	public EFALocoCoef(FloatPolygon fp, int nmode)
	{
		npts = fp.npoints+1;
		Nmode = nmode;
		x = new double[npts];
		y = new double[npts];
		for ( int i = 0; i < (npts-1); i++ )
		{
			x[i] = (double) fp.xpoints[i];
			y[i] = (double) fp.ypoints[i];
		}
		x[npts-1] = x[0];
		y[npts-1] = y[0];
	}

	public double hypot( double x, double y)
	{
		return Math.sqrt( x*x + y*y );
	}
	
	public double distance( double x1, double x2, double y1, double y2)
	{
		return Math.sqrt( (x1-x2)*(x1-x2) + (y1-y2)*(y1-y2) );
	}

	public void calcEFACoefficients()
	{

	//Calculate Eq 5 coef 
		double[] dt = new double[npts];
		t = new double[npts];
		double[] dx = new double[npts];
		double[] dy = new double[npts];
		dx[0] = 0;
		dy[0] = 0;
		t[0] = 0;
		dt[0] = 0;
		T = t[0];
		for ( int i = 1; i < npts; i++ )
		{
			dx[i] = x[i]-x[i-1];
			dy[i] = y[i]-y[i-1];
			dt[i] = hypot( dx[i], dy[i]);
			T += dt[i];
			t[i] = T;
		}

		// Calc alpha, gama, beta, delta modes
		 // Equation 6 and 7
		
		alpha = new double[Nmode+1];
		gama = new double[Nmode+1];
		beta = new double[Nmode+1];
		delta = new double[Nmode+1];

		alpha[0] = x[0];
		gama[0] = y[0];

		double xii = 0;
		double sumdxj = 0;
		double sumdyj = 0;
		double sumdtj = 0;
		double epsi = 0;
		// calculate alpha0, gama0, (eq 7)
		for ( int k = 1; k < npts; k++ )
		{
			xii = sumdxj - dx[k]/dt[k] * sumdtj;
			epsi = sumdyj - dy[k]/dt[k] * sumdtj;
			sumdxj += dx[k];
			sumdyj += dy[k];
			sumdtj += dt[k];

			// alpha 0
			alpha[0] += 1.0/T*( dx[k]/(2*dt[k])*(t[k]*t[k] - t[k-1]*t[k-1]) + xii*(t[k]-t[k-1]) );
			// gama 0
			gama[0] += 1.0/T*( dy[k]/(2*dt[k])*(t[k]*t[k] - t[k-1]*t[k-1]) + epsi*(t[k]-t[k-1]) );
		}

		// calculate alpha, beta, gama, delta (eq 6)

		for ( int m=1; m<=Nmode; m++)
		{
			alpha[m] = 0; 
			beta[m] = 0; 
			gama[m] = 0; 
			delta[m] = 0; 
			for ( int i=1; i < npts; i++ )
			{
				alpha[m] += dx[i]/dt[i]*( Math.cos(2.0*Math.PI*m*t[i]/T) - Math.cos(2.0*Math.PI*m*t[i-1]/T) );
				beta[m] += dx[i]/dt[i]*( Math.sin(2.0*Math.PI*m*t[i]/T) - Math.sin(2.0*Math.PI*m*t[i-1]/T) );
				gama[m] += dy[i]/dt[i]*( Math.cos(2.0*Math.PI*m*t[i]/T) - Math.cos(2.0*Math.PI*m*t[i-1]/T) );
				delta[m] += dy[i]/dt[i]*( Math.sin(2.0*Math.PI*m*t[i]/T) - Math.sin(2.0*Math.PI*m*t[i-1]/T) );
			}
			
			alpha[m] *= T/(2*m*m*Math.PI*Math.PI);
			beta[m] *= T/(2*m*m*Math.PI*Math.PI);
			gama[m] *= T/(2*m*m*Math.PI*Math.PI);
			delta[m] *= T/(2*m*m*Math.PI*Math.PI);
		}
	}

	public void calcLocoCoefficients()
	{
		// eq 14, calculate tau1
		double tau1 = 0.5 * Math.atan2( 2*(alpha[1]*beta[1] + gama[1]*delta[1]), alpha[1]*alpha[1] + gama[1]*gama[1] - beta[1]*beta[1] - delta[1]*delta[1] );

		// eq 15, get alphaprime1, tauprime1
		double alphap1 = alpha[1]*Math.cos(tau1) + beta[1]*Math.sin(tau1);
		double gamap1 = gama[1]*Math.cos(tau1) + delta[1]*Math.sin(tau1);

		// eq 16, get rho
		double rho = Math.atan2( gamap1, alphap1 );
		// eq 17 symetry if needed
		if ( rho < 0 )
			tau1 += Math.PI;

		// eq 18, alpha*, beta*, gama*, delta*
		alphastar = new double[Nmode+1];
		betastar = new double[Nmode+1];
		gamastar = new double[Nmode+1];
		deltastar = new double[Nmode+1];
	
		alphastar[0] = 0;
		betastar[0] = 0;
		gamastar[0] = 0;
		deltastar[0] = 0;
		for ( int i = 1; i <= Nmode; i ++ )
		{
			alphastar[i] = alpha[i] * Math.cos(i*tau1) + beta[i]*Math.sin(i*tau1);
			betastar[i] = -alpha[i] * Math.sin(i*tau1) + beta[i]*Math.cos(i*tau1);
			gamastar[i] = gama[i] * Math.cos(i*tau1) + delta[i]*Math.sin(i*tau1);
			deltastar[i] = -gama[i] * Math.sin(i*tau1) + delta[i]*Math.cos(i*tau1);
		//System.out.println( "Mode1 "+i+" alphastar "+alphastar[i]+" "+betastar[i]+" "+gamastar[i]+" "+deltastar[i]);
		}

	

		//inversion if needed (eq 9 et eq 19 )
		double r1 = alphastar[1]*deltastar[1] - betastar[1]*gamastar[1];
		if ( r1 < 0 )
		{
			for (int i=1; i <= Nmode; i++)
			{
				betastar[i] = -betastar[i];
				deltastar[i] = -deltastar[i];
			}
		}
		//System.out.println( "r1 "+r1);

		// eq 20 a,b,c,d=alphastar, betastar, gamastar, deltastar
		// eq 26, calculate phi

		double phi, aprime, bprime, cprime, dprime;
		double theta, lambda1, lambda12, lambda21, lambda2;
		double[] lambdaplus = new double[Nmode+2];
		double[] lambdaminus = new double[Nmode+2];
		double[] zetaplus = new double[Nmode+2];
		double[] zetaminus = new double[Nmode+2];
		for ( int i =1; i <= Nmode; i++ )
		{
			phi = 0.5*Math.atan2( 2*(alphastar[i]*betastar[i] + gamastar[i]*deltastar[i]), alphastar[i]*alphastar[i] + gamastar[i]*gamastar[i] - betastar[i]*betastar[i] - deltastar[i]*deltastar[i] );
			// eq 27, a',b',c',d'
			aprime = alphastar[i]*Math.cos(phi) + betastar[i]*Math.sin(phi);
			bprime = -alphastar[i]*Math.sin(phi) + betastar[i]*Math.cos(phi);
			cprime = gamastar[i]*Math.cos(phi) + deltastar[i]*Math.sin(phi);
			dprime = -gamastar[i]*Math.sin(phi) + deltastar[i]*Math.cos(phi);
			//System.out.println( "Mode "+i+" gamastar "+gamastar[i]+" "+deltastar[i]);

			theta = Math.atan2( cprime, aprime);
			//System.out.println( "Mode "+i+" phi "+phi+" "+theta+" "+aprime+" "+cprime);
			
			// equataion 25, lambdas
			lambda1 = Math.cos(theta)*aprime + Math.sin(theta)*cprime;
			lambda12 = Math.cos(theta)*bprime + Math.sin(theta)*dprime;
			lambda21 = -Math.sin(theta)*aprime + Math.cos(theta)*cprime;
			lambda2 = -Math.sin(theta)*bprime + Math.cos(theta)*dprime;

			//System.out.println( "Mode "+i+" lam1 "+lambda1+" "+lambda12+" "+lambda21+" "+lambda2);
			// eq 32
			lambdaplus[i] = (lambda1+lambda2) /2.0;
			lambdaminus[i] = (lambda1-lambda2) / 2.0; 

			// eq 37
			zetaplus[i] = theta-phi;
			zetaminus[i] = -theta-phi;
		}

		// print off set results
		//IJ.log("LOCO-EFA A0 offset: \t a="+alpha[0]+"\t c="+gama[0]+"\n");

		double[] loclambplus = new double[Nmode+2];
		double[] loczetaplus = new double[Nmode+2];
		double[] loclambminus = new double[Nmode+2];
		double[] loczetaminus = new double[Nmode+2];

		//Below eq. 41: A+(l=0)
		loclambplus[0] = lambdaplus[2];
		loclambminus[0] = 0;
		loczetaplus[0] = zetaplus[2];
		loczetaminus[0] = 0;
		//Below eq. 41: A+(l=1)
		loclambplus[1] = lambdaplus[1];
		loclambminus[1] = 0;
		loczetaplus[1] = zetaplus[1];
		loczetaminus[1] = 0;
		//Below eq. 41: A+(l>1)
		for ( int i=2; i <= Nmode-1; i++)
		{
			loclambplus[i] = lambdaplus[i+1];
			loczetaplus[i] = zetaplus[i+1];
		}
		if (Nmode >= 2 )
		{
			loclambplus[Nmode] = 0;
			loczetaplus[Nmode] = 0;
		}
		loclambplus[Nmode+1] = 0;
		loczetaplus[Nmode+1] = 0;
		//Below eq. 41: A-(l>0)
		for(int i=2; i<=Nmode+1; i++) 
		{
			loclambminus[i] = lambdaminus[i-1];
			loczetaminus[i] = zetaminus[i-1];
		}
	
		//IJ.log(" \n LOCOEFA Ln quadruplets:  \n");
		//for ( int i=0; i <= Nmode+1; i++ )
		//{
			//IJ.log("LOCO-EFA mode "+i+" \t lambdaplus="+loclambplus[i]+" \t lambdaminus="+loclambminus[i]+" \t zetaplus="+loczetaplus[i]+" t zetaminus="+loczetaminus[i]+"\n");
		//}
		

		// Eq 38., Lambda*zeta	
		locoL = new double[Nmode+2];
		double mloc = 0;
		posMax = 0;
		aplus = new double[Nmode+2];
		bplus = new double[Nmode+2];
		cplus = new double[Nmode+2];
		dplus = new double[Nmode+2];
		aminus = new double[Nmode+2];
		bminus = new double[Nmode+2];
		cminus = new double[Nmode+2];
		dminus = new double[Nmode+2];
		for ( int i =0; i <= Nmode+1; i++ )
		{
			aplus[i] = loclambplus[i]*Math.cos(loczetaplus[i]);
			bplus[i] = -loclambplus[i]*Math.sin(loczetaplus[i]);
			cplus[i] = loclambplus[i]*Math.sin(loczetaplus[i]);
			dplus[i] = loclambplus[i]*Math.cos(loczetaplus[i]);
			aminus[i] = loclambminus[i]*Math.cos(loczetaminus[i]);
			bminus[i] = -loclambminus[i]*Math.sin(loczetaminus[i]);
			cminus[i] = -loclambminus[i]*Math.sin(loczetaminus[i]);
			dminus[i] = -loclambminus[i]*Math.cos(loczetaminus[i]);
	
			//System.out.println("aplus "+aplus[i]);
			//System.out.println("cplus "+cplus[i]);
			//System.out.println("aminus "+aminus[i]);
			//System.out.println("bminus "+bminus[i]);

			//Equation 47: L
			locoL[i] = Math.sqrt( loclambplus[i]*loclambplus[i] + loclambminus[i]*loclambminus[i] + 2.0*loclambplus[i]*loclambminus[i]*Math.cos(loczetaplus[i]-loczetaminus[i]-2.0*loczetaplus[1]) );
			
			if ( (i>=3) && (locoL[i] > mloc) )
			{
				mloc = locoL[i];
				posMax = i;
			}
			//IJ.log("LOCO-EFA mode contribution "+i+": \t Ln= "+locoL[i]+"\n");
		}


		//          //the next line applies area normalisation, if wanted
		//              //mode[i].locoL/=sqrt(25793);

	}

	public double entropy(int Lmode)
	{
		double sumL = 0;
	   for ( int i = 0; i < Lmode; i++ )
		   sumL += locoL[i];

	   double e = 0;
	   for ( int i = 0; i < Lmode; i++ )
	   {
		   double fl = locoL[i]/sumL;
		   e -= fl*Math.log(fl); 
	   }	   

	   return e;
	}


	public Roi reconstruct( int Lmode)
	{
		int Npts = npts*10;
		float[] xpts = new float[Npts];
		float[] ypts = new float[Npts];
		double dstep = T/Npts;
		double tcur = dstep;
	
		// equation 42 of reconstruction
		for ( int i = 0; i < Npts; i++ )
		{
			double x = alpha[0]; // offset, A0
			double y = gama[0];  // offset
			x += aplus[0]*Math.cos(2*Math.PI*2*tcur/T) + bplus[0]*Math.sin(2*Math.PI*2*tcur/T);
			y += cplus[0]*Math.cos(2*Math.PI*2*tcur/T) + dplus[0]*Math.sin(2*Math.PI*2*tcur/T);
			
			x += aplus[1]*Math.cos(2*Math.PI*1*tcur/T) + bplus[1]*Math.sin(2*Math.PI*1*tcur/T);
			y += cplus[1]*Math.cos(2*Math.PI*1*tcur/T) + dplus[1]*Math.sin(2*Math.PI*1*tcur/T);

			if ( Lmode >=2 )
			{
			for ( int j = 2; j <= Lmode-2; j++ )
			{
				x += aplus[j]*Math.cos(2*Math.PI*(j+1)*tcur/T) + bplus[j]*Math.sin(2*Math.PI*(j+1)*tcur/T);
				y += cplus[j]*Math.cos(2*Math.PI*(j+1)*tcur/T) + dplus[j]*Math.sin(2*Math.PI*(j+1)*tcur/T);
			}
			for ( int j = 2; j <= Lmode; j++ )
			{
				x += aminus[j]*Math.cos(2*Math.PI*(j-1)*tcur/T) + bminus[j]*Math.sin(2*Math.PI*(j-1)*tcur/T);
				y += cminus[j]*Math.cos(2*Math.PI*(j-1)*tcur/T) + dminus[j]*Math.sin(2*Math.PI*(j-1)*tcur/T);
			}
		 }
		 tcur += dstep;
			xpts[i] = (float) x;	
			ypts[i] = (float) y;	
			//System.out.println("point "+i+" "+x+" "+y);
		}
		Roi res = new PolygonRoi( xpts, ypts, Roi.POLYGON);
		return res;
	}

	public double meanLn(int nmode)
	{
		double res = 0.0;
		for ( int i = 0; i < nmode; i++ )
		{
			res += locoL[i];
		}
		return res/nmode;
	}
	public double locoStd( int nmode, double mean)
	{
		double res = 0.0;
		for ( int i = 0; i < nmode; i++ )
			res += (locoL[i]-mean)*(locoL[i]-mean);
		return Math.sqrt(res/nmode);
	}

	public double maxContribPos()
	{
		return posMax;
	}

}
