package cirb.oocytor;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.measure.*;
import java.awt.*;
import ij.plugin.frame.*;

public class StraightZP
{
	double epaisseur;
	int bord;
	int sigma;
	ImagePlus straight;

	public StraightZP( double ep, int b, int s)
	{
		epaisseur = ep;
		b = bord;
		sigma = s;
		straight = null;
	}

	public void createStraight(ImagePlus ip, Roi roi)
	{
		if ( straight != null )
			close();
		ip.setRoi(roi);
		IJ.run(ip, "Area to Line", "");
		Straightener st = new Straightener();
		ImageProcessor iproc = st.straighten(ip, roi, (int) epaisseur);
		straight = new ImagePlus("ZP_"+ip.getTitle(),iproc);
		straight.show();
		IJ.run(straight, "8-bit", "");
		IJ.run(ip, "Select None", "");
		IJ.run(straight, "Select None", "");
	}

	public ImagePlus getStraight()
	{
		return straight;
	}

	public void closeImage(ImagePlus ip)
	{
		ip.changes = false;
		ip.close();
	}

	public void close()
	{
		straight.changes = false;
		straight.close();
	}

	public void clearZone(RoiManager rm)
	{
		rm.reset();
		straight.show();
		IJ.run(straight, "Select None", "");
		ImagePlus var = straight.duplicate();
		var.show();
		IJ.run(var, "Variance...", "radius=2.0");
		Prefs.blackBackground = false;
		IJ.setThreshold(var, 2, 255);
		IJ.run(var, "Convert to Mask", "");
		IJ.run(var, "Invert", "");
		IJ.run(var, "Analyze Particles...", "size=400-Infinity add");
		closeImage(var);
		if ( (rm.getCount() > 0) )
		{
			Roi paf = rm.getRoi(0);
			Rectangle rec = paf.getBounds();
			if ( (rec.width < (straight.getWidth()-5)) )
			{
			// left side is bigger
			if ( rec.x > (straight.getWidth()-(rec.x+rec.width)) )
			{
				straight.setRoi(0, 0, rec.x, straight.getHeight());
				ImagePlus dup = straight.crop();
				close();
				straight = dup;
				straight.show();
				IJ.run(straight, "8-bit", "");
				clearZone(rm);
			}
			else
			{
				straight.setRoi(rec.x+rec.width, 0, straight.getWidth()-(rec.x+rec.width), straight.getHeight());
				ImagePlus dup = straight.crop();
				close();
				straight = dup;
				straight.show();
				IJ.run(straight, "8-bit", "");
				clearZone(rm);
			}
			}
		}
		IJ.run(straight, "Select None", "");
		rm.reset();
	}

	public void getStructures(ResultsTable myrt)
	{
		IJ.run(straight, "Tubeness", "sigma="+sigma);
		IJ.selectWindow("tubeness of "+straight.getTitle());
		ImagePlus tub = IJ.getImage();
		tub.show();
		IJ.run(tub, "8-bit", "");
		IJ.run(tub, "8-bit", "");
			
		// all structures
		IJ.setAutoThreshold(tub, "Moments dark");
		Prefs.blackBackground = false;
		IJ.run(tub, "Convert to Mask", "");
		ImageStatistics stat = tub.getStatistics();
		if ( stat.mean > 175 )
		{
		//	System.out.println(stat.mean);
			IJ.run(tub, "Select None", "");
			IJ.run(tub, "Invert", "");
		}
		IJ.run(tub, "Open", "");
		IJ.run(tub, "Skeletonize", "");
		tub.setRoi(0,bord,tub.getWidth(),tub.getHeight()-(bord*2));
		stat = tub.getStatistics();
		if ( stat.mean > 175 )
		{
			IJ.run(tub, "Select None", "");
			IJ.run(tub, "Invert", "");
			tub.setRoi(0,bord,tub.getWidth(),tub.getHeight()-(bord*2));
			stat = tub.getStatistics();
		}
		myrt.addValue("ZPTubularStructure", stat.mean);

		IJ.run(tub, "Select None", "");
		ImagePlus vert1 = tub.duplicate();
		IJ.run(vert1, "Convolve...", "text1=[-1 0 1\n-1 0 1\n-1 0 1\n] normalize");
		ImagePlus vert2 = tub.duplicate();
		IJ.run(vert2, "Convolve...", "text1=[1 0 -1\n1 0 -1\n1 0 -1\n] normalize");
		ImagePlus hor1 = tub.duplicate();
		IJ.run(hor1, "Convolve...", "text1=[-1 -1 -1\n0 0 0\n1 1 1\n] normalize");
		ImagePlus hor2 = tub.duplicate();
		IJ.run(hor2, "Convolve...", "text1=[1 1 1\n0 0 0\n-1 -1 -1\n] normalize");
		
		closeImage(tub);
		ImageCalculator ic = new ImageCalculator();
		ic.run("Add", hor1, hor2);
		ic.run("Add", vert1, vert2);
		ic.run("Substract", vert1, hor1);
		//closeImage(hor);
		//closeImage(ver);
		closeImage(hor1);
		closeImage(hor2);
		ImagePlus res = vert1.duplicate();
		closeImage(vert1);
		closeImage(vert2);

		// vertical structures
		res.show();
		res.setRoi(0,bord,res.getWidth(),res.getHeight()-(bord*2));
		stat = res.getStatistics();
		if ( stat.mean > 175 )
		{
			IJ.run(res, "Select None", "");
			IJ.run(res, "Invert", "");
			res.setRoi(0,bord,res.getWidth(),res.getHeight()-(bord*2));
			stat = res.getStatistics();
		}
		myrt.addValue("ZPTubularStructureVertical", stat.mean);
		
                //new WaitForUserDialog("save").show();
		closeImage(res);
	}

}

