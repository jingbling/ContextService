package org.jingbling.ContextEngine;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/28/12
 * Time: 2:58 PM
 * This activity gives the user an interface for generating a training data set
 *  For now have a set sampling rate
 */
public class DataCollectionAct extends Activity implements View.OnClickListener{
    private String[] featuresToUse;
    private String contextGroup;
    private String[] contextLabels;
    private String dir;
    private String trainingFileName;
    private String modelFileName;
    private StringBuffer dataToWrite = new StringBuffer("");

    private Spinner bContextToTrainSelection;
    private Button bStartTrainBtn;
    private Button bStopTrainBtn;
    private Button bWriteFileBtn;
    private Button bTrainClassifierBtn;
    private Button bExitBtn;

    private TextView bCountDownText;
    private EditText bFilename;
    private long CountTimeRemaining = 30000;
    private int TrainingDurationMS = 30000;
    private int CountDownTickMS = 1000;
    private int TrainingBufferMS = 3000;
    private int dataCaptureFrequencyMS = 1000;
    private MyCountDown trainingCountdown = new MyCountDown(TrainingDurationMS, CountDownTickMS);

    private static SensorManager mSensorManager;
    private Sensor accelSensor;
    private boolean dataCaptureFlag = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.data_collect);

        // retrieve values
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            featuresToUse = extras.getStringArray("features");
            contextGroup = extras.getString("context");
            trainingFileName = extras.getString("filename");
        }

        // Initialize context to train selection
        // ***To BE ADDED - database lookup for what context labels are in the context group provided.  For now, hardcode
        // these values for testing basic functionality
        if (contextGroup.toLowerCase().equals("activity")) {

            contextLabels = new String[3];
            contextLabels[0] = "standing";
            contextLabels[1] = "running";
            contextLabels[2] = "walking";
        }

        bContextToTrainSelection = (Spinner) findViewById(R.id.context_to_train);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(this,
                android.R.layout.simple_spinner_item, contextLabels);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        bContextToTrainSelection.setAdapter(adapter);


        // Initialize buttons

        bStartTrainBtn = (Button)findViewById(R.id.btnStartTrain);
        bStartTrainBtn.setOnClickListener(this);

        bStopTrainBtn = (Button)findViewById(R.id.btnAbortTrain);
        bStopTrainBtn.setOnClickListener(this);

        bWriteFileBtn = (Button)findViewById(R.id.btnWriteFile);
        bWriteFileBtn.setOnClickListener(this);

        bTrainClassifierBtn = (Button)findViewById(R.id.btnTrainData);
        bTrainClassifierBtn.setOnClickListener(this);

        bExitBtn = (Button)findViewById(R.id.btnExitTrain);
        bExitBtn.setOnClickListener(this);

        bCountDownText = (TextView)findViewById(R.id.CountDownTxt);
        bFilename  = (EditText)findViewById(R.id.trained_filename);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Look at features to use to determine which sensors to get data from
        // ***To BE ADDED: Will need to call a function when database is implemented - for now hardcode:
        accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (accelSensor != null) {
            mSensorManager.registerListener(mySensorEventListener, accelSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            Log.i("SENSOR_CREATE", "Registered Accel Sensor");

        } else {
            Log.e("SENSOR_CREATE", "could not find accel Sensor");
            Toast.makeText(this, "Accel Sensor not found",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }


    private SensorEventListener mySensorEventListener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // First check if data capture flag indicates that we should be capturing
            float tempValue1;
            float tempValue2;
            float tempValue3;

            if (dataCaptureFlag==true) {
//                Log.i("SENSOR_ONCHANGE", "dataCaptureFlag=true");
                // Save sensor data to buffer before writing to file
                tempValue1=event.values[0];
                tempValue2=event.values[1];
                tempValue3=event.values[2];
//                dataToWrite.append(String.format("accelx:%f,accely:%f,accelz:%f,label:%s%n",tempValue1,tempValue2,tempValue3,bContextToTrainSelection.getSelectedItem().toString()));
                dataToWrite.append(String.format("%d 1:%f 2:%f 3:%f%n",bContextToTrainSelection.getSelectedItemPosition()-1,tempValue1,tempValue2,tempValue3));
//                Log.i("SENSOR_ONCHANGE", "dataToWrite = "+dataToWrite.toString());
            }


        }
    };


    @Override
    public void onClick(View v) {
        switch ( v.getId() ) {
            case R.id.btnStartTrain:
                // Clear data buffer
                if (dataToWrite.length()>0)
                    dataToWrite.delete(0,dataToWrite.length());

                trainingCountdown.start();
                dataCaptureFlag = true;
                break;
            case R.id.btnAbortTrain:
                // Stop timer and clear countdown text
                dataCaptureFlag = false;
                trainingCountdown.cancel();
                bCountDownText.setText("0");
                CountTimeRemaining = 0;
                break;
            case R.id.btnWriteFile:
                // grab training filename to write
                trainingFileName = bFilename.getText().toString();
                // Open and write contents of saved data to file
                // First check that external storage is available
                boolean mExternalStorageAvailable = false;
                boolean mExternalStorageWriteable = false;
                String SDCardState = Environment.getExternalStorageState();

                if (SDCardState.equals(Environment.MEDIA_MOUNTED)) {
                    // We can read and write the media
                    Log.i("WRITE_BTN", "external media mounted");
                    mExternalStorageAvailable = mExternalStorageWriteable = true;
                } else if (SDCardState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                    // We can only read the media
                    Log.i("WRITE_BTN", "external media mounted but read only");
                    mExternalStorageAvailable = true;
                    mExternalStorageWriteable = false;
                } else if (SDCardState.equals(Environment.MEDIA_NOFS)) {
                    Log.i("WRITE_BTN","Error, the SDCard has format error");
                    mExternalStorageAvailable = false;
                } else if (SDCardState.equals(Environment.MEDIA_REMOVED)) {
                    Log.i("WRITE_BTN","Error, the SDCard is removed");
                    mExternalStorageAvailable = false;
                } else if (SDCardState.equals(Environment.MEDIA_SHARED)) {
                    Log.i("WRITE_BTN","Error, the SDCard is not mounted, used by USB");
                    mExternalStorageAvailable = false;
                } else if (SDCardState.equals(Environment.MEDIA_UNMOUNTABLE)) {
                    Log.i("WRITE_BTN","Error, the SDCard could not be mounted");
                    mExternalStorageAvailable = false;
                } else if (SDCardState.equals(Environment.MEDIA_UNMOUNTED)) {
                    Log.i("WRITE_BTN","Error, the SDCard is unmounted");
                    mExternalStorageAvailable = false;
                } else {
                    // Something else is wrong. It may be one of many other states, but all we need
                    //  to know is we can neither read nor write
                    Log.i("WRITE_BTN", "external media mounted but read only");
                    mExternalStorageAvailable = mExternalStorageWriteable = false;
                }
                // If external storage is not available or writeable, write to local phone instead
                FileOutputStream outStream = null;
                if (mExternalStorageAvailable && mExternalStorageWriteable) {
                    Toast.makeText(getApplicationContext(),
                            "Writeable External Media found",
                            Toast.LENGTH_LONG).show();
                    // ???TO MODIFY: use external data storage on mobile device for saving training data for now
                    File sdCard = Environment.getExternalStorageDirectory();
                    File testdir = new File(sdCard.getAbsolutePath() + "/ContextServiceFiles/" + contextGroup);
                    testdir.mkdirs();
                    File file = new File(testdir, trainingFileName);
                    dir = testdir.toString()+"/";

                    try {
                        // open file for append
                        outStream = new FileOutputStream(file, true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }

                } else {
                    Log.i("WRITE_BTN", "Writing to internal file:" + dataToWrite);

                    try {
                        // open file for append
                        outStream = openFileOutput(trainingFileName, MODE_APPEND);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    dir = "";
                }
                // Use trainingFileName, assume this is passed in
                Toast.makeText(getApplicationContext(),
                        "Writing to file: " + trainingFileName,
                        Toast.LENGTH_LONG).show();

                try {
                    //                    outStream = new FileOutputStream(fileToWrite);
                    outStream.write(dataToWrite.toString().getBytes());
                    Log.i("WRITE_BTN", "wrote output file");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } finally {
                    if (outStream != null) {
                        try {
                            outStream.close();
                            Log.i("WRITE_BTN", "closed output file");
                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }
                break;

            case R.id.btnTrainData:
                // Open a file explorer to choose training data file     ??? TO BE ADDED

                //Instantiate class for training and saving libSVM model for now ???
                LearningServer newServer = new LearningServer();
                try {
                    modelFileName = newServer.runSVMTraining(dir+trainingFileName);
                    Toast.makeText(getApplicationContext(),
                            "Wrote model: "+modelFileName,
                            Toast.LENGTH_LONG).show();
                    Log.i("TRAIN_BTN", "writing model: "+modelFileName);

                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }

                break;
            case R.id.btnExitTrain:
                dataCaptureFlag = false;

                //Finally, close training window and return to previous activity
//                android.os.Process.killProcess(Process.myPid());
                break;
        }
    }


    public class MyCountDown extends CountDownTimer{
        public MyCountDown(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }
        @Override
        public void onTick(long millisUntilFinished) {
            bCountDownText.setText("seconds remaining: " + millisUntilFinished / 1000);
            CountTimeRemaining = millisUntilFinished;
        }
        @Override
        public void onFinish() {
            bCountDownText.setText("0");
            dataCaptureFlag = false;
//            bCountDownText.setText("done!");
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accelSensor != null) {
            mSensorManager.unregisterListener(mySensorEventListener);
        }
    }

}