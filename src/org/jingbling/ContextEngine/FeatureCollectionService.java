package org.jingbling.ContextEngine;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.*;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 2/9/13
 * Time: 3:58 PM
 * The purpose of this class is to take in a list of desired features, optional sample rate and sample duration,
 *   and return the data as a StringBuffer
 */
public class FeatureCollectionService extends Service implements SensorEventListener{

    private static SensorManager mSensorManager;
    private ArrayList<String> featureList = new ArrayList<String>();
    private ArrayList<String> featuresAccepted = new ArrayList<String>();
    // define expected sensors to be used
    private Sensor accelSensor;
    private Sensor locationSensor;
    private Sensor orientSensor;
    // more sensors to be added

    private StringBuffer dataToWrite=new StringBuffer(); //temp buffer to store data to be written to file or to classify
    private File dataCaptureFile;   // file for saving data for training
    // more blocking queues to be added as mailboxes to classifier service
    ArrayBlockingQueue<String> classifierFeaturesBuffer;

    private int intLabel; //for libSVM, use integer labels
    private String stringLabel; //for other classifier algorithms, may want string labels
    private String action; //desired feature collection action - either collecting, or classifying
    public static String TRAIN_ACTION = "train";
    public static String CLASSIFY_ACTION = "classify";
    public static String ACTION_KEY = "action";
    public static String FEATURES_KEY = "features";
    public static String LABELS_KEY = "contextLabels";
    public static String LABELSID_KEY = "labelID";
    public static String TRAINING_FILE_KEY="trainingFile";
    public static String BUFFER_SIZE_KEY="bufferSize";

    //buffers for saving feature data
    private static ArrayBlockingQueue<Double> accelSensorXBuffer;
    private static ArrayBlockingQueue<Double> accelSensorYBuffer;
    private static ArrayBlockingQueue<Double> accelSensorZBuffer;
    private static ArrayBlockingQueue<Double> accelSensorMagBuffer;
    private static ArrayBlockingQueue<Double> gyroSensorXBuffer;
    private static ArrayBlockingQueue<Double> gyroSensorYBuffer;
    private static ArrayBlockingQueue<Double> gyroSensorZBuffer;
    private static ArrayBlockingQueue<Double> gyroSensorMagBuffer;
    private static ArrayBlockingQueue<Double> locationDataBuffer;
    private static ArrayBlockingQueue<Double> orientDataBuffer;

    // for features calculation
    // todo save only max buffer depths
    private int accelFFTBuffSize= 64; //number of points to collect for FFT calculation
    private int accelBuffSize = 12; //todo make passed parameter later
    private int gyroBuffSize = 64;
    private int orientBuffSize = 12;
    calculateFeatures saveDataTask=null;

    // for classifying and sending message back to service

    private Intent broadcastReturnIntent = new Intent("org.jingbling.ContextEngine.ContextService");
    private Bundle returnBundle = new Bundle();

    @Override
    public void onCreate() {
        Log.d("FEATURE_COLLECT", "running Feature Collect ON CREATE");
        //todo allocate buffer sizes - for now just do accelerometer data
        accelSensorXBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        accelSensorYBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        accelSensorZBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        accelSensorMagBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        gyroSensorMagBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        orientDataBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);


        featuresAccepted.add(0,"accelmag.fft");
//        featuresAccepted.add(featuresAccepted.size(),"accelmagavg.fft");
//        featuresAccepted.add(featuresAccepted.size(),"accelmagmed.fft");
//        featuresAccepted.add(featuresAccepted.size(),"gyromag");
//        featuresAccepted.add(featuresAccepted.size(),"gyromag");

