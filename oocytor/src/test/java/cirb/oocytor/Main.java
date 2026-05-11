package cirb.oocytor;

import ij.IJ;
import net.imagej.ImageJ;

public class Main 
{
	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "/home/gaelle/Ext/MIV/EXT2026_ooMass/data/test/cell1_TRANS-1-3.tif" ).show();
		//interact.run("");
		GetCortex plugin = new GetCortex();
		plugin.run( "" );
		//GetNucleus plugin = new GetNucleus();
		//plugin.run( "position" );
	}
}
