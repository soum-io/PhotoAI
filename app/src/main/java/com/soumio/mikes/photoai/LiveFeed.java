package com.soumio.mikes.photoai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class LiveFeed extends AppCompatActivity {


    public static final int IMAGE_SIZE = 1024;
    public static final int IMAGE_ORIENTATION = 90;
    public static final int MY_CAMERA_REQUEST_CODE = 101;
    public static final int MY_STORAGE_REQUEST_CODE = 102;

    private boolean computing = false;

    private String name;
    private String chosen;
    private int DIM_IMG_SIZE_X;
    private int DIM_IMG_SIZE_Y;
    private int DIM_PIXEL_SIZE;
    private boolean quant;
    private String LABEL_PATH;
    private String MODEL_NAME;
    private int[] intValues;

    private static final int RESULTS_TO_SHOW = 3;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private Interpreter tflite;
    private List<String> labelList;
    private ByteBuffer imgData = null;
    private float[][] labelProbArray = null;
    private byte[][] labelProbArrayB = null;
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

    private SurfaceView cameraPreview;
    private RelativeLayout overlay;
    private Camera camera = null;
    private Button stopButton;
    private Button startButton;
    private Button backButton;
    private Camera.Parameters camParams;
    private ImageView preview_image;
    private ImageView waiting;
    private String[] topLables = null;
    private String[] topConfidence = null;

    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView Confidence1;
    private TextView Confidence2;
    private TextView Confidence3;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        name = (String) getIntent().getStringExtra("name");
        chosen = (String) getIntent().getStringExtra("chosen");
        DIM_IMG_SIZE_X = (int) getIntent().getIntExtra("DIM_IMG_SIZE_X", 0);
        DIM_IMG_SIZE_Y = (int) getIntent().getIntExtra("DIM_IMG_SIZE_Y", 0);
        DIM_PIXEL_SIZE = (int) getIntent().getIntExtra("DIM_PIXEL_SIZE", 0);
        quant = (boolean) getIntent().getBooleanExtra("quant", false);
        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
        LABEL_PATH = chosen + "/labels.txt";
        MODEL_NAME = chosen + "/graph.lite";

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

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_feed);

        label1 = (TextView) findViewById(R.id.label1);
        label2 = (TextView) findViewById(R.id.label2);
        label3 = (TextView) findViewById(R.id.label3);
        Confidence1 = (TextView) findViewById(R.id.Confidence1);
        Confidence2 = (TextView) findViewById(R.id.Confidence2);
        Confidence3 = (TextView) findViewById(R.id.Confidence3);
        topLables = new String[RESULTS_TO_SHOW];
        topConfidence = new String[RESULTS_TO_SHOW];

        preview_image = (ImageView) findViewById(R.id.preview_image);
        preview_image.setImageBitmap(getBitmapFromAssets("AppImages/start_feed.PNG"));

        waiting = (ImageView) findViewById(R.id.waiting);

        if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(LiveFeed.this, new String[] {android.Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(LiveFeed.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_STORAGE_REQUEST_CODE);
        }

        cameraPreview = (SurfaceView) findViewById(R.id.camera_preview);
        overlay = (RelativeLayout) findViewById(R.id.overlay);

        stopButton = (Button)findViewById(R.id.stop_button);

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                waiting.setVisibility(View.VISIBLE);
                if(camera != null){
                    camera.stopPreview();
                }
            }
        });

        backButton = (Button)findViewById(R.id.back);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(camera != null){
                    camera.stopPreview();
                    camera.setPreviewCallback(null);
                    camera.release();
                }
                Intent i = new Intent(LiveFeed.this, Start.class);
                i.putExtra("chosen", chosen);
                startActivity(i);
            }
        });

        startButton = (Button)findViewById(R.id.start_feed);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startView();

                waiting.setVisibility(View.INVISIBLE);

                camera.setPreviewCallback(new Camera.PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera _camera) {
                        try{
                            if(computing){
                                return;
                            }
                            computing = true;
                            Camera.Parameters parameters = _camera.getParameters();
                            int width = parameters.getPreviewSize().width;
                            int height = parameters.getPreviewSize().height;
                            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                            Rect rect = new Rect(0, 0, width, height);
                            YuvImage yuvimage=new YuvImage(data,ImageFormat.NV21,width,height,null);
                            yuvimage.compressToJpeg(rect, 100, outstr);
                            Bitmap bitmap_orig = processImage(outstr.toByteArray());
                            if(bitmap_orig == null){
                                computing = false;
                                return;
                            }
                            final Bitmap bitmap = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);
                            LiveFeed.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((ImageView) findViewById(R.id.preview_image)).setImageBitmap(bitmap);
                                }
                            });



                            convertBitmapToByteBuffer(bitmap);
                            if(quant){
                                tflite.run(imgData, labelProbArrayB);
                            } else {
                                tflite.run(imgData, labelProbArray);
                            }

                            printTopKLabels();
                            computing = false;
                        } catch (Exception ex){
                            ex.printStackTrace();
                        }
                    }
                });
            }
        });
    }


    public void startView() {
        if(camera == null){
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
        try {
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(cameraPreview.getHolder());
        } catch (Exception ex){
            ex.printStackTrace();
        }

        camera.startPreview();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Get the preview size
        int previewWidth = cameraPreview.getMeasuredWidth(),
                previewHeight = cameraPreview.getMeasuredHeight();

        waiting.getLayoutParams().height = previewWidth;

        waiting.setImageBitmap(getResizedBitmap(getBitmapFromAssets("AppImages/start_feed.PNG"),previewWidth,previewWidth));

        // Set the height of the overlay so that it makes the preview a square
        if(overlay != null){
            RelativeLayout.LayoutParams overlayParams = (RelativeLayout.LayoutParams) overlay.getLayoutParams();
            overlayParams.height = previewHeight - previewWidth;
            overlay.setLayoutParams(overlayParams);
        }

        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        camParams = camera.getParameters();

        // Find a preview size that is at least the size of our IMAGE_SIZE
        Camera.Size previewSize = camParams.getSupportedPreviewSizes().get(0);
        for(Camera.Size size : camParams.getSupportedPreviewSizes()) {
            if (size.width >= IMAGE_SIZE && size.height >= IMAGE_SIZE) {
                previewSize = size;
                break;
            }
        }
        camParams.setPreviewSize(previewSize.width, previewSize.height);

        // Try to find the closest picture size to match the preview size.
        Camera.Size pictureSize = camParams.getSupportedPictureSizes().get(0);
        for (Camera.Size size : camParams.getSupportedPictureSizes()) {
            if (size.width == previewSize.width && size.height == previewSize.height) {
                pictureSize = size;
                break;
            }
        }
        camParams.setPictureSize(pictureSize.width, pictureSize.height);
        camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

        camera.setParameters(camParams);


    }

    private Bitmap processImage(byte[] data) throws IOException {
        // Determine the width/height of the image
        int width = camera.getParameters().getPictureSize().width;
        int height = camera.getParameters().getPictureSize().height;

        // Load the bitmap from the byte array
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if(bitmap == null){
            return null;
        }

        // Rotate and crop the image into a square
        int croppedWidth = (width > height) ? height : width;
        int croppedHeight = (width > height) ? height : width;

        Matrix matrix = new Matrix();
        matrix.postRotate(IMAGE_ORIENTATION);
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, croppedWidth, croppedHeight, matrix, true);
        bitmap.recycle();

        // Scale down to the output size
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(cropped, IMAGE_SIZE, IMAGE_SIZE, true);
        cropped.recycle();

        return scaledBitmap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case MY_CAMERA_REQUEST_CODE:{
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("loll","granted");
                } else {
                    Toast.makeText(getApplicationContext(),"This application needs camera permissions to run. Application now closing.",Toast.LENGTH_LONG);
                    System.exit(0);
                }
                break;
            }
            case MY_STORAGE_REQUEST_CODE:{
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("loll","granted");
                } else {
                    Toast.makeText(getApplicationContext(),"This application needs storage permissions to run. Application now closing.",Toast.LENGTH_LONG);
                    System.exit(0);
                }
                break;
            }
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
