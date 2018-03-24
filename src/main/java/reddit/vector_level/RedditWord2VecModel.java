package main.java.reddit.vector_level;

import main.java.util.DefaultScoreListener;
import main.java.util.FileMultiMinibatchIterator;
import main.java.util.Function2;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

//import org.nd4j.jita.conf.CudaEnvironment;

public class RedditWord2VecModel {
    private static final File modelFile = new File("reddit_word2vec_vector_level_model.nn");
    private static final int MINI_BATCH_SIZE = 64;
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
        /*Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        try {
            Nd4j.getMemoryManager().setAutoGcWindow(100);
            CudaEnvironment.getInstance().getConfiguration().setMaximumGridSize(512).setMaximumBlockSize(512)
                    .setMaximumDeviceCacheableLength(2L * 1024 * 1024 * 1024L)
                    .setMaximumDeviceCache(10L * 1024 * 1024 * 1024L)
                    .setMaximumHostCacheableLength(2L * 1024 * 1024 * 1024L)
                    .setMaximumHostCache(10L * 1024 * 1024 * 1024L);
        } catch(Exception e) {
            e.printStackTrace();
        }*/

        final int testIters = 100;
        final int numChars = BuildWord2VecDatasets.VOCAB_SIZE;
        final int hiddenLayerSize = 64;
        final int numEpochs = 3;

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(0.01)//0.05
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .regularization(false)
                .seed(12)
                //.cacheMode(CacheMode.DEVICE)
                .activation(Activation.TANH)
                .miniBatch(true)
                .updater(Updater.RMSPROP)
                .graphBuilder()
                .addInputs("x1")
                //.backpropType(BackpropType.TruncatedBPTT)
                //.tBPTTBackwardLength(100)
                .addLayer("r1", new GravesLSTM.Builder().nIn(numChars).nOut(hiddenLayerSize).build(),"x1")
                //.addLayer("r2", new GravesLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(),"r1")
                //.addLayer("r3", new GravesLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(),"r2")
                .addLayer("y1", new RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).activation(Activation.SOFTMAX).nIn(hiddenLayerSize+numChars).nOut(numChars).build(),"r1","x1")
                .setOutputs("y1")
                .build();

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
        int c = 0;
        while(testIterator.hasNext()&&c<10) {
            testSets.add(testIterator.next());
            c++;
            System.out.println("Loaded test set "+c);
        }
        Function<Object,Double> testErrorFunction = obj -> {
            double score = 0d;
            int count = 0;
            for(org.nd4j.linalg.dataset.api.MultiDataSet ds : testSets) {
                double s = net.score(ds,false);
                score+=s;
                //System.out.println(s);
                count++;//ds.getFeatures(0).shape()[0];
            }
            return (score/count);
        };

        System.out.println("Initial test: "+testErrorFunction.apply(net));

        IterationListener listener = new DefaultScoreListener(testIters,testErrorFunction,obj->0d,saveFunction);

        net.setListeners(listener);

        FileMultiMinibatchIterator iterator = new FileMultiMinibatchIterator(BuildWord2VecDatasets.trainDir,-1,MINI_BATCH_SIZE,false);
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
