// Macro to run all the Oocytor options: segmentation cortex, ZP, detection NEBD, measures of all features
// To not do some steps, comment them by adding "//" in the begining of the line
// To not measure all features, remove the not desired options, e.g. get_piv
// author: Gaëlle Letort, Terret/Verlhac team, CIRB, Collège de France

setBatchMode(true);
path = File.getDirectory(filename);
scale = 0.227; // pixel size in microns
dt = 0.05;     // temporal interval for movies
work(path);
setBatchMode(false);

function work(path)
{
	run("Get cortex", "smooth_contour=5 reach_proportion=0.005 nb_networks=2 choose="+path);
	run("Get ZP", "choose="+path);
	run("Find NEBD", "choose="+path);
	// Possible options: oocyte_feature zp_feature periv_feature cortex_fluctuation image_texture local_binary_pattern get_piv spatial zp_structure
	run("Measure features", "scale_xy="+scale+" time_tonebd=0.0 dt="+dt+" texture_size_xy="+scale+" piv_size_xy="+scale+" max_slice=-1 oocyte_feature zp_feature periv_feature cortex_fluctuation image_texture local_binary_pattern get_piv spatial zp_structure choose="+path);
}
	