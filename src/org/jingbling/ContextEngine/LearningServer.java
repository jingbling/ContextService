package org.jingbling.ContextEngine;

import android.os.Environment;
import libsvm.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;

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
    private String classifierReturn = "none";
//    public context_classifier_model classifier_model = new context_classifier_model();
    // SVM training variables
    private svm_parameter param;		// set internally
    private svm_problem prob;		// set by read_problem
    private String error_msg;
    private int nr_fold;
    // Following used for scaling
    private String line;
    private double lower = -1.0;
    private double upper = 1.0;
    private double[] feature_max;
    private double[] feature_min;
    private double y_max = -Double.MAX_VALUE;
    private double y_min = Double.MAX_VALUE;
    private int max_index;
    private long num_nonzeros = 0;
    private long new_num_nonzeros = 0;


//    public static void main(String argv[]) throws IOException
//    {
//        LearningServer ls = new LearningServer();
//
//        // Determine
////        ls.run(argv);
//    }

    public String runSVMTraining (String TrainingDataFile) throws IOException {

        // LibSVM training is split up into a few steps:
        /*  1) converting to libSVM format - this is currently already done in DataCollectionAct.
            2) scaling data - call svm_scale to accomplish this
            3) Use RBF kernel to train, and
            4) perform 10-fold cross-validation to find best RBF parameters
            5) Use best training parameters to train entire data set
        newProblem.
         */
        // Define parameters for training
        param = new svm_parameter();
        // default values
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.RBF;
        param.degree = 3;
        param.gamma = 0;	// 1/num_features
        param.coef0 = 0;
        param.nu = 0.5;
        param.cache_size = 100;
        param.C = 1;
        param.eps = 1e-3;
        param.p = 0.1;
        param.shrinking = 1;
        param.probability = 0;
        param.nr_weight = 0;
        param.weight_label = new int[0];
        param.weight = new double[0];
        nr_fold = 10;


        // Scale training data file
        svm_scale(TrainingDataFile);

        try {
            read_problem(TrainingDataFile);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        error_msg = svm.svm_check_parameter(prob,param);

        if(error_msg != null)
        {
            System.err.print("ERROR: "+error_msg+"\n");
            System.exit(1);
        }

//        do_cross_validation();



        svm_model trainedModel = svm.svm_train(prob,param);
        // write trained classifier to file
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File(sdCard.getAbsolutePath() + "/ContextServiceModels/LibSVM");
        dir.mkdirs();
        // Get input filename only
        File tempFile = new File(TrainingDataFile);
        String model_file_name = dir.toString()+tempFile.getName()+".model";
        svm.svm_save_model(model_file_name,trainedModel);
        return model_file_name;
    }

    public String evaluateSVMModel (String modelFile, svm_model model) {
        // Function for evaluating SVM model
        String classifiedLabel="undefined";
        return classifiedLabel;
    }

    public String evaluateModel (String features, String PlaceholderForPMMLModel) {
        // Function for evaluating SVM model
        String classifiedLabel="undefined";
        return classifiedLabel;
    }



    private void do_cross_validation()
    {
        int i;
        int total_correct = 0;
        double total_error = 0;
        double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
        double[] target = new double[prob.l];

        svm.svm_cross_validation(prob,param,nr_fold,target);
        if(param.svm_type == svm_parameter.EPSILON_SVR ||
                param.svm_type == svm_parameter.NU_SVR)
        {
            for(i=0;i<prob.l;i++)
            {
                double y = prob.y[i];
                double v = target[i];
                total_error += (v-y)*(v-y);
                sumv += v;
                sumy += y;
                sumvv += v*v;
                sumyy += y*y;
                sumvy += v*y;
            }
            System.out.print("Cross Validation Mean squared error = "+total_error/prob.l+"\n");
            System.out.print("Cross Validation Squared correlation coefficient = "+
                    ((prob.l*sumvy-sumv*sumy)*(prob.l*sumvy-sumv*sumy))/
                            ((prob.l*sumvv-sumv*sumv)*(prob.l*sumyy-sumy*sumy))+"\n"
            );
        }
        else
        {
            for(i=0;i<prob.l;i++)
                if(target[i] == prob.y[i])
                    ++total_correct;
            System.out.print("Cross Validation Accuracy = "+100.0*total_correct/prob.l+"%\n");
        }
    }



    private static double atof(String s)
    {
        double d = Double.valueOf(s).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d))
        {
            System.err.print("NaN or Infinity in input\n");
            System.exit(1);
        }
        return(d);
    }

    private static int atoi(String s)
    {
        return Integer.parseInt(s);
    }

    // read in a problem (in svmlight format)

    private void read_problem(String TrainingDataFile) throws IOException
    {
        BufferedReader fp = new BufferedReader(new FileReader(TrainingDataFile));
        Vector<Double> vy = new Vector<Double>();
        Vector<svm_node[]> vx = new Vector<svm_node[]>();
        int max_index = 0;

        while(true)
        {
            String line = fp.readLine();
            if(line == null) break;

            StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");

            vy.addElement(atof(st.nextToken()));
            int m = st.countTokens()/2;
            svm_node[] x = new svm_node[m];
            for(int j=0;j<m;j++)
            {
                x[j] = new svm_node();
                x[j].index = atoi(st.nextToken());
                x[j].value = atof(st.nextToken());
            }
            if(m>0) max_index = Math.max(max_index, x[m-1].index);
            vx.addElement(x);
        }

        prob = new svm_problem();
        prob.l = vy.size();
        prob.x = new svm_node[prob.l][];
        for(int i=0;i<prob.l;i++)
            prob.x[i] = vx.elementAt(i);
        prob.y = new double[prob.l];
        for(int i=0;i<prob.l;i++)
            prob.y[i] = vy.elementAt(i);

        if(param.gamma == 0 && max_index > 0)
            param.gamma = 1.0/max_index;


        fp.close();
    }


    private void svm_scale(String data_filename) throws IOException
    {
        int i,index;
        BufferedReader fp = null, fp_restore = null;

        // This function is used to scale training data between -1 and 1 before training

        try {
            fp = new BufferedReader(new FileReader(data_filename));
        } catch (Exception e) {
            System.err.println("can't open file " + data_filename);
            System.exit(1);
        }

		/* assumption: min index of attributes is 1 */
		/* pass 1: find out max index of attributes */
        max_index = 0;

        while (readline(fp) != null)
        {
            StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
            st.nextToken();
            while(st.hasMoreTokens())
            {
                index = Integer.parseInt(st.nextToken());
                max_index = Math.max(max_index, index);
                st.nextToken();
                num_nonzeros++;
            }
        }

        try {
            feature_max = new double[(max_index+1)];
            feature_min = new double[(max_index+1)];
        } catch(OutOfMemoryError e) {
            System.err.println("can't allocate enough memory");
            System.exit(1);
        }

        for(i=0;i<=max_index;i++)
        {
            feature_max[i] = -Double.MAX_VALUE;
            feature_min[i] = Double.MAX_VALUE;
        }

        fp = rewind(fp, data_filename);

		/* pass 2: find out min/max value */
        while(readline(fp) != null)
        {
            int next_index = 1;
            double target;
            double value;

            StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
            target = Double.parseDouble(st.nextToken());
            y_max = Math.max(y_max, target);
            y_min = Math.min(y_min, target);

            while (st.hasMoreTokens())
            {
                index = Integer.parseInt(st.nextToken());
                value = Double.parseDouble(st.nextToken());

                for (i = next_index; i<index; i++)
                {
                    feature_max[i] = Math.max(feature_max[i], 0);
                    feature_min[i] = Math.min(feature_min[i], 0);
                }

                feature_max[index] = Math.max(feature_max[index], value);
                feature_min[index] = Math.min(feature_min[index], value);
                next_index = index + 1;
            }

            for(i=next_index;i<=max_index;i++)
            {
                feature_max[i] = Math.max(feature_max[i], 0);
                feature_min[i] = Math.min(feature_min[i], 0);
            }
        }

        fp = rewind(fp, data_filename);


		/* pass 3: scale */
        while(readline(fp) != null)
        {
            int next_index = 1;
            double target;
            double value;

            StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
            target = Double.parseDouble(st.nextToken());
        //  output_target(target);
            while(st.hasMoreElements())
            {
                index = Integer.parseInt(st.nextToken());
                value = Double.parseDouble(st.nextToken());
                for (i = next_index; i<index; i++)
                    output(i, 0);
                output(index, value);
                next_index = index + 1;
            }

            for(i=next_index;i<= max_index;i++)
                output(i, 0);
            System.out.print("\n");
        }
        if (new_num_nonzeros > num_nonzeros)
            System.err.print(
                    "WARNING: original #nonzeros " + num_nonzeros+"\n"
                            +"         new      #nonzeros " + new_num_nonzeros+"\n"
                            +"Use -l 0 if many original feature values are zeros\n");

        fp.close();
    }

    private String readline(BufferedReader fp) throws IOException
    {
        line = fp.readLine();
        return line;
    }

    private BufferedReader rewind(BufferedReader fp, String filename) throws IOException
    {
        fp.close();
        return new BufferedReader(new FileReader(filename));
    }
    private void output(int index, double value)
    {
		/* skip single-valued attribute */
        if(feature_max[index] == feature_min[index])
            return;

        if(value == feature_min[index])
            value = lower;
        else if(value == feature_max[index])
            value = upper;
        else
            value = lower + (upper-lower) *
                    (value-feature_min[index])/
                    (feature_max[index]-feature_min[index]);

        if(value != 0)
        {
//            System.out.print(index + ":" + value + " ");
            new_num_nonzeros++;
        }
    }

}
