# Oocytor  
Fiji plugin to segment oocyte and zona pellucida contours from transmitted light images and extract hundreds of morphological features to describe numerically the oocyte. 
![image Oocytor](./imgs/oo_logo.png?raw=true "Oocytor logo")
 
Overview:

 * [Presentation](#presentation)
 * [Installation](#installation)
 * [References](#references)
 * [Remarks](#remarks)

## Presentation
Oocytor is a Fiji plugin (developped in Java). It offers the possibility to segment oocyte contours from transmitted light images, based on U-Net like neural networks which were trained on both mouse and human oocytes (in prophase and meiosis I) acquired in different conditions for robutness. It can also extract hundreds morphological features to characterize numerically the oocyte, e.g. its perimeter, circularity...  


### Segmentation
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

### Dependencies

### Use
Once installed, the plugin can be found in Fiji in the `Plugins>CIRB>Oocytor` menu.
