package cirb.oocytor;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;



public class GetNucleus implements PlugIn
{
	private final ImageIcon icon = new ImageIcon( this.getClass().getResource("/oo_logo.png") );
	private String model_file = "";
	private String dir = ""; // directory of images to process
	private RoiManager rm;
	private ImagePlus imp;
	private Calibration cal;
	private Utils util;
	private double reference_diameter = 700; // average diameter of training images, reference size
	private double diameter = 700; // average oocyte diameter, to resize images if necessary
	private double confidence_threshold = 0.2;
	private boolean one_by_slice = true; // only one detection by slice/frame
	private boolean ask_directory = false; // work on current image or on a directory
	private boolean visible = true;
	private boolean debug = false; // more messages to help debugging
	
	/** \brief Dialog window 
	  @return true if no pb, false else
	  */
	public boolean getParameters()
	{
		GenericDialogPlus gd = new GenericDialogPlus("Get nucleus position - Options", IJ.getInstance() );
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
		gd.setFont(boldy);
		//gd.addMessage("----------------------------------------------------------------------------------------------- ");
		gd.addNumericField( "Oocyte_diameter_pixels", diameter );
		gd.addNumericField( "confidence_threshold :", confidence_threshold );
		gd.addCheckbox( "Only one nucleus", one_by_slice );
		//gd.addCheckbox("visible_mode", visible);
        		
		gd.setBackground(new Color(100,140,170));
		gd.setInsets​(-100, 240, 0);
		ImagePlus iconimg = new ImagePlus();
		iconimg.setImage(icon.getImage());
		gd.addImage(iconimg);
              
        gd.addFileField("model_file:", model_file);
        if ( !ask_directory )
        {
        	gd.addMessage( "Processing opened image. Close it to choose a directory instead" );
        }
        if ( ask_directory )
        	gd.addDirectoryField("images_directory:", dir);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		//visible = gd.getNextBoolean();
		diameter = (double) gd.getNextNumber();
        confidence_threshold = (double) gd.getNextNumber();
        one_by_slice = gd.getNextBoolean();
		model_file = gd.getNextString();
        if ( ask_directory )
        	dir = gd.getNextString();	
        return true;
	}
	
	/** Initialisation of an image 
	  @param imgname: name of the image to treat
	  */
	public void openResetImage(String imgname)
	{
		String ext = imgname.substring(imgname.lastIndexOf('.'));
		if ( ext.equals(".tif") )
			imp = IJ.openVirtual(imgname);
		else
			imp = IJ.openImage(imgname);
		//imp = IJ.getImage();
		cal = util.initCalibration(imp);
		if (visible) imp.show();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		util.unselectImage(imp);
	}
	
	/**
	 * Process all slices in the image and detect the nucleus position with trained yolo model
	 */
	public void detectNucleus( String imagename )
	{
		IJ.log( "Processing image "+imagename );
		openResetImage( imagename );
		process();
		
		if ( imp != null )
			imp.close();
	}
	
	/** Process currently opened image */
	public void detectNucleusOnImp()
	{
		IJ.log( "Processing opened image "+imp.getTitle() );
		cal = util.initCalibration(imp);
		if (visible) imp.show();
		rm.runCommand(imp,"Deselect");
		rm.reset();
		util.unselectImage(imp);
		process();	
	}

	/** Resize imp if necessary */
	public ImagePlus resizeImage()
	{
		double factor = reference_diameter/diameter;
		if ( Math.abs( 1 - factor ) > 0.3 )
		{
			int newWidth  = (int) ( imp.getWidth()  * factor );
	        int newHeight = (int) ( imp.getHeight() * factor );

	        ImageProcessor ip = imp.getProcessor().duplicate();
	        ip.setInterpolationMethod( ImageProcessor.BILINEAR );
	        ImageProcessor resizedIp = ip.resize(newWidth, newHeight, true);

	        ImagePlus resized = new ImagePlus("Resized-"+imp.getTitle(), resizedIp);
	        return resized;
		}
		return imp;
	}
	
	/**
	 * Prepare the python environment and initialize nnInteractive to the active image
	 */
	public < T extends RealType< T > & NativeType< T > > void process()
	{
		ImagePlus resized = resizeImage();
		final ImgPlus< T > img = ImagePlusAdapter.wrapImgPlus( resized);
		final String script = getScript( this.getClass().getResource("nucleus_detector.py" ) );
		
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "model_file", model_file );
		inputs.put( "window_size", 800 );
		inputs.put( "overlap_ratio", 0.2 );
		inputs.put( "confidence_threshold", confidence_threshold );
		inputs.put( "keep_best", one_by_slice );
		inputs.put( "debug", debug ); // to catch more messages
		
		IJ.log( "Downloading/Installing the environment if necessary..." );
		String envName = "default"; // can be modified if need several types of environment
		
