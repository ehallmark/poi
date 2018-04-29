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
import org.nd4j.linalg.primitives.Pair;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LearnTechnologyStatistics {
    private static final Function<String,String[]> textToWordFunction = text -> {
        return text.toLowerCase().replaceAll("[^a-z ]"," ").split("\\s+");
    };

    public static final File matrixFile = new File("tech_tag_statistics_matrix.jobj");
    public static final File titleListFile = new File("tech_tag_statistics_title_list.jobj");
    public static final File wordListFile = new File("tech_tag_statistics_word_list.jobj");
    public static final File parentToChildrenMapFile = new File("tech_tag_statistics_parent_child_map.jobj");
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
        final int minVocabSize = 10;
        final int vocabLimit = 40000;
        final int maxWordsPerDoc = 1000;
        final int minNumWords = 500;
        Map<String,AtomicDouble> vocabScore = new HashMap<>();
        Map<String,AtomicLong> docCount = new HashMap<>();
        Map<String,AtomicLong> vocabCount = new HashMap<>();
        Set<String> allTitles = new HashSet<>();
        AtomicLong numDocs = new AtomicLong(0);
        AtomicLong numWords = new AtomicLong(0);
        Graph graph = new BayesianNet();
        Consumer<CategoryWithText> vocabConsumer = category -> {
            numDocs.getAndIncrement();
            String[] words = textToWordFunction.apply(category.getText());
            if(words.length<minNumWords) return;
            if(!filterWordsFunction.apply(words)) return;
            Node node = graph.addBinaryNode(category.getTitle());
            category.getCategories().forEach(link->{
                link = ExtractCategories.titleTransformer.apply(link);
                if(ExtractCategories.shouldNotMatchTitle.apply(link)) return;
                Node parent = graph.addBinaryNode(link);
                graph.connectNodes(parent,node);
            });
            allTitles.add(category.getTitle());
            for(String word : words) {
                if(!stopWords.contains(word)) {
                    vocabScore.putIfAbsent(word, new AtomicDouble(0));
                    vocabScore.get(word).getAndAdd(1d/words.length);
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
                .filter(e->vocabCount.get(e.getKey()).get()*averageNumWords>minVocabSize&&((double)docCount.get(e.getKey()).get())<((double)0.6*numDocs.get()))
                .limit(vocabLimit)
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        System.out.println("Vocab size before: "+vocabScore.size());
        System.out.println("Vocab size after: "+filteredVocabCount.size());

        List<String> topParents = new ArrayList<>(buildWeightedTree(allTitles.stream().map(t->graph.findNode(t)).collect(Collectors.toList()), 5));

        Map<String,Set<String>> parentsToChildrenMap = topParents.stream()
                .collect(Collectors.toMap(e->e,e->findChildren(graph.findNode(e),null)));

        System.out.println("Top level parents: "+parentsToChildrenMap.size());


        // map top level to intermediary level
        parentsToChildrenMap.forEach((parent,children)->{
            System.out.println("Parent: "+parent);
            for(String child : children) {
                System.out.println("\t\tChild: " + child);
            }
        });

        Set<String> allTechnologyNodes = new HashSet<>();
        parentsToChildrenMap.forEach((parent,children)->{
            allTechnologyNodes.add(parent);
            allTechnologyNodes.addAll(children);
        });


        Map<String,Integer> titleToIndexMap = new HashMap<>();
        Map<String,Integer> wordToIndexMap = new HashMap<>();

        List<String> allWordsList = new ArrayList<>(filteredVocabCount.keySet());
        allWordsList.sort(Comparator.naturalOrder());
        List<String> allTitlesList = new ArrayList<>(allTechnologyNodes);
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

            List<Integer> otherIndices = category.getCategories().stream()
                    .map(c->{
                        c = ExtractCategories.titleTransformer.apply(c);
                        if(parentsToChildrenMap.containsKey(c)) {
                            return c;
                        } else {
                            return null;
                        }
                    }).filter(c->c!=null)
                    .map(c->titleToIndexMap.get(c))
                    .collect(Collectors.toList());

            Map<String,Double> tfidfMap = Stream.of(words).collect(Collectors.groupingBy(e->e,Collectors.counting())).entrySet().stream()
                    .map(e->{
                        return new Pair<>(e.getKey(),((double)e.getValue())/Math.log(Math.E+docCount.get(e.getKey()).get()));
                    }).sorted((e1, e2)->{
                        return Double.compare(e2.getRight(),e1.getRight());
                    }).limit(Math.min(maxWordsPerDoc,words.length/2)).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

            tfidfMap.forEach((k,v)->{
                Integer wordIdx = wordToIndexMap.get(k);
                if(wordIdx!=null) {
                    coocurrenceMatrix.get(NDArrayIndex.point(titleIdx),NDArrayIndex.point(wordIdx)).addi(v);
                    otherIndices.forEach(otherIdx->{
                        coocurrenceMatrix.get(NDArrayIndex.point(otherIdx),NDArrayIndex.point(wordIdx)).addi(v);
                    });
                }
            });

        };
        // main pass
        iterate(mainConsumer);

        // save weights
        coocurrenceMatrix.diviColumnVector(coocurrenceMatrix.norm2(1));

        Database.saveObject(parentsToChildrenMap,parentToChildrenMapFile);
        Database.saveObject(allWordsList,wordListFile);
        Database.saveObject(allTitlesList,titleListFile);
        Database.saveObject(coocurrenceMatrix,matrixFile);
    }


    private static Set<String> buildWeightedTree(List<Node> titles, int minimumSize) {
        Set<String> parents = new HashSet<>();
        Set<String> seen = new HashSet<>();
        titles.forEach(title->{
            buildWeightedTreeHelper(title,minimumSize,parents,seen);
        });
        return parents;
    }

    private static void buildWeightedTreeHelper(Node node, int minimumSize, Set<String> parents, Set<String> seen) {
        seen.add(node.getLabel());
        if(countNodes(node)>=minimumSize) {
            parents.add(node.getLabel());
            return;
        }
        for(Node parent : node.getParents()) {
            if(seen.contains(parent.getLabel())) continue;
            buildWeightedTreeHelper(parent,minimumSize,parents,seen);
        }
    }

    private static Set<String> findChildren(Node node, Collection<String> possible) {
        Set<String> children = new HashSet<>();
        Set<String> seen = new HashSet<>();
        seen.add(node.getLabel());
        node.getChildren().forEach(child -> {
            findChildrenHelper(child, seen, children, possible);
        });
        return children;
    }

    private static void findChildrenHelper(Node node, Set<String> seen, Set<String> set, Collection<String> possible) {
        seen.add(node.getLabel());
        if(possible==null||possible.contains(node.getLabel())) {
            set.add(node.getLabel());
        }
        node.getChildren().forEach(child->{
            if(!seen.contains(child.getLabel())) {
                findChildrenHelper(child, seen, set, possible);
            }
        });
    }

    private static int countNodes(Node node) {
        Set<String> seen = new HashSet<>();
        return countNodesHelper(node,seen)-1;
    }

    private static int countNodesHelper(Node node, Set<String> seen) {
        if(node.getChildren().isEmpty()) return 1;
        else {
            final Set<String> _seen = new HashSet<>(seen);
            _seen.addAll(node.getChildren().stream().map(n->n.getLabel()).collect(Collectors.toList()));
            return 1+node.getChildren().stream().filter(n->!seen.contains(n.getLabel())).mapToInt(n->countNodesHelper(n,_seen)).sum();
        }
    }


    private static int computeDepth(Node node) {
        Set<String> seen = new HashSet<>();
        return computeDepthHelper(node,0,seen);
    }

    private static int computeDepthHelper(Node node, int score, Set<String> seen) {
        if(node.getChildren().isEmpty()) return score;
        else {
            final Set<String> _seen = new HashSet<>(seen);
            _seen.addAll(node.getChildren().stream().map(n->n.getLabel()).collect(Collectors.toList()));
            return node.getChildren().stream().filter(n->!seen.contains(n.getLabel())).mapToInt(n->computeDepthHelper(n,score+1,_seen)).max().orElse(0);
        }
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
