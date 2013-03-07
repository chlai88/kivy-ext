import sys
import os
from os.path import join, dirname
from os import environ
from distutils.core import setup
from distutils.extension import Extension

# detect Python for android project (http://github.com/kivy/python-for-android)
# or kivy-ios (http://github.com/kivy/kivy-ios)
platform = sys.platform
ndkplatform = environ.get('NDKPLATFORM')
if ndkplatform is not None and environ.get('LIBLINK'):
    platform = 'android'
kivy_ios_root = environ.get('KIVYIOSROOT', None)
if kivy_ios_root is not None:
    platform = 'ios'

# ensure Cython is installed for desktop app
have_cython = False
cmdclass = {}
if platform in ('android', 'ios'):
    print 'Cython import ignored'
else:
    try:
        from Cython.Distutils import build_ext
        have_cython = True
        cmdclass['build_ext'] = build_ext
    except ImportError:
        print '**** Cython is required to compile audiostream ****'
        raise

# configure the env
OPENCV_ROOT = environ.get( 'OPENCV_ROOT', None )
OPENCV_LIB_DIR = join( OPENCV_ROOT, "android", "build", "lib", "armeabi-v7a" )
include_dirs=['.']
libraries = ['gnustl_static', 'opencv_core', 'opencv_imgproc', 'SDL']
library_dirs = []
extra_objects = [ join(OPENCV_LIB_DIR, 'libopencv_core.a'), join(OPENCV_LIB_DIR,'libopencv_imgproc.a') ]
extra_compile_args =['-ggdb', '-O2']
extra_link_args = []
extensions = []

# if not have_cython:
#     libraries = ['sdl', 'sdl_mixer']
# else:
#     include_dirs.append('.')
#     include_dirs.append('/usr/include/SDL')

# generate an Extension object from its dotted name
def makeExtension(extName, files=None):
    extPath = extName.replace('.', os.path.sep) + (
            '.cpp' if not have_cython else '.pyx')
    if files is None:
        files = []
    return Extension(
        extName,
        [extPath] + files,
        include_dirs=include_dirs,
        library_dirs=library_dirs,
        libraries=libraries,
        extra_objects=extra_objects,
        extra_compile_args=extra_compile_args,
        extra_link_args=extra_link_args
        )

if platform == 'android':
    extensions.append(makeExtension('androidcam.platform.plat_android', [ join("androidcam", "platform", "android_ext.c") ]))

elif platform == 'ios':
    include_dirs = [
            join(kivy_ios_root, 'build', 'include'),
            join(kivy_ios_root, 'build', 'include', 'SDL')]
    extra_link_args = [
        '-L', join(kivy_ios_root, 'build', 'lib'),
        '-undefined', 'dynamic_lookup']
    extensions.append(makeExtension('androidcam.platform.plat_ios',
        [join('androidcam', 'platform', 'ios_ext.m')]))


config_pxi = join(dirname(__file__), 'androidcam', 'config.pxi')
with open(config_pxi, 'w') as fd:
    fd.write('DEF PLATFORM = "{}"'.format(platform))


# indicate which extensions we want to compile
# extensions += [makeExtension(x) for x in (
#     'audiostream.sources.thread',
#     'audiostream.sources.wave',
#     'audiostream.sources.puredata',
#     'audiostream.core')]

setup(
    name='camera-preview',
    version='0.1',
    author='CH Lai',
    author_email='chlai@simiwako.com',
    packages=['androidcam', 'androidcam.platform'],
    url='http://simiwako.com/',
    license='LGPL',
    description='Camera preview addon for kivy on android & ios',
    ext_modules=extensions,
    cmdclass=cmdclass,
)
