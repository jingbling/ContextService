package org.jingbling.ContextEngine;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
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
    private String contextGroup;
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

    private TextView bCountDownText;
    private EditText bFilename;
    private long CountTimeRemaining = 60000;
    private int TrainingDurationMS = 60000;
    private int CountDownTickMS = 1000;
    private int TrainingBufferMS = 3000;
    private int dataCaptureFrequencyMS = 1000;
    private MyCountDown trainingCountdown = new MyCountDown(TrainingDurationMS, CountDownTickMS);

    private boolean dataCaptureFlag = false;

    private Bundle bundleFromService;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.data_collect);

        // retrieve values from service
        bundleFromService = getIntent().getExtras();
        if (bundleFromService != null) {
            featuresToUse = bundleFromService.getStringArrayList("features");
            contextLabels = bundleFromService.getStringArrayList("contextLabels");
            contextGroup = bundleFromService.getString("context");
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
        bStopTrainBtn.setClickable(false);

        bWriteFileBtn = (Button)findViewById(R.id.btnWriteFile);
        bWriteFileBtn.setOnClickListener(this);

        bTrainClassifierBtn = (Button)findViewById(R.id.btnTrainData);
        bTrainClassifierBtn.setOnClickListener(this);

        bExitBtn = (Button)findViewById(R.id.btnExitTrain);
        bExitBtn.setOnClickListener(this);

        bCountDownText = (TextView)findViewById(R.id.CountDownTxt);
        bFilename  = (EditText)findViewById(R.id.trained_filename);

        // Setup data collection service intent

        dataCollectIntent = new Intent(this, FeatureCollectionService.class);
    }




    @Override
    public void onClick(View v) {
        switch ( v.getId() ) {
            case R.id.btnStartTrain:
                // Clear data buffer
                if (dataToWrite.length()>0)
                    dataToWrite.delete(0,dataToWrite.length());

                trainingCountdown.start();
                dataCaptureFlag = true;

                // grab training filename to write
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/ContextServiceFiles/"+contextGroup+"/");
                dir.mkdirs();
                trainingFileName = dir.toString()+bFilename.getText().toString();

                // put desired inputs into bundle
                Bundle extras = new Bundle();
                extras.putInt("labelID", bContextToTrainSelection.getSelectedItemPosition() - 1);
                extras.putString("labelName", bContextToTrainSelection.getSelectedItem().toString());
                extras.putStringArrayList("features",featuresToUse);
                extras.putString("filename",trainingFileName);
                extras.putString("action", "training");

                // todo Use feature server to save desired features to specified file for training
                dataCollectIntent.putExtras(extras);
                startService(dataCollectIntent);

                bStopTrainBtn.setClickable(true);

                break;
            case R.id.btnAbortTrain:
                // Stop timer and clear countdown text
                dataCaptureFlag = false;
                trainingCountdown.cancel();
                bCountDownText.setText("0");
                CountTimeRemaining = 0;
                // stop feature collection server
                stopService(dataCollectIntent);

                bStopTrainBtn.setClickable(false);
                break;
            case R.id.btnWriteFile:

                //todo move this section to collectionservice and replace with option of deleting data file
//                // grab training filename to write
//                sdCard = Environment.getExternalStorageDirectory();
//                dir = new File(sdCard.getAbsolutePath() + "/ContextServiceFiles/"+contextGroup+"/");
//                dir.mkdirs();
//                trainingFileName = dir.toString()+bFilename.getText().toString();

//                trainingFileName = bFilename.getText().toString();
//                boolean result=false;
//                result = writeFile(trainingFileName,dataToWrite);
//
//                if (result==false) {
//                    // file write failed, set trainingFileName to null
//                    trainingFileName=null;
//                }

                break;

            case R.id.btnTrainData:
                //todo decide where to implement train data - for now train LibSVM only from this activity
                // Open a file explorer to choose training data file     ??? TO BE ADDED
//
//                //Instantiate class for training and saving libSVM model for now ???
                LearningServer newServer = new LearningServer();
                try {
                    // create a model file name in LibSVM directory, named same thing as trainingFileName
                    sdCard = Environment.getExternalStorageDirectory();
                    dir = new File(sdCard.getAbsolutePath() + "/ContextServiceModels/LibSVM/");
                    dir.mkdirs();
                    modelFileName =  dir.toString()+bFilename.getText().toString()+".model";
                    newServer.runSVMTraining(trainingFileName, modelFileName);
                    Toast.makeText(getApplicationContext(),
                            "Wrote model: "+modelFileName,
                            Toast.LENGTH_LONG).show();
                    Log.i("TRAIN_BTN", "writing model: "+modelFileName);

                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } finally {
                    // todo add messages back to service or save link to newly created model file?
                }

                break;
            case R.id.btnExitTrain:
                dataCaptureFlag = false;

                // Add information into message bundle to return to calling service
                // At end of call, pass classified context back to calling application
                if (bundleFromService != null) {
                    Messenger messenger = (Messenger) bundleFromService.get("SERVICE_MESSENGER");
                    Message msg = Message.obtain();
                    Bundle returnBundle = new Bundle();
                    // For reference, need to return table of index values to label as well
                    returnBundle.putString("trainingFile", trainingFileName);
                    returnBundle.putBoolean("trainingFinished", false);
//                    returnBundle.putString("labelHash",classLabelHashmap);
//                    returnBundle.putString("filename",classLabelHashmap);
                    msg.setData(returnBundle);
                    try {
                        messenger.send(msg);
                    } catch (android.os.RemoteException e1) {
                        Log.w(getClass().getName(), "Exception sending message", e1);
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
            dataCaptureFlag = false;
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