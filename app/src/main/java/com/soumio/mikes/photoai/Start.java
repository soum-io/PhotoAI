package com.soumio.mikes.photoai;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.soundcloud.android.crop.Crop;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class Start extends AppCompatActivity {
    private ImageView titleImage;

    private Button buttonOpenCamera;
    private Button buttonOpenGallery;
    private Button buttonOpenLiveFeed;
    private Button buttonBack;
    public static final int REQUEST_IMAGE = 100;
    public static final int PICK_IMAGE = 200;
    public static final int REQUEST_PERMISSION = 300;

    private Uri imageUri;
    private String chosen;
    private String name;
    private int DIM_IMG_SIZE_X;
    private int DIM_IMG_SIZE_Y;
    private int DIM_PIXEL_SIZE;
    private boolean quant;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        chosen = (String) getIntent().getStringExtra("chosen");
        String classifierData = LoadData(chosen + "/info.txt");
        String lines[] = classifierData.split("\\r?\\n");
        name = lines[0].substring(6);
        quant = lines[4].substring(7).equals("Y");
        DIM_IMG_SIZE_X = Integer.parseInt(lines[1].substring(7));
        DIM_IMG_SIZE_Y = Integer.parseInt(lines[2].substring(7));
        DIM_PIXEL_SIZE = Integer.parseInt(lines[3].substring(5));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        }

        if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.CAMERA}, REQUEST_PERMISSION);
        }

        setContentView(R.layout.activity_start);


        buttonOpenCamera = (Button)findViewById(R.id.buttonOpenCamera);
        buttonOpenGallery = (Button)findViewById(R.id.buttonOpenGallery);
        buttonOpenLiveFeed = (Button)findViewById(R.id.buttonOpenLiveFeed);
        buttonBack= (Button)findViewById(R.id.back);


        buttonOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCameraIntent();
            }
        });

        buttonOpenGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGalleryIntent();
            }
        });

        buttonOpenLiveFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Start.this, LiveFeed.class);
                sendData(i);
                startActivity(i);
            }
        });

        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Start.this, choose_classifier.class);
                startActivity(i);
            }
        });

        titleImage = (ImageView)findViewById(R.id.titleImage);
        titleImage.setImageBitmap(getBitmapFromAssets("AppImages/title.PNG"));
    }

    public String LoadData(String inFile) {
        String tContents = "";
        try {
            InputStream stream = getAssets().open(inFile);
            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer);
            stream.close();
            tContents = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tContents;
    }

    public void sendData(Intent i){
        i.putExtra("name", name);
        i.putExtra("chosen", chosen);
        i.putExtra("quant", quant);
        i.putExtra("DIM_IMG_SIZE_X", DIM_IMG_SIZE_X);
        i.putExtra("DIM_IMG_SIZE_Y", DIM_IMG_SIZE_Y);
        i.putExtra("DIM_PIXEL_SIZE", DIM_PIXEL_SIZE);
    }

    private void openCameraIntent(){
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
        imageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void openGalleryIntent(){
        Intent intent = new Intent();
        // Show only images, no videos or anything else
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        // Always show the chooser (if there are multiple options available)
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
    }

    public void send(Bitmap image_to_send){
        Intent i = new Intent(Start.this, ViewSelectedImage.class);
        i.putExtra("resID_bit", image_to_send);
        sendData(i);
        startActivity(i);
    }

    public void sendUri(Uri uri_image_to_send){
        Intent i = new Intent(Start.this, ViewSelectedImage.class);
        i.putExtra("resID_uri", uri_image_to_send);
        sendData(i);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            try {
                Uri source_uri = imageUri;
                Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
                Crop.of(source_uri, dest_uri).asSquare().start(Start.this);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("uri_p","11");
            }
        } else if(requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri source_uri = data.getData();
            Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
            Crop.of(source_uri, dest_uri).asSquare().start(Start.this);
        } else if(requestCode == Crop.REQUEST_CROP && resultCode == RESULT_OK){
            imageUri = Crop.getOutput(data);
            sendUri(imageUri);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.e("loll","granted");
            } else {
                Toast.makeText(getApplicationContext(),"This application needs read, write, and camera permissions to run. Application now closing.",Toast.LENGTH_LONG);
                System.exit(0);
            }
        }
    }

    private Uri getImageUri(Context context, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (ContextCompat.checkSelfPermission(Start.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), inImage, "Title", null);
            Uri toReturn =  Uri.parse(path);
            return toReturn;
        }
        return null;

    }

    private Bitmap getBitmapFromAssets(String fileName){
        AssetManager am = getAssets();
        InputStream is = null;
        try{
            is = am.open(fileName);
        }catch(IOException e){
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        return bitmap;
    }

}
