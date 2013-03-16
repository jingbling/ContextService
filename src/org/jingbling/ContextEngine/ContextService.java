package org.jingbling.ContextEngine;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import libsvm.svm_model;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
    import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/27/12
 * Time: 11:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ContextService extends Service {

   // Some variables for defining request
    private String contextGroup = new String();
    private String classifierAlg = new String();
    private ArrayList<String> features =new ArrayList<String>();
    private ArrayList<String> contextLabels =new ArrayList<String>();
    private int requestedClassifierID;
    private int requestedClassifierPeriod;
    private String JSONDataFile="DataStructure.JSON";
    private static JSONArray JSONLabels = new JSONArray();
    private static JSONArray JSONFeatures = new JSONArray();
    private static JSONArray JSONAlgorithms = new JSONArray();


    private String trainingFileName;
    private ArrayList<String> classifiedModelFile;
    private JSONArray existingJSONClassifiers = new JSONArray();

    // Expected input labels and values
    public static String ACTION_KEY = "action";
    public static String INIT_ACTION = "init";
    public static String START_ACTION = "start";
    public static String TRAIN_ACTION = "train";
    public static String CLASSIFY_ACTION = "classify";
    public static String LABELS_KEY = "contextLabels";
    public static String FEATURES_KEY = "features";
    public static String ALGORITHM_KEY = "algorithm";
    public static String CLASSIFIER_ID_KEY = "classifierID";
    public static String LABEL_RATE_KEY = "rate";
    public static String LABELSID_KEY = "labelID";
    public static String TRAINING_FILE_KEY="trainingFile";
    public static String BUFFER_SIZE_KEY="bufferSize";

    // Expected output labels and values
    public static String OUTPUT_RETURNVAL_KEY = "return";
    public static String OUTPUT_MESSAGE_KEY = "message";
    static int INIT_RETURN_VALUE = 2;
    static int NOM_RETURN_VALUE = 0;
    static int MESSAGE_RETURN_VALUE = 1;
    static int CLASSIFIER_EXISTS_NOT_RUNNING = 3;
    static int CLASSIFIER_RUNNING = 4;

    // Create a hash map for each group of values that need to be looked up
    private HashMap featuresHashMap = new HashMap();
    private HashMap classifierAlgorithmHashMap = new HashMap();
    private HashMap runningClassModelHashMap = new HashMap();
    private HashMap classModelPeriodHashMap = new HashMap();
    private HashMap contextLabelsHashMap = new HashMap();
    private HashMap existingClassifiersHashMap = new HashMap();

    private Bundle outputBundle = new Bundle();
    public boolean activityRunning = false;
    private String action;
    private static ArrayBlockingQueue<String> dataBuffer;
    private static ArrayBlockingQueue<String> mailboxOut;
    private boolean runClassify=false;
    private Intent featureCollectIntent;
    boolean mBounded;
    FeatureCollectionService featureServer;

    private Handler timingHandler = new Handler();
    private long elapsedTime = 0;
//    MessageReceiver myReceiver = null;
    Boolean receiverRegistered = false;
    //TODO eventually add more svm_model models and respective hashmaps
    private svm_model currentSVMModel1;
    HashMap labelHash1 = new HashMap();
    private ArrayList<Integer> currentlyExecutingModels = new ArrayList<Integer>();

    static final int LAUNCH_DATACOLLECT = 1;

    final Messenger mMessenger = new Messenger(new IncomingHandler());
    private static final ScheduledExecutorService ScheduledWorker =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduleWorkerFuture;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            outputBundle.clear();
            // Initialize data arrays from assets file if there is no local file found
            try {
                InitJSONtoHashMap(JSONDataFile);
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            //After launching service, first parse JSON from intent
            Bundle inputExtras = msg.getData();

            // instead of JSON input, just parse strings
            action = inputExtras.getString(ACTION_KEY);
            contextLabels = inputExtras.getStringArrayList(LABELS_KEY);
            features = inputExtras.getStringArrayList(FEATURES_KEY);
            classifierAlg = inputExtras.getString(ALGORITHM_KEY);
            Messenger messenger = msg.replyTo;


            // if action is to initialize with allowable values, do this and return message.
            if (action.toLowerCase().equals(INIT_ACTION)) {
                // return allowable labels and features
                outputBundle.putInt(OUTPUT_RETURNVAL_KEY,INIT_RETURN_VALUE);
                // query allowable values and send to requesting app
                String[] tempArray;
                outputBundle.putStringArray("allowedFeatures",(String[]) featuresHashMap.keySet().toArray(new String[featuresHashMap.size()]));
                outputBundle.putStringArray("allowedLabels",(String[])contextLabelsHashMap.values().toArray(new String[contextLabelsHashMap.size()]));
                tempArray = (String[])classifierAlgorithmHashMap.keySet().toArray(new String[classifierAlgorithmHashMap.size()]);
                outputBundle.putStringArray("allowedAlgorithms", tempArray.clone());
                outputBundle.putString(OUTPUT_MESSAGE_KEY,"initialization variables sent");

                // send message then return
                sendMessageToClient(messenger, outputBundle);
                return;
            }


            // For any other action other than init, need to check inputs - throw error if no label or feature selected
            if (contextLabels.isEmpty() || features.isEmpty()) {
                outputBundle.putInt(OUTPUT_RETURNVAL_KEY, MESSAGE_RETURN_VALUE);
                outputBundle.putString(OUTPUT_MESSAGE_KEY,"Please select at least one label and feature");
                // send message then return
                sendMessageToClient(messenger, outputBundle);
                return;
            }

            // For remaining actions, check for existing classifier
            // Check for existing model
            try {
                classifiedModelFile = findClassifierMatch(features, classifierAlg, contextLabels, existingJSONClassifiers);
            } catch (JSONException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            if (action.toLowerCase().equals(CLASSIFY_ACTION)) {
                // todo add check for classifier IDs currently running, and add save of which classifier IDs are running - need separate thread?
                // check if there is a requested ID, if -1, then check for existing classifier
                requestedClassifierID = inputExtras.getInt(CLASSIFIER_ID_KEY);
                requestedClassifierPeriod = inputExtras.getInt(LABEL_RATE_KEY);
                if (requestedClassifierID<0) {

                    if (classifiedModelFile == null) {

                        // Do not automatically launch activity - instead, return a message telling application to request training activity
                        // define output bundle
                        outputBundle.putInt(OUTPUT_RETURNVAL_KEY, MESSAGE_RETURN_VALUE);
                        outputBundle.putString(OUTPUT_MESSAGE_KEY,"No classifier found, please run training");
                        // send message and return
                        sendMessageToClient(messenger, outputBundle);
                        return;


                    } else {
                        // Otherwise, there is an existing model - check if it is running
                        int currentID = Integer.parseInt(existingClassifiersHashMap.get(classifiedModelFile.get(0)).toString());
                        if (runningClassModelHashMap.containsKey(currentID)){
                            // classifier requested is already being run - check that the current period is fast enough
                            if (Integer.parseInt(classModelPeriodHashMap.get(currentID).toString())<requestedClassifierPeriod) {
                                classModelPeriodHashMap.put(currentID,requestedClassifierPeriod);
                            }
                            // send message that classifier already running
                            outputBundle.putInt(OUTPUT_RETURNVAL_KEY, CLASSIFIER_RUNNING);
                            outputBundle.putInt(CLASSIFIER_ID_KEY, currentID);
                            outputBundle.putString(OUTPUT_MESSAGE_KEY,"Classifier already running");
                            // send message then return
                            sendMessageToClient(messenger, outputBundle);
                            return;


                        } else {
                            // Not yet running, return message saying classifier exists but needs to be started
                            outputBundle.putInt(OUTPUT_RETURNVAL_KEY, CLASSIFIER_EXISTS_NOT_RUNNING);
                            outputBundle.putInt(CLASSIFIER_ID_KEY, currentID);
                            outputBundle.putString(OUTPUT_MESSAGE_KEY,"Please start classifier");
                            // send message then return
                            sendMessageToClient(messenger, outputBundle);
                            return;
                        }
                    }
                } else { // classifier ID given so return latest label
                    //todo add options for blocking vs nonblocking - for now, non-blocking for latest value
                    // todo add check that classifierID is valid

                    // send a message with latest label
                    outputBundle.putInt(OUTPUT_RETURNVAL_KEY, NOM_RETURN_VALUE);
                    outputBundle.putString(LABELS_KEY, mailboxOut.peek());
                    // send message then return
                    sendMessageToClient(messenger, outputBundle);
                    return;
                }
            } else if (action.equals(START_ACTION)) {
                // run initialization of classifier
                requestedClassifierID = inputExtras.getInt(CLASSIFIER_ID_KEY);
                requestedClassifierPeriod = inputExtras.getInt(LABEL_RATE_KEY);

                // start service for grabbing feature data for classifier input
                Bundle extras = new Bundle();
                extras.putInt(LABELSID_KEY, -99); // chose a bogus value as this is not needed for classifying
                extras.putString(LABELS_KEY, "blah");  // chose a bogus value as this is not needed for classifying
                extras.putStringArrayList(FEATURES_KEY,features);
                extras.putString(TRAINING_FILE_KEY,"notNeeded");
                extras.putString(ACTION_KEY, CLASSIFY_ACTION);
                // todo determine the desired mailbox size on feature collection side, but for now fix at size 2
                extras.putInt(BUFFER_SIZE_KEY, 2);

                // Start feature collection service
                featureCollectIntent.putExtras(extras);

                startService(featureCollectIntent);
                Runnable classify = new ClassifyContext(requestedClassifierID,requestedClassifierPeriod,contextLabels, mailboxOut);
//                new Thread(classify).start();
                scheduleWorkerFuture = ScheduledWorker.scheduleWithFixedDelay(classify, requestedClassifierPeriod, requestedClassifierPeriod, TimeUnit.MILLISECONDS);

                // send a message that model has been loaded
                outputBundle.putInt(OUTPUT_RETURNVAL_KEY, CLASSIFIER_RUNNING);
                outputBundle.putString(OUTPUT_MESSAGE_KEY,"Classifier Started");
                // send message then return
                sendMessageToClient(messenger, outputBundle);
                return;

            } else if(action.equals(TRAIN_ACTION)){ //action = train
                //check for valid inputs - if no labels or features selected, exit and return an error

                //no classifier found, launch activity to train model
                saveTrainingData(features, contextLabels);

//
//                // if training success (model file not null), return message
//                outputBundle.putInt(OUTPUT_RETURNVAL_KEY, MESSAGE_RETURN_VALUE);
//                outputBundle.putString(OUTPUT_MESSAGE_KEY,"left training activity");


    //            //todo automatically train data based on training file created
    //            if (trainingFileName == null) {
    //                // todo training file was not created correctly, return error
    //                outputBundle.putInt(OUTPUT_RETURNVAL_KEY, 1);
    //                outputBundle.putString("error", "No training file found");
    //                classifiedModelFile = null;
    //            } else {
    //                // train model and then save trained model
    //                // todo create more generic model training function
    //                LearningServer trainModel = new LearningServer();
    //                File sdCard = Environment.getExternalStorageDirectory();
    //                File dir = new File(sdCard.getAbsolutePath() + "/ContextServiceModels/LibSVM");
    //                dir.mkdirs();
    //                classifiedModelFile = dir.toString()+"/ContextServiceModels/"+classifier+"/Classifier"+lookupID+".model";
    //                Log.v("TrainModel", "writing model file: "+ classifiedModelFile);
    //
    //                try {
    //                    trainModel.runSVMTraining(trainingFileName,classifiedModelFile);
    //                } catch (IOException e) {
    //                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    //                }
    //
    //                // classifiedModelFile created, update hashmap
    //                classifierAlgorithmHashMap.put(lookupID,classifiedModelFile);
    //            }

            }
        }
    }


    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        featureCollectIntent= new Intent("org.jingbling.ContextEngine.FeatureCollectionService");
        // also bind to feature service to be able to call method for obtaining input vector
        bindService(featureCollectIntent, featureConnection, BIND_AUTO_CREATE);
    }


    @Override
    public void onDestroy() {
        //todo Before exiting, write hashmap to file
        Log.d("CONTEXTSERVICE","ON DESTROY METHOD CALLED");
        runClassify = false;
        // stop threads
        scheduleWorkerFuture.cancel(true);
        ScheduledWorker.shutdown();
    }


    ServiceConnection featureConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            mBounded = false;
            featureConnection = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            mBounded = true;
            FeatureCollectionService.LocalBinder mLocalBinder = (FeatureCollectionService.LocalBinder)service;
            featureServer = mLocalBinder.getServerInstance();
        }
    };

    public class ClassifyContext implements Runnable  {
        private LearningServer classifierServer;
        private ArrayList<String> contextLabels;
        private long period;
        private long duration;
        private ArrayBlockingQueue<String> mailbox;
        private long elapsedTime;
        private final Handler classHandler = new Handler();

        public ClassifyContext(int classifierID, int desiredPeriod, ArrayList<String> contextLabels, ArrayBlockingQueue<String> mailbox) {
            // start classifier
            this.contextLabels = contextLabels;
            this.mailbox = mailbox;
            //Add values to hashmaps
            classModelPeriodHashMap.put(classifierID,desiredPeriod);

            this.period = (long) desiredPeriod;
            this.duration = 60000; // todo for testing purposes only, define a max timeout value
            int sizeBuffer = 2; //todo - for now set data buffer size to 2, will need to come up with smarter way of determining from inputs / classifier

            // initialize buffers for holding input data to classify and output
            dataBuffer = new ArrayBlockingQueue<String>(sizeBuffer);
            // initialize mailbox for current labels
            mailboxOut = new ArrayBlockingQueue<String>(1);

            // set flag to indicate classification should start
            runClassify = true;
            this.elapsedTime = 0;

            // First set up model before running in a loop
            this.classifierServer = new LearningServer();
            if (classifierAlg.toLowerCase().equals("libsvm")) {
                // load model from file

                try {
                    currentSVMModel1 = classifierServer.loadSVMModel(classifiedModelFile.get(0));
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
            // save current model to hashmap for id lookup
            runningClassModelHashMap.put(classifierID, currentSVMModel1);
            // also save hashmap of labels, after sorting contextLabels alphabetically
            Collections.sort(contextLabels, String.CASE_INSENSITIVE_ORDER);
            for (int i=0;i<contextLabels.size();i++) {
                labelHash1.put(i-1,contextLabels.get(i));
            }
        }
        public void run(){

//            classHandler.postDelayed(this, this.period);

            while (runClassify) {

                // if we have not yet reached end time, wait a delay
//                if (this.elapsedTime<duration){
//                    this.elapsedTime+=period;
//                    try {
//                        HandlerThread.sleep(period);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                    }
//                } else {
//                    // max duration reached, end classifying
//                    runClassify = false;
//                    Log.d("CLASSIFY_THREAD", "completed thread, max duration reached");
//                }

                Log.d("CONTEXT_SERVICE_RUN_CLASSIFY", "in while loop...");
                // while the duration to run the classifier has not completed, grab input data from feature collection service
                // and classify and return depending on frequency.
                // Run classifier model to determine label
                String classifiedLabel = new String();
                classifiedLabel = "";

                if (classifierAlg.toLowerCase().equals("libsvm")) {

                    if (featureServer == null) {
                        // feature was not set up correctly, log error
                        Log.d("CONTEXT_SERVICE_RUN_CLASSIFY","feature server not bound");
                    } else {
                        // todo Only run classifier once every desired period - add timer here?
                        // todo add lookup of which models to run and iterate through list - for now just run the first
                        String inputString = null;
                        try {
                            inputString = featureServer.getFeatureBufferData();
                        } catch (InterruptedException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        if (inputString == null) {
                            Log.d("CONTEXT_SERVICE_RUN_CLASSIFY", "ERROR, no input to classify");
                        } else {
                            try {
                                classifiedLabel = classifierServer.evaluateSVMModel(inputString, labelHash1, currentSVMModel1);
                                Log.d("CONTEXT_SERVICE_RUN_CLASSIFY", "classifierLabel = "+classifiedLabel);
                            } catch (IOException e) {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                    }
                }

                // add label to mailbox - check to see if mailbox is full, if so, update with latest values
                if (classifiedLabel.equals("")==false) {
                    Log.d("CLASSIFY_THREAD", "trying to add label to mailbox");
                    if (mailboxOut.offer(classifiedLabel)==false) {
                        Log.d("CLASSIFY_THREAD", "trying to add label to mailbox, but full, so clear and try again");
                        mailboxOut.clear();
                        try {
                            mailboxOut.put(classifiedLabel);
                        } catch (InterruptedException e) {
                            Log.d("MAILBOX_FUNCTION", "failed to add label to mailbox");
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                    Log.d("CLASSIFY_THREAD", "added label to mailbox");
                }
            }
        }

    }

    public ArrayList<String> findClassifierMatch (ArrayList<String> features, String classifierAlgorithm, ArrayList<String> labels, JSONArray inputJSONArray) throws JSONException {
        // This function takes in requested inputs and finds a matching classifier to run
        // If a matching classfier(s) is(are) found, they will be returned in a string arraylist.  Otherwise a null ArrayList is returned

        // First, parse JSON Array for desired inputs
        String FEATURES_KEY = "features";
        String LABELS_KEY = "labels";
        String ALG_KEY = "classifierAlgorithm";
        String FILE_KEY = "filename";
        String ID_KEY = "id";
        int numMatches = 0;
        ArrayList<String> matchOutput =  new ArrayList<String>();

        // Cycle through each array entry and look for exact match of all inputs
        for (int i = 0; i<inputJSONArray.length(); i++) {
            if (classifierAlgorithm.toLowerCase().equals(inputJSONArray.getJSONObject(i).getString(ALG_KEY).toLowerCase())) {
                // now check that the sorted labels match between input and classifier object
                Collections.sort(labels);
                // Parse JSONArray into an ArrayList
                JSONArray jsonArrayLabel = inputJSONArray.getJSONObject(i).getJSONArray(LABELS_KEY);
                ArrayList<String> labelArrayList = new ArrayList<String>();
                // for the two arrays to be equal,
                for (int j=0; j<jsonArrayLabel.length(); j++) {
                    labelArrayList.add(j, jsonArrayLabel.get(j).toString().toLowerCase());
                }
                Collections.sort(labelArrayList);

                if (labels.equals(labelArrayList)) {
                    // Continue in same fashion to check the features array
                    // now check that the sorted labels match between input and classifier object
                    Collections.sort(features);
                    // Parse JSONArray into an ArrayList
                    JSONArray jsonArrayFeat = inputJSONArray.getJSONObject(i).getJSONArray(FEATURES_KEY);
                    ArrayList<String> featArrayList = new ArrayList<String>();
                    for (int count=0; count<jsonArrayFeat.length(); count++) {
                        featArrayList.add(count, jsonArrayFeat.get(count).toString().toLowerCase());
                    }
                    Collections.sort(featArrayList);
                    if (features.equals(featArrayList)) {
                        // all aspects match, add filename to output and classifier ID to hashmap and increment counter
                        matchOutput.add(numMatches,inputJSONArray.getJSONObject(i).getString(FILE_KEY));
                        existingClassifiersHashMap.put(inputJSONArray.getJSONObject(i).getString(FILE_KEY),inputJSONArray.getJSONObject(i).getInt(ID_KEY));
                        //
                        numMatches++;
                    }
                }
            }
        }
        // check for any matches, otherwise need to return null
        if (numMatches > 0) {
            return matchOutput;
        } else {
            return null;
        }
    }
    public void InitJSONtoHashMap (String inputJSONFile) throws IOException {

        JSONObject inputJSON = null;
        try {
            inputJSON = parseJSONFromFile(inputJSONFile);
        } catch (JSONException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        // Parse JSON to establish data mappings for lookups
        try {

            JSONLabels = inputJSON.getJSONArray("labels");
            for (int i=0;i<JSONLabels.length();i++) {
                contextLabelsHashMap.put(JSONLabels.getJSONObject(i).getInt("id"),JSONLabels.getJSONObject(i).getString("name"));
            }

            JSONAlgorithms = inputJSON.getJSONArray("classifierAlgorithm");
            for (int i=0;i<JSONAlgorithms.length();i++) {
                // Reverse the order for classifier Models, as lookup will be usually be performed with id
                classifierAlgorithmHashMap.put(JSONAlgorithms.getJSONObject(i).getString("name"), JSONAlgorithms.getJSONObject(i).getInt("id"));
            }

            JSONFeatures = inputJSON.getJSONArray("features");
            for (int i=0;i<JSONFeatures.length();i++) {
                featuresHashMap.put(JSONFeatures.getJSONObject(i).getString("name"),JSONFeatures.getJSONObject(i).getInt("id"));
            }

            existingJSONClassifiers = inputJSON.getJSONArray("classifier");
//
//            tempObject = inputJSON.getJSONArray("classifier");
//            for (int i=0;i<tempObject.length();i++) {
//                // Reverse the order for classifier Models, as lookup will be usually be performed with id
//                classModel.put(tempObject.getJSONObject(i).getInt("id"),tempObject.getJSONObject(i));
//            }

        } catch (JSONException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public JSONObject parseJSONFromFile (String inputJSONFile) throws JSONException, IOException {

        String jString = null;
        InputStream istream = null;
        // Parse expected elements from JSON file into a JSON Object
        try {
//          // first try opening private file - if doesn't exist, open default assets file
            File checkfile = new File(getBaseContext().getFilesDir()+"/"+JSONDataFile);
            boolean exists = checkfile.exists();
            try {
                istream = new BufferedInputStream(new FileInputStream(checkfile));
                Log.d("JSON_Writer", "internal file found and being used");

            } catch (IOException e) {
                Log.d("JSON_Writer", "No internal file found, opening read-only JSONDataFile");
                istream = getResources().getAssets().open(inputJSONFile);
            }

            Writer writer = new StringWriter();
            char[] buffer = new char[2048];

            Reader reader = new BufferedReader(new InputStreamReader(istream,
                    "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }

            jString = writer.toString();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (istream != null)
                istream.close();
        }

        if (jString != null) {

            JSONObject outputJSONObj = new JSONObject(jString);
            Log.d("parseJSONFromFile","SUCCESS parsing JSON from file");
            return outputJSONObj;
        } else {
            Log.w("parseJSONFromFile","ERROR parsing JSONObject from file");
            return null;
        }
    }

    public void writeJSONtoFile () {
        //todo Utility function to save internal data in form of JSON Object into a file
        // Save current state of features to internally kept JSON file
        JSONObject newJSONData = new JSONObject();
        try {
            newJSONData.put("features", JSONFeatures);
            newJSONData.put("labels", JSONLabels);
            newJSONData.put("classifierAlgorithm", JSONAlgorithms);
            newJSONData.put("classifier", existingJSONClassifiers);
        } catch (JSONException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


        try {
            // Write new file locally and update JSONDataFile to match
            FileOutputStream newFile = openFileOutput(JSONDataFile ,MODE_PRIVATE);
            newFile.write(newJSONData.toString().getBytes());
            newFile.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }


    public void saveTrainingData(ArrayList featuresToUse, ArrayList contextLabels) {
        //launch activity that will guide user through recording labeled data
        Intent dialogIntent = new Intent(getBaseContext(), DataCollectionAct.class);
//        dialogIntent.setAction(Intent.ACTION_VIEW);
//        Intent dialogIntent = new Intent("org.jingbling.ContextEngine.DataCollectionAct");
//        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        dialogIntent.putExtra("features", featuresToUse);
        dialogIntent.putExtra("contextLabels", contextLabels);

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(serviceMsgHandler);
        dialogIntent.putExtra("SERVICE_MESSENGER", messenger);

//        startActivity(dialogIntent);
        getApplication().startActivity(dialogIntent);
    }

    private void sendMessageToClient (Messenger messengerOut, Bundle outBundle) {
        // function to send message out
        Message msg = Message.obtain();
        msg.setData(outBundle);
        try {
            messengerOut.send(msg);
        } catch (android.os.RemoteException e1) {
            Log.w(getClass().getName(), "Exception sending message", e1);
        }

    }

    private Handler serviceMsgHandler = new Handler() {
        public void handleMessage(Message message) {
            Bundle output = message.getData();
            if (output != null) {
                // Also allow for getting final model file from activity
                String fileReceived = output.getString("fileType");
                if (fileReceived.toLowerCase().equals("model")) {
                    String modelFile = output.getString("modelFileName");
                    String modelAlgorithm = output.getString("algorithm");
                    ArrayList<String> modelLabels = output.getStringArrayList(LABELS_KEY);
                    ArrayList<String> modelFeatures = output.getStringArrayList("features");

                    // first check if this is a unique entry
                    ArrayList<String> matchFile = null;
                    try {
                        matchFile = findClassifierMatch(modelFeatures,modelAlgorithm,modelLabels,existingJSONClassifiers);
                    } catch (JSONException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    if (matchFile==null){ // no matches found, save model data to data file
    //                    classifiedModelFile.add(classifiedModelFile.size()+1, modelFile);
                        Log.d("MESSAGE_HANDLER_SERVICE", "current number of classifiers: "+existingJSONClassifiers.length());
                        //todo update JSON file to include new modelFile mapping
                        JSONObject newJSONClassifier = new JSONObject();
                        try {
                            newJSONClassifier.put("id",existingJSONClassifiers.length());
                            newJSONClassifier.put("filename",output.getString("modelFileName"));
                            newJSONClassifier.put("labels",new JSONArray(modelLabels));
                            newJSONClassifier.put("classifierAlgorithm",modelAlgorithm);
                            newJSONClassifier.put("features", new JSONArray(modelFeatures));
                            existingJSONClassifiers.put(newJSONClassifier);
                            Log.d("MESSAGE_HANDLER_SERVICE","JSONClassifiers: "+existingJSONClassifiers.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        //todo write JSON file
                        writeJSONtoFile();
                    }
                    Log.v("FROM_DATACOLLECT","Model file received: " + classifiedModelFile);
                } else if (fileReceived.toLowerCase().equals("trainingdata")) {

                    trainingFileName = output.getString("trainingFile");
                    Toast.makeText(getApplicationContext(),
                            "Training file received: " + trainingFileName, Toast.LENGTH_LONG)
                            .show();
                } else {

                    Toast.makeText(getApplicationContext(),
                            "Unrecognized message received from data collection activity", Toast.LENGTH_LONG)
                            .show();
                }
            } else {
                Log.d("FROM_DATACOLLECT","Training failed");
            }
            // after return received from activity, set activity running to false
            activityRunning = false;
        };
    };


//    private class MessageReceiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            Bundle output = intent.getExtras();
//            if (output != null) {
//                String input = output.getString("fileType");
//                if (input.toLowerCase().equals("datatolabel")){
//                    Log.d("CONTEXT_SERVICE","Data input to classify: "+output.getString("inputData"));
//                    // put data into a buffer
//                    String tempString = output.getString("inputData");
//                    // there is new data available, clear existing buffer before adding to ensure classify is done on latest input
//                    dataBuffer.clear();
//                    dataBuffer.offer(tempString);
//
//                }  else if (input.toLowerCase().equals("error")) {
//                   // error received
//                }
//            }
//        }
//    }

    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
