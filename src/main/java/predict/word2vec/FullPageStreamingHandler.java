package main.java.predict.word2vec;

import main.java.nlp.wikipedia.PageCallbackHandler;
import main.java.nlp.wikipedia.WikiPage;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An even simpler callback demo.
 *
 * @author Jason Smith
 * @see PageCallbackHandler
 *
 */

public class FullPageStreamingHandler implements PageCallbackHandler {
    private Consumer<WikiPage> consumer;
    public FullPageStreamingHandler(Consumer<WikiPage> consumer) {
        this.consumer=consumer;
    }
    public void process(WikiPage page) {
        consumer.accept(page);
    }
}
