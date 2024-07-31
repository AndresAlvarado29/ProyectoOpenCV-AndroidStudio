package ups.edu.ec.aplicacionnativa;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.OutputStream;
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
import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;



public class MainActivity extends AppCompatActivity {
    private static final String TAG = "OCVSample::Activity";
    // Used to load the 'aplicacionnativa' library on application startup.
    static {
        System.loadLibrary("aplicacionnativa");
    }

    private ActivityMainBinding binding;
    private android.widget.Button btnLaplacian, btnCamara, btnCombinar, btnSuavizado, btnEnviar, btnDuoTono, btnDeteccionRostros,btnHog;
    private android.widget.TextView txtPredict;
    private android.widget.ImageView original, modificado, combinado;
    private Intent intent;
    private Bitmap bitmap;
    private Bitmap bitmap2;
    private String nombre = "Foto_Modificada_";
    private Bitmap bOut3,bOutC;

    public interface FileUploadService {
        @Multipart
        @POST("/upload")
        Call<ResponseBody> uploadFile(@Part MultipartBody.Part file);
        @Multipart
        @POST("/efecto")
        Call<ResponseBody> efecto(@Part MultipartBody.Part file);
    }

    public Uri almacenar(Bitmap bitmap) {
        nombre = nombre + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".PNG";
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
    public void capturarYEnviarImagen(Bitmap imagen) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imagen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), byteArray);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", "foto.PNG", requestFile);
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
                    Toast.makeText(MainActivity.this, "Imagen subida con éxito", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Error en la subida: " + response.message());
                    Toast.makeText(MainActivity.this, "Error en la subida: " + response.message(),
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Error: " + t.getMessage());
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    public void enviarImagenAEfecto(Bitmap imagen) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imagen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), byteArray);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", "foto.PNG", requestFile);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://172.16.211.7:5000")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        FileUploadService service = retrofit.create(FileUploadService.class);
        Call<ResponseBody> call = service.efecto(body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Imagen enviada y procesada con éxito");
                    Toast.makeText(MainActivity.this, "Imagen enviada y procesada con éxito", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Error en el procesamiento: " + response.message());
                    Toast.makeText(MainActivity.this, "Error en el procesamiento: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Error: " + t.getMessage());
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        txtPredict = findViewById(R.id.txtPredict);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Inicializar AssetManager
        AssetManager assetManager = getAssets();
        initAssetManager(assetManager);

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
                Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
            }
        } else {
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
                Bitmap bIn = bitmap;
                Bitmap bOut = bIn.copy(bIn.getConfig(), true);
                detectorBordes(bIn, bOut);
                original.setImageBitmap(bOut);
                almacenar(bOut);
            }
        });

        btnCombinar = findViewById(R.id.btnCombinar);
        btnCombinar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap bIn2 = bitmap;
                Bitmap bIn3 = bitmap2;
                bOutC = bIn3.copy(bIn3.getConfig(), true);
                combinar(bIn2,bIn3,bOutC);
                combinado.setImageBitmap(bOutC);
                almacenar(bOutC);
            }
        });

        btnDeteccionRostros = findViewById(R.id.btnDeteccionRostros);
        btnDeteccionRostros.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap bIn = bitmap;
                Bitmap bOut = bIn.copy(bIn.getConfig(), true);
                detectarRostros(bIn, bOut);
                combinado.setImageBitmap(bOut);
                enviarImagenAEfecto(bOut);
                almacenar(bOut);
            }
        });
        btnHog = findViewById(R.id.btnHog);
        btnHog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initAssetManager(assetManager);
                Bitmap bIn = bitmap;
                if (bIn != null) {
                    String prediction = predecirNumeros(bIn);
                    txtPredict.setText(prediction);
                } else {
                    Toast.makeText(MainActivity.this, "No hay imagen para predecir", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnDuoTono = findViewById(R.id.btnDuo);
        btnDuoTono.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap bIn6 = bitmap;
                bOut3 = bIn6.copy(bIn6.getConfig(), true);
                duotono(bIn6,bOut3);
                original.setImageBitmap(bOut3);
                almacenar(bOut3);
            }
        });
        btnEnviar = findViewById(R.id.btnEnviar);
        btnEnviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(bOutC==null){
                    capturarYEnviarImagen(bOut3);
                }else{capturarYEnviarImagen(bOutC);
                }
            }
        });
    }

    /**
     * A native method that is implemented by the 'aplicacionnativa' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    public native void detectorBordes(Bitmap in, Bitmap out);
    public native void combinar(Bitmap in, Bitmap in2, Bitmap out);
    public native void suavizado(Bitmap in, Bitmap out);
    public native void duotono(Bitmap in, Bitmap out);
    public native void detectarRostros(Bitmap in, Bitmap out);
    public native void initAssetManager(AssetManager assetManager);
    public native String predecirNumeros(Bitmap in);
}
