package main.java.util;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.util.Random;

public class CombinedSequenceIterator implements SequenceIterator<VocabWord> {
    private SequenceIterator<VocabWord> iterator1;
    private SequenceIterator<VocabWord> iterator2;
    private static final Random rand = new Random(2352);
    private double p1;
    public CombinedSequenceIterator(SequenceIterator<VocabWord> iterator1, SequenceIterator<VocabWord> iterator2, double p1) {
        this.iterator1=iterator1;
        this.iterator2=iterator2;
        this.p1=p1;
    }

    @Override
    public boolean hasMoreSequences() {
        return iterator2.hasMoreSequences()&&iterator1.hasMoreSequences();
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        if(rand.nextDouble()<p1) {
            return iterator1.nextSequence();
        } else {
            return iterator2.nextSequence();
        }
    }

    @Override
    public void reset() {
        iterator1.reset();
        iterator2.reset();
    }
}
