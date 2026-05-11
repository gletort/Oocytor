
package cirb.oocytor;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.PrintWriter;
import javax.swing.ImageIcon;

/**
 *
 * @author gaelle
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
public class FindNEBD implements PlugIn
{
    private ImagePlus imp;
	Calibration cal;
	RoiManager rm;
	String dir = "";
	String modeldir = "";
	Utils util;
	final ImageIcon icon = new ImageIcon(this.getClass().getResource("/oo_logo.png"));
    boolean twins = false;
    private String[] models = {"nebd/mouse"};
	//private int nfeatures = 16; // nb features of the input model
	private boolean ask_directory = false; // working on opened image or on a directory
	private boolean debug = false; // add debug prints
	private String model_name = "";
	private String model_path = "";
	private boolean visible = true;
   
        
        /** \brief Dialog window 
        @return true if no pb, false else
         */
	public boolean getParameters()
	{
		if ( ask_directory )
		{
			visible = false;
		}
            GenericDialogPlus gd = new GenericDialogPlus("Find NEBD - Options", IJ.getInstance() );
            Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
            gd.setFont(boldy);
            gd.setBackground(new Color(100,140,170));
            gd.setInsets​(-100, 240, 0);
            ImagePlus iconimg = new ImagePlus();
            iconimg.setImage(icon.getImage());
            gd.addImage(iconimg);
            
            gd.addChoice( "Choose model:", models, models[0] );
			//gd.addDirectoryField("model_path:", modeldir);
	      if ( !ask_directory )
	      {
	           gd.addMessage( "Processing opened image. Close it if you wish to choose a directory instead" );
	           if ( dir == null )
	        	   gd.addDirectoryField("main_directory:", dir);
	           //gd.addCheckbox( "save_rois", save_rois );
	       }
	       else
	       {
	           gd.addDirectoryField("images_directory:", dir);
	       }

            gd.showDialog();
            if (gd.wasCanceled()) return false;
            
            model_name = gd.getNextChoice();
            //modeldir = gd.getNextString();
            if ( ask_directory )
           	 dir = gd.getNextString();	
            else
            {
           	 if ( dir == null )
           		 dir = gd.getNextString();	
           	 //save_rois = gd.getNextBoolean();
            }
            return true;
        }
	
	/** Get the path to the model local download or path */
	public void getModelPath()
	{
		// from name to path
		//model_path = model_name.replace("_", "/");
		model_path = util.getModelDir( model_name );
		if ( model_path == null )
		{
			IJ.error( "Problem to download/find local model." );
		}
	}
        
        /** Initialisation of an image */
	public void openResetImage(String imgname)
	{
		if ( ask_directory )
		{
		String ext = imgname.substring(imgname.lastIndexOf('.'));
		if ( ext.equals(".tif") )
			imp = IJ.openVirtual(imgname);
		else
			imp = IJ.openImage(imgname);
		}
		//imp = IJ.getImage();
		cal = util.initCalibration(imp);
		imp.show();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		util.unselectImage(imp);
	}
        
        public double localMean(double[] tab, int dep, int size)
        {
           int deb = dep - size;
           if (deb < 0) deb = 0;
           int end = dep + size;
           if (end > tab.length) end = tab.length;
           double mean = 0;
           int nb = 0;
           for (int i=deb; i<end; i++)
           {
                mean += tab[i];
                nb ++;
           }
           if ( nb==0 ) return 0;
            return mean/nb;
        }
        
        public int getNEBDFromUnet(ImagePlus ip)
        {
            int cum = 0;
            int nebd = -1;
            int nslices = ip.getWidth();
            if ( twins ) nslices = 1;
            double[] res = new double[nslices];
            for (int k=0; k<nslices; k++ ) res[k] = ip.getPixel(k,0)[0];
            util.close(ip);
            
            // Tmp write the results
            //for (double val: res) System.out.print(val+"\t");
            //System.out.println("");
            
            if ( nslices == 1 ) return ((res[0]>(0.5*255))?1:-1);
            
            int winsize = 3;
            int stay = 5;
            if (nslices <= 5) 
            {
                winsize = 1;
                stay = 1;
            }
            //double[] smooth = new double[nslices];
            for ( int j=1; j<= nslices; j++)
            {
                double lmean = localMean(res, j, winsize);
                //System.out.print(lmean+"\t");
                //smooth[j] = lmean;
                if (nebd < 0 )
                {
                    if ( lmean > (0.75*255) )
                    {
                        // stay above from some time, not too close to the end
                        if ( localMean(res, j+stay,winsize)> (0.75*255) )
                        {
                            if (winsize>=2 ) nebd = j-2;
                            if ((winsize>=2) && (j<2)) nebd = j;
                        }
                    }
                }
            }
            
         return nebd;
        }

        public int lookForNEBD(String inname )
        {
            IJ.log("Doing "+dir+inname);
            if (ask_directory) IJ.run("Close All", "");
            rm.reset();

            String imgname = dir+inname;
            openResetImage(imgname);
            util.reOrder(imp);
         
            if (imp.getNSlices() == 1)
            {
                twins = true;
                ImageStack stack = imp.createEmptyStack();
                stack.addSlice(imp.getProcessor());
                stack.addSlice(imp.getProcessor());
                imp.setStack(stack);
            }
            
            // run neural network for segmentation
            //ImagePlus unet = net.runUnet(imp, dir+inname, nnet, modeldir, 250, true);
            RunUNet runet = new RunUNet( "nebd_detector.py" );
            ImagePlus unet = runet.runUnet( imp, model_path, 32, visible, debug );
           
            
            // extract contours from the binary image, smooth a little
            IJ.showStatus("Find NEBD time...");
            int nebd = getNEBDFromUnet(unet);
            IJ.run(imp, "Select None", "");
            rm.runCommand(imp,"Deselect");
            String purinname = inname.substring(0, inname.lastIndexOf('.'));
            //rm.runCommand("Save", dir+"/contours/"+purinname+"_ZP.zip");
            util.close(imp);
            return nebd;
        }
        
        public void run(String arg)
        {
        	imp = WindowManager.getCurrentImage();  
    		if ( imp == null )
    		{
    			ask_directory = true;
    		}
    		else
    		{
    			ask_directory = false;
    			dir = IJ.getDirectory( "image" );
    			//System.out.println( "Image located in "+dir );
    		}
    		
            if (!getParameters()) return;
		
            if ( ask_directory) IJ.run("Close All");
            
            rm = RoiManager.getInstance();
            if ( rm == null )
            	rm = new RoiManager();
            rm.reset();
            util = new Utils();
		
            // Get/download if necessary the model
            getModelPath();
            
            // Prepare the result folder
              if (! dir.endsWith(File.separator))
               {
                    dir = dir + File.separator;
              }
              File directory = new File(dir+"/contours");
        	if (! directory.exists())
        		directory.mkdir();
               

        	if ( ask_directory )
        	{
                // Performs on all images in chosen directory
        		File thedir = new File(dir); 
        		File[] fileList = thedir.listFiles(); 
		     
        		String results = "FileName , NEBDSlice\n";
        		for (File fily : fileList) 
        		{
        			if ( fily.isFile() )
        			{
        				String inname = fily.getName();
        				int j = inname.lastIndexOf('.');
        				if (j > 0)
        				{
        					String extension = inname.substring(j);
        					if ( extension.equals(".tif") | extension.equals(".TIF") | extension.equals(".png") | extension.equals(".jpg") | extension.equals(".JPG") )
        					{
        						int nebd = lookForNEBD( inname );
        						results += inname+" , "+nebd+"\n";
        					}
                    	}
        			}
        		}
        		
        		System.gc(); // garbage collector   
        		try 
        		{
        			PrintWriter writer = new PrintWriter(new File(dir+"/nebd_times.csv"));
        			writer.write(results);
        			writer.close();
        			IJ.showStatus("Done");
        		} 
        		catch (Exception e) { IJ.error(e.getMessage()); }
        	}
        	else
        	{
        		String inname = imp.getTitle();
            	int nebd = lookForNEBD( inname );
            	IJ.log( "Filename: "+inname+" NEBD time: "+nebd );
        	}
    }

    
}
