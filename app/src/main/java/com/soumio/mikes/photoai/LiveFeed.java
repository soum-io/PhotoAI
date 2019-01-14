package com.soumio.mikes.photoai;

import android.content.Intent;
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
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
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

/*
 By    : Michael Shea
 email : mjshea3@illinois.edu
 phone : 708 - 203 - 8272
 Description:
 Controls the activity classifies image in real time based of what the camera is seeing
 */

public class LiveFeed extends AppCompatActivity {

    // presets for live preview view
    public static final int IMAGE_SIZE = 1024;
    public static final int IMAGE_ORIENTATION = 90;

    // states if an image is currently being classified
    private boolean computing = false;

    // selected classifier information received from extras
    private String name;
    private String chosen;
    private int DIM_IMG_SIZE_X;
    private int DIM_IMG_SIZE_Y;
    private int DIM_PIXEL_SIZE;
    private boolean quant;

    // holds path to text files that contains all the labels of the CNN
    private String LABEL_PATH;
    // holds path to file that folds the tflite graph
    private String MODEL_NAME;
    // int array to hold image data
    private int[] intValues;

    // presets for rgb conversion
    private static final int RESULTS_TO_SHOW = 3;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    // tflite graph
    private Interpreter tflite;
    // holds all the possible labels
    private List<String> labelList;
    // holds the selected image data asa bytes
    private ByteBuffer imgData = null;
    // holds the probabilities of each label for non-quantized graphs
    private float[][] labelProbArray = null;
    // holds the probabilities of each label for quantized graphs
    private byte[][] labelProbArrayB = null;
    // array that holds the labels with the highest probabilities
    private String[] topLables = null;
    // array that holds the highest probabilities
    private String[] topConfidence = null;

    // priority queue that will hold the top results from the CNN
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

    // activity elements
    private SurfaceView cameraPreview;
    private RelativeLayout overlay;
    private Camera camera = null;
    private Button stopButton;
    private Button startButton;
    private Button backButton;
    private ImageView preview_image;
    private ImageView waiting;
    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView Confidence1;
    private TextView Confidence2;
    private TextView Confidence3;

    // holds camera parameters
    private Camera.Parameters camParams;



    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // get all selected classifier data from classifiers
        name = (String) getIntent().getStringExtra("name");
        chosen = (String) getIntent().getStringExtra("chosen");
        DIM_IMG_SIZE_X = (int) getIntent().getIntExtra("DIM_IMG_SIZE_X", 0);
        DIM_IMG_SIZE_Y = (int) getIntent().getIntExtra("DIM_IMG_SIZE_Y", 0);
        DIM_PIXEL_SIZE = (int) getIntent().getIntExtra("DIM_PIXEL_SIZE", 0);
        quant = (boolean) getIntent().getBooleanExtra("quant", false);

        // initialize array that holds image data
        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
        // use extras to create paths to labels and graph
        LABEL_PATH = chosen + "/labels.txt";
        MODEL_NAME = chosen + "/graph.lite";

        //initilize graph and labels
        try{
            tflite = new Interpreter(loadModelFile());
            labelList = loadLabelList();
        } catch (Exception ex){
            ex.printStackTrace();
        }

        // initialize byte array. The size depends if the input data needs to be quantized or not
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

        // initialize probabilities array. The datatypes that array holds depends if the input data needs to be quantized or not
        if(quant){
            labelProbArrayB= new byte[1][labelList.size()];
        } else {
            labelProbArray = new float[1][labelList.size()];
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_feed);

        // labels that hold top three results of CNN
        label1 = (TextView) findViewById(R.id.label1);
        label2 = (TextView) findViewById(R.id.label2);
        label3 = (TextView) findViewById(R.id.label3);
        // displays the probabilities of top labels
        Confidence1 = (TextView) findViewById(R.id.Confidence1);
        Confidence2 = (TextView) findViewById(R.id.Confidence2);
        Confidence3 = (TextView) findViewById(R.id.Confidence3);


        // initialize array to hold top labels
        topLables = new String[RESULTS_TO_SHOW];
        // initialize array to hold top probabilities
        topConfidence = new String[RESULTS_TO_SHOW];

        // imageView to show user the last classified image
        preview_image = (ImageView) findViewById(R.id.preview_image);
        // set the last classified image to "waiting to start live feed" image
        preview_image.setImageBitmap(getBitmapFromAssets("AppImages/start_feed.PNG"));

