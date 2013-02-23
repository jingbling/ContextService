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
    private ArrayList featureList;
    // define expected sensors to be used
    private Sensor accelSensor;
    private Sensor locationSensor;
    private Sensor orientSensor;
    // more sensors to be added

    private StringBuffer dataToWrite=new StringBuffer(); //temp buffer to store data to be written to file or to classify
    private File dataCaptureFile;   // file for saving data for training

    private int intLabel; //for libSVM, use integer labels
    private String stringLabel; //for other classifier algorithms, may want string labels
    private String action; //desired feature collection action - either collecting, or classifying

    //buffers for saving feature data
    private static ArrayBlockingQueue<Double> accelSensorXBuffer;
    private static ArrayBlockingQueue<Double> accelSensorYBuffer;
    private static ArrayBlockingQueue<Double> accelSensorZBuffer;
    private static ArrayBlockingQueue<Double> accelSensorMagBuffer;
    private static ArrayBlockingQueue<Double> locationDataBuffer;
    private static ArrayBlockingQueue<Double> orientDataBuffer;

    // for features calculation
    private int accelFFTBuffSize= 64; //number of points to collect for FFT calculation
    calculateFeatures saveDataTask=null;

    // for classifying and sending message back to service

    private Intent broadcastReturnIntent = new Intent("org.jingbling.ContextEngine.ContextService");
    private Bundle returnBundle = new Bundle();

    @Override
    public void onCreate() {
        //todo allocate buffer sizes - for now just do accelerometer data
        accelSensorXBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        accelSensorYBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        accelSensorZBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);
        accelSensorMagBuffer = new ArrayBlockingQueue<Double>(accelFFTBuffSize*2);

        saveDataTask = new calculateFeatures();
        super.onCreate();
//        android.os.Debug.waitForDebugger(); //todo TO BE REMOVED

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // grab info from bundle
        Bundle inputExtras = intent.getExtras();
        featureList = inputExtras.getStringArrayList("features");
        dataCaptureFile = new File(inputExtras.getString("filename"));
        intLabel = inputExtras.getInt("labelID");
        stringLabel = inputExtras.getString("labelName");
        action = inputExtras.getString("action");
//        messengerToService = (Messenger) inputExtras.get ("SERVICE_MESSENGER");
//        msgToService = Message.obtain();

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

    private class calculateFeatures extends AsyncTask<Void,Void,Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            // depending on the desired features, calculate and save to data buffer
            //Check each feature for desired values to save
            if (featureList.contains("accel.FFT")) {
                // calculate average of FFT
                // todo accept parameters for FFT calculation, for now just get it working
                int buffSize = 0;
                double[] dataBlock = new double[accelFFTBuffSize];
                double[] re = dataBlock;
                double[] im = new double[accelFFTBuffSize]; //want zero imaginary part

                FFT fft = new FFT(accelFFTBuffSize);

                // grab data from buffer - if no data, following while loop will block automatically
                while (true) {
                    try {
                        //grab accel magnitude data
                        dataBlock[buffSize++] = accelSensorMagBuffer.take().doubleValue();

                        if (buffSize == accelFFTBuffSize) {
                            // buffer has been filled
                            buffSize = 0;

                            // calculate FFT
                            fft.fft(re,im);

                            // save fft values and labels to data to write
                            // To avoid saving partial data, save to an intermediary loop before adding to dataToWrite
                            StringBuffer tempBuffer = new StringBuffer();
                            tempBuffer.append(String.format("%d ",intLabel));
                            for (int i=0;i<re.length;i++) {
                                double mag = Math.sqrt(re[i]*re[i] + im[i]*im[i]);
                                tempBuffer.append(String.format("%d:%f ",i+1,mag));
                                im[i]= .0; // clear imaginary field
                            }
                            tempBuffer.append(String.format("%n"));

                            // Once tempBuffer is built, add to dataToWrite
                            dataToWrite.append(tempBuffer);

                            //clear tempBuffer
                            if (tempBuffer.length()>0)
                                tempBuffer.delete(0,tempBuffer.length());

                            //if action is not to save item to file, send broadcast of data to service, otherwise append to buffer to write
                            if (action.equals("classify")) {
                                // todo broadcast dataToWrite and clear buffer
                                returnBundle.putString("fileType","datatolabel");
                                returnBundle.putString("inputData",dataToWrite.toString());
                                // Send message with latest file information to service
                                //                                msgToService.setData(returnBundle);
                                broadcastReturnIntent = new Intent("org.jingbling.ContextEngine.ContextService");
                                broadcastReturnIntent.putExtras(returnBundle);
                                sendBroadcast(broadcastReturnIntent);
//                                    messengerToService.send(msgToService);

                                if (dataToWrite.length()>0)
                                    dataToWrite.delete(0,dataToWrite.length());
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }

        return null;
        }

        @Override
        protected void onCancelled() {
            Log.d("AsyncTask", "Cancel detected");
            // check actions - if classifying, do not need to do anything but exit
            if (action.equals("classify")) {
                super.onCancelled();
                return;
            }

            //Otherwise, training, so need to write data:
            boolean writeResult = writeFile (dataCaptureFile.toString(),dataToWrite);
            if (writeResult) {
                // write successful, so clear dataToWrite for next round
                if (dataToWrite.length()>0)
                    dataToWrite.delete(0,dataToWrite.length());
            }

            super.onCancelled();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onDestroy() {
        // cancel async task when leaving service
        saveDataTask.cancel(true);
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
}
