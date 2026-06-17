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

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.PointRoi;
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
		imp.show();
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
	    	File nucFile = new File( contours_dir + rootname+ "_nucleusPosition.csv" );
		    if ( nucFile.exists() )
		    {
	          ResultsTable rt;
	          try {
	              rt = ResultsTable.open( nucFile.getAbsolutePath() );
	          } catch (IOException e) {
	              IJ.error("Load NucPos ROIs",
	                       "Could not open file:\n" + nucFile.getAbsolutePath() + "\n" + e.getMessage());
	              return;
	          }
	        
	          for (int i = 0; i < rt.getCounter(); i++) 
	          {

	              int slice = (int) rt.getValue("Frame",      i);
	              int x     = (int) rt.getValue("X",      i);
	              int y     = (int) rt.getValue("Y",      i);
	              
	              imp.setSlice(slice);
	              PointRoi roi = new PointRoi(x, y);
	              roi.setPosition(slice);
	              roi.setSize(4);
	              imp.setRoi(roi);
	              roi.setImage(imp);
	              rm.addRoi(roi);
	          }
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