		Environment env = null;
		try {
			env = Appose // the builder
					.pixi( this.getClass().getResource("pixi.toml") ) // we chose pixi as the environment manager
					.subscribeProgress( this::showProgress ) // report progress visually
					.subscribeOutput( this::showProgress ) // report output visually
					.subscribeError( IJ::log ) // log problems
			        .environment( envName )  // choose env based on OS (to get cuda or not)
					.build();
		} 
		catch (Exception e) 
		{
			IJ.error( "Error in creating/initializing the python environment: "+e.toString() );
			e.printStackTrace();
		} // create the environment
		hideProgress();

		
		/*
		 * Using this environment, we create a service that will run the Python
		 * script.
		 */
		Service nnservice = env.python();
		try
		{
			// Import all that depends on numpy for Windows
			nnservice.init("import os\n"
					+ "import numpy as np\n"
					+ "from sahi import AutoDetectionModel\n"
					+ "from sahi.predict import get_sliced_prediction\n"
					+ "import torch\n"
					+ "import ultralytics\n");
			
			//python.debug( msg -> show_messages( msg ) );
			
			Task task = nnservice.task( script, inputs );
			
			// Start the script, and return to Java immediately.
			IJ.log( "Starting yolo task..." );
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
			
			Map<String, Object> map_detections = (Map<String, Object>) task.outputs.get( "detections" );
			readDetections( map_detections, true );
			
			nnservice.close();
		}
		catch ( Exception e)
		{
			IJ.error( "" + e );
		}
	}
	
	/** Read outputs from python script and convert them to ROIs */
	public void readDetections( Map<String, Object> map_detections, boolean add_to_manager )
	{
		try 
		{
			// If need to rescale the positions (image was resized)
			double factor = reference_diameter/diameter;
    		factor = ( Math.abs( 1 - factor ) > 0.3 ) ? factor: 1;
    		
        	List<Integer> zpos = (List<Integer>) map_detections.get( "slice" );
        	List<List<Integer>> center = (List<List<Integer>>) map_detections.get( "center" );
	
        	for ( int i=0; i<center.size(); i++ )
        	{
        		int cx = (int) ( center.get(i).get(0) / factor );
        		int cy = (int) ( center.get(i).get(1) / factor );
        		PointRoi proi = new PointRoi( cx, cy );
        		proi.setSize( 4 );
        		String roi_name = (String) "nucleus_"+i;
        		proi.setName( ""+roi_name );
        		proi.setImage(imp);
        		int roi_frame = (int) zpos.get(i)+1; // starts at 0 in python
        		proi.setPosition( 1, 1, roi_frame );
        		if ( add_to_manager )
        			rm.addRoi( proi ); // Add to RoiManager
        		
        	}		
		}catch (Exception e) 
		{
			System.err.println("Error creating ROI: " + e.getMessage());
			e.printStackTrace();
		}
	}

	
	/*
	 * The Python script.
	 * 
	 * This is the Python code that will be run by the service. It is loaded from an existing
	 * .py file, placed in the URL location */
	public static String getScript( URL python_script )
	{
		String script = "";
		try {
			final URL scriptFile = python_script;
			script = IOUtils.toString(scriptFile, StandardCharsets.UTF_8);
			
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return script;
	}
	

private volatile JDialog progressDialog;

private volatile JProgressBar progressBar;

private void showProgress( final String msg )
{
	showProgress( msg, null, null );
}

private void showProgress( final String msg, final Long cur, final Long max )
{
	EventQueue.invokeLater( () ->
	{
		if ( progressDialog == null ) {
			final Window owner = IJ.getInstance();
			progressDialog = new JDialog( owner, "Fiji ♥ Appose" );
			progressDialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
			progressBar = new JProgressBar();
			progressDialog.getContentPane().add( progressBar );
			progressBar.setFont( new Font( "Courier", Font.PLAIN, 14 ) );
			progressBar.setString(
				"--------------------==================== " +
				"Building Python environment " +
				"====================--------------------"
			);
			progressBar.setStringPainted( true );
			progressBar.setIndeterminate( true );
			progressDialog.pack();
			progressDialog.setLocationRelativeTo( owner );
			progressDialog.setVisible( true );
		}
		if ( msg != null && !msg.trim().isEmpty() ) progressBar.setString( "Building Python environment: " + msg.trim() );
		if ( cur != null || max != null ) progressBar.setIndeterminate( false );
		if ( max != null ) progressBar.setMaximum( max.intValue() );
		if ( cur != null ) progressBar.setValue( cur.intValue() );
	} );
}
private void hideProgress()
{
	EventQueue.invokeLater( () ->
	{
		if ( progressDialog != null )
			progressDialog.dispose();
		progressDialog = null;
	} );
}
	
	
	public void run( String arg )
	{
		imp = WindowManager.getCurrentImage();  
		if ( imp == null )
		{
			ask_directory = true;
		}
		
        model_file = ""; // see if keep in memory
		// get parameters, initialize
		if ( !getParameters() ) { return; }
		//IJ.run("Close All");
		rm = RoiManager.getInstance();
		if ( rm == null )
			rm = new RoiManager();
		rm.reset();
		util = new Utils();
		
		if (! dir.endsWith(File.separator))
        {
             dir = dir + File.separator;
        }
        if ( !model_file.endsWith(".pt") )
        {
        	IJ.error( "Selected model file is not of supported type. Should be a .pt file" );
        	return;
        }             
                
		// Performs on all images in chosen directory
		if ( ask_directory )
		{
			File thedir = new File( dir ); 
			File[] fileList = thedir.listFiles(); 
			File directory = new File( dir+"contours" );
			if (! directory.exists())
				directory.mkdir();

			for (File fily : fileList) 
			{
				if ( fily.isFile() )
				{
					String inname = fily.getName();
					int j = inname.lastIndexOf('.');
					if (j > 0)
					{
						String extension = inname.substring(j);
						if ( extension.equals(".tif") | extension.equals(".TIF") | extension.equals(".czi")  | extension.equals(".png") | extension.equals(".jpg") | extension.equals(".JPG") )
						{
							detectNucleus( fily.getAbsolutePath() );
						}   
                                        
					}
					System.gc(); // garbage collector
				}
			}
		}
		else
		{
			detectNucleusOnImp();
		}
               
	}


}
