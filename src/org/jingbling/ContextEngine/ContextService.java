package org.jingbling.ContextEngine;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/27/12
 * Time: 11:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ContextService extends Service {

    public class ContextServiceImplement extends IContextService.Stub {
        @Override
        public String getContext(List<String> featuresToUse, String classifierToUse, String contextGroup) throws RemoteException {
            return ContextService.this.classifyContext(featuresToUse, classifierToUse, contextGroup);
        }

        @Override
        public void gatherTrainingData(List<String> featuresToUse, String contextGroup, String filename) throws RemoteException {
            ContextService.this.saveTrainingData(featuresToUse, contextGroup, filename);
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
        super.onStartCommand(intent, flags, startId);
        return Service.BIND_AUTO_CREATE;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ContextServiceImplement();
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
        String returnValue = "nullString using classifier: "+classifierToUse + " and group:" +contextGroup;
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
