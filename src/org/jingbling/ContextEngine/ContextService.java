package org.jingbling.ContextEngine;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/27/12
 * Time: 11:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ContextService extends IntentService {

   // Some variables for defining request
    String contextGroup = new String();
    String classifier = new String();
    String features = new String();

    public ContextService(){
        super("ContextService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //After launching service, first parse JSON from intent
        Bundle inputExtras = intent.getExtras();
        //??? Validate JSON against schema?

        // Add JSON parser call here
        try {
            JSONObject jsonInput = new JSONObject(inputExtras.getString("JSONInput"));
            contextGroup = jsonInput.getString("contextGroup");
            classifier = jsonInput.getString("classifier");
            JSONArray featuresArray = jsonInput.getJSONArray("features");
            features = featuresArray.toString();

        } catch (JSONException e) {
            e.printStackTrace();
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
    }

    @Override
    public void onDestroy() {
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

    public String classifyContext(List<String> featuresToUse, String classifierToUse, String contextGroup) {
        String classifierToRun;
        // check database for whether a suitable classifier already exists, and return serialized object filename if it does
        classifierToRun = lookupClassifier(featuresToUse, classifierToUse, contextGroup);
//
//        if (classifierToRun.equals("null")) {
//            // null means object not found, run training data program to generate classifier register with database
//            classifierToRun = "/"+classifierToUse.toLowerCase() + "/"+ contextGroup + Calendar.getInstance().getTime().toString();
//            saveTrainingData(featuresToUse, classifierToUse, classifierToRun);
//        }
//        // Check on classifier to use, depending on type, run training locally or perform on remote machine
//        if (classifierToUse.toLowerCase().equals("libsvm")) {
//            //if libSVM chosen, run on machine
//
//            //First just run libSVM to train data
//        }
        String returnValue = "toBeImplemented using classifier: "+classifierToUse + " and group:" +contextGroup;
        return returnValue;
    }

    public void saveTrainingData(List<String> featuresToUse, String contextGroup, String filename) {
        // placeholder for launching activity that will guide user through recording labeled data
        Intent dialogIntent = new Intent(getBaseContext(), DataCollectionAct.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        dialogIntent.setAction(Intent.ACTION_VIEW);
//        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        dialogIntent.putExtra("features", featuresToUse.toArray());
        dialogIntent.putExtra("context", contextGroup);
        dialogIntent.putExtra("filename", filename);
//        startActivity(dialogIntent);
        getApplication().startActivity(dialogIntent);
    }

    public String lookupClassifier(List<String> featuresToUse, String classifierToUse, String contextGroup) {
        String classifierObjFile = "null";
        return classifierObjFile;
    }


}
