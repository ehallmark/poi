package main.java.reddit;

import org.nd4j.linalg.dataset.MultiDataSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class BuildDatasetsFromPostgres {
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

    public static void main(String[] args) throws Exception {
        Function<List<Comment>,MultiDataSet> commentListToDataSetFunction = null;

        final int batchSize = 1024;
        final List<Comment> comments = new ArrayList<>(batchSize);
        final AtomicInteger idx = new AtomicInteger(0);
        Consumer<Comment> commentConsumer = comment ->{
            if(comments.size()>=batchSize) {
                MultiDataSet dataSet = commentListToDataSetFunction.apply(new ArrayList<>(comments));
                if(dataSet!=null) {
                    File dataDir = sample(trainDir,testDir,devDir,0.95,0.025,0.025);
                    try {
                        dataSet.save(new File(dataDir, baseName + idx.getAndIncrement()));
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                comments.clear();
            }
            comments.add(comment);
        };

        Postgres.iterate(commentConsumer);

        System.out.println("Finished iterating...");

    }
}
