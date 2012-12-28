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
        public void gatherTrainingData(List<String> featuresToUse, String contextGroup) throws RemoteException {
            ContextService.this.saveTrainingData(featuresToUse, contextGroup);
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
        // for now return some random string
        String returnValue = "nullString";
        return returnValue;
    }

    public void saveTrainingData(List<String> featuresToUse, String contextGroup) {
        // placeholder for launching activity that will guide user through recording labeled data
    }
}
