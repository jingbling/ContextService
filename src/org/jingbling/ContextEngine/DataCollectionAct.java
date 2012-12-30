package org.jingbling.ContextEngine;

import android.app.Activity;
import android.content.Context;
import android.hardware.SensorManager;
import android.os.*;
import android.os.Process;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

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
    private String trainingFileName;
    private StringBuffer dataToWrite;

    private Spinner bContextToTrainSelection;
    private Button bStartTrainBtn;
    private Button bStopTrainBtn;
    private Button bExitBtn;

    private TextView bCountDownText;
    private long CountTimeRemaining = 30000;
    private int TrainingDurationMS = 30000;
    private int CountDownTickMS = 1000;
    private int TrainingBufferMS = 3000;
    private int dataCaptureFrequencyMS = 1000;
    private MyCountDown trainingCountdown = new MyCountDown(TrainingDurationMS, CountDownTickMS);

    private SensorManager mSensorManager;
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
        // To BE ADDED - database lookup for what context labels are in the context group provided.  For now, hardcode
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

        bExitBtn = (Button)findViewById(R.id.btnExitTrain);
        bExitBtn.setOnClickListener(this);

        bCountDownText = (TextView)findViewById(R.id.CountDownTxt);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }


    @Override
    public void onClick(View v) {
        switch ( v.getId() ) {
            case R.id.btnStartTrain:

//                Toast.makeText(getApplicationContext(),
//                    "Starting ",
//                    Toast.LENGTH_LONG).show();
                saveTrainingData();
                break;
            case R.id.btnAbortTrain:
                // Stop timer and clear temporary buffer
                trainingCountdown.cancel();
                bCountDownText.setText("0");
                break;
            case R.id.btnExitTrain:
                // Open and write contents of saved data to file

                //Finally, close training window
                android.os.Process.killProcess(Process.myPid());
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
//            bCountDownText.setText("done!");
        }
    }

    public void saveTrainingData() {
        // Look at features to use to determine which sensors to get data from
//        Sensor accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // kick off countdown timer, then wait a few seconds to allow for user to start activity before recording
        trainingCountdown.start();

//        try {
//            Thread.sleep(TrainingBufferMS);
//        } catch (InterruptedException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }

        dataCaptureFlag = true;
        // Record data while countdown is still going based on data capture frequency
//        while (CountTimeRemaining > TrainingBufferMS) {
//            try {
//                Thread.sleep(dataCaptureFrequencyMS);
//            } catch (InterruptedException e) {
//                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//            }
//            Toast.makeText(getApplicationContext(),
//                    "Captured Data Point: ",
//                    Toast.LENGTH_LONG).show();
//        }
        // stop recording 5 seconds before end of training time to minimize transient data recorded
        dataCaptureFlag = false;

    }
}