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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.soundcloud.android.crop.Crop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/*
 By    : Michael Shea
 email : mjshea3@illinois.edu
 phone : 708 - 203 - 8272
 Description:
 Controls activity where the user chooses how to obtain the image they want to classify.
*/

public class Start extends AppCompatActivity {

    // title image
    private ImageView titleImage;

    // buttons that display availble options to user on how to select image to classify
    private Button buttonOpenCamera;
    private Button buttonOpenGallery;
    private Button buttonOpenLiveFeed;
    private Button buttonBack;

    // request codes for permission requests to the os
    public static final int REQUEST_IMAGE = 100;
    public static final int PICK_IMAGE = 200;
    public static final int REQUEST_PERMISSION = 300;

    // will hold uri of image obtained in camera and gallery options
    private Uri imageUri;
    // string passed from "choose classifier" activity that says what classifier was chosen
    private String chosen;
    // official name of classifier that is described in the classifier assets text file. Currently unused,
    // but will most likely be used in future updates
    private String name;

    // dimensions that need to be passed in the CNN
    private int DIM_IMG_SIZE_X;
    private int DIM_IMG_SIZE_Y;
    private int DIM_PIXEL_SIZE;

    // boolean that dictates if the tflite file is quantized or not
    private boolean quant;


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get the chosen extra from the "choose classifier" activity
        chosen = (String) getIntent().getStringExtra("chosen");
        // use 'chosen' to get required data from assets of selected classifier
        String classifierData = LoadData(chosen + "/info.txt");
        // split up data based on new lines into string array
        String lines[] = classifierData.split("\\r?\\n");
        // first line is the name of the classifier
        name = lines[0].substring(6);
        // second line states if the CNN is quantized or not
        quant = lines[4].substring(7).equals("Y");

        // the remaining three lines are the dimensions that are required to pass into the CNN
        DIM_IMG_SIZE_X = Integer.parseInt(lines[1].substring(7));
        DIM_IMG_SIZE_Y = Integer.parseInt(lines[2].substring(7));
        DIM_PIXEL_SIZE = Integer.parseInt(lines[3].substring(5));

        // request permission to write data (aka images) to the user's external storage of their phone - needed for camera option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        }

        // request permission to read data (aka images) from the user's external storage of their phone - needed for gallery option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        }

        // request permission to use the camera on the user's phone - needed for camera and live feed options
        if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.CAMERA}, REQUEST_PERMISSION);
        }

        setContentView(R.layout.activity_start);

        // button that allows the user to take a picture
        buttonOpenCamera = (Button)findViewById(R.id.buttonOpenCamera);
        buttonOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCameraIntent();
            }
        });

        // button that allows the user to select an image from their gallery
        buttonOpenGallery = (Button)findViewById(R.id.buttonOpenGallery);
        buttonOpenGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGalleryIntent();
            }
        });

        // button that allows the user to start a live feed of what their phone's camera sees in
        // real time
        buttonOpenLiveFeed = (Button)findViewById(R.id.buttonOpenLiveFeed);
        buttonOpenLiveFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Start.this, LiveFeed.class);
                // send selected classifier data to live feed acitivty
                sendData(i);
                startActivity(i);
            }
        });

        // allows the user to go back to the "choose classifier scene"
        buttonBack= (Button)findViewById(R.id.back);
        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Start.this, choose_classifier.class);
                startActivity(i);
            }
        });

        // set the title image from assets
        titleImage = (ImageView)findViewById(R.id.titleImage);
        titleImage.setImageBitmap(getBitmapFromAssets("AppImages/title.PNG"));
    }

    // reads a text file into a single string and then returns it
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

    // sends selected classifier data to the next chosen activity
    public void sendData(Intent i){
        i.putExtra("name", name);
        i.putExtra("chosen", chosen);
        i.putExtra("quant", quant);
        i.putExtra("DIM_IMG_SIZE_X", DIM_IMG_SIZE_X);
        i.putExtra("DIM_IMG_SIZE_Y", DIM_IMG_SIZE_Y);
        i.putExtra("DIM_PIXEL_SIZE", DIM_PIXEL_SIZE);
    }

    // opens camera for user
    private void openCameraIntent(){
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
        // tell camera where to store the resulting picture
        imageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // start camera, and wait for it to finish
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    // opens gallery for user
    private void openGalleryIntent(){
        Intent intent = new Intent();
        // Show only images, no videos or anything else
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        // Always show the chooser (if there are multiple options available)
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
    }

    // given an image uri obtained from camera or gallery, send it to "ViewSelectedImage" acitivty
    public void sendUri(Uri uri_image_to_send){
        Intent i = new Intent(Start.this, ViewSelectedImage.class);
        // put image data in extras to send
        i.putExtra("resID_uri", uri_image_to_send);
        // send other required data
        sendData(i);
        startActivity(i);
    }

    // dictates what to do after the user takes an image, selects and image, or crops an image
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        // if the camera activity is finished, obtained the uri, crop it to make it square, and send it to 'ViewSelectedImage" activity
        if(requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            try {
                Uri source_uri = imageUri;
                Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
                // need to crop it to square image as CNN's always reqiuire square input
                Crop.of(source_uri, dest_uri).asSquare().start(Start.this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // if the gallery activity is finished, obtained the uri, crop it to make it square, and send it to 'ViewSelectedImage" activity
        else if(requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri source_uri = data.getData();
            Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
            Crop.of(source_uri, dest_uri).asSquare().start(Start.this);
        }
        // if cropping acitivty is finished, get the resulting cropped image uri and send it to 'ViewSelectedImage" activity
        else if(requestCode == Crop.REQUEST_CROP && resultCode == RESULT_OK){
            imageUri = Crop.getOutput(data);
            sendUri(imageUri);
        }
    }

    // checks that the user has allowed all the required permission of read and write and camera. If not, notify the user and close the application
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(getApplicationContext(),"This application needs read, write, and camera permissions to run. Application now closing.",Toast.LENGTH_LONG);
                System.exit(0);
            }
        }
    }

    // given image filename on user's phone, return the image as a bitmap
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
