package org.jingbling.ContextEngine;

import android.app.IntentService;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/27/12
 * Time: 11:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ContextService extends IntentService {

   // Some variables for defining request
    private String contextGroup = new String();
    private String classifier = new String();
    private ArrayList<String> features =new ArrayList<String>();
    private String JSONDataFile = "/ContextServiceFiles/data/DataStructure.JSON";

    // Create a hash map for each group of values that need to be looked up
    JSONArray allowableValues = new JSONArray();
    private HashMap ContextGroupHashMap = new HashMap();
    private HashMap featuresHashMap = new HashMap();
    private HashMap classifiersHashMap = new HashMap();
    private HashMap classModel = new HashMap();
    private HashMap contextGroup2LabelsHashMap = new HashMap();

    static final int LAUNCH_DATACOLLECT = 1;

    public ContextService(){
        super("ContextService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        // Initialize data arrays from a file
        InitJSONtoHashMap(JSONDataFile);

        //After launching service, first parse JSON from intent
        Bundle inputExtras = intent.getExtras();
        //??? Validate JSON against schema?

        // Add JSON parser call here
        try {
            JSONObject jsonInput = new JSONObject(inputExtras.getString("JSONInput"));
            contextGroup = jsonInput.getString("contextGroup");
            classifier = jsonInput.getString("classifier");
            JSONArray featuresArray = jsonInput.getJSONArray("features");
            for (int i=0;i<featuresArray.length();i++) {
                features.add(i, featuresArray.get(i).toString());
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Next, calculate unique id from adding the sum of the IDs from the contextGroup, classifier, and features
        // Lookup in the hashmap the corresponding IDs for each, and then for an existing context model file
        // classifiers will have ID 99-100
        // contextGroup should have ID 100-9900
        // And features will have integers n=13-32, representing bit mapped values of 2^n
        int lookupID = (Integer)ContextGroupHashMap.get(contextGroup);
        lookupID = lookupID + (Integer)classifiersHashMap.get(classifier);
        // Loop through supplied features and add bitmapped value
        for (int i=0;i<features.size();i++) {
            int tempValue = (Integer)featuresHashMap.get(features.get(i));
            lookupID = (int) (lookupID + Math.pow(2, tempValue));
        }

        // Not use lookup ID to determine if there is an existing model to run
        String classifiedModelFile = (String)classModel.get(lookupID);
        if (classifiedModelFile == null) {
            // no classifier found, launch training mode
            classifiedModelFile = "/mnt/sdcard/ContextServiceModels/"+classifier+"Classifier.model";
//            saveTrainingData(features, contextGroup, classifiedModelFile);
        }

        String classifiedLabel = new String();

        // For testing messenger, hard code classified label for now
        classifiedLabel = "classifiedLabel_tobeadded, contextGroup: "+contextGroup+" classifier: "+ classifier + " features: "+features;

        // At end of call, pass classified context back to calling application
        if (inputExtras != null) {
            Messenger messenger = (Messenger) inputExtras.get("MESSENGER");
            Message msg = Message.obtain();
            Bundle classifiedBundle = new Bundle();
            classifiedBundle.putString("label",classifiedLabel);
            msg.setData(classifiedBundle);
            try {
                messenger.send(msg);
            } catch (android.os.RemoteException e1) {
                Log.w(getClass().getName(), "Exception sending message", e1);
            }


        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        android.os.Debug.waitForDebugger(); //??? TO BE REMOVED
    }

    @Override
    public void onDestroy() {
        // Before exiting, write hashmap to file ???
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
//        return Service.BIND_AUTO_CREATE;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    public void InitJSONtoHashMap (String inputJSONFile) {

        JSONObject inputJSON = null;
        try {
            inputJSON = parseJSONFromFile(inputJSONFile);
        } catch (JSONException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        JSONArray tempObject = new JSONArray();
        // Parse JSON to establish data mappings for lookup
        try {
            tempObject = inputJSON.getJSONArray("contextGroups");
            for (int i=0;i<tempObject.length();i++) {
                ContextGroupHashMap.put(tempObject.getJSONObject(i).getString("name"),tempObject.getJSONObject(i).getInt("id"));
            }

            tempObject = inputJSON.getJSONArray("classifiers");
            for (int i=0;i<tempObject.length();i++) {
                // Reverse the order for classifier Models, as lookup will be usually be performed with id
                classifiersHashMap.put(tempObject.getJSONObject(i).getString("name"),tempObject.getJSONObject(i).getInt("id"));
            }

            tempObject = inputJSON.getJSONArray("features");
            for (int i=0;i<tempObject.length();i++) {
                featuresHashMap.put(tempObject.getJSONObject(i).getString("name"),tempObject.getJSONObject(i).getInt("id"));
            }

            tempObject = inputJSON.getJSONArray("classifierModels");
            for (int i=0;i<tempObject.length();i++) {
                // Reverse the order for classifier Models, as lookup will be usually be performed with id
                classModel.put(tempObject.getJSONObject(i).getInt("id"),tempObject.getJSONObject(i).getString("name"));
            }

        } catch (JSONException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    public JSONObject parseJSONFromFile (String inputJSONFile) throws JSONException {

        String jString = null;
        // Parse expected elements from JSON file into a JSON Object
        try {

            File dir = Environment.getExternalStorageDirectory();
            File inputFile = new File(dir, inputJSONFile);
            FileInputStream istream = new FileInputStream(inputFile);
            try {
                FileChannel fc = istream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                /* Instead of using default, pass in a decoder. */
                jString = Charset.defaultCharset().decode(bb).toString();
            }
            finally {
                istream.close();
            }

        } catch (Exception e) {e.printStackTrace();}

        if (jString != null) {

            JSONObject outputJSONObj = new JSONObject(jString);
            return outputJSONObj;
        } else {
            Log.w("parseJSONFromFile","ERROR parsing JSONObject from file");
            return null;
        }
    }

    public void writeJSONtoFile (JSONObject inputObject, String outputFileName) {
        // Utility function to save internal data in form of JSON Object into a file
    }
//
//    public String classifyContext(List<String> featuresToUse, String classifierToUse, String contextGroup) {
//        String classifierToRun;
//        // check database for whether a suitable classifier already exists, and return serialized object filename if it does
//        classifierToRun = lookupClassifier(featuresToUse, classifierToUse, contextGroup);
////
////        if (classifierToRun.equals("null")) {
////            // null means object not found, run training data program to generate classifier register with database
////            classifierToRun = "/"+classifierToUse.toLowerCase() + "/"+ contextGroup + Calendar.getInstance().getTime().toString();
////            saveTrainingData(featuresToUse, classifierToUse, classifierToRun);
////        }
////        // Check on classifier to use, depending on type, run training locally or perform on remote machine
////        if (classifierToUse.toLowerCase().equals("libsvm")) {
////            //if libSVM chosen, run on machine
////
////            //First just run libSVM to train data
////        }
//        String returnValue = "toBeImplemented using classifier: "+classifierToUse + " and group:" +contextGroup;
//        return returnValue;
//    }

    public void saveTrainingData(String featuresToUse, String contextGroup, String filename) {
        // placeholder for launching activity that will guide user through recording labeled data
        Intent dialogIntent = new Intent(getBaseContext(), DataCollectionAct.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        dialogIntent.setAction(Intent.ACTION_VIEW);
//        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        dialogIntent.putExtra("features", featuresToUse);
        dialogIntent.putExtra("context", contextGroup);
        dialogIntent.putExtra("filename", filename);
//        startActivity(dialogIntent);
        getApplication().startActivity(dialogIntent);
    }


    private Handler handler = new Handler() {
        public void handleMessage(Message message) {
            Bundle output = message.getData();
            if (output != null) {
                Toast.makeText(getApplicationContext(),
                        "Training file found: " + output.getString("trainFile"), Toast.LENGTH_LONG)
                        .show();
            } else {
                Toast.makeText(getApplicationContext(), "Classification failed",
                        Toast.LENGTH_LONG).show();
            }

        };
    };
//
//    public static <T, E> T getKeyByValue(HashMap<T, E> map, E value) {
//        // Used for reverse lookup of key to value
//        for (Map.Entry<T, E> entry : map.entrySet()) {
//            if (value.equals(entry.getValue())) {
//                return entry.getKey();
//            }
//        }
//        return null;
//    }
}
