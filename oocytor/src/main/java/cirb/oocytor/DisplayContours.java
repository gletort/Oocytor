package cirb.oocytor;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

public class DisplayContours implements PlugIn
{
	private ImagePlus imp;
	RoiManager rm;
	private String image_name;
	private boolean show_cortex = true;
	private boolean show_zp = true;
	private boolean show_nucleus = true;
	
	
	public boolean chooseImage()
	{
		GenericDialogPlus gd = new GenericDialogPlus("Get Zona Pellucida - Options", IJ.getInstance());
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
		gd.setFont(boldy);
		//gd.addMessage("----------------------------------------------------------------------------------------------- ");

		gd.setBackground(new Color(100, 140, 170));
		gd.addFileField( "Choose image to open", "" );
		gd.addCheckbox( "Show cortex contour", show_cortex);
		gd.addCheckbox( "Show ZP contour", show_zp);
		gd.addCheckbox( "Show nucleus position", show_nucleus);
	    
		gd.showDialog();
		if (gd.wasCanceled()) return false;

		image_name = gd.getNextString();
		show_cortex = gd.getNextBoolean();
		show_zp = gd.getNextBoolean();
		show_nucleus = gd.getNextBoolean();
		return true;
	}
	
	/** Initialisation of an image */
	public void openResetImage( String imgname ) 
	{
		imp = IJ.openImage(imgname);
		rm.runCommand(imp, "Deselect");
		
	}
	
	public void openContours()
	{
		File img = new File( image_name );
		String image_dir = img.getParent();
		String rootname = img.getName();
		rootname = rootname.substring(0, rootname.lastIndexOf('.'));
	        
		String contours_dir = image_dir + File.separator + "contours" + File.separator;

	    if ( show_cortex ) 
	    {
	    	File cortex = new File( contours_dir + rootname+ "_UnetCortex.zip" );
	    	if ( cortex.exists() )
	            rm.runCommand( "Open", cortex.getAbsolutePath() );
	    }
	    
	    if ( show_zp ) 
	    {
	    	File zp = new File( contours_dir + rootname+ "_ZP.zip" );
	    	if ( zp.exists() )
	            rm.runCommand( "Open", zp.getAbsolutePath() );
	    }
	    
	    if ( show_nucleus )
	    {
	    	  String nucposFile = contours_dir + rootname + "_nucpos.csv";
	          
	          ResultsTable rt;
	          try {
	              rt = ResultsTable.open( nucposFile );
	          } catch (IOException e) {
	              IJ.error("Load NucPos ROIs",
	                       "Could not open file:\n" + nucposFile + "\n" + e.getMessage());
	              return;
	          }
	        
	          for (int i = 0; i < rt.getCounter(); i++) 
	          {

	              int slice = (int) rt.getValue("T",      i);
	              int x     = (int) rt.getValue("X",      i);
	              int y     = (int) rt.getValue("Y",      i);
	              int w     = (int) rt.getValue("Width",  i);
	              int h     = (int) rt.getValue("Height", i);

	              imp.setSlice(slice);
	              Roi roi = new Roi(x, y, w, h);
	              roi.setPosition(slice);
	              imp.setRoi(roi);
	              roi.setImage(imp);
	              rm.addRoi(roi);
	          }
	    }
	        
	        rm.runCommand("Associate", "true");
	        rm.runCommand("Centered", "false");
	        rm.runCommand("UseNames", "false");
	        rm.runCommand("Show All");
	}
	
	public void run( String arg )
	{
		if ( !chooseImage() )
		{
			IJ.error("Non valid parameters choice");
			return;
		}
		
		rm = RoiManager.getInstance();
		if (rm == null)
			rm = new RoiManager();
		rm.reset();
		openResetImage( image_name );
		openContours();
	}
}
