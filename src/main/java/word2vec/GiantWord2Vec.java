package main.java.word2vec;

import main.java.util.StopWords;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GiantWord2Vec {
    public static final File modelFile = new File("word2vec_model_large.nn");
    public static final File gloveModel = new File("glove_model_large.nn");
    private static final int BATCH_SIZE = 512;
    private static org.deeplearning4j.models.word2vec.Word2Vec net256;
    private static Glove glove;

    private static void save(org.deeplearning4j.models.word2vec.Word2Vec paragraphVectors) {
        WordVectorSerializer.writeWord2VecModel(paragraphVectors,modelFile.getAbsolutePath()+paragraphVectors.getLayerSize());
    }

    private static void save(Glove paragraphVectors) {
        WordVectorSerializer.writeWordVectors(paragraphVectors,gloveModel.getAbsolutePath()+paragraphVectors.getLayerSize());
    }


    public static void main(String[] args) {
        buildAndTrainModel();
    }


    private static Word2Vec.Builder newBuilder(int vectorSize) {
        int windowSize = 7;
        int minWordFrequency = 100;//400;
        double negativeSampling = 4;
        double sampling = 0.001;
        //double learningRate = 0.1;
        //double minLearningRate = 0.001;
        double learningRate = 0.05;
        double minLearningRate = 0.0001;

        ZippedFileSequenceIterator iterator = new ZippedFileSequenceIterator(new File("/usb/word2vec_text/").listFiles(),0,500000000,-1);
        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });

        org.deeplearning4j.models.word2vec.Word2Vec.Builder builder = new org.deeplearning4j.models.word2vec.Word2Vec.Builder()
                .seed(41)
                .batchSize(BATCH_SIZE)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(windowSize)
                .layerSize(vectorSize)
                .sampling(sampling)
                //.stopWords(stopWords)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .minLearningRate(minLearningRate)
                .allowParallelTokenization(true)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(minWordFrequency)
                .tokenizerFactory(tf)
                .workers(4)
                .iterations(1)
                //.trainElementsRepresentation(true)
                //.trainSequencesRepresentation(true)
                .useHierarchicSoftmax(false)
                //.sequenceLearningAlgorithm(new DBOW<>())
                .elementsLearningAlgorithm(new SkipGram<>())
                .iterate(iterator);

        return builder;
    }

    private static Glove.Builder newGloveBuilder(int vectorSize) {
        int minWordFrequency = 30;

        double learningRate = 0.05;
        double minLearningRate = 0.001;

        ZippedFileSequenceIterator iterator = new ZippedFileSequenceIterator(new File("/usb/word2vec_text/").listFiles(),0,500000000,-1);
        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });

        return new Glove.Builder()
                .iterate(iterator)
                .tokenizerFactory(tf)
                .alpha(0.75)
                .learningRate(learningRate)
                .minWordFrequency(minWordFrequency)
                .minLearningRate(minLearningRate)
                .layerSize(vectorSize)
                .workers(8)
                // number of epochs for training
                .epochs(1)
                // cutoff for weighting function
                .xMax(100)
                // training is done in batches taken from training corpus
                .batchSize(1000)
                .stopWords(new ArrayList<>(StopWords.getStopWords()))
                // if set to true word pairs will be built in both directions, LTR and RTL
                .symmetric(true);
    }



    public static void buildAndTrainModel() {
        Function<org.deeplearning4j.models.word2vec.Word2Vec,Void> saveWord2VecFunction = sequenceVectors->{
            System.out.println("Saving...");
            try {
                save(sequenceVectors);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };
        Function<Glove,Void> saveGloveFunction = sequenceVectors->{
            System.out.println("Saving...");
            try {
                save(sequenceVectors);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        Collection<String> words = Arrays.asList("multnomah","country","churchill","jefferson","washington","george","church","hospital","semiconductor","electricity","artificial","intelligence","chemistry","biology","vehicle","drone");
        Function<SequenceVectors<VocabWord>,Void> afterEpochFunction = (v) -> {
            for (String word : words) {
                Collection<String> lst = v.wordsNearest(word, 10);
                System.out.println("10 Words closest to '" + word + "': " + lst);
            }
            System.out.println("Vocab weights shape: "+Arrays.toString(v.getLookupTable().getWeights().shape()));
            return null;
        };

        // build train save
        // TODO run 128 model
        //net128 = newBuilder(128).build();
        //net256 = newBuilder(256).build();
        // TODO run 512 model
        //net512 = newBuilder(512).build();


        glove = newGloveBuilder(128).build();
        glove.fit();
        saveGloveFunction.apply(glove);
        afterEpochFunction.apply(glove);
    }
}
