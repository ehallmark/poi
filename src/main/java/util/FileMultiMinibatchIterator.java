package main.java.util;

import lombok.Setter;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.*;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FileMultiMinibatchIterator implements MultiDataSetIterator{
    public static final String DEFAULT_PATTERN = "dataset-%d";
    private AtomicInteger currIdx;
    private File rootDir;
    private int totalBatches;
    private final String pattern;
    private int[] shuffledIndices;
    private boolean testing;
    private MultiDataSetPreProcessor dataSetPreProcessor;
    @Setter
    private boolean compressed = false;
    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {
        this.dataSetPreProcessor=multiDataSetPreProcessor;
    }

    @Override
    public MultiDataSetPreProcessor getPreProcessor() {
        return dataSetPreProcessor;
    }

    private final List<RecursiveTask<List<MultiDataSet>>> dataSetQueue;
    private Iterator<MultiDataSet> currentIterator;
    private int miniBatch;


    public FileMultiMinibatchIterator(File rootDir, int limit, int miniBatch, boolean testing) {
        this(rootDir, DEFAULT_PATTERN, limit, miniBatch,testing);
    }


    public FileMultiMinibatchIterator(File rootDir, String pattern, int limit, int miniBatch, boolean testing) {
        this.totalBatches = -1;
        this.rootDir = rootDir;
        int numFiles = rootDir.list().length;
        this.totalBatches = limit > 0 ? Math.min(limit,numFiles) : numFiles;
        this.pattern = pattern;
        this.dataSetQueue = new ArrayList<>();
        this.currIdx = new AtomicInteger(0);
        this.miniBatch=miniBatch;
        this.shuffledIndices=new int[totalBatches];
        for(int i = 0; i < totalBatches; i++) {
            shuffledIndices[i]=i;
        }
        this.testing=testing;
        if(!testing)shuffleArray(shuffledIndices); // randomizes mini batch order
    }

    public MultiDataSet next(int num) {
        throw new UnsupportedOperationException("Unable to load custom number of examples");
    }

    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    public int inputColumns() {
        throw new UnsupportedOperationException();
    }

    public int totalOutcomes() {
        throw new UnsupportedOperationException();
    }

    public boolean resetSupported() {
        return true;
    }

    public boolean asyncSupported() {
        return false;
    }

    public void reset() {
        this.currIdx.set(0);
        if(!testing)shuffleArray(shuffledIndices); // randomizes mini batch order
    }

    public int batch() {
        throw new UnsupportedOperationException();
    }

    public int cursor() {
        return this.currIdx.get();
    }

    public int numExamples() {
        throw new UnsupportedOperationException();
    }


    public List<String> getLabels() {
        return null;
    }

    public boolean hasNext(){
        int nWorkers = 2;
        while(dataSetQueue.size()<nWorkers && this.currIdx.get()<shuffledIndices.length) {
            final int readIdx = shuffledIndices[this.currIdx.getAndIncrement()];
            RecursiveTask<List<MultiDataSet>> task = new RecursiveTask<List<MultiDataSet>>() {
                @Override
                protected List<MultiDataSet> compute() {
                   // System.gc();
                    try {
                        MultiDataSet e = read(readIdx);
                        if (dataSetPreProcessor != null) {
                            dataSetPreProcessor.preProcess(e);
                        }
                        //System.out.println("Shape: "+ Arrays.toString(e.getFeatures(0).shape()));

                        // split
                        if(miniBatch<e.getFeatures(0).shape()[0]&&miniBatch>0) {
                            return IntStream.range(0, e.getFeatures(0).shape()[0]/miniBatch).mapToObj(i->{
                                int start = i*miniBatch;
                                int end = Math.min(e.getFeatures(0).shape()[0],start+miniBatch);
                                if(start<end) {
                                    INDArray[] features = e.getFeatures().clone();
                                    for(int j = 0; j < features.length; j++) {
                                        int nDims = features[j].shape().length;
                                        INDArrayIndex[] indices = new INDArrayIndex[nDims];
                                        indices[0] = NDArrayIndex.interval(start,end);
                                        for(int k = 1; k < indices.length; k++) {
                                            indices[k] = NDArrayIndex.all();
                                        }
                                        features[j]=features[j].get(indices);
                                    }
                                    INDArray[] labels = e.getLabels().clone();
                                    for(int j = 0; j < labels.length; j++) {
                                        int nDims = labels[j].shape().length;
                                        INDArrayIndex[] indices = new INDArrayIndex[nDims];
                                        indices[0] = NDArrayIndex.interval(start,end);
                                        for(int k = 1; k < indices.length; k++) {
                                            indices[k] = NDArrayIndex.all();
                                        }
                                        labels[j]=labels[j].get(indices);
                                    }
                                    INDArray[] featuresMasks = null;
                                    if(e.getFeaturesMaskArrays()!=null) {
                                        featuresMasks = e.getFeaturesMaskArrays().clone();
                                        for (int j = 0; j < featuresMasks.length; j++) {
                                            int nDims = featuresMasks[j].shape().length;
                                            INDArrayIndex[] indices = new INDArrayIndex[nDims];
                                            indices[0] = NDArrayIndex.interval(start, end);
                                            for (int k = 1; k < indices.length; k++) {
                                                indices[k] = NDArrayIndex.all();
                                            }
                                            featuresMasks[j] = featuresMasks[j].get(indices);
                                        }
                                    }
                                    INDArray[] labelsMasks = null;
                                    if(e.getLabelsMaskArrays()!=null) {
                                        labelsMasks = e.getLabelsMaskArrays().clone();
                                        for (int j = 0; j < labelsMasks.length; j++) {
                                            int nDims = labelsMasks[j].shape().length;
                                            INDArrayIndex[] indices = new INDArrayIndex[nDims];
                                            indices[0] = NDArrayIndex.interval(start, end);
                                            for (int k = 1; k < indices.length; k++) {
                                                indices[k] = NDArrayIndex.all();
                                            }
                                            labelsMasks[j] = labelsMasks[j].get(indices);
                                        }
                                    }
                                    return new org.nd4j.linalg.dataset.MultiDataSet(
                                            features,
                                            labels,
                                            featuresMasks,
                                            labelsMasks
                                    );
                                }
                                return null;
                            }).filter(d->d!=null).collect(Collectors.toList());
                        } else {
                            return Collections.singletonList(e);
                        }
                    } catch (Exception var2) {
                        var2.printStackTrace();
                        System.out.println("Reading file: "+readIdx);
                        throw new IllegalStateException("Unable to read dataset");
                    }
                }
            };
            task.fork();

            dataSetQueue.add(task);
        }
        return (currentIterator!=null&&currentIterator.hasNext())||dataSetQueue.size()>0;
    }

    public MultiDataSet next() {
        return nextDataSet();
    }

    private MultiDataSet nextDataSet() {
        if(currentIterator==null||!currentIterator.hasNext()) {
            currentIterator = dataSetQueue.remove(0).join().iterator();
        }
        return currentIterator.next();
    }

    private MultiDataSet read(int idx) throws IOException {
        File path = new File(this.rootDir, String.format(this.pattern, new Object[]{Integer.valueOf(idx)}));
        InputStream inputStream = new BufferedInputStream(new FileInputStream(path));
        if(compressed) {
           // System.out.println(path.getAbsolutePath());
            inputStream = new GzipCompressorInputStream(inputStream);
        }
        MultiDataSet d =new org.nd4j.linalg.dataset.MultiDataSet();
        d.load(inputStream);
        return d;
    }

    private static Random random = new Random(System.currentTimeMillis());
    public static void shuffleArray(int[] a) {
        int n = a.length;
        random.nextInt();
        for (int i = 0; i < n; i++) {
            int change = i + random.nextInt(n - i);
            swap(a, i, change);
        }
    }

    private static void swap(int[] a, int i, int change) {
        int helper = a[i];
        a[i] = a[change];
        a[change] = helper;
    }
}
