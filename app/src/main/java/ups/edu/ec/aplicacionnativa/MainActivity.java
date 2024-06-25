package ups.edu.ec.aplicacionnativa;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import ups.edu.ec.aplicacionnativa.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String  TAG = "OCVSample::Activity";
    // Used to load the 'aplicacionnativa' library on application startup.
    static {
        System.loadLibrary("aplicacionnativa");
    }

    private ActivityMainBinding binding;
    private android.widget.Button btnLaplacian, btnCamara,btnCombinar,btnSuavizado,btnEnviar,btnDuoTono;
    private android.widget.ImageView original, modificado, combinado;
    private Intent intent;
    private Bitmap bitmap;
    private Bitmap bitmap2;
    private String nombre="Foto_Modificada_";


    public interface FileUploadService {
        @Multipart
        @POST("/upload")
        Call<ResponseBody> uploadFile(@Part MultipartBody.Part file);
    }
    public Uri almacenar(Bitmap bitmap){
        nombre=nombre+new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, nombre);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;
        tv.setText("Hello from C++");

        original = findViewById(R.id.imgModificado1);
        modificado = findViewById(R.id.imgModificado2);
        combinado = findViewById(R.id.imgCombinado);

        String uriString = getIntent().getStringExtra("imagenUri");
        String uriString2 = getIntent().getStringExtra("imagenUri2");
        if (uriString != null) {
            Uri imageUri = Uri.parse(uriString);
            Uri imageUri2 = Uri.parse(uriString2);
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                bitmap2 = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri2);
                    original.setImageBitmap(bitmap);
                    modificado.setImageBitmap(bitmap2);


            } catch (Exception e) {
                //Log.e(TAG, "Error al cargar la imagen desde el URI: " + e.getMessage());
                Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
            }
        } else {
            //Log.e(TAG, "URI de imagen es null.");
            Toast.makeText(this, "No se recibió ninguna imagen", Toast.LENGTH_SHORT).show();
        }
        btnCamara = findViewById(R.id.btnCamara);
        btnCamara.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intent = new Intent(MainActivity.this, CamaraOpencv.class);
                startActivity(intent);
            }
        });
        btnLaplacian = findViewById(R.id.btnLaplacian);
        btnLaplacian.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               // tv.setText(stringFromJNI());

                android.graphics.Bitmap bIn = bitmap;
                android.graphics.Bitmap bOut = bIn.copy(bIn.getConfig(), true);
                detectorBordes(bIn, bOut);
                original.setImageBitmap(bOut);
                almacenar(bOut);
            }
        });
        btnCombinar = findViewById(R.id.btnCombinar);
        btnCombinar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.graphics.Bitmap bIn2 = bitmap;
                android.graphics.Bitmap bIn3 = bitmap2;
                android.graphics.Bitmap bOut = bIn2.copy(bIn2.getConfig(), true);
                combinar(bIn2,bIn3,bOut);
                combinado.setImageBitmap(bOut);
                almacenar(bOut);
            }
        });
        btnSuavizado = findViewById(R.id.btnSuavizado);
        btnSuavizado.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.graphics.Bitmap bIn4 = bitmap;
                android.graphics.Bitmap bOut = bIn4.copy(bIn4.getConfig(), true);
                suavizado(bIn4,bOut);
                modificado.setImageBitmap(bOut);
                almacenar(bOut);
                android.graphics.Bitmap bIn5 = bitmap2;
                android.graphics.Bitmap bOut2 = bIn5.copy(bIn5.getConfig(), true);
                suavizado(bIn5,bOut2);
                original.setImageBitmap(bOut2);
                almacenar(bOut2);
            }
        });
        btnDuoTono = findViewById(R.id.btnDuoTono);
        btnDuoTono.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.graphics.Bitmap bIn6 = bitmap;
                android.graphics.Bitmap bOut3 = bIn6.copy(bIn6.getConfig(), true);
                duotono(bIn6,bOut3);
                original.setImageBitmap(bOut3);
                almacenar(bOut3);
            }
        });
    }



    /**
     * A native method that is implemented by the 'aplicacionnativa' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    public native void detectorBordes(android.graphics.Bitmap in, android.graphics.Bitmap out);
    public native void combinar(android.graphics.Bitmap in,android.graphics.Bitmap in2 , android.graphics.Bitmap out);
    public native void suavizado(android.graphics.Bitmap in , android.graphics.Bitmap out);
    public native void duotono(android.graphics.Bitmap in, android.graphics.Bitmap out);
}