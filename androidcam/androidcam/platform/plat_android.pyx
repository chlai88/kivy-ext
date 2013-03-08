__all__ = ('AndroidCameraPreview', )


include "../config.pxi"

cdef extern from "android_ext.h":
    ctypedef void (*preview_callback_t)(object, unsigned char *, int, int, int)
    void camera_preview_jni_register()
    void camera_preview_callback_register(object, preview_callback_t)

import time
from kivy.logger import Logger
from kivy.clock import Clock
from kivy.graphics.texture import Texture
from kivy.event import EventDispatcher
from kivy.properties import (BooleanProperty, NumericProperty, ObjectProperty,
        OptionProperty)
from Queue import Queue

from jnius import autoclass, JavaClass, MetaJavaClass, JavaMethod

from cv2.opencv cimport *

cdef void previewCallback(object py, unsigned char * frameData, int bufLen, int width, int height) with gil:
    # cdef Mat rgbOut = Mat(height, width, CV_8UC4)
    # # print "previewCallback b4 cvtColor", rgbOut.size().width, rgbOut.size().height, rgbOut.total(), rgbOut.elemSize()
    # cdef Mat yuvIn = Mat(height+height/2, width, CV_8UC1, frameData)
    # cvtColor(yuvIn, rgbOut, COLOR_YUV2BGRA_NV21, 4)
    # rgbaBuf = rgbOut.data[:rgbOut.total()*rgbOut.elemSize()]
    # print "previewCallback after", rgbOut.size().width, rgbOut.size().height, rgbOut.total()
    # py.setFrameRate()
    cdef bytes f = frameData[:bufLen]
    py.update(f, width, height)

cdef class AndroidCameraPreview:
    ''' 
        Camera preview frame handler on android
    '''
    # _timeStamp = int(round(time.time() * 1000))
    cdef object _index, _queue, _preview, stopped, resolution, colorFormat, fps, frameRate
    
    property fps:
        def __get__(self):
            return self.fps
        def __set__(self, value):
            self.fps = value

    property frameRate:
        def __get__(self):
            return self.frameRate

    property colorFormat:
        def __get__(self):
            return self.colorFormat

    def __init__(self, **kwargs):
        kwargs.setdefault('stopped', False)
        kwargs.setdefault('resolution', (640, 480))
        kwargs.setdefault('index', 0)
        kwargs.setdefault('maxQSize', 2)
        kwargs.setdefault('fps', 1/30)

        self._index = kwargs.get('index')
        self._queue = Queue(maxsize=kwargs.get('maxQSize'))

        self.stopped = kwargs.get('stopped')
        self.resolution = kwargs.get('resolution')
        self.colorFormat = 'bgra'
        self.fps = kwargs.get('fps')

        self.init_camera()

        if not self.stopped:
            self.start()

    def init_camera(self):
        CameraPreview = autoclass('org.androidcam.CameraPreview')
        PythonActivity = autoclass('org.renpy.android.PythonActivity')
        camera_preview_callback_register(self, previewCallback)
        self._preview = CameraPreview(PythonActivity.mActivity)
        self._preview.initGrabber(self.resolution[0], self.resolution[1], 30, 0)

    def update(self, unsigned char *frameData, int width, int height):
        cdef Mat rgbOut
        cdef Mat yuvIn
        if self.stopped:
            return
        # print "kivycam: full?", self._queue.full()
        if not (self._queue.full()):
            rgbOut.create(height, width, CV_8UC4)
            yuvIn = Mat(height+height/2, width, CV_8UC1, frameData)
            cvtColor(yuvIn, rgbOut, COLOR_YUV2BGRA_NV21, 4)
            Logger.debug('androidcam: after cvtColor {0} {1} {2}'.format(rgbOut.size().width, rgbOut.size().height, rgbOut.total()*rgbOut.elemSize()))
            rgbaBuf = rgbOut.data[:rgbOut.total()*rgbOut.elemSize()]
            # Logger.debug('androidcam: put queue {0} {1} {2}'.format(width, height, len(rgbaBuf)))
            self._queue.put({'width': width, 'height': height, 'frameData': rgbaBuf})

        self.setFrameRate()
        return

    def isQueueEmpty(self):
        return self._queue.empty();

    def getFrame(self):
        return self._queue.get();

    def on_texture(self):
        pass

    def on_load(self):
        pass
    
    def setFrameRate(self):
        # now = int(round(time.time() * 1000))
        # interval = now - self._timeStamp
        # self._timeStamp = now
        # self.frameRate = int(round(1000/interval))
        self.frameRate = Clock.get_rfps()

    def start(self):
        '''Start the camera acquire'''
        self.stopped = False

    def stop(self):
        '''Release the camera'''
        self.stopped = True

