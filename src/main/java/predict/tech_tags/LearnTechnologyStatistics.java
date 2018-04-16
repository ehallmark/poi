package main.java.predict.tech_tags;

import main.java.predict.Database;
import main.java.util.StopWords;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LearnTechnologyStatistics {
    private static final Function<String,String[]> textToWordFunction = text -> {
        return text.toLowerCase().replaceAll("[^a-z ]"," ").split("\\s+");
    };

    public static final File matrixFile = new File("tech_tag_statistics_matrix.jobj");
    public static void main(String[] args) throws Exception {
        final Set<String> stopWords = new HashSet<>(StopWords.getStopWords());
        final int minVocabSize = 3;
        final int vocabLimit = 40000;
        Map<String,AtomicLong> vocabCount = new HashMap<>();
        Map<String,AtomicLong> docCount = new HashMap<>();
        Set<String> allTitles = new HashSet<>();
        Consumer<CategoryWithText> vocabConsumer = category -> {
            String[] words = textToWordFunction.apply(category.getText());
            if(words.length<3) return;
            for(int i = 0; i < 3; i++) {
                if(words[i].equals("redirect")) return;
            }
            allTitles.add(category.getTitle());
            for(String word : words) {
                if(!stopWords.contains(word)) {
                    vocabCount.putIfAbsent(word, new AtomicLong(0));
                    vocabCount.get(word).getAndIncrement();
                }
            }
            for(String word : new HashSet<>(Arrays.asList(words))) {
                if(!stopWords.contains(word)) {
                    docCount.putIfAbsent(word, new AtomicLong(0));
                    docCount.get(word).getAndIncrement();
                }
            }
        };

        // vocab pass
        iterate(vocabConsumer);

        Map<String,AtomicLong> filteredVocabCount = vocabCount.entrySet().stream()
                .sorted((e1,e2)->{
                    double s1 = ((double)e1.getValue().get())/Math.log(1f+docCount.get(e1.getKey()).get());
                    double s2 = ((double)e2.getValue().get())/Math.log(1f+docCount.get(e2.getKey()).get());
                    return Double.compare(s2,s1);
                })
                .filter(e->e.getValue().get()>minVocabSize)
                .limit(vocabLimit)
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));


        System.out.println("Vocab size before: "+vocabCount.size());
        System.out.println("Vocab size after: "+filteredVocabCount.size());

        Map<String,Integer> titleToIndexMap = new HashMap<>();
        Map<String,Integer> wordToIndexMap = new HashMap<>();

        List<String> allWordsList = new ArrayList<>(filteredVocabCount.keySet());
        allWordsList.sort(Comparator.naturalOrder());
        List<String> allTitlesList = new ArrayList<>(allTitles);
        allTitlesList.sort(Comparator.naturalOrder());

        for(int i = 0; i < allWordsList.size(); i++) {
            wordToIndexMap.put(allWordsList.get(i),i);
        }
        for(int i = 0; i < allTitlesList.size(); i++) {
            titleToIndexMap.put(allTitlesList.get(i),i);
        }

        final INDArray coocurrenceMatrix = Nd4j.zeros(allTitles.size(),filteredVocabCount.size());
        Consumer<CategoryWithText> mainConsumer = category -> {
            String title = category.getTitle();
            Integer titleIdx = titleToIndexMap.get(title);
            if(titleIdx==null) return;
            String[] words = textToWordFunction.apply(category.getText());
            if(words.length<3) return;
            for(int i = 0; i < 3; i++) {
                if(words[i].equals("redirect")) return;
            }

            for(String word : words) {
                Integer wordIdx = wordToIndexMap.get(word);
                if(wordIdx!=null) {
                    coocurrenceMatrix.get(NDArrayIndex.point(titleIdx),NDArrayIndex.point(wordIdx)).addi(1d);
                }
            }

        };
        // main pass
        iterate(mainConsumer);

        // save weights
        coocurrenceMatrix.diviColumnVector(coocurrenceMatrix.norm2(1));

        Database.saveObject(coocurrenceMatrix,matrixFile);
    }

    public static void iterate(Consumer<CategoryWithText> consumer) {
        try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(WriteTechnologiesToText.categoriesWithTextFile)))) {
            Object obj = null;
            while((obj=ois.readObject())!=null) {
                consumer.accept((CategoryWithText)obj);
            }
        } catch(Exception e) {
            if(!(e instanceof EOFException)) {
                e.printStackTrace();
            }
        }
    }
}
