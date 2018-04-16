package main.java.predict.word2vec;

import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLParserFactory;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WikipediaParagraphIterator implements SequenceIterator<VocabWord> {
    private File file;
    private RecursiveAction task;
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    StreamingHandler handler;
    public WikipediaParagraphIterator(File file) {
        this.file = file;
        this.queue = new ArrayBlockingQueue<>(1000);
        reset();
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
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                WikiXMLParser wxsp = null;
                try {
                    wxsp = WikiXMLParserFactory.getSAXParser(file.getAbsolutePath());
                } catch (Exception e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    return;
                }

                try {
                    wxsp.setPageCallback(handler);

                    System.out.println("Starting to parse...");
                    wxsp.parse();
                    System.out.println("Parsed.");
                }catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };
        task.fork();

    }


}
