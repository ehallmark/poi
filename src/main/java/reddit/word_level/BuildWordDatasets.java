package main.java.reddit.word_level;

import main.java.predict.Database;
import main.java.reddit.Comment;
import main.java.reddit.Postgres;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
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

public class BuildWordDatasets {
    public static final String UNK_TOKEN = "(UNK)";
    public static final String END_TOKEN = "(END)";
    public static final int MAX_SENTENCE_LENGTH = 16; // max length of an input...
    public static final int VOCAB_SIZE = 5000;
    public static final String baseName = "dataset-";
    public static final File trainDir = new File("reddit_word_datasets_train/");
    public static final File testDir = new File("reddit_word_datasets_test/");
    public static final File devDir = new File("reddit_word_datasets_dev/");
    private static final Random rand = new Random(2352);
    private static File sample(File f1, File f2, File f3, double d1, double d2, double d3) {
        double r = rand.nextDouble();
        if(r<=d1) return f1;
        else if(r<=d1+d2) return f2;
        else return f3;
    }

    private static String[] cleanInputToWords(String textStr) {
        return (textStr.replace("[^a-z ]"," ").toLowerCase()+" "+END_TOKEN).split("\\s+");
    }

    public static Pair<Pair<float[][],float[]>,Pair<float[][],float[]>> textToVec(String textStr, String textStr2, int maxSentenceLength, Map<String,Integer> vocabMap, int unknownIdx) {
        String[] text = cleanInputToWords(textStr);
        String[] text2 = cleanInputToWords(textStr2);

        if(text.length>maxSentenceLength||text2.length>maxSentenceLength) return null;
        if(text.length<=1||text2.length<=1) return null;

        float[][] x = new float[maxSentenceLength*2][vocabMap.size()];
        float[] mask = new float[maxSentenceLength*2];
        float[][] x2 = new float[maxSentenceLength*2][vocabMap.size()];
        float[] mask2 = new float[maxSentenceLength*2];
        for(int i = 0; i < maxSentenceLength*2; i++) {
            x[i] = new float[vocabMap.size()];
            x2[i] = new float[vocabMap.size()];
        }
        int i = 0;
        for(; i < text.length && i < maxSentenceLength; i++) {
            mask[i]=1;
            String c = text[i];
            float[] xi = x[i];
            int pos = vocabMap.getOrDefault(c,-1);
            if(pos>=0) {
                // exists
                xi[pos]=1;
            } else {
                xi[unknownIdx]=1;
            }
        }
        for(; i < Math.min(maxSentenceLength,text.length)+text2.length && i < 2*maxSentenceLength; i++) {
            mask2[i]=1;
            String c = text2[i-Math.min(maxSentenceLength,text.length)];
            float[] xi = x2[i];
            int pos = vocabMap.getOrDefault(c,-1);
            if(pos>=0) {
                // exists
                xi[pos]=1;
            } else {
                xi[unknownIdx]=1;
            }
        }
        return new Pair<>(new Pair<>(x,mask),new Pair<>(x2,mask2));
    }

    private static Map<String,Integer> VOCABULARY;
    private static final File VOCABULARY_FILE = new File("reddit_word_model_vocabulary.jobj");
    private static Map<String,Integer> buildVocabulary(int maxSentenceLength,int maxVocabSize) {
        if(VOCABULARY==null) {
            VOCABULARY = (Map<String,Integer>)Database.loadObject(VOCABULARY_FILE);
            if(VOCABULARY==null) {
                // build it
                Map<String,Integer> frequencyMap = Collections.synchronizedMap(new HashMap<>());
                final int vocabSampling = 10000000;
                Consumer<Comment> consumer = comment->{
                    String[] text = cleanInputToWords(comment.getBody());
                    if(text.length>maxSentenceLength) return;
                    if(text.length<=1) return;
                    Stream.of(text).distinct().forEach(w->{
                        frequencyMap.put(w,frequencyMap.getOrDefault(w,0)+1);
                    });
                    frequencyMap.put(UNK_TOKEN,frequencyMap.getOrDefault(UNK_TOKEN,0)+1);
                    frequencyMap.put(END_TOKEN,frequencyMap.getOrDefault(END_TOKEN,0)+1);
                };
                try {
                    Postgres.iterateNoParents(consumer, vocabSampling);
                    System.out.println("Total words found: "+frequencyMap.size());
                    final AtomicInteger idx = new AtomicInteger(0);
                    VOCABULARY = Collections.synchronizedMap(new HashMap<>());
                    frequencyMap.entrySet().parallelStream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                            .limit(maxVocabSize)
                            .forEach(e->{
                                VOCABULARY.put(e.getKey(),idx.getAndIncrement());
                            });
                    System.out.println("Vocabulary size: "+VOCABULARY.size());
                    Database.saveObject(VOCABULARY, VOCABULARY_FILE);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return VOCABULARY;
    }

    public static void main(String[] args) throws Exception {
        Map<String,Integer> vocabMap = buildVocabulary(MAX_SENTENCE_LENGTH,VOCAB_SIZE);
        final int unknownIdx = vocabMap.get(UNK_TOKEN);


        Function<List<Pair<Pair<float[][],float[]>,Pair<float[][],float[]>>>,MultiDataSet> pairsToDatasetFunction = pairs -> {
            INDArray input = Nd4j.create(pairs.size(),vocabMap.size(),MAX_SENTENCE_LENGTH*2);
            INDArray inputMask = Nd4j.create(pairs.size(),MAX_SENTENCE_LENGTH*2);
            INDArray output = Nd4j.create(pairs.size(),vocabMap.size(),MAX_SENTENCE_LENGTH*2);
            INDArray outputMask = Nd4j.create(pairs.size(),MAX_SENTENCE_LENGTH*2);
            AtomicInteger idx = new AtomicInteger(0);
            pairs.forEach(pair->{
                int i = idx.getAndIncrement();
                input.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(Nd4j.create(pair.getFirst().getFirst()).transposei());
                output.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(Nd4j.create(pair.getSecond().getFirst()).transposei());
                inputMask.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(Nd4j.create(pair.getFirst().getSecond()));
                outputMask.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(Nd4j.create(pair.getSecond().getSecond()));
            });

            return new MultiDataSet(new INDArray[]{input},new INDArray[]{output},new INDArray[]{inputMask},new INDArray[]{outputMask});
        };


        Function<Comment,Pair<Pair<float[][],float[]>,Pair<float[][],float[]>>> commentListToDataSetFunction = comment -> {
            return textToVec(comment.getParent_body(),comment.getBody(),MAX_SENTENCE_LENGTH,vocabMap,unknownIdx);
        };

        final int batchSize = 2048;
        final List<Pair<Pair<float[][],float[]>,Pair<float[][],float[]>>> comments = new ArrayList<>(batchSize);
        final Map<String,AtomicInteger> folderToDatasetCount = new HashMap<>();
        folderToDatasetCount.put(trainDir.getAbsolutePath(),new AtomicInteger(0));
        folderToDatasetCount.put(testDir.getAbsolutePath(),new AtomicInteger(0));
        folderToDatasetCount.put(devDir.getAbsolutePath(),new AtomicInteger(0));
        AtomicReference<RecursiveAction> task = new AtomicReference<>(null);
        Consumer<Comment> commentConsumer = comment ->{
            Pair<Pair<float[][],float[]>,Pair<float[][],float[]>> pair = commentListToDataSetFunction.apply(comment);
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
