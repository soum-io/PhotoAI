package com.example.mikes.portdetectorsample;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

public class choose_classifier extends AppCompatActivity {

    private ImageView titleImage;

    private Button generalClassifier;
    private Button flowersClassifier;
    private Button dogsClassifier;
    private String chosen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_classifier);

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


        titleImage = (ImageView)findViewById(R.id.titleImage);
        titleImage.setImageBitmap(getBitmapFromAssets("AppImages/title.PNG"));

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
