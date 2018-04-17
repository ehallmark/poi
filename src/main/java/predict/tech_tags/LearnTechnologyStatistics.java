package main.java.predict.tech_tags;

import com.google.common.util.concurrent.AtomicDouble;
import main.java.graphical_modeling.model.graphs.BayesianNet;
import main.java.graphical_modeling.model.graphs.Graph;
import main.java.graphical_modeling.model.nodes.Node;
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
    public static final File titleListFile = new File("tech_tag_statistics_title_list.jobj");
    public static final File wordListFile = new File("tech_tag_statistics_word_list.jobj");
    public static void main(String[] args) throws Exception {
        final Function<String[],Boolean> filterWordsFunction = words -> {
            if(words.length<3) return false;
            for(int i = 0; i < 3; i++) {
                if(words[i].equals("redirect")) return false;
            }
            return true;
        };


        final Set<String> stopWords = new HashSet<>(StopWords.getStopWords());
        stopWords.add("nbsp");
        stopWords.add("system");
        final int minVocabSize = 5;
        final int vocabLimit = 25000;
        Map<String,AtomicDouble> vocabScore = new HashMap<>();
        Map<String,AtomicLong> docCount = new HashMap<>();
        Set<String> allTitles = new HashSet<>();
        AtomicLong numDocs = new AtomicLong(0);
        AtomicLong numWords = new AtomicLong(0);
        Graph graph = new BayesianNet();
        Set<String> parents = new HashSet<>();
        Consumer<CategoryWithText> vocabConsumer = category -> {
            numDocs.getAndIncrement();
            String[] words = textToWordFunction.apply(category.getText());
            if(!filterWordsFunction.apply(words)) return;
            Node node = graph.addBinaryNode(category.getTitle());
            category.getCategories().forEach(link->{
                link = ExtractCategories.titleTransformer.apply(link);
                if(ExtractCategories.shouldNotMatchTitle.apply(link)) return;
                Node parent = graph.addBinaryNode(link);
                graph.connectNodes(parent,node);
                parents.add(link);
            });
            allTitles.add(category.getTitle());
            for(String word : words) {
                if(!stopWords.contains(word)) {
                    vocabScore.putIfAbsent(word, new AtomicDouble(0));
                    vocabScore.get(word).getAndAdd(1d/words.length);
                }
            }
            for(String word : new HashSet<>(Arrays.asList(words))) {
                if(!stopWords.contains(word)) {
                    docCount.putIfAbsent(word, new AtomicLong(0));
                    docCount.get(word).getAndIncrement();
                }
            }
            numWords.getAndAdd(words.length);
        };

        // vocab pass
        iterate(vocabConsumer);

        final double averageNumWords = ((double)numWords.get())/numDocs.get();

        Map<String,AtomicDouble> filteredVocabCount = vocabScore.entrySet().stream()
                .sorted((e1,e2)->{
                    double s1 = (e1.getValue().get())/Math.log(1f+docCount.get(e1.getKey()).get());
                    double s2 = (e2.getValue().get())/Math.log(1f+docCount.get(e2.getKey()).get());
                    return Double.compare(s2,s1);
                })
                .filter(e->e.getValue().get()*averageNumWords>minVocabSize&&((double)docCount.get(e.getKey()).get())<((double)0.6*numDocs.get()))
                .limit(vocabLimit)
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        System.out.println("Vocab size before: "+vocabScore.size());
        System.out.println("Vocab size after: "+filteredVocabCount.size());

        List<String> popularParents = parents.stream().map(parent->{
            Node parentNode = graph.findNode(parent);
            return parentNode;
        }).map(p->{
            if(p.getParents().size()>0) {
            //    System.out.println("Parents of "+p.getLabel()+": "+String.join("; ",p.getParents().stream().map(l->l.getLabel()).collect(Collectors.toList())));
            }
            return p;
        }).filter(p->p.getChildren().size()>1)
                .map(p->p.getLabel())
                .collect(Collectors.toList());

        List<String> topLevelParents = popularParents.stream().map(parent->{
            Node parentNode = graph.findNode(parent);
            return parentNode;
        }).filter(p->p.getParents().size()==0)
                .map(p->p.getLabel())
                .collect(Collectors.toList());

        List<String> intermediaryParents = popularParents.stream().filter(parent->!topLevelParents.contains(parent)).collect(Collectors.toList());

        List<String> popularTitles = allTitles.stream().filter(title->{
            Node node = graph.findNode(title);
            if(node.getParents().size()<=1) return false;
            //System.out.println("parents for "+node.getLabel()+": "+String.join("; ",node.getParents().stream().map(p->p.getLabel()).collect(Collectors.toList())));
            List<Node> nodes = new ArrayList<>();
            nodes.add(node);
            Set<String> seen = new HashSet<>();
            while(!nodes.isEmpty()) {
                seen.addAll(nodes.stream().map(n->n.getLabel()).collect(Collectors.toList()));
                if(nodes.stream().anyMatch(parent->popularParents.contains(parent.getLabel()))) return true;
                nodes = nodes.stream().flatMap(n->n.getParents().stream()).filter(n->!seen.contains(n.getLabel())).collect(Collectors.toList());
            }
            return false;
        }).collect(Collectors.toList());

        System.out.println("Top level parents: "+topLevelParents.size());
        System.out.println("Popular parents: "+popularParents.size());
        System.out.println("Intermediary parents: "+intermediaryParents.size());
        System.out.println("Popular titles: "+popularTitles.size());
        System.out.println("All titles: "+allTitles.size());

        topLevelParents.forEach(parent->{
            System.out.println(parent);
        });

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
            if(!filterWordsFunction.apply(words)) return;

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

        Database.saveObject(allWordsList,wordListFile);
        Database.saveObject(allTitlesList,titleListFile);
        Database.saveObject(coocurrenceMatrix,matrixFile);
    }

    public static void iterate(Consumer<CategoryWithText> consumer) {
        try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(ExtractCategories.TECH_CATEGORIES_WITH_TEXT_FILE)))) {
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
