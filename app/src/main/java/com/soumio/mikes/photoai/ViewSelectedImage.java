package com.soumio.mikes.photoai;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.soundcloud.android.crop.Crop;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;


public class ViewSelectedImage extends AppCompatActivity {

    private static final int RESULTS_TO_SHOW = 3;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private String name;
    private String chosen;
    private int DIM_IMG_SIZE_X;
    private int DIM_IMG_SIZE_Y;
    private int DIM_PIXEL_SIZE;
    private boolean quant;
    private String LABEL_PATH;
    private String MODEL_NAME;
    private int[] intValues;


    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });


    private ImageView selected_image;
    private Button back_button;
    private Button crop_button;
    private Button left_rotate;
    private Button right_rotate;
    private Button classify_button;
    private float cur_rotation;
    private Interpreter tflite;
    private List<String> labelList;
    private ByteBuffer imgData = null;
    private float[][] labelProbArray = null;
    private byte[][] labelProbArrayB = null;
    private String[] topLables = null;
    private String[] topConfidence = null;

    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView Confidence1;
    private TextView Confidence2;
    private TextView Confidence3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        name = (String) getIntent().getStringExtra("name");
        chosen = (String) getIntent().getStringExtra("chosen");
        DIM_IMG_SIZE_X = (int) getIntent().getIntExtra("DIM_IMG_SIZE_X", 0);
        DIM_IMG_SIZE_Y = (int) getIntent().getIntExtra("DIM_IMG_SIZE_Y", 0);
        DIM_PIXEL_SIZE = (int) getIntent().getIntExtra("DIM_PIXEL_SIZE", 0);
        quant = (boolean) getIntent().getBooleanExtra("quant", false);
        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
        LABEL_PATH = chosen + "/labels.txt";
        MODEL_NAME = chosen + "/graph.lite";



        super.onCreate(savedInstanceState);

        try{
            tflite = new Interpreter(loadModelFile());
            labelList = loadLabelList();
        } catch (Exception ex){
            ex.printStackTrace();
        }

        if(quant){
            imgData =
                    ByteBuffer.allocateDirect(
                            DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        } else {
            imgData =
                    ByteBuffer.allocateDirect(
                            4 * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        }

        imgData.order(ByteOrder.nativeOrder());

        if(quant){
            labelProbArrayB= new byte[1][labelList.size()];
        } else {
            labelProbArray = new float[1][labelList.size()];
        }

        setContentView(R.layout.activity_view_selected_image);

        label1 = (TextView) findViewById(R.id.label1);
        label2 = (TextView) findViewById(R.id.label2);
        label3 = (TextView) findViewById(R.id.label3);
        Confidence1 = (TextView) findViewById(R.id.Confidence1);
        Confidence2 = (TextView) findViewById(R.id.Confidence2);
        Confidence3 = (TextView) findViewById(R.id.Confidence3);
        topLables = new String[RESULTS_TO_SHOW];
        topConfidence = new String[RESULTS_TO_SHOW];


        back_button = (Button)findViewById(R.id.back_button);
        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(ViewSelectedImage.this, Start.class);
                i.putExtra("chosen", chosen);
                startActivity(i);
            }
        });

        left_rotate = (Button)findViewById(R.id.left_rotate);
        left_rotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selected_image.setRotation(selected_image.getRotation() + -90);
            }
        });

        right_rotate = (Button)findViewById(R.id.right_rotate);
        right_rotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selected_image.setRotation(selected_image.getRotation() + 90);
            }
        });

        crop_button = (Button)findViewById(R.id.crop_image);
        crop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cur_rotation = selected_image.getRotation();
                selected_image.invalidate();
                BitmapDrawable drawable = (BitmapDrawable)selected_image.getDrawable();
                Bitmap bitmap = drawable.getBitmap();
                Matrix matrix = new Matrix();
                matrix.postRotate(cur_rotation);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                Uri source_uri = getImageUri(getApplicationContext(), rotatedBitmap);
                Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
                Crop.of(source_uri, dest_uri).asSquare().start(ViewSelectedImage.this);
            }
        });

        classify_button = (Button)findViewById(R.id.classify_image);
        classify_button.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
                Bitmap bitmap_orig = ((BitmapDrawable)selected_image.getDrawable()).getBitmap();
                Bitmap bitmap = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);
                convertBitmapToByteBuffer(bitmap);
                if(quant){
                    tflite.run(imgData, labelProbArrayB);
                } else {
                    tflite.run(imgData, labelProbArray);
                }
                printTopKLabels();
           }
       });

        selected_image = (ImageView) findViewById(R.id.selected_image);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            Set<String> keys = bundle.keySet();
            Iterator<String> it = keys.iterator();
            Log.e("uri","Dumping Intent start");
            while (it.hasNext()) {
                String key = it.next();
                Log.e("uri","[" + key + "=" + bundle.get(key)+"]");
                if(key.equals("resID_bit")){
                    Bitmap bitmap = (Bitmap)getIntent().getParcelableExtra("resID_bit");
                    selected_image.setImageBitmap(bitmap);
                } else if(key.equals("resID_uri")){
                    Uri uri = (Uri)getIntent().getParcelableExtra("resID_uri");
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        selected_image.setImageBitmap(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            Log.e("uri","Dumping Intent end");
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(MODEL_NAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                if(quant){
                    imgData.put((byte) ((val >> 16) & 0xFF));
                    imgData.put((byte) ((val >> 8) & 0xFF));
                    imgData.put((byte) (val & 0xFF));
                } else {
                    imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    imgData.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                }

            }
        }
    }

    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(this.getAssets().open(LABEL_PATH)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private void printTopKLabels() {
        for (int i = 0; i < labelList.size(); ++i) {
            if(quant){
                sortedLabels.add(
                        new AbstractMap.SimpleEntry<>(labelList.get(i), (labelProbArrayB[0][i] & 0xff) / 255.0f));
            } else {
                sortedLabels.add(
                        new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
            }
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            topLables[i] = label.getKey();
            topConfidence[i] = String.format("%.0f%%",label.getValue()*100);
        }
        label1.setText("1. "+topLables[2]);
        label2.setText("2. "+topLables[1]);
        label3.setText("3. "+topLables[0]);
        Confidence1.setText(topConfidence[2]);
        Confidence2.setText(topConfidence[1]);
        Confidence3.setText(topConfidence[0]);
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    private Uri getImageUri(Context context, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (ContextCompat.checkSelfPermission(ViewSelectedImage.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), inImage, "Title", null);
            return Uri.parse(path);
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == Crop.REQUEST_CROP && resultCode == RESULT_OK){
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Crop.getOutput(data));
                selected_image.setImageBitmap(bitmap);
                selected_image.setRotation(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
