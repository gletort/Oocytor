package cirb.oocytor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.plugin.ImageCalculator;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class RunUNet 
{
	private String script_name = "cortex_detector.py";
	
	public RunUNet( String script )
	{
		script_name = script;
	}
	
	/**
	 * Prepare the python environment and initialize nnInteractive to the active image
	 */
	public < T extends RealType< T > & NativeType< T > > ImagePlus process( ImagePlus resized, String model_path, int nfeat, boolean standardize, boolean debug )
	{
		
		final ImgPlus< T > img = ImagePlusAdapter.wrapImgPlus( resized );
		final String script = Utils.getScript( this.getClass().getResource( script_name ) );
		
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "model_path", model_path );
		inputs.put( "model_size", 256 );
		inputs.put( "batch_size", 30 );
		inputs.put( "nfeatures", nfeat );
		inputs.put( "standardize", standardize );
		inputs.put( "debug", debug ); // to catch more messages
		
		IJ.log( "Downloading/Installing the environment if necessary..." );
		// Check if cuda or not
		String os = System.getProperty("os.name").toLowerCase();
        boolean isGpuPlatform = os.contains("linux") || os.contains("win");
        String envName = isGpuPlatform ? "cuda" : "default";
		
        Environment env = null;
		try {
			env = Appose // the builder
					.pixi( this.getClass().getResource("pixi.toml") ) // we chose pixi as the environment manager
					.subscribeProgress( Utils::showProgress ) // report progress visually
					.subscribeOutput( Utils::showProgress ) // report output visually
					.subscribeError( IJ::log ) // log problems
			        .environment( envName )  // choose env based on OS (to get cuda or not)
					.build();
		} 
		catch (Exception e) 
		{
			IJ.error( "Error in creating/initializing the python environment: "+e.toString() );
			e.printStackTrace();
		} // create the environment
		Utils.hideProgress();

		
		/* Create a service that will run the Python script*/
		Service nnservice = env.python();
		try
		{
			// Import all that depends on numpy for Windows
			nnservice.init("import os\n"
					+ "import numpy as np\n"
					+ "import keras\n"
					+ "import tensorflow as tf\n");
		
			Task task = nnservice.task( script, inputs );
			
			// Start the script, and return to Java immediately.
			IJ.log( "Starting segmentation task..." );
			 // Listen for events from Python
			task.listen( e -> {
				if (e.message != null) 
				{ 
					if (debug )
						System.out.println( e.message );
					IJ.showStatus( e.message );
				}
				if ( e.current > 0 )
				{
					IJ.showProgress( (int)e.current, (int)e.maximum );
				}
			} );
		    		    
		    task.start();
			task.waitFor();
			
			// Verify that it worked.
			if ( task.status != TaskStatus.COMPLETE )
				throw new RuntimeException( "Python script failed with error: " + task.error );
			
			final NDArray maskArr = ( NDArray ) task.outputs.get( "mask" );
			final Img< T > mask = new ShmImg<>( maskArr );
			final ImagePlus impmask = ImageJFunctions.wrap( mask, "mask" );
			nnservice.close();
			return impmask;
		}
		catch ( Exception e)
		{
			IJ.error( "" + e );
		}
		return null;
	}
	
	 /** Find all model folder in the directory given */
	 public static List<Path> findModelFolders( String modelDir ) throws IOException 
	 {
	        Path root = Paths.get( modelDir );

	        return Files.walk(root)
	                .filter(Files::isDirectory)
	                .filter( dir -> dir.getFileName().toString().equals("variables") )
	                .filter( dir -> hasVariablesFile(dir) )
	                .map(Path::getParent)
	                .collect( Collectors.toList() );
	    }

	 	/** If folder has a file called variables */
	    private static boolean hasVariablesFile( Path variablesDir ) 
	    {
	        try 
	        {
	            return Files.list(variablesDir)
	                    .anyMatch(f -> f.getFileName().toString().startsWith("variables"));
	        } catch (IOException e) 
	        {
	            return false;
	        }
	    }
	
    /** \brief run all the networks, and take the average result */
    public ImagePlus runUnet(ImagePlus imp, String model_dir, int nfeat, boolean standardize, boolean show, boolean debug )
    {
    	// resize the image to the network training size
	    IJ.run(imp, "Select None", "");
		ImagePlus resized = imp.duplicate();
		IJ.run(resized, "Size...", "width=256 height=256 depth="+(imp.getNSlices())+" average interpolation=Bilinear");
	    IJ.run(resized, "8-bit", "");
        
        if (show) resized.show();
        ImagePlus res = null;
        ImageCalculator calc = new ImageCalculator();
        List<Path> networks;
		try {
			networks = findModelFolders( model_dir );
		} catch (IOException e) 
		{
			IJ.error( "No model(s) found in "+model_dir );
			e.printStackTrace();
			return null;
		}
        IJ.showStatus("Segment oocyte with "+networks.size()+" neural networks...");
        
        for ( int i = 0; i < networks.size(); i++ )
        {
        	IJ.showProgress( i, networks.size() );
        	Path model_path = (networks.get(i)).toAbsolutePath();
        	ImagePlus bin = process( resized, model_path.toString(), nfeat, standardize, debug );

        	if ( i >= 1) calc.run("add 32-bit stack", res, bin);
            else res = (ImagePlus) (bin.duplicate());
              //res.show();
              //new WaitForUserDialog("test").show();
              bin.changes=false;
              bin.close();
        }
        resized.changes = false;
        resized.close();
        IJ.run(res, "Divide...", "value="+networks.size()+" stack");
        res.resetDisplayRange();
       // res.show();
        //new WaitForUserDialog("test").show();
        IJ.run(res, "8-bit", "");
        
        return res;
    }
	
}
