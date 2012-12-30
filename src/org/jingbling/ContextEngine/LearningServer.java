package org.jingbling.ContextEngine;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Jing
 * Date: 12/28/12
 * Time: 12:12 PM
 * To change this template use File | Settings | File Templates.
 * The purpose of the learning server is to create a classifier object if there is not one already,
 * or to update an existing classifier object if there is additional training data.
 *
 * For the focus of this project, the updating is not yet implemented, but a stub left for passing in
 * the classifier object to be modified.
 */
public class LearningServer {
    public String classifierReturn = "none";
    public context_classifier_model classifier_model = new context_classifier_model();


    public static void main(String argv[]) throws IOException
    {
        LearningServer ls = new LearningServer();

        // Determine
//        ls.run(argv);
    }

    public void runTraining (String TrainingDataFile) {

        //


        // write trained classifier to file
    }
}
