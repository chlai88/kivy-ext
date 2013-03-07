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
    cdef Mat rgbOut = Mat(height, width, CV_8UC4)
    # print "previewCallback b4 cvtColor", rgbOut.size().width, rgbOut.size().height, rgbOut.total(), rgbOut.elemSize()
    cdef Mat yuvIn = Mat(height+height/2, width, CV_8UC1, frameData)
    cvtColor(yuvIn, rgbOut, COLOR_YUV2BGRA_NV21, 4)
    rgbaBuf = rgbOut.data[:rgbOut.total()*rgbOut.elemSize()]
    # print "previewCallback after", rgbOut.size().width, rgbOut.size().height, rgbOut.total()
    py.setFrameRate()
    py._update(rgbaBuf, rgbOut.size().width, rgbOut.size().height)

class AndroidCameraPreview(EventDispatcher):
    ''' 
        Camera preview event dispatcher on android
    '''
    _preview = ObjectProperty({})

    _timeStamp = int(round(time.time() * 1000))
    frameRate = NumericProperty(0)

    def __init__(self, **kwargs):
        kwargs.setdefault('stopped', False)
        kwargs.setdefault('resolution', (640, 480))
        kwargs.setdefault('index', 0)
        kwargs.setdefault('maxQSize', 2)
        kwargs.setdefault('fps', 1/30)

        super(AndroidCameraPreview, self).__init__()
        self._index = kwargs.get('index')
        self._device = None
        self._queue = Queue(maxsize=kwargs.get('maxQSize'))

        self.stopped = kwargs.get('stopped')
        self.resolution = kwargs.get('resolution')
        self.colorFormat = 'bgra'
        self.fps = kwargs.get('fps')

        self.register_event_type('on_load')
        self.register_event_type('on_texture')

        self.init_camera()

        if not self.stopped:
            self.start()
        # class CameraPreview(JavaClass):
        #     __metaclass__ = MetaJavaClass
        #     __javaclass__ = 'org/kivycam/CameraPreview'
        #     initGrabber = JavaMethod('()Ljava/lang/String;')

    def init_camera(self):
        CameraPreview = autoclass('org.androidcam.CameraPreview')
        PythonActivity = autoclass('org.renpy.android.PythonActivity')
        camera_preview_callback_register(self, previewCallback)
        self._preview = CameraPreview(PythonActivity.mActivity)
        self._preview.initGrabber(self.resolution[0], self.resolution[1], 30)

    def _update(self, frameData, width, height):
        if self.stopped:
            return
        # print "kivycam: full?", self._queue.full()
        self.setFrameRate()
        if not (self._queue.full()):
            print 'kivycam: put queue', width, height, len(frameData)
            self._queue.put({'width': width, 'height': height, 'frameData': frameData})
        # self.dispatch('on_load')
        # if self._texture is None:
        #     # Create the texture
        #     self._texture = Texture.create(self._resolution)
        #     self._texture.flip_vertical()
        #     self.dispatch('on_load')

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

