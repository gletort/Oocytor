###
# #%L
# Plugins to segment different oocytes structures, and to extract numerous features to describe them
# %%
# Copyright (C) 2021 - 2026 Gaelle Letort
# %%
# Redistribution and use in source and binary forms, with or without modification,
# are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 
# 3. Neither the name of the CIRB nor the names of its contributors
#    may be used to endorse or promote products derived from this software without
#    specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
# IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
# OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# #L%
###
import os
import numpy as np
import keras
from keras.models import Sequential, Model
from keras.layers import Dense, Dropout, Flatten
from keras.layers import Conv2D, MaxPooling2D, Conv2DTranspose, GlobalAveragePooling2D, UpSampling2D
from keras.layers import Input
from keras.layers import concatenate
from tensorflow.keras.layers import BatchNormalization
import tensorflow as tf
from keras.models import load_model
from keras.models import model_from_json


def normalise(img):
    """ Normalize with the quantiles """
    img = np.float32(img) ## be sure it's float
    quantiles = np.quantile( img, [0.001, 0.9998] )
    img = (img - quantiles[0] )/ (quantiles[1]-quantiles[0])
    img = np.clip( img, 0.0, 1.0 )
    if standardize:
         img = (img - img.mean()) / img.std()
    return img

def jaccard_distance(y_true, y_pred, smooth=100):
    """Jaccard distance for semantic segmentation.
    Also known as the intersection-over-union loss.
    Jaccard = (|X & Y|)/ (|X|+ |Y| - |X & Y|)
            = sum(|A*B|)/(sum(|A|)+sum(|B|)-sum(|A*B|))
  
    """
    #y_pred = K.cast(K.greater(y_pred, .5), dtype='float32')
    intersection = tf.reduce_sum(y_true * y_pred, axis=(1,2))
    sum_ =tf.reduce_sum(y_true+y_pred, axis=(1,2))
    jac = (intersection + smooth) / (sum_ - intersection + smooth)
    return (1-jac)*smooth


def conv_block( nfils, inputs):
    conv = Conv2D(nfils, 3, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(inputs)
    conv = BatchNormalization()(conv)
    conv = Conv2D(nfils, 3, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(conv)
    conv = BatchNormalization()(conv)
    return conv


def build_unet(IMG_SIZE, nfil):
    inputs = Input((IMG_SIZE, IMG_SIZE, 1))
    
    conv1 =  conv_block( nfil, inputs)
    pool1 = MaxPooling2D(pool_size=(2, 2))(conv1)
    conv2 =  conv_block( nfil*2, pool1)
    pool2 = MaxPooling2D(pool_size=(2, 2))(conv2)
    conv3 =  conv_block( nfil*4, pool2)
    pool3 = MaxPooling2D(pool_size=(2, 2))(conv3)
    conv4 =  conv_block( nfil*8, pool3)
    pool4 = MaxPooling2D(pool_size=(2, 2))(conv4)

    conv5 =  conv_block( nfil*16, pool4)

    up6 = Conv2D(nfil*8, 2, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(UpSampling2D(size = (2,2))(conv5))
    merge6 = concatenate([conv4,up6], axis = 3)
    conv6 = conv_block( nfil*8, merge6 )
    
    up7 = Conv2D(nfil*4, 2, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(UpSampling2D(size = (2,2))(conv6))
    merge7 = concatenate([conv3,up7], axis = 3)
    conv7 = conv_block( nfil*4, merge7 )

    up8 = Conv2D(nfil*2, 2, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(UpSampling2D(size = (2,2))(conv7))
    merge8 = concatenate([conv2,up8], axis = 3)
    conv8 = conv_block( nfil*2, merge8 )
    
    up9 = Conv2D(nfil*1, 2, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(UpSampling2D(size = (2,2))(conv8))
    merge9 = concatenate([conv1,up9], axis = 3)
    conv9 = conv_block( nfil*1, merge9 )
    
    conv10 = Conv2D(1, 1, activation = 'sigmoid')(conv9)
    model = Model(inputs = inputs, outputs = conv10)

    model.compile(optimizer = 'adam', loss = jaccard_distance, metrics = [mean_iou, 'accuracy'] )
    return model

def mean_iou(y_true, y_pred):
    #y_pred = tf.nn.sigmoid(y_pred)
    #y_pred_class = tf.to_float(y_pred > 0.5)
    y_pred_class = K.cast(K.greater(y_pred, .5), dtype='float32') # .5 is the threshold
    #inter = K.sum(K.sum(K.squeeze(y_true * y_pred, axis=2), axis=1))
    #union = K.sum(K.sum(K.squeeze(y_true + y_pred, axis=2), axis=1)) - inter
    tp = tf.reduce_sum(y_pred_class * y_true)
    fp = tf.reduce_sum(tf.nn.relu(y_pred_class-y_true))
    fn = tf.reduce_sum(tf.nn.relu(y_true-y_pred_class))

def to_5d(arr):
    """Convert 2D or 3D array to 5D"""
    arr = np.squeeze( arr )
    while arr.ndim < 5:
        arr = np.expand_dims(arr, axis=0)
    return arr

def share_as_ndarray(img):
    """Copies a NumPy array into a same-sized newly allocated block of shared memory"""
    from appose import NDArray
    shared = NDArray(str(img.dtype), img.shape)
    shared.ndarray()[:] = img
    return shared

img = image.ndarray()
task.update( f"Starting python segmentation, input image {img.shape}" )
if debug:
    task.update( f"Normalizing each slice" )
images = np.array([normalise(i) for i in img]).reshape(-1, model_size, model_size, 1)
if debug:
    task.update( f"Building model {model_path} and load weights" )
#model = build_unet( model_size, nfeatures )
#model.load_weights( model_path+"/variables/variables" )
model = tf.keras.models.load_model( model_path, custom_objects={"jaccard_distance":jaccard_distance, "mean_iou":mean_iou}  )

if debug:
    task.update( f"Do prediction" )
res = model.predict( images, batch_size = batch_size )
#if debug:
#    task.update( f"Send results back from shape {res.shape} to 5d shape" )
res = to_5d( res )
# ZYX1 -> TZCYX
res = np.rollaxis( res, -3, -4 )
if debug:
    task.update( f"Send results back shape {res.shape}" )
task.outputs["mask"] = share_as_ndarray( res )
   
