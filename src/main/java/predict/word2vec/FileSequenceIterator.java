package main.java.predict.word2vec;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/21/17.
 */
public class FileSequenceIterator implements SequenceIterator<VocabWord> {
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private Function<Void,Void> afterEpochFunction;
    private SequenceIterator<VocabWord> iterator;
    public FileSequenceIterator(SequenceIterator<VocabWord> iterator, int numEpochs, Function<Void,Void> afterEpochFunction) {
        this.numEpochs=numEpochs;
        this.queue = new ArrayBlockingQueue<>(5000);
        this.vocabPass=true;
        this.iterator=iterator;
        this.afterEpochFunction=afterEpochFunction;
    }

    public void setRunVocab(boolean vocab) {
        this.vocabPass=vocab;
    }


    public boolean hasNextDocument() {
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
        final boolean singleEpoch = vocabPass;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                int finalNumEpochs = singleEpoch ? 1 : numEpochs;
                System.out.println("Running "+finalNumEpochs+" epochs: "+finalNumEpochs);
                for(int i = 0; i < finalNumEpochs; i++) {
                    while(iterator.hasMoreSequences()) {
                        Sequence<VocabWord> document = iterator.nextSequence();
                        try {
                            queue.put(document);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    iterator.reset();
                    System.out.println("Finished epoch: "+(i+1));
                    // Evaluate model
                    if(afterEpochFunction!=null)afterEpochFunction.apply(null);
                }
            }
        };
        task.fork();
        vocabPass=false;
    }


}
