package main.java.reddit.character_level;

import main.java.beam_search.BeamSearch;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.distribution.impl.UniformDistribution;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;

import java.util.Arrays;
import java.util.function.Function;

public class TestModel {
    public static void main(String[] args) {
        ComputationGraph net = RedditCharacterModel.load();

        String[] testTexts = new String[]{
                "hello my name is Evan.",
                "this is another Example",
                "the dog went running",
                "what is your favorite movie?",
                "who would you rather be, brad pitt or angelina jolie?",
                "the dog went on a run",
                "Twitter faces more challenges than most technology companies: ISIS terrorists, trolls, bots, and Donald Trump."
        };
        org.nd4j.linalg.dataset.api.MultiDataSet[] testDsArray = new org.nd4j.linalg.dataset.api.MultiDataSet[testTexts.length];
        for(int i = 0; i < testDsArray.length; i++) {
            testDsArray[i]=BuildCharacterDatasets.textToVec(testTexts[i],10,128);
        }

        for(int i = 0; i < testDsArray.length; i++) {
            MultiDataSet testDs = testDsArray[i];
            String text = testTexts[i];
            INDArray vec = testDs.getFeatures(0);
            vec = vec.dup();
            INDArray mask = testDs.getFeaturesMaskArray(0).dup();

            Function<INDArray,Pair<INDArray,Double>> predictNextStep = vector -> {
                //System.out.println("Prevector shape: "+ Arrays.toString(vector.shape()));
                INDArray results = Transforms.softmax(vector,true);
                //System.out.println("Softmax shape: "+ Arrays.toString(results.shape()));
                INDArray randResults = results.muli(Transforms.softmax(Nd4j.rand(results.shape()),false));
                int sampleIdx = Nd4j.argMax(results,0).getInt(0);
                INDArray ret = Nd4j.zeros(results.shape());
                ret.putScalar(sampleIdx,1f);
                return new Pair<>(ret,results.getDouble(sampleIdx));
            };

            Function<INDArray,String> convertPredictionToOutput = vector -> {
                System.out.println("Final shape: "+Arrays.toString(vector.shape()));
                return BuildCharacterDatasets.vectorsToStrings(vector,null)[0];
            };

            BeamSearch<String> beamSearch = new BeamSearch<>(10,net,predictNextStep,0.7,convertPredictionToOutput);
            Pair<String,Double> results = beamSearch.run(vec,10);
            String[] newText = BuildCharacterDatasets.vectorsToStrings(vec, mask);
            System.out.println("Text: " + text);
            System.out.println("New Text: " + newText[0]);
            System.out.println("Prediction (score: "+results.getSecond()+"): {"+results.getFirst()+"}");
        }
    }
}
