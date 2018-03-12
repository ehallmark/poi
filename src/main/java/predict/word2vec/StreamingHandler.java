package main.java.predict.word2vec;

import main.java.nlp.wikipedia.InfoBox;
import main.java.nlp.wikipedia.PageCallbackHandler;
import main.java.nlp.wikipedia.WikiPage;
import main.java.predict.Database;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static main.java.predict.Database.*;

/**
 * An even simpler callback demo.
 *
 * @author Jason Smith
 * @see PageCallbackHandler
 *
 */

public class StreamingHandler implements PageCallbackHandler {
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    public StreamingHandler(ArrayBlockingQueue<Sequence<VocabWord>> queue) {
        this.queue=queue;
    }
    private static final AtomicLong total = new AtomicLong(0);
    public void process(WikiPage page) {
        String text = page.getText();
        Stream.of(text.split("\\.\\s+")).filter(word->word!=null&&word.length()>0).forEach(line-> {
            List<VocabWord> words = Arrays.stream(line.split("\\s+")).map(str -> {
                str = str.toLowerCase().replaceAll("[^a-z0-9\\-]","");
                if(str.isEmpty()) return null;
                if(!Character.isAlphabetic(str.charAt(0))) return null;
                VocabWord word = new VocabWord(1, str);
                word.setElementFrequency(1);
                word.setSequencesCount(1);
                word.setSpecial(false);
                return word;
            }).filter(t->t!=null).collect(Collectors.toList());
            if(words.size()<3) return;
            Sequence<VocabWord> sequence = new Sequence<>(words);
            try {
                if(total.getAndIncrement()%1000==999) {
                    System.out.println("Finished "+total.get());
                }
                queue.put(sequence);
            } catch(Exception e) {
                System.out.println("Interrupted...");
                e.printStackTrace();
            }
        });
    }
}
