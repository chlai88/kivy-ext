#ifndef __KIVYCAM_ANDROID_EXT
#define __KIVYCAM_ANDROID_EXT

#include <jni.h>
#include <stdio.h>
#include <android/log.h>
#include <string.h>
#include <Python.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEnv *SDL_ANDROID_GetJNIEnv(void);

typedef void (*preview_callback_t)(PyObject* py, unsigned char *buffer, int bufLen, int width, int height);
static preview_callback_t preview_callback = NULL;
static int camera_preview_jni_registered = 0;
static PyObject* pyPreviewInstance = NULL;

JNIEXPORT void JNICALL
camera_preview_previewFrame(JNIEnv* env, jobject thiz, jbyteArray buf, jint bufLen, jint width, jint height);

void camera_preview_jni_register();

void camera_preview_callback_register(PyObject* py, preview_callback_t callback);

#ifdef __cplusplus
}
#endif

#endif
