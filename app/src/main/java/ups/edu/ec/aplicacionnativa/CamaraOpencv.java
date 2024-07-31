package ups.edu.ec.aplicacionnativa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class CamaraOpencv extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String  TAG = "OCVSample::Activity";
    private CameraBridgeViewBase camaraActivity;
    private Button boton,boton2,boton3,boton4,boton5,boton6;
    private String nombre="Foto_";
    private Mat frame;
    private Mat mIntermediateMat;
    private Mat mSepiaKernel;
    private Mat anterior;
    private Mat resta;

    //prueba de items
    private MenuItem iCanny;
    private MenuItem iSobel;
    private MenuItem iOriginal;
    private MenuItem iSepia;
    private MenuItem iMovimiento;
    //Modos de la camara
    public static final int VIEW_MODE_RGBA = 0;
    public static final int VIEW_MODE_CANNY = 1;
    public static final int VIEW_MODE_SEPIA = 2;
    public static final int VIEW_MODE_SOBEL = 3;
    public static final int VIEW_MODEL_MOVIMIENTO =4;
    public static int viewMode;



    public interface FileUploadService {
        @Multipart
        @POST("/upload")
        Call<ResponseBody> uploadFile(@Part MultipartBody.Part file);
    }
    public CamaraOpencv() throws IOException {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camara_opencv);
        camaraActivity = (CameraBridgeViewBase) findViewById(R.id.camaraVista);
        camaraActivity.setVisibility(SurfaceView.VISIBLE);
        camaraActivity.setCvCameraViewListener(this);
        //boton
        boton=findViewById(R.id.btnTomarFoto);
        boton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        String[] permissions = {android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
                        requestPermissions(permissions, 1);
                    }else{
                        Log.d(TAG, "Permisos ya concedidos. Capturando imagen.");
                        //capturarImagen(frame);
                        capturarYEnviarImagen(frame);
                        Uri imagenUri = almacenar(frame);
                        Intent intent = new Intent(CamaraOpencv.this, MainActivity.class);
                        intent.putExtra("imagenUri", imagenUri.toString());
                        intent.putExtra("imagenUri2", imagenUri.toString());
                        startActivity(intent);


                    }
                }
            }
        });
        boton2=findViewById(R.id.btnSobel);
        boton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewMode = VIEW_MODE_SOBEL;
            }
        });
        boton3=findViewById(R.id.btnMovimiento);
        boton3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewMode = VIEW_MODEL_MOVIMIENTO;
            }
        });
        boton4=findViewById(R.id.btnSepia);
        boton4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewMode = VIEW_MODE_SEPIA;
            }
        });
        boton5=findViewById(R.id.btnCanny);
        boton5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewMode = VIEW_MODE_CANNY;
            }
        });
        boton6=findViewById(R.id.btnOriginal);
        boton6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewMode = VIEW_MODE_RGBA;
            }
        });
    }

    public void capturarYEnviarImagen(Mat frame) {
        if (frame == null) {
            Log.e(TAG, "Frame es null, no se puede capturar imagen.");
            return;
        }
        Core.rotate(frame, frame, Core.ROTATE_90_CLOCKWISE);
        Bitmap bitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame, bitmap);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), byteArray);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", "foto.png", requestFile);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://172.16.211.7:5000")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        FileUploadService service = retrofit.create(FileUploadService.class);
        Call<ResponseBody> call = service.uploadFile(body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Imagen subida con éxito");
                    Toast.makeText(CamaraOpencv.this, "Imagen subida con éxito", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Error en la subida: " + response.message());
                    Toast.makeText(CamaraOpencv.this, "Error en la subida: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Error: " + t.getMessage());
                Toast.makeText(CamaraOpencv.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    public Bitmap capturarImagen(Mat frame){
        if (frame == null) {
            Log.e(TAG, "Frame es null, no se puede capturar imagen.");
            return null;
        }
        Core.rotate(frame,frame, Core.ROTATE_90_CLOCKWISE);
        Bitmap bitmap= Bitmap.createBitmap(frame.cols(),frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame,bitmap);
        return bitmap;
    }
    //almacenar
    public Uri almacenar(Mat frame){
        if (frame == null) {
            Log.e(TAG, "Frame es null, no se puede capturar imagen.");
            return null;
        }
        Core.rotate(frame,frame, Core.ROTATE_90_CLOCKWISE);
        Bitmap bitmap= Bitmap.createBitmap(frame.cols(),frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame,bitmap);
        nombre=nombre+new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, nombre);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try {
            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                    return uri;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar la imagen: " + e.getMessage());
        }

        return null;
    }
    @Override
    public void onPause()
    {
        super.onPause();
        if (camaraActivity != null)
            camaraActivity.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (camaraActivity != null)
            camaraActivity.enableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(camaraActivity);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (camaraActivity != null)
            camaraActivity.disableView();
    }
    //menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        iOriginal = menu.add("Original");
        iSepia = menu.add("Sepia");
        iCanny = menu.add("Canny");
        iSobel = menu.add("Sobel");
        iMovimiento = menu.add("Movimiento");
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == iOriginal)
            viewMode = VIEW_MODE_RGBA;
        else if (item == iCanny)
            viewMode = VIEW_MODE_CANNY;
        else if (item == iSepia)
            viewMode = VIEW_MODE_SEPIA;
        else if (item == iSobel)
            viewMode = VIEW_MODE_SOBEL;
        else if (item == iMovimiento)
            viewMode = VIEW_MODEL_MOVIMIENTO;
        return true;
    }
    @Override
    public void onCameraViewStarted(int width, int height) {
        mIntermediateMat = new Mat();
        resta = new Mat();
        anterior = new Mat();
        mSepiaKernel = new Mat(4, 4, CvType.CV_32F);
        mSepiaKernel.put(0, 0, /* R */0.189f, 0.769f, 0.393f, 0f);
        mSepiaKernel.put(1, 0, /* G */0.168f, 0.686f, 0.349f, 0f);
        mSepiaKernel.put(2, 0, /* B */0.131f, 0.534f, 0.272f, 0f);
        mSepiaKernel.put(3, 0, /* A */0.000f, 0.000f, 0.000f, 1f);
    }

    @SuppressLint("SuspiciousIndentation")
    @Override
    public void onCameraViewStopped() {
        if (mIntermediateMat != null)
            mIntermediateMat.release();
            mIntermediateMat = null;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        frame = inputFrame.rgba(); // Obtén el frame en color RGBA

        switch (CamaraOpencv.viewMode) {
            case CamaraOpencv.VIEW_MODE_RGBA:
                // No se necesita procesamiento adicional
                break;

            case CamaraOpencv.VIEW_MODE_CANNY:
                Imgproc.Canny(frame, mIntermediateMat, 80, 90);
                Imgproc.cvtColor(mIntermediateMat, frame, Imgproc.COLOR_GRAY2BGRA, 4);
                break;

            case CamaraOpencv.VIEW_MODE_SOBEL:
                Imgproc.Sobel(inputFrame.gray(), mIntermediateMat, CvType.CV_8U, 1, 1);
                Core.convertScaleAbs(mIntermediateMat, mIntermediateMat, 10, 0);
                Imgproc.cvtColor(mIntermediateMat, frame, Imgproc.COLOR_GRAY2BGRA, 4);
                break;

            case CamaraOpencv.VIEW_MODE_SEPIA:
                Core.transform(frame, frame, mSepiaKernel);
                break;

            case CamaraOpencv.VIEW_MODEL_MOVIMIENTO:
                Imgproc.cvtColor(frame,frame, Imgproc.COLOR_BGR2GRAY);
                if(anterior.empty()){
                    anterior = frame.clone();
                }
                Core.absdiff(frame,anterior,resta);
                anterior = frame.clone();
                Imgproc.threshold(resta,frame,4,100, Imgproc.THRESH_BINARY);

                break;
        }

        return frame;
    }
}