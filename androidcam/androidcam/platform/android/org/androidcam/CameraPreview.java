/*
 * Audio microphone thread.
 */

package org.androidcam;

import java.nio.ByteBuffer;
import java.lang.Thread;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.os.Process;
import android.util.Log;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioSource;
import android.media.MediaRecorder.OutputFormat;
import android.media.MediaRecorder.AudioEncoder;
import android.media.AudioFormat;
import android.media.AudioRecord;

import android.content.Context;
import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.opengl.GLSurfaceView;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Mat;

import org.renpy.android.PythonActivity;

class CameraPreview implements Runnable, Camera.PreviewCallback {

	private static String TAG = "CameraPreview";

	private Camera camera;
	private int deviceID = -1;
	private byte[] buffer;
	private int width, height, targetFps;
	private Thread thread;
	// private int id;
	// private static int nextId=0;
	// public static Map<Integer,OFAndroidVideoGrabber> camera_instances = new HashMap<Integer,OFAndroidVideoGrabber>();
	//private static OFCameraSurface cameraSurface = null;
	//private static ViewGroup rootViewGroup = null;
	private boolean initialized = false;
	private boolean previewStarted = false;
	private Method addBufferMethod;
	private OrientationListener orientationListener;
	private Context context;

  private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(PythonActivity.mActivity) {
      @Override
      public void onManagerConnected(int status) {
          switch (status) {
              case LoaderCallbackInterface.SUCCESS:
              {
                  Log.i(TAG, "OpenCV loaded successfully");
                  // mOpenCvCameraView.enableView();
                  super.onManagerConnected(status);
              } break;
              default:
              {
                  Log.i(TAG, "Failed OpenCV load");
                  super.onManagerConnected(status);
              } break;
          }
      }
  };

	public CameraPreview(Context ctx) {
		context = ctx;
	}

	public void initGrabber(int w, int h, int _targetFps){
		initGrabber(w,h,_targetFps,-1);
	}
	
	public void initGrabber(int w, int h, int _targetFps, int texID){
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, PythonActivity.mActivity, mLoaderCallback);

		if(deviceID==-1)
			camera = Camera.open();
		else{			
			try {
				int numCameras = (Integer) Camera.class.getMethod("getNumberOfCameras").invoke(null);
				Class<?> cameraInfoClass = Class.forName("android.hardware.Camera$CameraInfo");
				Object cameraInfo = null;
				Field field = null;
		        if ( cameraInfoClass != null ) {
		            cameraInfo = cameraInfoClass.newInstance();
		        }
		        if ( cameraInfo != null ) {
		            field = cameraInfo.getClass().getField( "facing" );
		        }
				Method getCameraInfoMethod = Camera.class.getMethod( "getCameraInfo", Integer.TYPE, cameraInfoClass );
				for(int i=0;i<numCameras;i++){
					getCameraInfoMethod.invoke( null, i, cameraInfo );
	                int facing = field.getInt( cameraInfo );
	                Log.v("OF","Camera " + i + " facing: " + facing);
				}
				camera = (Camera) Camera.class.getMethod("open", Integer.TYPE).invoke(null, deviceID);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Log.e("OF","Error trying to open specific camera, trying default",e);
				camera = Camera.open();
			} 
		}

		if(supportsTextureRendering()){
			try {
				Class surfaceTextureClass = Class.forName("android.graphics.SurfaceTexture");
				Constructor constructor = surfaceTextureClass.getConstructor(int.class);
				Object surfaceTexture = constructor.newInstance(0);
				Method setPreviewTexture = camera.getClass().getMethod("setPreviewTexture", surfaceTextureClass);
				setPreviewTexture.invoke(camera, surfaceTexture);
			} catch (Exception e1) {
				Log.e("OF","Error initializing gl surface",e1);
			} 
		}

		Camera.Parameters config = camera.getParameters();
		
		Log.i(TAG,"Grabber supported sizes");
		for(Size s : config.getSupportedPreviewSizes()){
			Log.i(TAG,s.width + " " + s.height);
		}
		
		Log.i(TAG,"Grabber supported formats");
		for(Integer i : config.getSupportedPreviewFormats()){
			Log.i(TAG,i.toString());
		}
		
		Log.i(TAG,"Grabber supported fps");
		for(Integer i : config.getSupportedPreviewFrameRates()){
			Log.i(TAG,i.toString());
		}
		
