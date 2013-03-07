#include "android_ext.h"

JNIEXPORT void JNICALL
camera_preview_previewFrame(JNIEnv* env, jobject thiz, jbyteArray NV21Framedata, jint bufLen, jint width, jint height)
{
  if ( preview_callback == NULL )
    return;

  jboolean iscopy;
  jbyte* bbuf = (*env)->GetByteArrayElements(env, NV21Framedata, &iscopy);
  preview_callback(pyPreviewInstance, (unsigned char *)bbuf, bufLen, width, height);
  (*env)->ReleaseByteArrayElements(env, NV21Framedata, bbuf, JNI_ABORT);
}

static JNINativeMethod methods[] = {
  { "previewFrame", "([BIII)I", (void *)&camera_preview_previewFrame }
};

void camera_preview_jni_register() {
  if ( !camera_preview_jni_registered ) {
    JNIEnv *env = SDL_ANDROID_GetJNIEnv();
    jclass cls = (*env)->FindClass(env, "org/androidcam/CameraPreview");
    (*env)->RegisterNatives(env, cls, methods, sizeof(methods) / sizeof(methods[0]));
    camera_preview_jni_registered = 1;
  }
}

void camera_preview_callback_register(PyObject* py, preview_callback_t callback) {
  camera_preview_jni_register();
  preview_callback = callback;
  pyPreviewInstance = py;
}

