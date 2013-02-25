package org.jingbling.ContextEngine;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/28/12
 * Time: 2:58 PM
 * This activity gives the user an interface for generating a training data set
 *  For now have a set sampling rate
 */
public class DataCollectionAct extends Activity implements View.OnClickListener{
    private ArrayList<String> featuresToUse;
//    private String contextGroup;      todo no longer using contextGroup
    private ArrayList<String> contextLabels;
    private String trainingFileName;
    private String modelFileName;
    private StringBuffer dataToWrite = new StringBuffer();

    private Spinner bContextToTrainSelection;
    private Button bStartTrainBtn;
    private Button bStopTrainBtn;
    private Button bWriteFileBtn;
    private Button bTrainClassifierBtn;
    private Button bExitBtn;

    private Intent dataCollectIntent;
    private boolean serviceIsStarted = false;

    private TextView bCountDownText;
    private EditText bFilename;
    private long CountTimeRemaining = 60000;
    private int TrainingDurationMS = 60000;
    private int CountDownTickMS = 1000;
    private int TrainingBufferMS = 3000;
    private int dataCaptureFrequencyMS = 1000;
    private MyCountDown trainingCountdown = new MyCountDown(TrainingDurationMS, CountDownTickMS);

    // for message back to service
    private Messenger messengerToService;
    private Message msgToService;

    // for filesaving
    File sdCard;
    File dir;

    // put desired inputs into bundle
    private Bundle extras = new Bundle();
    private Bundle bundleFromService;
    private Bundle returnBundle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.data_collect);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // retrieve values from service
        bundleFromService = getIntent().getExtras();
        if (bundleFromService != null) {
            featuresToUse = bundleFromService.getStringArrayList("features");
            contextLabels = bundleFromService.getStringArrayList("contextLabels");
//            contextGroup = bundleFromService.getString("context");
            messengerToService = (Messenger) bundleFromService.get("SERVICE_MESSENGER");
            msgToService = Message.obtain();
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
        bStopTrainBtn.setEnabled(false);

//        bWriteFileBtn = (Button)findViewById(R.id.btnWriteFile);
//        bWriteFileBtn.setOnClickListener(this);

        bTrainClassifierBtn = (Button)findViewById(R.id.btnTrainData);
        bTrainClassifierBtn.setOnClickListener(this);

        bExitBtn = (Button)findViewById(R.id.btnExitTrain);
        bExitBtn.setOnClickListener(this);

        bCountDownText = (TextView)findViewById(R.id.CountDownTxt);
        bFilename  = (EditText)findViewById(R.id.trained_filename);
        bFilename.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                //To change body of implemented methods use File | Settings | File Templates.

                trainingFileName = dir.toString()+"/"+ bFilename.getText().toString();
            }
        });

        // Setup data collection service intent

        dataCollectIntent = new Intent(this, FeatureCollectionService.class);

        // Setup other common data
        // grab training filename to write
        sdCard = Environment.getExternalStorageDirectory();
        dir = new File(sdCard.getAbsolutePath() + "/ContextServiceFiles/inputs/");
        dir.mkdirs();

        extras.putInt("labelID", bContextToTrainSelection.getSelectedItemPosition() - 1);
        extras.putString("labelName", bContextToTrainSelection.getSelectedItem().toString());
        extras.putStringArrayList("features",featuresToUse);
        extras.putString("filename",trainingFileName);
        extras.putString("action", "training");
        returnBundle = new Bundle();

    }


    @Override
    public void onClick(View v) {
        switch ( v.getId() ) {
            case R.id.btnStartTrain:
                // Clear data buffer
                if (dataToWrite.length()>0)
                    dataToWrite.delete(0,dataToWrite.length());

                trainingCountdown.start();

                // todo Use feature server to save desired features to specified file for training
                dataCollectIntent.putExtras(extras);
                startService(dataCollectIntent);

                bStopTrainBtn.setEnabled(true);

                // Save training file information here - depending on what the last action is (training or data collecting)
                // will determine which file (model or training file, respectively) will be returned to service

                returnBundle.putString("fileType", "trainingData");
                returnBundle.putString("trainingFile", trainingFileName);
//                    returnBundle.putBoolean("trainingFinished", false);

                break;
            case R.id.btnAbortTrain:
                // Stop timer and clear countdown text
                trainingCountdown.cancel();
                bCountDownText.setText("0");
                CountTimeRemaining = 0;
                // stop feature collection server
                stopService(dataCollectIntent);

                bStopTrainBtn.setEnabled(false);
                break;

            case R.id.btnTrainData:
                // todo add check that filename to train exists
                File testfile = new File(trainingFileName);

                if(testfile.exists()) {

                    //todo decide where to implement train data - for now train LibSVM only from this activity
                    // Open a file explorer to choose training data file     ??? TO BE ADDED
                    Toast.makeText(getApplicationContext(),
                            "Writing model file: "+modelFileName,
                            Toast.LENGTH_LONG).show();
                    // stop feature collection server
                    stopService(dataCollectIntent);

                    // Run following in separate thread
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {

                            //                //Instantiate class for training and saving libSVM model for now ???
                            LearningServer newServer = new LearningServer();
                            try {
                                // create a model file name in LibSVM directory, named same thing as trainingFileName
                                File tempdir = new File(sdCard.getAbsolutePath() + "/ContextServiceModels/libsvm/");
                                tempdir.mkdirs();
                                modelFileName =  tempdir.toString()+"/"+bFilename.getText().toString()+".model";


                                newServer.runSVMTraining(trainingFileName, modelFileName);

                                Toast.makeText(getApplicationContext(),
                                        "Wrote model: "+modelFileName,
                                        Toast.LENGTH_LONG).show();
                                Log.i("TRAIN_BTN", "wrote model: "+modelFileName);

                            } catch (IOException e) {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            } finally {
                                // todo Send messages back to calling service with action "addModel" to indicate that we just
                                // trained a LibSVM model with the given features / contextGroup
                                returnBundle.putString("fileType", "model");
                                returnBundle.putString("algorithm","libsvm");
                                returnBundle.putString("modelFileName",modelFileName);
                                //                    returnBundle.putString("contextGroup", contextGroup);
                                returnBundle.putStringArrayList("features", featuresToUse);
                                returnBundle.putStringArrayList("labels",contextLabels);


                            }
                        }
                    };
                    r.run();

                } else {
                    // send error that training file not found
                    Toast.makeText(getApplicationContext(),
                            "Error, training file not found: "+trainingFileName,
                            Toast.LENGTH_LONG).show();
                }

                break;
            case R.id.btnExitTrain:
                // Send message with latest file information to service if available
                if (returnBundle != null) {
                    msgToService.setData(returnBundle);
                    try {
                        // handle leaving training immediately without doing anything
                        if (returnBundle.get("fileType")==null) {
                            returnBundle.putString("fileType","none");
                        }
                        messengerToService.send(msgToService);
                    } catch (android.os.RemoteException e1) {
                        Log.w(getClass().getName(), "Exception sending message from DataCollectionAct", e1);
                    }
                }
                //Finally, close training window and return to previous activity
                super.finish();
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
            //todo - automatically stop collection service after timer elapses
            stopService(dataCollectIntent);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}