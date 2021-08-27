# Oocytor  
Fiji plugin to segment oocyte and zona pellucida contours from transmitted light images and extract hundreds of morphological features to describe numerically the oocyte. 

![image Oocytor](./imgs/oo_logo.png?raw=true "Oocytor logo")
 
 * [Presentation](#presentation)
	* [Segmentation](#segmentation)
	* [Features](#features)
 * [Installation](#installation)
	* [Usage](#use)
	* [Macros](#macros)
 * [References](#references)
 * [Remarks](#remarks)

## Presentation
Oocytor is a Fiji plugin (developped in Java). It offers the possibility to segment oocyte contours from transmitted light images, based on U-Net like neural networks which were trained on both mouse and human oocytes (in prophase and meiosis I) acquired in different conditions for robutness. It can also extract hundreds morphological features to characterize numerically the oocyte, e.g. its perimeter, circularity...  


### Segmentation
`Oocytor` uses neural networks for the segmentation. It was trained with images of individual oocytes placed in the center at different resolutions.
Hence, to use it you need to have a unique full oocyte in the image. When you have several oocytes in the same image, it is possible to separate them automatically with our [individualize macro](#macros "macro"). 

#### Oocyte contour segmentation
The plugin option `Get cortex` detects the oocyte contour of all images/stacks in a chosen folder and returns the oocyte contour as ROIs which are saved in a folder named `contours` automatically created in the given folder.
![image cortex segmentation](./imgs/cortexseg.png?raw=true "Example of cortex segmentation")

To use it, the plugin ask for 3 parameters:
* `smooth contour`: possibility to smooth the final ROI contour, to avoid too wavy contours. The value of the parameter is the number of neighboring points that are taken into account to smooth. Default parameter (5) is low so as not to smooth too much. 
* `reach proportion`: to build the contour, the binary image calculated from the output of the neural networks is analysed to find the limit between the segmented part (white) and the outside (black). The result can be refined by looking at the local intensity changes in the initial image and the limit will be updated to the highest point of intensity change in the neighborhood of the found limit. This parameter control the extend of this neighborhood. Low value means to stick to the pure network output while higher value implies sensitivity to the local intensity changes.
* `nb networks`: to increase the robutness of the results, it is possible to combine the outputs of several neural networks trained on this segmentation task and to take the common output. In the default version, we use 2 networks, but users can add their own networks or remove one.

![gui cortex](./imgs/interfaceCortex.png?raw=true "Cortex option interface")

#### ZP contour segmentation
The plugin option `Get ZP` detects the zona pellucida outside and inside contours of all images/stacks in a chosen folder and returns the contours as ROIs which are saved in a folder named `contours` automatically created in the given folder.
![image zp segmentation](./imgs/zpseg.png?raw=true "Example of zp segmentation")

#### Neural networks used for segmentation
To perform the segmentation, we used neural networks trained on thausend images of mouse and human oocytes acquired in transmitted light. However it might be necessary to retrain it to adapt it to oocyte images, particularly if they are quite different from the one used for the training. 
Our trained neural networks are available in this github in the models folder. They should be placed in a `models` folder in the Fiji directory (see [Installation](#installation)). They can be used as pre-trained networks to retrain it with only a few new images. New networks (retrain or new architecture network) can be used by oocytor plugin by replacing the `oocyte*.zip` folders by your own networks in the corresponding folders (`models/cortex` or `models/zp`). The names has to be kept the same.

### Features

## Installation

A compiled version is available in this release as a `.jar` file called [`oocytor_0.0.jar`](./oocytor/src/target/ "File link"). To install it, simply put this file in the `plugins` folder of ImageJ/Fiji and restart Fiji. 
You also need to put the neural networks for the segmentation in Fiji. 
For this, copy the `models` folder in the main Fiji directory (or insert it in the `models` folder of Fiji if it exists already).
Oocytor uses other Fiji plugins which you may have to install if they are not already installed. See the following section.

You can also download the source code and compile it directly. As a lot of Fiji plugins, it is organized as a `Maven` project.

### Dependencies
Oocytor uses the following Fiji plugins:
* [CSBDeep_fiji](https://github.com/CSBDeep/CSBDeep_fiji#imagej-update-site "CSBDeep install"): this plugin is used in the segmentation part. To install it, add it in Fiji update sites (go to `Help>Update..`, clik on `Manage update sites`, look for `CSBDeep` and select it)
* [FeatureJ](https://imagescience.org/meijering/software/featurej/ "FeatureJ website"): this plugin in the features part only. Add the `.jar` files given in FeatureJ website to the `plugins` folder of Fiji.		

### Use
Once installed, the plugin can be found in Fiji in the `Plugins>CIRB>Oocytor` menu.

### Macros
We put in the [`macros`](./macros "macros folder") folder of this repository several ImageJ/Fiji macros that can be usefull to automatize some parts of using `Oocytor` on several files for example.
* `Oocytor_DisplayROIs.ijm` ask to choose a file and open it with the corresponding segmentation ROIs if they have been done.
* `Oocytor_DrawROI.ijm` open and draw the corresponding segmentation ROIs on one slice or on the full stack, for one image or all images of a folder. The color of the drawing (red for cortex and orange for zona pellucida) can be changed in the code of the macro.
* `Oocytor_individualizeOocytes.ijm` find all the oocytes in a stack/images containing several ones and create new stack/images of cropped region containing only one full oocyte in the center. 
* `Oocytor_runAll.ijm` performs directly all the steps of `Oocytor` analysis (cortex and zp segmentation, NEBD detection and features extraction) on a given file/directory.
* `Oocytor_WriteOoCyteCenterPosition.ijm` writes the position of the ROIs cortex of the oocyte (found by oocytor) at all time/z points if it is stack of images for all the files of a given directory.

Other macros will be added while developping `Oocytor` and of course feel free to add yours.

## References

## Remarks

This plugin was developped in the [Terret/Verlhac](https://www.college-de-france.fr/site/en-cirb/Terret-Verlhac.htm "website team") team and the imaging facility [Orion] (http://orion-cirb.fr/ "orion website") at the Centre de Recherche Interdisciplinaire en Biologie in the College de France.
`Oocytor` is freely available open-source under the GNU GPL v3.0 License (see License file). 