        // displays "start live preview" message to user over the live camera feed view
        waiting = (ImageView) findViewById(R.id.waiting);

        // shows the user the live preview of what the camera sees
        cameraPreview = (SurfaceView) findViewById(R.id.camera_preview);
        // overlay that covers some of the live preview to make it seem square
        overlay = (RelativeLayout) findViewById(R.id.overlay);

        // allows user to pause the live classification
        stopButton = (Button)findViewById(R.id.stop_button);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // set imageView with "start live feed" message over camera preview
                waiting.setVisibility(View.VISIBLE);
                if(camera != null){
                    camera.stopPreview();
                }
            }
        });

        // allows user to go back to previous activity
        backButton = (Button)findViewById(R.id.back);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // make sure the live preview is safely stopped before exiting activity
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

        // allows user to start the live classification
        startButton = (Button)findViewById(R.id.start_feed);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // start real time view of camera
                startView();
                // set image telling user to start live feed to invisible
                waiting.setVisibility(View.INVISIBLE);
                // set callback so this method is called on every single frame
                camera.setPreviewCallback(new Camera.PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera _camera) {
                        try{
                            // if we are in the middle of classifying another frame, return
                            if(computing){
                                return;
                            }
                            // let other threads know we are classifying current frame. While this is
                            // not thread safe code, it does not matter as it isn't harmful to classify
                            // multiple frames at once as long as it is not too many to lag the activity.
                            computing = true;
                            // get current camera parameters
                            Camera.Parameters parameters = _camera.getParameters();
                            int width = parameters.getPreviewSize().width;
                            int height = parameters.getPreviewSize().height;
                            // get bitmap of current frame's top sqaure portions that is shown in
                            // the preview
                            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                            Rect rect = new Rect(0, 0, width, height);
                            YuvImage yuvimage=new YuvImage(data,ImageFormat.NV21,width,height,null);
                            yuvimage.compressToJpeg(rect, 100, outstr);
                            Bitmap bitmap_orig = processImage(outstr.toByteArray());
                            if(bitmap_orig == null){
                                computing = false;
                                return;
                            }
                            // resize the bitmap to fit the required input size into the tflite graph
                            final Bitmap bitmap = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);
                            // tell the UI thread to set the new bitmap as the last classified image imageView
                            LiveFeed.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((ImageView) findViewById(R.id.preview_image)).setImageBitmap(bitmap);
                                }
                            });
                            // convert bitmap to byte array
                            convertBitmapToByteBuffer(bitmap);
                            // classify the iamge
                            if(quant){
                                tflite.run(imgData, labelProbArrayB);
                            } else {
                                tflite.run(imgData, labelProbArray);
                            }
                            // display the results
                            printTopKLabels();
                            // tell other threads we are done computing
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
        // open camera if it hasnt been done already
        if(camera == null){
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
        try {
            // idk why this is the case but the original orientation is sideways
            camera.setDisplayOrientation(90);
            // tell camera what cameraPreview to display onto
            camera.setPreviewDisplay(cameraPreview.getHolder());
        } catch (Exception ex){
            ex.printStackTrace();
        }

        // start live feed
        camera.startPreview();
    }

    // set ui elements dynamically when window has focused. This is required because the overlay element's
    // dimensions (that makes the camera preview appear square) is depended on the current phone's
    // screen size
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

        // start camera
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

    // get bitmap from byte data that the camera callback function receives on every frame
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

    // loads tflite grapg from file
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(MODEL_NAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // converts bitmap to byte array which is passed in the tflite graph
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // loop through all pixels
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                // get rgb values from intValues where each int holds the rgb values for a pixel.
                // if quantized, convert each rgb value to a byte, otherwise to a float
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

    // loads the labels from the label txt file in assets into a string array
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

    // print the top labels and respective confidences
    private void printTopKLabels() {
        // add all results to priority queue
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

        // get top results from priority queue
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            topLables[i] = label.getKey();
            topConfidence[i] = String.format("%.0f%%",label.getValue()*100);
        }

        // set the corresponding textviews with the results
        label1.setText("1. "+topLables[2]);
        label2.setText("2. "+topLables[1]);
        label3.setText("3. "+topLables[0]);
        Confidence1.setText(topConfidence[2]);
        Confidence2.setText(topConfidence[1]);
        Confidence3.setText(topConfidence[0]);
    }

    // resizes bitmap to given dimensions
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
