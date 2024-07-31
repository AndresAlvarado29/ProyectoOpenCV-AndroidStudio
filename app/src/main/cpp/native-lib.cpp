#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/ml/ml.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/dnn.hpp>
#include <opencv2/objdetect.hpp>
#include <opencv2/video.hpp>
#include "android/bitmap.h"
#include "android/log.h"
#include "android/asset_manager.h"
#include "android/asset_manager_jni.h"

#define LOG_TAG "native-lib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


AAssetManager* assetManager = nullptr;
using namespace std;
using namespace cv;
using namespace cv::ml;
Ptr<ANN_MLP> mlp;
const Size IMG_SIZE = Size(28, 28);

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
bool loadFileFromAssets(const char* filename, vector<char>& buffer) {
    if (!assetManager) {
        LOGE("AssetManager is null");
        return false;
    }

    AAsset* asset = AAssetManager_open(assetManager, filename, AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open asset: %s", filename);
        return false;
    }

    size_t size = AAsset_getLength(asset);
    buffer.resize(size);

    int readBytes = AAsset_read(asset, buffer.data(), size);
    if (readBytes != size) {
        LOGE("Failed to read asset: %s", filename);
        AAsset_close(asset);
        return false;
    }

    AAsset_close(asset);
    LOGI("Loaded asset file %s successfully, size: %zu bytes", filename, size);
    return true;
}

bool loadCascade(cv::CascadeClassifier& cascade, const char* filename) {
    vector<char> buffer;
    if (!loadFileFromAssets(filename, buffer)) {
        LOGE("Failed to load file from assets: %s", filename);
        return false;
    }

    // Crear un archivo temporal
    string tempFilePath = "/data/data/ups.edu.ec.aplicacionnativa/files/temp_cascade.xml";
    FILE* tempFile = fopen(tempFilePath.c_str(), "wb");
    if (!tempFile) {
        LOGE("Failed to create temporary file");
        return false;
    }
    fwrite(buffer.data(), 1, buffer.size(), tempFile);
    fclose(tempFile);

    // Cargar el archivo temporal en CascadeClassifier
    if (!cascade.load(tempFilePath)) {
        LOGE("Failed to load cascade from temporary file: %s", filename);
        remove(tempFilePath.c_str()); // Eliminar el archivo temporal
        return false;
    }

    remove(tempFilePath.c_str()); // Eliminar el archivo temporal
    return true;
}
// Función para cargar el modelo desde assets
bool loadModelFromAssets(const char* filename) {
    vector<char> buffer;
    if (!loadFileFromAssets(filename, buffer)) {
        LOGE("Failed to load model from assets: %s", filename);
        return false;
    }

    string tempFilePath = "/data/data/ups.edu.ec.aplicacionnativa/files/temp_model.xml";
    FILE* tempFile = fopen(tempFilePath.c_str(), "wb");
    if (!tempFile) {
        LOGE("Failed to create temporary file");
        return false;
    }
    fwrite(buffer.data(), 1, buffer.size(), tempFile);
    fclose(tempFile);

    mlp = ANN_MLP::load(tempFilePath);
    if (mlp.empty()) {
        LOGE("Failed to load model from temporary file: %s", filename);
        remove(tempFilePath.c_str());
        return false;
    }

    remove(tempFilePath.c_str());
    LOGI("Model loaded successfully");
    return true;
}
void computeHOG(Mat& img, vector<float>& descriptors) {
    if (img.channels() == 3) {
        cvtColor(img, img, COLOR_BGR2GRAY);
    }
    HOGDescriptor hog(
            IMG_SIZE,      // Tamaño de la imagen
            Size(14, 14),  // Tamaño de la celda
            Size(7, 7),    // Tamaño del bloque
            Size(7, 7),    // Tamaño del bloque de pasos
            9              // Número de histogramas de dirección
    );
    hog.compute(img, descriptors);
}

