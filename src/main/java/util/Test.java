package main.java.util;

import main.java.word2vec.ZippedFileSequenceIterator;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    public static void main(String[] args) {
        ZippedFileSequenceIterator iterator = new ZippedFileSequenceIterator(new File("word2vec_text/").listFiles(),20,20, -1);
        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });

        org.deeplearning4j.models.word2vec.Word2Vec.Builder builder = new org.deeplearning4j.models.word2vec.Word2Vec.Builder()
                .seed(41)
                .batchSize(5)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(5)
                .layerSize(5)
                .sampling(0)
                //.stopWords(stopWords)
                .negativeSample(8)
                .learningRate(0.001)
                .minLearningRate(0.0001)
                .allowParallelTokenization(true)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(2)
                .limitVocabularySize(100000000)
                .tokenizerFactory(tf)
                .workers(1)
                .iterations(1)
                //.trainElementsRepresentation(true)
                //.trainSequencesRepresentation(true)
                .useHierarchicSoftmax(false)
                //.sequenceLearningAlgorithm(new DBOW<>())
                .elementsLearningAlgorithm(new SkipGram<>())
                .iterate(iterator);

        Word2Vec word2Vec = builder.build();
        word2Vec.fit();


        System.out.println("Finished.");

    }
}