		Log.i(TAG, "Grabber default format: " + config.getPreviewFormat());
		Log.i(TAG, "Grabber default preview size: " + config.getPreviewSize().width + "," + config.getPreviewSize().height);
		// config.setPreviewSize(w, h);
		config.setPreviewFormat(ImageFormat.NV21);
		try{
			camera.setParameters(config);
		}catch(Exception e){
			Log.e(TAG,"couldn init camera", e);
		}

		config = camera.getParameters();
		width = config.getPreviewSize().width;
		height = config.getPreviewSize().height;
		if(width!=w || height!=h)  Log.w(TAG,"camera size different than asked for, resizing (this can slow the app)");
		
		
		if(_targetFps!=-1){
			config = camera.getParameters();
			config.setPreviewFrameRate(_targetFps);
			try{
				camera.setParameters(config);
			}catch(Exception e){
				Log.e(TAG,"couldn init camera", e);
			}
		}
		
		targetFps = _targetFps;
		Log.i(TAG,"camera settings: " + width + "x" + height);
		
		// it actually needs (width*height) * 3/2
		int bufferSize = width * height;
		bufferSize  = bufferSize * ImageFormat.getBitsPerPixel(config.getPreviewFormat()) / 8;
		buffer = new byte[bufferSize];
		Log.i(TAG,"preview buffer created size: " + bufferSize);
		
		orientationListener = new OrientationListener(context);
		orientationListener.enable();
		
		thread = new Thread(this);
		thread.start();
		initialized = true;
	}

	public static boolean supportsTextureRendering(){
		try {
			Class surfaceTextureClass = Class.forName("android.graphics.SurfaceTexture");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	public void run() {
		thread.setPriority(Thread.MAX_PRIORITY);
		try {
			addBufferMethod = Camera.class.getMethod("addCallbackBuffer", byte[].class);
			addBufferMethod.invoke(camera, buffer);
			Camera.class.getMethod("setPreviewCallbackWithBuffer", Camera.PreviewCallback.class).invoke(camera, this);
			Log.i(TAG,"setting camera callback with buffer");
		} catch (SecurityException e) {
			Log.e(TAG,"security exception, check permissions to access the camera",e);
		} catch (NoSuchMethodException e) {
			try {
				Camera.class.getMethod("setPreviewCallback", Camera.PreviewCallback.class).invoke(camera, this);
				Log.i(TAG,"setting camera callback without buffer");
			} catch (SecurityException e1) {
				Log.e(TAG,"security exception, check permissions to access the camera",e1);
			} catch (Exception e1) {
				Log.e(TAG,"cannot create callback, the camera can only be used from api v7",e1);
			} 
		} catch (Exception e) {
			Log.e(TAG,"error adding callback",e);
		}
		
		//camera.addCallbackBuffer(buffer);
		//camera.setPreviewCallbackWithBuffer(this);
		try{
			camera.startPreview();
			previewStarted = true;
		} catch (Exception e) {
			Log.e(TAG,"error starting preview",e);
		}
	}

	public void onPreviewFrame(byte[] data, Camera camera) {
		//Log.i("OF","video buffer length: " + data.length);
		//Log.i("OF", "size: " + camera.getParameters().getPreviewSize().width + "x" + camera.getParameters().getPreviewSize().height);
		//Log.i("OF", "format " + camera.getParameters().getPreviewFormat());
		Log.d(TAG,"call native previewFrame " + data.length);
		previewFrame(data, data.length, width, height);
		
		if(addBufferMethod!=null){
			try {
				addBufferMethod.invoke(camera, buffer);
			} catch (Exception e) {
				Log.e("OF","error adding buffer",e);
			} 
		}
		//camera.addCallbackBuffer(data);
		
	}

	public native int previewFrame(byte[] data, int dataLen,  int width, int height);

	private class OrientationListener extends OrientationEventListener{

		public OrientationListener(Context context) {
			super(context);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void onOrientationChanged(int orientation) {
			if (orientation == ORIENTATION_UNKNOWN) return;
			try{
				Camera.Parameters config = camera.getParameters();
				/*Camera.CameraInfo info =
				        new Camera.CameraInfo();*/
				//Camera.getCameraInfo(camera, info);
				orientation = (orientation + 45) / 90 * 90;
				int rotation = orientation % 360;
				//if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				    //rotation = (info.orientation - orientation + 360) % 360;
				/*} else {  // back-facing camera
				    rotation = (info.orientation + orientation) % 360;
				}*/
				config.setRotation(rotation);
				camera.setParameters(config);
			}catch(Exception e){
				
			}
		}
		
	}

  // public void onCameraViewStarted(int width, int height) {
  // }

  // public void onCameraViewStopped() {
  // }

  // public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
  //     return null;
  // }

}
