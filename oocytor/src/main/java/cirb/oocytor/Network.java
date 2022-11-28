package cirb.oocytor;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Concatenator;
import ij.plugin.ImageCalculator;
import ij.plugin.SubstackMaker;
import ij.process.ImageStatistics;
import java.io.OutputStream;
import java.io.PrintStream;
import net.imagej.ImageJ;
import java.util.HashMap;
import java.util.concurrent.Future;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.scijava.command.CommandModule;

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
public class Network {
             SubstackMaker sub = new SubstackMaker();
             Concatenator cont = new Concatenator();
             final ImageJ ij = new ImageJ(); 
             DatasetService datasetIJ;
             final AxisType[] axes = new AxisType[]{Axes.X, Axes.Y, Axes.TIME};
             final HashMap<String, Object> paramsCNN = new HashMap<>();
             PrintStream nothing = new NullPrintStream();
                 
        public void init() {
            checkForCSBDeep();
             ij.launch();
             datasetIJ = ij.dataset();
              paramsCNN.put("normalizeInput", true);
              paramsCNN.put("percentileBottom", 0);
              paramsCNN.put("percentileTop", 100);
              paramsCNN.put("clip", false);
              paramsCNN.put("nTiles", 1);
              paramsCNN.put("blockMultiple", 256);
              paramsCNN.put("overlap", 0);
              paramsCNN.put("batchSize", 30);
              paramsCNN.put("showProgressDialog", false);   
        }
        
              private void checkForCSBDeep() {
        try {
            Class.forName("de.csbdresden.csbdeep.commands.GenericNetwork");
        } catch (ClassNotFoundException e) {
            IJ.error("Required CSBDeep plugin missing");
            throw new RuntimeException("CSBDeep not installed");
        }
    }
   
	public ImagePlus loadNetwork(int i, ImagePlus imp, String modeldir, int subst)
	{
            
            //System.setOut(nothing);   
            ImagePlus result = null;
            try
            {    
    
             int tsep = imp.getNSlices();
             if (tsep>subst) tsep = subst;     
             int end = 1;
                 
             while( end<=imp.getNSlices() )
             {
                int step = end+tsep;
                if ( step > imp.getNSlices() ) step = imp.getNSlices();
                ImagePlus tmp = sub.makeSubstack(imp, end+"-"+step);
                //System.out.println(end+" "+step);
                end = step+1;
                final Img inputImg = (Img) ImageJFunctions.wrap(tmp);
                Dataset dataset = datasetIJ.create(new ImgPlus(datasetIJ.create(inputImg), "input", axes));
                tmp.changes = false;
                tmp.close();
                
                paramsCNN.put("input", dataset);
                paramsCNN.put("modelFile", modeldir+"oocyte"+i+".zip"); 
                final Future<CommandModule> futureCNN = ij.command().run(de.csbdresden.csbdeep.commands.GenericNetwork.class, false, paramsCNN);
                Dataset prediction = (Dataset) futureCNN.get().getOutput("output");
                ImgPlus implus = prediction.getImgPlus();
                ImagePlus partresult = ImageJFunctions.wrap(implus, "result");
                if ( (partresult.getNChannels()>1) && (partresult.getNSlices() == 1) )
                {
                    partresult.setDimensions(1, partresult.getNChannels(), 1);
                }
                if ( result != null )
                {
                    ImagePlus tog = cont.concatenate(result, partresult, true);
                    result.changes = false;
                    result.close();
                    partresult.changes = false;
                    partresult.close();
                    result = tog;
                }
                else result = partresult;
                
                dataset = null;
                prediction = null;
                implus = null;
              }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                IJ.error("Problem loading neural network\n "+e.toString()+"\n "+e.getMessage());
                return null;
            }
            
            System.setOut(System.out);
            return result; 
          }
        
         
          /** \brief Normalization slice by slice 
         
         * img = (img-min)/(max-min) for each slice
         */
        public void normalizeBySlice(ImagePlus ip)
        {
            for ( int i = 1; i <= ip.getNSlices(); i++)
            {
                ip.setSlice(i);
                ImageStatistics stat = ip.getStatistics();
                IJ.run(ip, "Subtract...", "value="+stat.min+" slice");
                double diff = 255.0/(stat.max-stat.min);
                IJ.run(ip, "Multiply...", "value="+diff+" slice");
            }
        }
        
        /** \brief run all the networks, and take the average result */
        public ImagePlus runUnet(ImagePlus imp, String filename, int nnet, String modeldir, int subst, boolean show )
        {
            IJ.run(imp, "Select None", "");
	    ImagePlus resized = imp.duplicate();
	    //resized.show();
	    IJ.run(resized, "Size...", "width=256 height=256 depth="+(imp.getNSlices())+" average interpolation=Bilinear");
	    IJ.run(resized, "8-bit", "");
            normalizeBySlice(resized);
         
             IJ.showStatus("Segment oocyte with neural networks...");
            if (show) resized.show();
            ImagePlus res = null;
	    ImageCalculator calc = new ImageCalculator();
	    for ( int i = 0; i < nnet; i++ )
	    {
		 ImagePlus bin = loadNetwork(i, resized, modeldir, subst);
 
		  if ( i >= 1) calc.run("add 32-bit stack", res, bin);
                  else res = (ImagePlus) (bin.duplicate());
                  //res.show();
                  bin.changes=false;
                  bin.close();
	    }
            resized.changes = false;
            resized.close();
            IJ.run(res, "Divide...", "value="+nnet+" stack");
            res.resetDisplayRange();
            IJ.run(res, "8-bit", "");
           // new WaitForUserDialog("test").show();
	    return res;
        }

        public void end(){
            ij.dispose();
            paramsCNN.clear();
        }

    public void run() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
