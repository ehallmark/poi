package main.java.controversiality.character;

import main.java.reddit.Comment;
import main.java.reddit.Postgres;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class BuildCharacterDatasets {
    public static final char UNK_TOKEN = 'U';
    private static final int UNK_TOKEN_IDX;
    public static final char[] VALID_CHARS = new char[]{'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',' ',',','?','!','.',UNK_TOKEN };
    public static final Map<Character,Integer> CHAR_IDX_MAP = new HashMap<>(VALID_CHARS.length);
    static {
        Arrays.sort(VALID_CHARS);
        UNK_TOKEN_IDX = Arrays.binarySearch(VALID_CHARS,UNK_TOKEN);
        for(int i = 0; i < VALID_CHARS.length; i++) {
            CHAR_IDX_MAP.put(VALID_CHARS[i],i);
            INDArray vec = Nd4j.zeros(VALID_CHARS.length);
            vec.putScalar(i,1f);
        }
    }
    public static final int MAX_SENTENCE_LENGTH = 140; // max length of an input...
    public static final int WINDOW_SIZE = 5; // max length of prediction
    public static final String baseName = "dataset-";
    public static final File trainDir = new File("reddit_character_controversial_datasets_train/");
    public static final File testDir = new File("reddit_character_controversial_datasets_test/");
    public static final File devDir = new File("reddit_character_controversial_datasets_dev/");
    private static final Random rand = new Random(2352);
    private static File sample(File f1, File f2, File f3, double d1, double d2, double d3) {
        double r = rand.nextDouble();
        if(r<=d1) return f1;
        else if(r<=d1+d2) return f2;
        else return f3;
    }

    public static String[] vectorsToStrings(INDArray vec3d, INDArray mask2d) {
        if(vec3d.shape().length==2) vec3d = vec3d.reshape(1,vec3d.shape()[0],vec3d.shape()[1]);
        if(mask2d!=null&&mask2d.shape().length==1) mask2d = mask2d.reshape(1,mask2d.shape()[0]);
        int[] mask1d = mask2d==null?null:mask2d.data().asInt();
        INDArray argMax = Nd4j.argMax(vec3d,1);
        int[] indices = argMax.data().asInt();
        String[] ret = new String[vec3d.shape()[0]];
        int l = vec3d.shape()[2];
        for(int i = 0; i < argMax.rows(); i++) {
            char[] chars = new char[l];
            for (int j = 0; j < l; j++) {
                if(mask1d==null||mask1d[i*l+j]>0) {
                    chars[j] = VALID_CHARS[indices[i*l+j]];
                    //System.out.println("Char \'"+chars[j]+"\': "+max[i*l+j]);
                } else {
                    chars[j] = '*';
                }
            }
            ret[i] = new String(chars);
        }
        return ret;
    }

    public static MultiDataSet textToVec(String text, int maxSentenceLength, int controversiality) {
        text = String.join(" ",text.toLowerCase().split("\\s+"));
        if(text.isEmpty()) return null;
        if(text.length()>maxSentenceLength) {
            int randStart = rand.nextInt(text.length()-maxSentenceLength);
            text = text.substring(randStart,randStart+maxSentenceLength);
        }

        INDArray mask = Nd4j.create(maxSentenceLength);
        mask.get(NDArrayIndex.interval(0,text.length())).assign(1);
        if(text.length()<maxSentenceLength)mask.get(NDArrayIndex.interval(text.length(),maxSentenceLength)).assign(0);

        INDArray x = Nd4j.zeros(VALID_CHARS.length,maxSentenceLength);
        INDArray y = Nd4j.zeros(2);
        int i = 0;
        for(; i < text.length() && i < maxSentenceLength; i++) {
            char c = text.charAt(i);
            int pos = CHAR_IDX_MAP.getOrDefault(c,-1);
            if(pos<0) {
                pos = UNK_TOKEN_IDX;
            }
            x.putScalar(pos,i,1f);
        }
        y.putScalar(controversiality,1f);


        if(i < maxSentenceLength) {
            mask.get(NDArrayIndex.interval(i,maxSentenceLength)).assign(0);
            x.get(NDArrayIndex.all(),NDArrayIndex.interval(i,maxSentenceLength)).assign(0);
        }
        return new MultiDataSet(new INDArray[]{x},new INDArray[]{y},new INDArray[]{mask},null);
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);

        Function<List<MultiDataSet>,MultiDataSet> pairsToDatasetFunction = pairs -> {
            INDArray input = Nd4j.create(pairs.size(),VALID_CHARS.length,MAX_SENTENCE_LENGTH);
            INDArray inputMask = Nd4j.create(pairs.size(),MAX_SENTENCE_LENGTH);
            INDArray output = Nd4j.create(pairs.size(),2);
            AtomicInteger idx = new AtomicInteger(0);
            pairs.forEach(pair->{
                int i = idx.getAndIncrement();
                input.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(pair.getFeatures(0));
                output.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(pair.getLabels(0));
                inputMask.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(pair.getFeaturesMaskArray(0));
            });
            return new MultiDataSet(new INDArray[]{input},new INDArray[]{output},new INDArray[]{inputMask},null);
        };


        Function<Comment,MultiDataSet> commentListToDataSetFunction = comment -> {
            return textToVec(comment.getBody(),MAX_SENTENCE_LENGTH,comment.getControversiality());
        };

        final int batchSize = 2048;
        final List<MultiDataSet> comments = new ArrayList<>(batchSize);
        final Map<String,AtomicInteger> folderToDatasetCount = new HashMap<>();
        folderToDatasetCount.put(trainDir.getAbsolutePath(),new AtomicInteger(0));
        folderToDatasetCount.put(testDir.getAbsolutePath(),new AtomicInteger(0));
        folderToDatasetCount.put(devDir.getAbsolutePath(),new AtomicInteger(0));
        AtomicReference<RecursiveAction> task = new AtomicReference<>(null);
        Consumer<Comment> commentConsumer = comment ->{
            MultiDataSet ds = commentListToDataSetFunction.apply(comment);
            if(ds!=null) {
                if(comments.size()>=batchSize) {
                    File dir = sample(trainDir,testDir,devDir,0.95,0.025,0.025);
                    File newFile = new File(dir, baseName+folderToDatasetCount.get(dir.getAbsolutePath()).getAndIncrement());
                    if(!newFile.exists()) {
                        MultiDataSet dataSet = pairsToDatasetFunction.apply(comments);
                        if(dataSet!=null) {
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
                comments.add(ds);
            }
        };

        Postgres.iterateForControversiality(commentConsumer,7000000);

        System.out.println("Finished iterating...");

    }
}