        super.onCreate();
        android.os.Debug.waitForDebugger(); //todo TO BE REMOVED

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d("FEATURE_COLLECT", "starting Feature Collection Service");
        // grab info from bundle
        Bundle inputExtras = intent.getExtras();
        featureList = inputExtras.getStringArrayList(FEATURES_KEY);
        intLabel = inputExtras.getInt(LABELSID_KEY);
        stringLabel = inputExtras.getString(LABELS_KEY);
        action = inputExtras.getString(ACTION_KEY);
//        messengerToService = (Messenger) inputExtras.get ("SERVICE_MESSENGER");
//        msgToService = Message.obtain();
        // check for training file only if this is a training action
        if (action.equals(TRAIN_ACTION))
            dataCaptureFile = new File(inputExtras.getString(TRAINING_FILE_KEY));
        else
            classifierFeaturesBuffer = new ArrayBlockingQueue<String>(inputExtras.getInt(BUFFER_SIZE_KEY));

        // initialize sensor manager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


        //Depending on features desired, select which sensors to register
        for (int i=0;i<featureList.size();i++) {
            if (featureList.get(i).toString().toLowerCase().contains("accel")) {
                accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                if (accelSensor != null) {
                    mSensorManager.registerListener(this, accelSensor,
                            SensorManager.SENSOR_DELAY_FASTEST);
                }
            }
            if (featureList.get(i).toString().toLowerCase().contains("orient")) {
                orientSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
                if (orientSensor != null) {
                    mSensorManager.registerListener(this, orientSensor,
                            SensorManager.SENSOR_DELAY_FASTEST);
                }
            }
            // todo more sensors to be registered later
        }

        saveDataTask = new calculateFeatures();
        // kick off async task for collecting data
        saveDataTask.execute();

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        // depending on the detected sensor type, perform different actions
        Sensor source = sensorEvent.sensor;
        if (source.equals(accelSensor)) {
            // save sensor data to queue for processing
            double tempValueX=sensorEvent.values[0];
            double tempValueY=sensorEvent.values[1];
            double tempValueZ=sensorEvent.values[2];
            accelSensorXBuffer.offer(tempValueX);
            accelSensorYBuffer.offer(tempValueY);
            accelSensorZBuffer.offer(tempValueZ);

            // also save magnitude for FFT
            double tempMag = Math.sqrt(tempValueX*tempValueX+tempValueY*tempValueY+tempValueZ*tempValueZ);
            accelSensorMagBuffer.offer(new Double(tempMag));

        }

