package main.java.reddit.character_level;

import main.java.util.DefaultScoreListener;
import main.java.util.FileMultiMinibatchIterator;
import main.java.util.Function2;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
//import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;

import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class RedditCharacterModel {
    private static final File modelFile = new File("reddit_character_level_model.nn");
    private static final int MINI_BATCH_SIZE = 64;
    private static ComputationGraph net;
    private static void save(ComputationGraph net) {
        try {
            ModelSerializer.writeModel(net, modelFile, true);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static ComputationGraph load() {
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
        final int numChars = BuildCharacterDatasets.VALID_CHARS.length;
        final int hiddenLayerSize = 256;
        final int numEpochs = 1;
        final double learningRate = 0.025;

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(learningRate)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .regularization(false)
                .seed(12)
                //.cacheMode(CacheMode.DEVICE)
                .activation(Activation.TANH)
                .miniBatch(true)
                .updater(new RmsProp(0.95))
                .graphBuilder()
                .addInputs("x1")
                //.backpropType(BackpropType.TruncatedBPTT)
                //.tBPTTBackwardLength(100)
                .addLayer("r1", new GravesLSTM.Builder().nIn(numChars).nOut(hiddenLayerSize).build(),"x1")
                .addLayer("r2", new GravesLSTM.Builder().nIn(hiddenLayerSize+numChars).nOut(hiddenLayerSize).build(),"r1","x1")
                //.addLayer("r3", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(),"r2", "r1")
                .addLayer("y1", new RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.XENT).activation(Activation.SOFTMAX).nIn(hiddenLayerSize+hiddenLayerSize).nOut(numChars).build(),"r2","r1")
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
        String[] testTexts = new String[]{
                "hello my name is Evan.",
                "this is another Example",
                "the dog went running",
                "the dog went on a run",
                "Twitter faces more challenges than most technology companies: ISIS terrorists, trolls, bots, and Donald Trump."
        };
        MultiDataSet[] testDsArray = new MultiDataSet[testTexts.length];
        for(int i = 0; i < testDsArray.length; i++) {
            testDsArray[i]=BuildCharacterDatasets.textToVec(testTexts[i],10,128);
        }

        FileMultiMinibatchIterator testIterator = new FileMultiMinibatchIterator(BuildCharacterDatasets.devDir,10,-1,true);
        testIterator.setCompressed(true);
        int c = 0;
        while(testIterator.hasNext()&&c<8) {
            testSets.add(testIterator.next());
            c++;
            System.out.println("Loaded test set "+c);
            System.gc();
        }
        testIterator.reset();

        Function<Object,Double> testErrorFunction = obj -> {
            double score = 0d;
            int count = 0;
            for(org.nd4j.linalg.dataset.api.MultiDataSet ds : testSets) {
                double s = net.score(ds,false);
                score+=s;
                //System.out.println(s);
                count++;//ds.getFeatures(0).shape()[1];
            }
            //MultiDataSet ds2 = BuildCharacterDatasets.textToVec(text2,2,96);
            for(int i = 0; i < testDsArray.length; i++) {
                MultiDataSet testDs = testDsArray[i];
                String text = testTexts[i];
                INDArray vec = testDs.getFeatures(0);
                vec = vec.reshape(1, vec.shape()[0], vec.shape()[1]);
                INDArray mask = testDs.getFeaturesMaskArray(0);
                //mask.assign(0);
                INDArray labelMask = testDs.getLabelsMaskArray(0);
                //labelMask.assign(1);
                String[] newText = BuildCharacterDatasets.vectorsToStrings(vec, mask);
                System.out.println("Text: " + text);
                System.out.println("New Text: " + newText[0]);
                net.setLayerMaskArrays(new INDArray[]{mask}, new INDArray[]{labelMask});
                INDArray output = net.output(vec)[0];
                net.clearLayerMaskArrays();
                String predictedText = BuildCharacterDatasets.vectorsToStrings(output, labelMask)[0];
                System.out.println("Predicted Text: " + predictedText);
            }
            return (score/count);
        };


        /*Function<Object,Double> testErrorFunction = obj -> {
            double score = 0d;
            int count = 0;
            FileMultiMinibatchIterator testIterator = new FileMultiMinibatchIterator(BuildCharacterDatasets.devDir,10,-1,true);
            testIterator.setCompressed(true);
            while(testIterator.hasNext()&&count<10) {
                double s = net.score(testIterator.next(),false);
                score+=s;
                //System.out.println(s);
                count++;
                System.gc();
            }
            System.gc();
            testIterator.reset();
            for(int i = 0; i < testDsArray.length; i++) {
                MultiDataSet testDs = testDsArray[i];
                String text = testTexts[i];
                INDArray vec = testDs.getFeatures(0);
                vec = vec.reshape(1, vec.shape()[0], vec.shape()[1]);
                INDArray mask = testDs.getFeaturesMaskArray(0);
                //mask.assign(0);
                INDArray labelMask = testDs.getLabelsMaskArray(0);
                //labelMask.assign(1);
                String[] newText = BuildCharacterDatasets.vectorsToStrings(vec, mask);
                System.out.println("Text: " + text);
                System.out.println("New Text: " + newText[0]);
                net.setLayerMaskArrays(new INDArray[]{mask}, new INDArray[]{labelMask});
                INDArray output = net.output(vec)[0];
                net.clearLayerMaskArrays();
                String predictedText = BuildCharacterDatasets.vectorsToStrings(output, labelMask)[0];
                System.out.println("Predicted Text: " + predictedText);
            }
            return (score/count);
        };*/

        System.out.println("Initial test: "+testErrorFunction.apply(net));

        IterationListener listener = new DefaultScoreListener(testIters,testErrorFunction,obj->0d,saveFunction);

        net.setListeners(listener);

        FileMultiMinibatchIterator iterator = new FileMultiMinibatchIterator(BuildCharacterDatasets.trainDir,-1,MINI_BATCH_SIZE,false);
        iterator.setCompressed(true);
        for(int i = 0; i < numEpochs; i++) {
            while (iterator.hasNext()) {
                org.nd4j.linalg.dataset.api.MultiDataSet ds = iterator.next();
                //System.out.println("Features: "+ ds.getFeatures(0).get(NDArrayIndex.point(0),NDArrayIndex.all(),NDArrayIndex.point(0)).toString());
                //System.out.println("FMask: "+ ds.getFeaturesMaskArray(0).getRow(0).toString());
                //System.out.println("Labels: "+ ds.getLabels(0).get(NDArrayIndex.point(0),NDArrayIndex.all(),NDArrayIndex.point(0)).toString());
                //System.out.println("LMask: "+ ds.getLabelsMaskArray(0).getRow(0).toString());
                net.fit(ds);
                System.gc();
            }
            System.out.println("Finished epoch: "+(i+1));
            iterator.reset();
        }
    }
}
