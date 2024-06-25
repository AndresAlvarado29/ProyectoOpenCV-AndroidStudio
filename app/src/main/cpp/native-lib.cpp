#include <jni.h>
#include <string>
//librerias opencv
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/dnn.hpp>
#include <opencv2/video.hpp>
#include "android/bitmap.h"

using namespace std;

void bitmapToMat(JNIEnv * env, jobject bitmap, cv::Mat &dst, jboolean needUnPremultiplyAlpha){
    AndroidBitmapInfo info;
    void* pixels = 0;
    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        dst.create(info.height, info.width, CV_8UC4);
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(needUnPremultiplyAlpha) cvtColor(tmp, dst, cv::COLOR_mRGBA2RGBA);
            else tmp.copyTo(dst);
        } else {
// info.format == ANDROID_BITMAP_FORMAT_RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            cvtColor(tmp, dst, cv::COLOR_BGR5652RGBA);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
//jclass je = env->FindClass("org/opencv/core/CvException");
        jclass je = env->FindClass("java/lang/Exception");
//if(!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nBitmapToMat}");
        return;
    }
}
void matToBitmap(JNIEnv * env, cv::Mat src, jobject bitmap, jboolean needPremultiplyAlpha) {
    AndroidBitmapInfo info;
    void* pixels = 0;
    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( src.dims == 2 && info.height == (uint32_t)src.rows && info.width == (uint32_t)src.cols );
        CV_Assert( src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, cv::COLOR_GRAY2RGBA);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, cv::COLOR_RGB2RGBA);
            } else if(src.type() == CV_8UC4){
                if(needPremultiplyAlpha) cvtColor(src, tmp, cv::COLOR_RGBA2mRGBA);
                else src.copyTo(tmp);
            }
        } else {
// info.format == ANDROID_BITMAP_FORMAT_RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, cv::COLOR_GRAY2BGR565);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, cv::COLOR_RGB2BGR565);
            } else if(src.type() == CV_8UC4){
                cvtColor(src, tmp, cv::COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
//jclass je = env->FindClass("org/opencv/core/CvException");
        jclass je = env->FindClass("java/lang/Exception");
//if(!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nMatToBitmap}");
        return;
    }
}
//codigo para presentar los diez numeros de fibonachi
extern "C" JNIEXPORT jstring JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    int a = 0;
    int b = 1;
    int c = 0;
    stringstream ss;
    ss << a << "," << b << ",";
    for (int i=0;i<10;i++){
        c = a + b;
        a = b;
        b = c;
        ss << c << ",";
    }
    return env->NewStringUTF(ss.str().c_str());
}
//codigo para usar opencv y cambiar las imagenes
extern "C" JNIEXPORT void JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_detectorBordes(
        JNIEnv* env,
        jobject /*this*/,
        jobject bitmapIn,
        jobject bitmapOut){
cv::Mat src;
cv::Mat tmp;
cv::Mat bordes;
bitmapToMat(env, bitmapIn, src, false);
cv:cvtColor(src, tmp, cv::COLOR_BGR2GRAY);
cv::Laplacian(tmp, bordes, CV_16S, 3);
cv::convertScaleAbs(bordes, bordes);
matToBitmap(env, bordes, bitmapOut, false);
}

extern "C" JNIEXPORT void JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_combinar(
        JNIEnv* env,
        jobject /*this*/,
        jobject bitmapIn,
        jobject bitmapIn2,
        jobject bitmapOut
        ){
    cv::Mat imagen1;
    cv::Mat imagen2;
    cv::Mat combinado;
    bitmapToMat(env, bitmapIn, imagen1, false);
    bitmapToMat(env, bitmapIn2, imagen2, false);
    cv::add(imagen1,imagen2,combinado);
    matToBitmap(env, combinado, bitmapOut, false);
}
extern "C" JNIEXPORT void JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_suavizado(
        JNIEnv* env,
        jobject /*this*/,
        jobject bitmapIn,
        jobject bitmapOut
        ){
    cv::Mat imagen1;
    cv::Mat suavizado;
    bitmapToMat(env, bitmapIn, imagen1, false);
    cv::GaussianBlur(imagen1,suavizado,cv::Size(5,5),0);
    matToBitmap(env, suavizado, bitmapOut, false);
}
extern "C" JNIEXPORT void JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_duotono(
        JNIEnv* env,
        jobject /*this*/,
        jobject bitmapIn,
        jobject bitmapOut
){
    cv::Mat imagen1;
    cv::Mat gray;
    cv::Mat normalizado;
    cv::Vec3f shadowColor(0,0,100);
    cv::Vec3f lightColor(0, 0, 255);
    cv::Mat duotonoSalida;
    bitmapToMat(env, bitmapIn, imagen1, false);
    cv::cvtColor(imagen1,gray,cv::COLOR_BGR2GRAY);
    gray.convertTo(normalizado, CV_32F, 1.0 / 255.0);
    cv::Mat duotono(imagen1.size(), CV_32FC3);
    for (int y = 0; y < imagen1.rows; ++y) {
        for (int x = 0; x < imagen1.cols; ++x) {
            float grayValue = normalizado.at<float>(y, x);
            duotono.at<cv::Vec3f>(y, x) = (1.0f - grayValue) * shadowColor + grayValue * lightColor;
        }
    }
    duotono.convertTo(duotonoSalida,CV_8UC3, 255.0);
    matToBitmap(env, duotonoSalida, bitmapOut, false);
}