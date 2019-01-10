# PhotoAI
Simple to use Android app to showcase the power of local image classification that can be found in the Android Play Store [here](https://www.google.com).

PhotoAI uses three different trained TensorFlow Lite graphs to classify images:
1. Every Day Objects (uses the MobileNet CNN)
2. Flower Types (created from the TensorFlow Lite tutorial found [here](https://www.tensorflow.org/lite/))
3. Dog Breeds (CNN created from using transfer learning on MobileNet)

PhotoAI enables users to choose an image to classify by:
1. Taking a picture
2. Choosing an image from their phone's gallery
3. Live classification
