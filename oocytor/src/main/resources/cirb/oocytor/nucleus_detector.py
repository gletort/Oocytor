import numpy as np
from sahi import AutoDetectionModel
from sahi.predict import get_sliced_prediction
import torch


from skimage import io
from skimage import util as skutil
from skimage.color import gray2rgb

report = print
def listen(callback):
    global report
    report = callback

# Listen for Appose task updates.
appose_mode = 'task' in globals()
if appose_mode:
    listen(task.update)
else:
    from appose.python_worker import Task
    task = Task()

def share_as_ndarray(img):
    """Copies a NumPy array into a same-sized newly allocated block of shared memory"""
    from appose import NDArray
    shared = NDArray(str(img.dtype), img.shape)
    shared.ndarray()[:] = img
    return shared


device = "cuda:0" if torch.cuda.is_available() else "cpu"

## initialize model
model = AutoDetectionModel.from_pretrained( model_type="ultralytics", model_path = model_file, confidence_threshold=confidence_threshold, device=device )

img = np.array(image.ndarray())
task.update( f"Input image shape {img.shape}" )
## 2D image 
if len(img.shape) == 2:
    img = [img]
   
## preprocess image to be normalised and rgb
img = np.uint16( img )
if debug:
    task.update( f"Normalising input image..." )
## preprocess: normalise to 0 256
for i in range(len(img)):
    quants = np.quantile( img[i], [0.001, 0.999] )
    img[i] =  (img[i]-quants[0])/(quants[1]-quants[0])*255 
    img[i] = np.uint8( np.clip( img[i], 0, 255 ) )

task.update(f"Running YOLO from model at {model_file}")
results = { "center": [], "slice": [], "confidence": [] }
## do each slice/frame
for ind, cimg in enumerate( img ):
    task.update( current=ind, maximum = len(img), message=f"Processing frame {ind}/{len(img)}" ) 
    if len(cimg.shape) == 2:  ## convert it to rgb
        cimg = np.uint8( np.stack( [cimg, cimg, cimg], axis=-1 ) )
    if debug:
        task.update( f"Slice shape {cimg.shape}, window_size {window_size}, overlap {overlap_ratio}" )
    yolores = get_sliced_prediction( cimg, model, slice_height=window_size, slice_width=window_size, overlap_height_ratio=overlap_ratio, overlap_width_ratio=overlap_ratio )
    if yolores is not None:
        if keep_best:
            indbest = -1
            best_score = 0
        for iroi, roi in enumerate(yolores.object_prediction_list):
            score = roi.score.value
            ## look only for best detection to keep
            if keep_best:
                if score > best_score:
                    best_score = score
                    indbest = iroi
            else:
                ## add all rois
                bbox = np.int32( roi.bbox.box )
                mean_size = ((bbox[2]+1-bbox[0]) + (bbox[3]+1-bbox[1])) / 2
                center_x = (round( (bbox[0] + bbox[2]+1)/2 ))
                center_y = (round( (bbox[1] + bbox[3]+1)/2 ))
                results["center"].append( [center_x, center_y] )
                #results["size"].append( mean_size )
                results["slice"].append( ind )
                results["confidence"].append( roi.score.value )
        if keep_best and indbest >= 0:
            roi = yolores.object_prediction_list[indbest]
            bbox = np.int32( roi.bbox.box )
            mean_size = ((bbox[2]+1-bbox[0]) + (bbox[3]+1-bbox[1])) / 2
            center_x = (round( (bbox[0] + bbox[2]+1)/2 ))
            center_y = (round( (bbox[1] + bbox[3]+1)/2 ))
            results["center"].append( [center_x, center_y] )
            #results["size"].append( mean_size )
            results["slice"].append( ind )
            results["confidence"].append( roi.score.value )

if appose_mode:
    task.outputs["detections"] = results
    task.update(f"There are {len(results['slice'])} detected potential nuclei")



