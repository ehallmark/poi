package main.java.beam_search;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.function.Function;

public class BeamSearch<T> {

    private List<ComputationGraph> graphs;
    // This function predicts the next step vector from the last output vector (could possibly be identity or transformation to 1-hot vector)
    private Function<INDArray,Pair<INDArray,Double>> predictNextStepFunction;
    private final double alpha;
    // This function converts the network output to the desired T class
    private Function<INDArray,T> convertPredictionToOutputSequenceFunction;
    public BeamSearch(int b, ComputationGraph graph, Function<INDArray,Pair<INDArray,Double>> predictNextStepFunction, double alpha, Function<INDArray,T> convertPredictionToOutputSequenceFunction) {
        this.alpha=alpha;
        this.predictNextStepFunction=predictNextStepFunction;
        if(alpha<0||alpha>1) throw new IllegalArgumentException("alpha must exist in [0,1].");
        this.convertPredictionToOutputSequenceFunction=convertPredictionToOutputSequenceFunction;
        this.graphs = Collections.synchronizedList(new ArrayList<>(b));
        for(int i = 0; i < b; i++) {
            ComputationGraph copy = new ComputationGraph(graph.getConfiguration().clone());
            copy.init(graph.params(),true);
            graphs.add(copy);
        }
    }

    public Pair<T,Double> run(INDArray input, int maxPredictionLength) {
        Pair<INDArray,Double> pair = graphs.parallelStream().map(graph->{
            Pair<INDArray,Double> prediction = computeBeam(graph,input,maxPredictionLength);
            return prediction;
        }).filter(p->p!=null).sorted((e1,e2)->e2.getSecond().compareTo(e1.getSecond())).findFirst().orElse(null);
        if(pair!=null) {
            T prediction = convertPredictionToOutputSequenceFunction.apply(pair.getFirst());
            if(prediction == null) return null;
            return new Pair<>(prediction,pair.getSecond());
        }
        return null;
    }


    private Pair<INDArray,Double> computeBeam(ComputationGraph graph, INDArray input, int iterations) {
        int originalSize = input.shape()[1];
        List<Double> scores = new ArrayList<>(iterations);
        INDArray prediction = computeBeamHelper(scores,graph,input,null,null,1,iterations);
        if(prediction.shape()[1]>originalSize) {
            double score = scores.stream().mapToDouble(s->Math.log(s)).sum()/Math.pow(scores.size(),alpha);
            INDArray output = prediction.get(NDArrayIndex.all(),NDArrayIndex.interval(originalSize,prediction.shape()[1]));
            return new Pair<>(output,score);
        } else {
            return null;
        }
    }

    private INDArray computeBeamHelper(List<Double> scores, ComputationGraph graph, INDArray input, INDArray featureMask, INDArray labelMask, int iteration, int totalIterations) {
        if(input.shape().length!=2) throw new RuntimeException("Expecting single rnn example. Dimensions should = 2.");
        // add output dimension
        INDArray newInput = Nd4j.hstack(input, Nd4j.zeros(input.rows()).transposei());
        //System.out.println("New input dims: "+ Arrays.toString(newInput.shape()));
        INDArray newFeatureMask;
        if(featureMask==null) {
            newFeatureMask = Nd4j.ones(newInput.shape()[1]);
            newFeatureMask.get(NDArrayIndex.point(newFeatureMask.columns()-1)).assign(0);
        } else {
            newFeatureMask = Nd4j.hstack(Nd4j.ones(1),featureMask);
        }
        INDArray newLabelMask;
        if(labelMask==null) {
            newLabelMask = Nd4j.zeros(newFeatureMask.shape());
            newLabelMask.get(NDArrayIndex.point(newLabelMask.columns()-1)).assign(1);
        } else {
            newLabelMask = Nd4j.hstack(Nd4j.zeros(1),labelMask);
        }

        graph.setLayerMaskArrays(new INDArray[]{newFeatureMask},new INDArray[]{newLabelMask});

        newInput = newInput.reshape(1,newInput.shape()[0],newInput.shape()[1]);
        INDArray output = graph.output(newInput)[0];
        //System.out.println("Output shape: "+Arrays.toString(output.shape()));
        graph.clearLayerMaskArrays();
        INDArray finalStepOutput = output.get(NDArrayIndex.point(0),NDArrayIndex.all(),NDArrayIndex.point(output.shape()[2]-1));
        Pair<INDArray,Double> prediction = predictNextStepFunction.apply(finalStepOutput);
        if(prediction==null) {
            return input;
        }
        //System.out.println("Prediction dims: "+Arrays.toString(prediction.getFirst().shape()));
        newInput = newInput.reshape(newInput.shape()[1],newInput.shape()[2]);
        newInput.get(NDArrayIndex.all(),NDArrayIndex.point(newInput.shape()[1]-1)).assign(prediction.getFirst());
        double score = prediction.getSecond();
        if(iteration<totalIterations) {
            scores.add(score);
            return computeBeamHelper(scores, graph,newInput,newFeatureMask,newLabelMask,iteration+1,totalIterations);
        } else {
            return newInput;
        }
    }

}
