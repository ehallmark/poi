package main.java.reddit.vector_level;

import main.java.predict.Database;
import main.java.predict.word2vec.WikiWord2Vec;
import main.java.reddit.Comment;
import main.java.reddit.Postgres;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class BuildWord2VecDatasets {
    public static final int MAX_SENTENCE_LENGTH = 16; // max length of an input...
    public static final int VOCAB_SIZE = 5000;
    public static final String baseName = "dataset-";
    public static final File trainDir = new File("reddit_vec_datasets_train/");
    public static final File testDir = new File("reddit_vec_datasets_test/");
    public static final File devDir = new File("reddit_vec_datasets_dev/");
    private static final Random rand = new Random(2352);
    private static File sample(File f1, File f2, File f3, double d1, double d2, double d3) {
        double r = rand.nextDouble();
        if(r<=d1) return f1;
        else if(r<=d1+d2) return f2;
        else return f3;
    }

    private static String[] cleanInputToWords(String textStr, Word2Vec word2Vec) {
        return Arrays.stream(textStr.replaceAll("[^a-z ]"," ").toLowerCase().split("\\s+"))
                .filter(word2Vec::hasWord).toArray(size->new String[size]);
    }

    public static Pair<Pair<INDArray,INDArray>,Pair<INDArray,INDArray>> textToVec(String textStr, String textStr2, int maxSentenceLength, Word2Vec word2Vec) {
        String[] text = cleanInputToWords(textStr,word2Vec);
        String[] text2 = cleanInputToWords(textStr2,word2Vec);
        if(text.length>maxSentenceLength||text2.length>maxSentenceLength) return null;
        if(text.length<=1||text2.length<=1) return null;

        INDArray mask = Nd4j.create(maxSentenceLength*2);
        mask.get(NDArrayIndex.interval(0,text.length)).assign(1);
        mask.get(NDArrayIndex.interval(text.length,maxSentenceLength*2)).assign(0);

        INDArray input = Nd4j.create(maxSentenceLength*2,word2Vec.getLayerSize());
        input.get(NDArrayIndex.interval(text.length,maxSentenceLength*2)).assign(0);
        input.get(NDArrayIndex.interval(0,text.length)).assign(word2Vec.getWordVectors(Arrays.asList(text)));

        INDArray mask2 = Nd4j.create(maxSentenceLength*2);
        mask2.get(NDArrayIndex.interval(0,text.length)).assign(0);
        mask2.get(NDArrayIndex.interval(text.length,text.length+text2.length)).assign(1);
        if(text.length+text2.length<maxSentenceLength*2) mask2.get(NDArrayIndex.interval(text.length+text2.length,maxSentenceLength*2)).assign(0);

        INDArray input2 = Nd4j.create(maxSentenceLength*2,word2Vec.getLayerSize());
        input2.get(NDArrayIndex.all(),NDArrayIndex.interval(0,text.length)).assign(0);
        input2.get(NDArrayIndex.all(),NDArrayIndex.interval(text.length,text.length+text2.length)).assign(word2Vec.getWordVectors(Arrays.asList(text2)));
        if(text.length+text2.length<maxSentenceLength*2) mask2.get(NDArrayIndex.all(),NDArrayIndex.interval(text.length+text2.length,maxSentenceLength*2)).assign(0);
        return new Pair<>(new Pair<>(input,mask),new Pair<>(input2,mask2));
    }

    public static void main(String[] args) throws Exception {
        Word2Vec word2Vec = WordVectorSerializer.readWord2VecModel(WikiWord2Vec.modelFile);

        Function<List<Pair<Pair<INDArray,INDArray>,Pair<INDArray,INDArray>>>,MultiDataSet> pairsToDatasetFunction = pairs -> {
            INDArray input = Nd4j.create(pairs.size(),word2Vec.getLayerSize(),MAX_SENTENCE_LENGTH*2);
            INDArray inputMask = Nd4j.create(pairs.size(),MAX_SENTENCE_LENGTH*2);
            INDArray output = Nd4j.create(pairs.size(),word2Vec.getLayerSize(),MAX_SENTENCE_LENGTH*2);
            INDArray outputMask = Nd4j.create(pairs.size(),MAX_SENTENCE_LENGTH*2);
            input.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.interval(MAX_SENTENCE_LENGTH,MAX_SENTENCE_LENGTH*2)).assign(0);
            output.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.interval(0,MAX_SENTENCE_LENGTH)).assign(0);
            inputMask.get(NDArrayIndex.all(),NDArrayIndex.interval(MAX_SENTENCE_LENGTH,MAX_SENTENCE_LENGTH*2)).assign(0);
            outputMask.get(NDArrayIndex.all(),NDArrayIndex.interval(0,MAX_SENTENCE_LENGTH)).assign(0);
            AtomicInteger idx = new AtomicInteger(0);
            pairs.forEach(pair->{
                int i = idx.getAndIncrement();
                input.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.interval(0,MAX_SENTENCE_LENGTH)).assign(pair.getFirst().getFirst().transposei());
                output.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.interval(MAX_SENTENCE_LENGTH,MAX_SENTENCE_LENGTH*2)).assign(pair.getSecond().getFirst().transposei());
                inputMask.get(NDArrayIndex.point(i),NDArrayIndex.interval(0,MAX_SENTENCE_LENGTH)).assign(pair.getFirst().getSecond());
                outputMask.get(NDArrayIndex.point(i),NDArrayIndex.interval(MAX_SENTENCE_LENGTH,MAX_SENTENCE_LENGTH*2)).assign(pair.getSecond().getSecond());
            });

            return new MultiDataSet(new INDArray[]{input},new INDArray[]{output},new INDArray[]{inputMask},new INDArray[]{outputMask});
        };


        Function<Comment,Pair<Pair<INDArray,INDArray>,Pair<INDArray,INDArray>>> commentListToDataSetFunction = comment -> {
            return textToVec(comment.getParent_body(),comment.getBody(),MAX_SENTENCE_LENGTH,word2Vec);
        };

        final int batchSize = 2048;
        final List<Pair<Pair<INDArray,INDArray>,Pair<INDArray,INDArray>>> comments = new ArrayList<>(batchSize);
        final Map<String,AtomicInteger> folderToDatasetCount = new HashMap<>();
        folderToDatasetCount.put(trainDir.getAbsolutePath(),new AtomicInteger(0));
        folderToDatasetCount.put(testDir.getAbsolutePath(),new AtomicInteger(0));
        folderToDatasetCount.put(devDir.getAbsolutePath(),new AtomicInteger(0));
        AtomicReference<RecursiveAction> task = new AtomicReference<>(null);
        Consumer<Comment> commentConsumer = comment ->{
            Pair<Pair<INDArray,INDArray>,Pair<INDArray,INDArray>> pair = commentListToDataSetFunction.apply(comment);
            if(pair!=null) {
                if(comments.size()>=batchSize) {
                    MultiDataSet dataSet = pairsToDatasetFunction.apply(comments);
                    if(dataSet!=null) {
                        File dir = sample(trainDir,testDir,devDir,0.95,0.025,0.025);
                        File newFile = new File(dir, baseName+folderToDatasetCount.get(dir.getAbsolutePath()).getAndIncrement());
                        if(!newFile.exists()) {
                            if(task.get()!=null) task.get().join();
                            RecursiveAction t = new RecursiveAction() {
                                @Override
                                protected void compute() {
                                    try {
                                        System.out.println("Saving " + newFile.getAbsolutePath());
                                        dataSet.save(new GzipCompressorOutputStream(new FileOutputStream(newFile)));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            t.fork();
                            task.set(t);
                        }
                    }
                    comments.clear();
                }
                comments.add(pair);
            }
        };

        Postgres.iterate(commentConsumer);

        System.out.println("Finished iterating...");

    }
}
