package main.java.reddit.character_level;

import lombok.NonNull;
import main.java.reddit.Comment;
import main.java.reddit.Postgres;
import main.java.reddit.word2vec.PostgresStreamingIterator;
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

public class BuildCharacterDatasets {
    public static final char END_TOKEN = 'E';
    public static final char UNK_TOKEN = 'U';
    private static final int UNK_TOKEN_IDX;
    public static final char[] VALID_CHARS = new char[]{'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',' ','?','!','.',UNK_TOKEN, END_TOKEN };
    private static final Map<Character,Integer> CHAR_IDX_MAP = new HashMap<>(VALID_CHARS.length);
    static {
        Arrays.sort(VALID_CHARS);
        UNK_TOKEN_IDX = Arrays.binarySearch(VALID_CHARS,UNK_TOKEN);
        for(int i = 0; i < VALID_CHARS.length; i++) {
            CHAR_IDX_MAP.put(VALID_CHARS[i],i);
        }
    }
    public static final int MAX_SENTENCE_LENGTH = 96; // max length of an input...
    public static final String baseName = "dataset-";
    public static final File trainDir = new File("reddit_datasets_train/");
    public static final File testDir = new File("reddit_datasets_test/");
    public static final File devDir = new File("reddit_datasets_dev/");
    private static final Random rand = new Random(2352);
    private static File sample(File f1, File f2, File f3, double d1, double d2, double d3) {
        double r = rand.nextDouble();
        if(r<=d1) return f1;
        else if(r<=d1+d2) return f2;
        else return f3;
    }

    public static Pair<Pair<float[][],float[]>,Pair<float[][],float[]>> textToVec(String text, String text2, int maxSentenceLength) {
        text = String.join(" ",text.toLowerCase().split("\\s+"))+END_TOKEN;
        text2 = String.join(" ",text2.toLowerCase().split("\\s+"))+END_TOKEN;
        if(text.length()>maxSentenceLength||text2.length()>maxSentenceLength) return null;
        if(text.length()<=1||text2.length()<=1) return null;

        float[][] x = new float[maxSentenceLength*2][VALID_CHARS.length];
        float[] mask = new float[maxSentenceLength*2];
        float[][] x2 = new float[maxSentenceLength*2][VALID_CHARS.length];
        float[] mask2 = new float[maxSentenceLength*2];
        for(int i = 0; i < maxSentenceLength*2; i++) {
            x[i] = new float[VALID_CHARS.length];
            x2[i] = new float[VALID_CHARS.length];
        }
        int i = 0;
        for(; i < text.length() && i < maxSentenceLength; i++) {
            mask[i]=1;
            char c = text.charAt(i);
            float[] xi = x[i];
            int pos = CHAR_IDX_MAP.getOrDefault(c,-1);
            if(pos>=0) {
                // exists
                xi[pos]=1;
            } else {
                xi[UNK_TOKEN_IDX]=1;
            }
        }
        for(; i < Math.min(maxSentenceLength,text.length())+text2.length() && i < 2*maxSentenceLength; i++) {
            mask2[i]=1;
            char c = text2.charAt(i-Math.min(maxSentenceLength,text.length()));
            float[] xi = x2[i];
            int pos = CHAR_IDX_MAP.getOrDefault(c,-1);
            if(pos>=0) {
                // exists
                xi[pos]=1;
            } else {
                xi[UNK_TOKEN_IDX]=1;
            }
        }
        return new Pair<>(new Pair<>(x,mask),new Pair<>(x2,mask2));
    }


    public static void main(String[] args) throws Exception {
        Function<List<Pair<Pair<float[][],float[]>,Pair<float[][],float[]>>>,MultiDataSet> pairsToDatasetFunction = pairs -> {
            INDArray input = Nd4j.create(pairs.size(),VALID_CHARS.length,MAX_SENTENCE_LENGTH*2);
            INDArray inputMask = Nd4j.create(pairs.size(),MAX_SENTENCE_LENGTH*2);
            INDArray output = Nd4j.create(pairs.size(),VALID_CHARS.length,MAX_SENTENCE_LENGTH*2);
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
            return textToVec(comment.getParent_body(),comment.getBody(),MAX_SENTENCE_LENGTH);
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