        if (source.equals(locationSensor)) {
            // todo save location sensor data
        }

    }

    public String getFeatureBufferData() throws InterruptedException {

            return classifierFeaturesBuffer.peek();

    }

    private class calculateFeatures extends AsyncTask<Void,Void,Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            // first check if isCancelled, then want to exit this loop
            boolean tempdebug = isCancelled();
            if (isCancelled()) {
                // don't do anything
            }
            else {
                // depending on the desired features, calculate and save to data buffer
                //Check each feature for desired values to save

                // first save label
                // To avoid saving partial data, save to an intermediary loop before adding to dataToWrite
                StringBuffer tempBuffer = new StringBuffer();
                int featureNum = 0;

                // initial values for expected features

                int buffSize = 0;
                double[] dataBlock = new double[accelFFTBuffSize];
                double[] re = dataBlock;
                double[] im = new double[accelFFTBuffSize]; //want zero imaginary part
                FFT fft = new FFT(accelFFTBuffSize);
                if (featureList.contains("accelmag.fft")) {
                    featureNum+=accelFFTBuffSize;
                }

                // initial values for accelx.avg
                int accelxavgbuffSize = 0;
                double[] accelxavgdataBlock = new double[accelxavgbuffSize];
                double accelxavgsum = 0;
                if (featureList.contains("accelx.avg")) {
                    featureNum+=accelBuffSize;
                }

                // grab data from buffer - if no data, following while loop will block automatically
                while (true) {
                    int labelNum = 0;
                    tempBuffer.append(String.format("%d ",intLabel));
                    if (featureList.contains("accelmag.fft")) {
                        // calculate average of FFT of magnitude of accelerometers
                        // todo accept parameters for FFT calculation, for now just get it working

                        try {
                            //grab accel magnitude data
                            dataBlock[buffSize++] = accelSensorMagBuffer.take().doubleValue();

                            if (buffSize == accelFFTBuffSize) {
                                // buffer has been filled
                                buffSize = 0;

                                // calculate FFT
                                fft.fft(re,im);

                                // save fft values and labels to data to write
                                for (int i=labelNum;i<re.length;i++) {
                                    double mag = Math.sqrt(re[i]*re[i] + im[i]*im[i]);
                                    tempBuffer.append(String.format("%d:%f ",i+1,mag));
                                    im[i]= .0; // clear imaginary field
                                }
                                labelNum += re.length;
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }


                    }
                    if (featureList.contains("accelx.avg")) {
                        try {
                            //grab data
                            double tempValue = accelSensorXBuffer.take().doubleValue();
                            accelxavgdataBlock[accelxavgbuffSize++] = tempValue;
                            accelxavgsum += tempValue;
                            if (accelxavgbuffSize == accelBuffSize) {
                                // buffer has been filled
                                accelxavgbuffSize = 0;
                                // calculate average
                                tempValue = accelxavgsum / accelBuffSize;
                                tempBuffer.append(String.format("%d:%f ",labelNum+1,tempValue));
                                labelNum+=1;
                                }
                        } catch (InterruptedException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }

                    }
//                    if (featureList.contains("accely.avg")) {
//                        int buffSize = 0;
//                        double[] dataBlock = new double[accelBuffSize];
//                        double sum = 0;
//                        try {
//                            //grab data
//                            double tempValue = accelSensorYBuffer.take().doubleValue();
//                            dataBlock[buffSize++] = tempValue;
//                            sum += tempValue;
//                            if (buffSize == accelFFTBuffSize) {
//                                // buffer has been filled
//                                buffSize = 0;
//                                // calculate average
//                                tempValue = sum / accelFFTBuffSize;
//                                tempBuffer.append(String.format("%d:%f ",labelNum+1,tempValue));
//                                labelNum+=1;
//                            }
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                        }
//
//                    }
//                    if (featureList.contains("accelz.avg")) {
//                        int buffSize = 0;
//                        double[] dataBlock = new double[accelBuffSize];
//                        double sum = 0;
//                        try {
//                            //grab data
//                            double tempValue = accelSensorZBuffer.take().doubleValue();
//                            dataBlock[buffSize++] = tempValue;
//                            sum += tempValue;
//                            if (buffSize == accelFFTBuffSize) {
//                                // buffer has been filled
//                                buffSize = 0;
//                                // calculate average
//                                tempValue = sum / accelFFTBuffSize;
//                                tempBuffer.append(String.format("%d:%f ",labelNum+1,tempValue));
//                                labelNum+=1;
//                            }
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                        }
//                    }
//                    if (featureList.contains("accelmag.avg")) {
//                        int buffSize = 0;
//                        double[] dataBlock = new double[accelBuffSize];
//                        double sum = 0;
//                        try {
//                            //grab data
//                            double tempValue = accelSensorMagBuffer.take().doubleValue();
//                            dataBlock[buffSize++] = tempValue;
//                            sum += tempValue;
//                            if (buffSize == accelFFTBuffSize) {
//                                // buffer has been filled
//                                buffSize = 0;
//                                // calculate average
//                                tempValue = sum / accelFFTBuffSize;
//                                tempBuffer.append(String.format("%d:%f ",labelNum+1,tempValue));
//                                labelNum+=1;
//                            }
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                        }
//                    }  else {
//                        // there is a feature that is not yet implemented or not recognized, log error and stop
//                        Log.e("message","error with saving data, feature undefined: "+featureList.toString()+"%n allowed values: "+featuresAccepted);
//                    }

                    // Only write data of expected number of features reached
                    if (labelNum >= featureNum) {

                        if (action.equals(CLASSIFY_ACTION)) {
                            // if using data to classify, add to blocking queue
                            // Want to always replace buffer with latest
                            if (classifierFeaturesBuffer.offer(tempBuffer.toString())==false){
                                // if full, remove oldest value and add current buffer
                                try {
                                    classifierFeaturesBuffer.take();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                }
                            }
                            try {
                                classifierFeaturesBuffer.put(tempBuffer.toString());
                            } catch (InterruptedException e) {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }

                        } else {
                            tempBuffer.append(String.format("%n"));
                            // Once tempBuffer is built, add to dataToWrite
                            dataToWrite.append(tempBuffer);

                        }
                    }

                    //clear tempBuffer
                    if (tempBuffer.length()>0)
                        tempBuffer.delete(0,tempBuffer.length());
                }

            }

        return null;
        }


        @Override
        protected void onCancelled() {

            super.onCancelled();
            Log.d("BACKGROUND FEATURE TASK", "background task cancelled");
        }
    }

    public void stopDataCollect() {
        Log.d("AsyncTask", "stop data collection detected");

        saveDataTask.cancel(true);

        mSensorManager.unregisterListener(this);

        //if training, write data to file
        if (action == TRAIN_ACTION) {
            boolean writeResult = writeFile (dataCaptureFile.toString(),dataToWrite);
            if (writeResult) {
                // write successful, so clear dataToWrite for next round
                if (dataToWrite.length()>0)
                    dataToWrite.delete(0,dataToWrite.length());
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    IBinder mBinder = new LocalBinder();


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public FeatureCollectionService getServerInstance() {
            return FeatureCollectionService.this;
        }
    }

    @Override
    public void onDestroy() {
        // cancel async task when leaving service
        saveDataTask.cancel(true);
        // unregister listeners
        mSensorManager.unregisterListener(this);

        super.onDestroy();
    }


    public boolean writeFile(String outputFile, StringBuffer data) {

        boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;
        String SDCardState = Environment.getExternalStorageState();

        if (SDCardState.equals(Environment.MEDIA_MOUNTED)) {
            // We can read and write the media
            Log.i("WriteFile", "external media mounted");
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (SDCardState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            // We can only read the media
            Log.i("WriteFile", "external media mounted but read only");
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else if (SDCardState.equals(Environment.MEDIA_NOFS)) {
            Log.i("WriteFile","Error, the SDCard has format error");
            mExternalStorageAvailable = false;
        } else if (SDCardState.equals(Environment.MEDIA_REMOVED)) {
            Log.i("WriteFile","Error, the SDCard is removed");
            mExternalStorageAvailable = false;
        } else if (SDCardState.equals(Environment.MEDIA_SHARED)) {
            Log.i("WriteFile","Error, the SDCard is not mounted, used by USB");
            mExternalStorageAvailable = false;
        } else if (SDCardState.equals(Environment.MEDIA_UNMOUNTABLE)) {
            Log.i("WriteFile","Error, the SDCard could not be mounted");
            mExternalStorageAvailable = false;
        } else if (SDCardState.equals(Environment.MEDIA_UNMOUNTED)) {
            Log.i("WriteFile","Error, the SDCard is unmounted");
            mExternalStorageAvailable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            //  to know is we can neither read nor write
            Log.i("WriteFile", "external media mounted but read only");
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
        // If external storage is not available or writeable, write to local phone instead
        // Expecting full filename, default to be to external storage
        File fileToWrite = new File(outputFile);

        FileOutputStream outStream = null;
        if (mExternalStorageAvailable && mExternalStorageWriteable) {
            Log.d("WriteFile", "Writeable External Media found");
            // use external data storage on mobile device for saving training data for now

            try {
                // open file for append
                outStream = new FileOutputStream(fileToWrite, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        } else {
            Log.d("WriteFile", "Writing to internal file:" + data);

            try {
                // open file for append
                outStream = openFileOutput(fileToWrite.getName(), MODE_APPEND);
            } catch (FileNotFoundException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }
        // Use trainingFileName, assume this is passed in
        Log.d("WriteFile", "Writing to file: " + outputFile);
        boolean writeResultOK = false;

        try {

            outStream.write(data.toString().getBytes());
            Log.d("WriteFile", "wrote output file");
        } catch (FileNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                    Log.d("WriteFile", "closed output file");
                    writeResultOK = true;
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
        return writeResultOK;
    }

    //todo add code to handle joining / leaving requests
}
