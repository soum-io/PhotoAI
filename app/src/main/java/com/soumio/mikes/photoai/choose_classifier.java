package com.soumio.mikes.photoai;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/*
 By    : Michael Shea
 email : mjshea3@illinois.edu
 phone : 708 - 203 - 8272
 Description:
 Controls the  initial activity which prompts the user to choose an image classifier.
*/

public class choose_classifier extends AppCompatActivity {

    // title image
    private ImageView titleImage;

    // button for each available classifier
    private Button generalClassifier;
    private Button flowersClassifier;
    private Button dogsClassifier;

    // string to send to next activity that describes the chosen classifier
    private String chosen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_classifier);

        // on click for every day objects classifier
        generalClassifier = (Button)findViewById(R.id.generalClassifier);
        generalClassifier.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chosen = "imageNet";
                Intent i = new Intent(choose_classifier.this, Start.class);
                i.putExtra("chosen", chosen);
                startActivity(i);
            }
        });


        // on click for flower type classifier
        flowersClassifier = (Button)findViewById(R.id.flowersClassifier);
        flowersClassifier.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chosen = "flowers";
                Intent i = new Intent(choose_classifier.this, Start.class);
                i.putExtra("chosen", chosen);
                startActivity(i);
            }
        });

        // on click for dog type classifier
        dogsClassifier = (Button)findViewById(R.id.dogsClassifier);
        dogsClassifier.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chosen = "dogs";
                Intent i = new Intent(choose_classifier.this, Start.class);
                i.putExtra("chosen", chosen);
                startActivity(i);
            }
        });

        // set the title image from assets
        titleImage = (ImageView)findViewById(R.id.titleImage);
        titleImage.setImageBitmap(getBitmapFromAssets("AppImages/title.PNG"));

    }


    // loads image from assets as bitmap and returns the bitmap
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
