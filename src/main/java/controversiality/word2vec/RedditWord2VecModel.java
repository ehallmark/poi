package main.java.controversiality.word2vec;

import main.java.reddit.word2vec.RedditWord2Vec;
import main.java.util.DefaultScoreListener;
import main.java.util.FileMultiMinibatchIterator;
import main.java.util.Function2;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.graph.rnn.LastTimeStepVertex;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.nd4j.jita.conf.CudaEnvironment;

public class RedditWord2VecModel {
    private static final File modelFile = new File("reddit_controversy_model.nn");
    private static final int MINI_BATCH_SIZE = 32;
    private static ComputationGraph net;
    private static void save(ComputationGraph net) {
        try {
            ModelSerializer.writeModel(net, modelFile, true);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static ComputationGraph load() {
        try {
            net = ModelSerializer.restoreComputationGraph(modelFile);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return net;
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        try {
            Nd4j.getMemoryManager().setAutoGcWindow(100);
            CudaEnvironment.getInstance().getConfiguration().setMaximumGridSize(512).setMaximumBlockSize(512)
                    .setMaximumDeviceCacheableLength(2L * 1024 * 1024 * 1024L)
                    .setMaximumDeviceCache(10L * 1024 * 1024 * 1024L)
                    .setMaximumHostCacheableLength(2L * 1024 * 1024 * 1024L)
                    .setMaximumHostCache(10L * 1024 * 1024 * 1024L);
        } catch(Exception e) {
            e.printStackTrace();
        }

        final int testIters = 100;
        final int numChars = RedditWord2Vec.VECTOR_SIZE;
        final int hiddenLayerSize = 128;
        final int numEpochs = 3;

        MultiDataSetPreProcessor preprocessor = null; /*new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(MultiDataSet multiDataSet) {
                INDArray features = multiDataSet.getFeatures(0);
                multiDataSet.setFeaturesMaskArrays(null);
                multiDataSet.setFeatures(0,features.reshape(features.shape()[0],1,features.shape()[1],features.shape()[2]));
            }
        }; //FOR CNN */

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(0.001)//0.01
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(12)
                .activation(Activation.TANH)
                .miniBatch(true)
                .updater(Updater.ADAM)
                //.regularization(true).l2(0.0001)
                .graphBuilder()
                .addInputs("x1")
                .addLayer("r1", new GravesLSTM.Builder().nIn(numChars).nOut(hiddenLayerSize).build(),"x1")
                .addLayer("r2", new GravesLSTM.Builder().nIn(hiddenLayerSize+numChars).nOut(hiddenLayerSize).build(),"r1","x1")
                .addVertex("preprocessor1", new LastTimeStepVertex("x1"),"r1")
                .addVertex("preprocessor2", new LastTimeStepVertex("x1"),"r2")
                .addLayer("d1",new DenseLayer.Builder().nIn(hiddenLayerSize+hiddenLayerSize).nOut(hiddenLayerSize).build(),"preprocessor1","preprocessor2")
                .addLayer("d2",new DenseLayer.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(),"d1")
                .addLayer("y1", new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).activation(Activation.SOFTMAX).nIn(hiddenLayerSize).nOut(2).build(),"d2")
                .setOutputs("y1")
                .build();



        /*//Basic configuration
        int vectorSize = numChars;               //Size of the word vectors. 300 in the Google News model
        int cnnLayerFeatureMaps = hiddenLayerSize;      //Number of feature maps / channels / depth for each CNN layer
        PoolingType globalPoolingType = PoolingType.MAX;
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
               .weightInit(WeightInit.XAVIER)
               .activation(Activation.TANH)
               .updater(Updater.RMSPROP)
               .convolutionMode(ConvolutionMode.Same)      //This is important so we can 'stack' the results later
               .regularization(true).l2(0.0001)
               .learningRate(0.001)
               .graphBuilder()
               .addInputs("input")
               .addLayer("cnn3", new ConvolutionLayer.Builder()
                       .kernelSize(vectorSize,3)
                       .stride(vectorSize,1)
                       .nIn(1)
                       .nOut(cnnLayerFeatureMaps)
                       .build(), "input")
               .addLayer("cnn4", new ConvolutionLayer.Builder()
                       .kernelSize(vectorSize,4)
                       .stride(vectorSize,1)
                       .nIn(1)
                       .nOut(cnnLayerFeatureMaps)
                       .build(), "input")
               .addLayer("cnn5", new ConvolutionLayer.Builder()
                       .kernelSize(vectorSize,5)
                       .stride(vectorSize,1)
                       .nIn(1)
                       .nOut(cnnLayerFeatureMaps)
                       .build(), "input")
               .addVertex("merge", new MergeVertex(), "cnn3", "cnn4", "cnn5")      //Perform depth concatenation
               .addLayer("globalPool", new GlobalPoolingLayer.Builder()
                       .poolingType(globalPoolingType)
                       .dropOut(0.5)
                       .build(), "merge")
               .addLayer("out", new OutputLayer.Builder()
                       .lossFunction(LossFunctions.LossFunction.MCXENT)
                       .activation(Activation.SOFTMAX)
                       .nIn(3*cnnLayerFeatureMaps)
                       .nOut(2)    //2 classes: positive or negative
                       .build(), "globalPool")
               .setOutputs("out")
               .build();*/


        load();
        if(net==null) {
            System.out.println("Initializing new model...");
            net = new ComputationGraph(conf);
            net.init();
        } else {
            System.out.println("Restoring network from previous model...");
            INDArray params = net.params();
            net = new ComputationGraph(conf);
            net.init(params,false);
        }


        Function2<LocalDateTime,Double,Void> saveFunction = (date,score)->{
            System.out.println("Saving...");
            try {
                save(net);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        List<org.nd4j.linalg.dataset.api.MultiDataSet> testSets = new ArrayList<>();
        FileMultiMinibatchIterator testIterator = new FileMultiMinibatchIterator(BuildWord2VecDatasets.devDir,10,-1,true);
        testIterator.setCompressed(true);
        testIterator.setPreProcessor(preprocessor);
        int c = 0;
        while(testIterator.hasNext()&&c<10) {
            testSets.add(testIterator.next());
            c++;
            System.out.println("Loaded test set "+c);

        }
        Function<Object,Double> testErrorFunction = obj -> {
            double score = 0d;
            int count = 0;
            Evaluation eval = new Evaluation(2);
            for(org.nd4j.linalg.dataset.api.MultiDataSet ds : testSets) {
                double s = net.score(ds,false);
                score+=s;
                //System.out.println(s);
                count++;//ds.getFeatures(0).shape()[0];
                net.setLayerMaskArrays(ds.getFeaturesMaskArrays(),ds.getLabelsMaskArrays());
                eval.eval(ds.getLabels()[0],net.output(false,ds.getFeatures())[0]);
                net.clearLayerMaskArrays();
            }
            System.out.println("Test: "+eval.stats(false));
            return (score/count);
        };

        System.out.println("Initial test: "+testErrorFunction.apply(net));

        IterationListener listener = new DefaultScoreListener(testIters,testErrorFunction,obj->0d,saveFunction);

        net.setListeners(listener);

        FileMultiMinibatchIterator iterator = new FileMultiMinibatchIterator(BuildWord2VecDatasets.trainDir,-1,MINI_BATCH_SIZE,false);
        iterator.setPreProcessor(preprocessor);
        iterator.setCompressed(true);
        AtomicLong iter = new AtomicLong(0);
        for(int i = 0; i < numEpochs; i++) {
            while (iterator.hasNext()) {
                org.nd4j.linalg.dataset.api.MultiDataSet ds = iterator.next();
                net.fit(ds);
            }
            System.out.println("Finished epoch: "+(i+1));
            iterator.reset();
        }
    }
}
