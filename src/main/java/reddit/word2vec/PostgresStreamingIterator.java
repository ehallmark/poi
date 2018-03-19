package main.java.reddit.word2vec;

import lombok.Setter;
import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLSAXParser;
import main.java.predict.word2vec.StreamingHandler;
import main.java.reddit.Postgres;
import main.java.util.ZipStream;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/21/17.
 */
public class PostgresStreamingIterator implements SequenceIterator<VocabWord> {
    private RecursiveAction task;
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    StreamingHandler handler;
    @Setter
    private int vocabSampling;
    public PostgresStreamingIterator(int vocabSampling) {
        this.vocabSampling=vocabSampling;
        this.queue = new ArrayBlockingQueue<>(10000);
        //reset();
    }

    private boolean hasNextDocument() {
        return queue.size()>0 || !task.isDone();
    }


    @Override
    public boolean hasMoreSequences() {
        return hasNextDocument();
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        while (!task.isDone() && queue.isEmpty()) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return queue.poll();
    }

    @Override
    public void reset() {
        if(task!=null && !task.isDone()) return;
        queue.clear();
        handler = new StreamingHandler(queue);
        final int vSampling = vocabSampling;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                try {
                    Postgres.iterateNoParents(comment -> {
                        String text = preprocessText(comment.getBody());
                        if(text.isEmpty()) return;
                        Collection<VocabWord> words = Stream.of(text.split(" ")).map(word->{
                            if(word==null||word.isEmpty()) return null;
                            VocabWord vocabWord = new VocabWord(1,word);
                            vocabWord.setElementFrequency(1);
                            vocabWord.setSequencesCount(1);
                            return vocabWord;
                        }).filter(v->v!=null).collect(Collectors.toList());
                        Sequence<VocabWord> sequence = new Sequence<>(words);
                        try {
                            queue.put(sequence);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    },vSampling);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };
        vocabSampling=-1;
        task.fork();

    }

    public static String preprocessText(String text) {
        return text.toLowerCase().replaceAll("[^a-z ]"," ");
    }


}