// Inicializa los Assets
extern "C"
JNIEXPORT void JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_initAssetManager(JNIEnv* env, jobject, jobject mgr) {
    assetManager = AAssetManager_fromJava(env, mgr);
    if (assetManager == nullptr) {
        LOGE("Failed to initialize AssetManager");
    } else {
        LOGI("AssetManager initialized successfully");
    }
    // Load the model from assets
    if (!loadModelFromAssets("mnist_mlp_490.xml")) {
        LOGE("Failed to load the model");
    }
}
//predecir numeros
string predictNumber(Ptr<ANN_MLP>& model, Mat& img) {
    resize(img, img, IMG_SIZE); // Redimensionar la imagen
    vector<float> descriptors;
    computeHOG(img, descriptors);

    // Verificar que el tamaño de los descriptores coincida con el tamaño esperado por el modelo
    if (descriptors.size() != model->getLayerSizes().at<int>(0)) {
        LOGE("El tamaño de los descriptores HOG (%zu) no coincide con el tamaño esperado por el modelo (%d).", descriptors.size(), model->getLayerSizes().at<int>(0));
        return "Error";
    }

    Mat sample(1, descriptors.size(), CV_32F);
    for (int j = 0; j < descriptors.size(); ++j) {
        sample.at<float>(0, j) = descriptors[j];
    }

    Mat response;
    model->predict(sample, response);

    Point maxLoc;
    minMaxLoc(response, 0, 0, 0, &maxLoc);
    int predictedLabel = maxLoc.x;

    return to_string(predictedLabel);
}
extern "C"
JNIEXPORT jstring JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_predecirNumeros(JNIEnv* env, jobject, jobject bitmap) {
    try {
        // Verificar si el modelo ha sido cargado correctamente
        if (mlp.empty()) {
            LOGE("Error: Modelo no cargado");
            return env->NewStringUTF("Model not loaded");
        }

        Mat img;
        bitmapToMat(env, bitmap, img, false);
        if (img.empty()) {
            LOGE("Error: La conversión de Bitmap a Mat falló");
            return env->NewStringUTF("Bitmap to Mat conversion failed");
        }

        string predictedNumber = predictNumber(mlp, img);
        if (predictedNumber == "Error") {
            LOGE("Error al predecir la imagen específica");
            return env->NewStringUTF("Error al predecir la imagen específica");
        }

        return env->NewStringUTF(predictedNumber.c_str());
    } catch (const cv::Exception& e) {
        LOGE("Error en JNI: %s", e.what());
        return env->NewStringUTF("Error en JNI");
    } catch (...) {
        LOGE("Error desconocido en JNI");
        return env->NewStringUTF("Error desconocido en JNI");
    }
}
//detectar rostros
extern "C"
JNIEXPORT void JNICALL
Java_ups_edu_ec_aplicacionnativa_MainActivity_detectarRostros(JNIEnv *env, jobject thiz, jobject bitmapIn, jobject bitmapOut) {
    // Cargar los clasificadores de cascada
    cv::CascadeClassifier face_cascade, eye_cascade, nose_cascade, mouth_cascade;
    if (!loadCascade(face_cascade, "haarcascade_frontalface_default.xml")) {
        LOGE("Error cargando la cascada de rostros");
        return;
    }
    if (!loadCascade(eye_cascade, "haarcascade_eye.xml")) {
        LOGE("Error cargando la cascada de ojos");
        return;
    }
    if (!loadCascade(nose_cascade, "haarcascade_mcs_nose.xml")) {
        LOGE("Error cargando la cascada de nariz");
        return;
    }
    if (!loadCascade(mouth_cascade, "haarcascade_mcs_mouth.xml")) {
        LOGE("Error cargando la cascada de boca");
        return;
    }
    LOGI("voy a detectar el rostro");
    cv::Mat src;
    cv::Mat gray;
    bitmapToMat(env, bitmapIn, src, false);
    cv::cvtColor(src, gray, cv::COLOR_BGR2GRAY);
    cv::equalizeHist(gray, gray);

    // Detectar rostros
    std::vector<cv::Rect> faces;
    face_cascade.detectMultiScale(gray, faces, 1.1, 3, 0, cv::Size(30, 30));

    for (size_t i = 0; i < faces.size(); i++) {
        // Dibujar rectángulo alrededor del rostro
        cv::rectangle(src, faces[i], cv::Scalar(255, 0, 0), 2);

        // Región de interés para la detección de otras características
        cv::Mat faceROI = gray(faces[i]);

        // Detectar ojos dentro de la región del rostro
        std::vector<cv::Rect> eyes;
        eye_cascade.detectMultiScale(faceROI, eyes, 1.1, 3, 0, cv::Size(20, 20));
        for (size_t j = 0; j < eyes.size(); j++) {
            cv::Rect eye_rect(faces[i].x + eyes[j].x, faces[i].y + eyes[j].y, eyes[j].width, eyes[j].height);
            cv::rectangle(src, eye_rect, cv::Scalar(0, 255, 0), 2);
        }

        // Detectar nariz dentro de la región del rostro
        std::vector<cv::Rect> noses;
        nose_cascade.detectMultiScale(faceROI, noses, 1.1, 3, 0, cv::Size(20, 20));
        for (size_t j = 0; j < noses.size(); j++) {
            cv::Rect nose_rect(faces[i].x + noses[j].x, faces[i].y + noses[j].y, noses[j].width, noses[j].height);
            cv::rectangle(src, nose_rect, cv::Scalar(255, 255, 0), 2);
        }

        // Detectar boca dentro de la región del rostro (ajustando la región de búsqueda a la parte inferior del rostro)
        std::vector<cv::Rect> mouths;
        cv::Mat lowerFaceROI = faceROI(cv::Rect(0, faces[i].height / 2, faces[i].width, faces[i].height / 2));
        mouth_cascade.detectMultiScale(lowerFaceROI, mouths, 1.1, 3, 0, cv::Size(20, 20));
        for (size_t j = 0; j < mouths.size(); j++) {
            cv::Rect mouth_rect(faces[i].x + mouths[j].x, faces[i].y + faces[i].height / 2 + mouths[j].y, mouths[j].width, mouths[j].height);
            cv::rectangle(src, mouth_rect, cv::Scalar(0, 0, 255), 2);
        }
    }

    // Convertir de nuevo a bitmap y devolver la imagen procesada
    matToBitmap(env, src, bitmapOut, false);
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

