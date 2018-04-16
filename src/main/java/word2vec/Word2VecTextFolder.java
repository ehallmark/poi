package main.java.word2vec;

import main.java.nlp.wikipedia.demo.RunPointsOfInterestDemo;
import main.java.predict.word2vec.WikipediaParagraphIterator;
import main.java.reddit.Comment;
import main.java.reddit.Postgres;
import main.java.reddit.word2vec.PostgresStreamingIterator;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.io.*;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Word2VecTextFolder {
    public static final File DATA_DIR = new File("word2vec_text");
    private static AtomicInteger fileCounter = new AtomicInteger(DATA_DIR.list().length);
    private static final int MAX_LINE_SIZE = 1000000;
    private static final int NUM_CHANNELS = 10;
    private static final AtomicLong[] lineCounters = new AtomicLong[NUM_CHANNELS];
    private static final BufferedWriter[] outputStreams = new BufferedWriter[NUM_CHANNELS];
    private static final Lock[] locks = new Lock[NUM_CHANNELS];
    private static final AtomicLong overallCounter = new AtomicLong(0);
    private static final Random rand = new Random(2352);
    static {
        for(int i = 0; i < NUM_CHANNELS; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public static void openAll() throws IOException {
        for(int i = 0; i < NUM_CHANNELS; i++) {
            open(i);
        }
    }

    public static void open(int channel) throws IOException {
        outputStreams[channel] = new BufferedWriter(new OutputStreamWriter(new GzipCompressorOutputStream(new FileOutputStream(new File(DATA_DIR, "text-" + fileCounter.getAndIncrement())))));
        lineCounters[channel] = new AtomicLong(0);
    }

    public static void closeAll() throws IOException{
        for(int i = 0; i < NUM_CHANNELS; i++) {
            closeChannel(i);
        }
    }

    public static void closeChannel(int channel) throws IOException {
        BufferedWriter outputStream = outputStreams[channel];
        if(outputStream!=null) {
            outputStream.flush();
            outputStream.close();
        }
    }

    public static void consume(String text) {
        if(text==null) return;
        text = PostgresStreamingIterator.preprocessText(text);
        if(text==null||text.isEmpty()) return;
        text = text.trim();
        if(text.isEmpty()) return;

        overallCounter.getAndIncrement();
        int randInt = rand.nextInt(NUM_CHANNELS);
        locks[randInt].lock();
        try {
            BufferedWriter outputStream = outputStreams[randInt];
            if (outputStream == null) {
                open(randInt);
            }
            AtomicLong lineCounter = lineCounters[randInt];
            // write
            outputStream.write(text + "\n");
            if (lineCounter.getAndIncrement() % MAX_LINE_SIZE == MAX_LINE_SIZE - 1) {
                // save and get new file
                System.out.println("Finished file: " + fileCounter.get() + ", Num documents seen: " + overallCounter.get());
                closeChannel(randInt);
                open(randInt);
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            locks[randInt].unlock();
        }
    }


    public static void main(String[] args)throws Exception {
        openAll();

        // wiki
        RecursiveAction action1 = new RecursiveAction() {
            @Override
            protected void compute() {
                WikipediaParagraphIterator iterator = new WikipediaParagraphIterator(new File(RunPointsOfInterestDemo.WIKI_FILE));
                while(iterator.hasMoreSequences()) {
                    Sequence<VocabWord> sequence = iterator.nextSequence();
                    if(sequence!=null&&sequence.size()>1) {
                        String text = String.join(" ",sequence.getElements().stream().map(v->v.getLabel()).collect(Collectors.toList()));
                        try {
                            consume(text);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };


        // reddit
        Consumer<Comment> consumer = comment ->{
            if(comment.getBody()!=null&&comment.getBody().length()>5) {
                try {
                    consume(comment.getBody());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };
        RecursiveAction action2 = new RecursiveAction() {
            @Override
            protected void compute() {
                try {
                    Postgres.iterateNoParents(consumer, -1);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };

        action1.fork();
        action2.fork();

        action1.join();
        action2.join();

        closeAll();
    }
}
