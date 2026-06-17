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
    quantiles = np.quantile( img, [0.01, 0.998] )
    img = (img - quantiles[0] )/ (quantiles[1]-quantiles[0])
    img = np.clip( img, 0.0, 1.0 )
    return img

def recall(y_true, y_pred):
        #ytrue, ypred = check_units(y_true, y_pred)
        true_positives = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
        all_positives = K.sum(K.round(K.clip(y_true, 0, 1)))

        recall = true_positives / (all_positives + K.epsilon())
        return recall

def precision(y_true, y_pred):
        #ytrue, ypred = check_units(y_true, y_pred)
        true_positives = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
        predicted_positives = K.sum(K.round(K.clip(y_pred, 0, 1)))
        precision = true_positives / (predicted_positives + K.epsilon())
        return precision

def conv_block( nfils, inputs, nconv):
    #conv = Conv2D(nfils, 3, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal', kernel_regularizer='l1')(inputs)
    conv = Conv2D(nfils, 3, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(inputs)
    #conv = BatchNormalization()(conv)
    conv = Conv2D(nfils, 3, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(conv)
    #conv = BatchNormalization()(conv)
    if nconv >= 3:
        conv = Conv2D(nfils, 3, activation = 'relu', padding = 'same', kernel_initializer = 'he_normal')(conv)
    conv = BatchNormalization()(conv)
    return conv

def build_mymodel(IMG_SIZE, nfil):
    inputs = Input((IMG_SIZE, IMG_SIZE, 1))
    conv1 =  conv_block( nfil, inputs, 2)
    pool1 = MaxPooling2D(pool_size=(2, 2))(conv1)
    conv2 =  conv_block( nfil*2, pool1, 2)
    pool2 = MaxPooling2D(pool_size=(2, 2))(conv2)
    conv3 =  conv_block( nfil*4, pool2, 3)
    pool3 = MaxPooling2D(pool_size=(2, 2))(conv3)
    conv4 =  conv_block( nfil*8, pool3, 3)
    pool4 = MaxPooling2D(pool_size=(2, 2))(conv4)
    #conv5 =  conv_block( nfil*16, pool4, 3)
    #pool5 = MaxPooling2D(pool_size=(2, 2))(conv5)
    flat = Flatten()(pool4)
    dense = Dense(nfil*8, activation='relu')(flat)
    dense = Dropout(0.5)(dense)
    dense = Dense(nfil*2, activation='relu')(dense)
    dense = Dense(2, activation='softmax')(dense)

    model = Model(inputs=inputs, outputs=dense)
    model.compile(loss='binary_crossentropy', optimizer="adam", metrics=['accuracy', recall, precision] )
    return model

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
model = build_mymodel( model_size, nfeatures )
model.load_weights( model_path+"/variables/variables" )

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
   
