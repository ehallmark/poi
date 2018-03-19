package main.java.reddit.word2vec;

import lombok.Getter;
import main.java.nlp.wikipedia.demo.SAXParserDemo;
import main.java.predict.word2vec.WikipediaParagraphIterator;
import main.java.util.StopWords;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class RedditWord2Vec {
    private static final File modelFile = new File("reddit_word2vec_model.nn");
    private static final int BATCH_SIZE = 1024;
    private static final int VECTOR_SIZE = 128;
    @Getter
    private static org.deeplearning4j.models.word2vec.Word2Vec net;
    private static void save(org.deeplearning4j.models.word2vec.Word2Vec paragraphVectors) {
        WordVectorSerializer.writeWord2VecModel(paragraphVectors,modelFile.getAbsolutePath());
    }

    public static void main(String[] args) {
        Function<org.deeplearning4j.models.word2vec.Word2Vec,Void> saveFunction = sequenceVectors->{
            System.out.println("Saving...");
            try {
                save(sequenceVectors);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        Collection<String> words = Arrays.asList("multnomah","pov","gender","sex","halo","cod","country","church","hospital","semiconductor","electricity","artificial","intelligence","chemistry","biology","vehicle","drone");
        Function<Void,Void> afterEpochFunction = (v) -> {
            for (String word : words) {
                Collection<String> lst = getNet().wordsNearest(word, 10);
                System.out.println("10 Words closest to '" + word + "': " + lst);
            }
            saveFunction.apply(getNet());
            return null;
        };


        int windowSize = 7;
        int minWordFrequency = 50;
        double negativeSampling = 20;
        double sampling = 0.0001;
        //double learningRate = 0.1;
        //double minLearningRate = 0.001;
        double learningRate =   0.05;
        double minLearningRate = 0.001;
        int vocabSampling = 100000000;

        try {
            System.out.println("Trying to load model....");
            net = WordVectorSerializer.readWord2VecModel(modelFile);
            System.out.println("Finished loading model.");
        } catch(Exception e) {
            e.printStackTrace();
            net = null;
        }

        PostgresStreamingIterator iterator = new PostgresStreamingIterator(vocabSampling);
        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });


        boolean newModel = net == null;
        org.deeplearning4j.models.word2vec.Word2Vec.Builder builder = new org.deeplearning4j.models.word2vec.Word2Vec.Builder()
                .seed(41)
                .batchSize(BATCH_SIZE)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(windowSize)
                .layerSize(VECTOR_SIZE)
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .minLearningRate(minLearningRate)
                .allowParallelTokenization(true)
                .useAdaGrad(true)
                .resetModel(newModel)
                .minWordFrequency(minWordFrequency)
                .tokenizerFactory(tf)
                .workers(Math.max(1,Runtime.getRuntime().availableProcessors()/2))
                .iterations(1)
                //.trainElementsRepresentation(true)
                //.trainSequencesRepresentation(true)
                .useHierarchicSoftmax(false)
                //.sequenceLearningAlgorithm(new DBOW<>())
                .elementsLearningAlgorithm(new SkipGram<>())
                .iterate(iterator);
        if(!newModel) {
            System.out.println("Using previous model...");
            iterator.setVocabSampling(-1);
            builder = builder
                    .vocabCache(net.vocab())
                    .lookupTable(net.lookupTable());
        }
        iterator.reset();

        // build train save
        net = builder.build();
        net.fit();
        afterEpochFunction.apply(null);
    }
}
