package main.java.reddit.character_level;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Arrays;

public class TestVecToString {
    public static void main(String[] args) {

        ComputationGraph net = RedditCharacterModel.load();

        String text = "hello my name is Evan.";
        String text1 = "this is another Example";
        MultiDataSet ds = BuildCharacterDatasets.textToVec(text1,5,96);
        //MultiDataSet ds2 = BuildCharacterDatasets.textToVec(text2,2,96);

        INDArray vec = ds.getFeatures(0);
        System.out.println("Features: "+ Arrays.toString(vec.shape()));
        vec = vec.reshape(1,vec.shape()[0],vec.shape()[1]);
        INDArray mask = ds.getFeaturesMaskArray(0);
        mask.assign(0);
        INDArray labelMask = ds.getLabelsMaskArray(0);
        labelMask.assign(1);

        System.out.println("Mask: "+ Arrays.toString(mask.shape()));
        //mask = mask.reshape(1,mask.shape()[0]);
        //mask.get(NDArrayIndex.interval(0,text1.length())).assign(1);
        String[] newText = BuildCharacterDatasets.vectorsToStrings(vec,mask);

        System.out.println("Text: "+text1);
        System.out.println("New Text: "+newText[0]);

        net.setLayerMaskArrays(new INDArray[]{mask},new INDArray[]{labelMask});
        INDArray output = net.output(vec)[0];
        net.clearLayerMaskArrays();

        String predictedText = BuildCharacterDatasets.vectorsToStrings(output,labelMask)[0];
        System.out.println("Predicted Text: "+predictedText);
    }
}
