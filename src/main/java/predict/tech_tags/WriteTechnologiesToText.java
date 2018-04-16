package main.java.predict.tech_tags;

import main.java.graphical_modeling.model.graphs.BayesianNet;
import main.java.graphical_modeling.model.graphs.Graph;
import main.java.graphical_modeling.model.nodes.Node;
import main.java.nlp.wikipedia.WikiPage;
import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLParserFactory;
import main.java.predict.Database;
import main.java.predict.word2vec.FullPageStreamingHandler;
import org.nd4j.linalg.primitives.Pair;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WriteTechnologiesToText {
    public static final File categoriesWithTextFile = new File("tech_tag_categories_with_text_file.jobj");
    public static void main(String[] args) throws Exception {
        Function<String,Boolean> badTitleFunction = title -> {
            return title.contains(" IN ") || title.endsWith("INVENTIONS") || title.startsWith("HISTORY OF ") || title.endsWith(" MUSICAL INSTRUMENTS") || title.endsWith("BYTE") || title.split(" ").length>3 || title.replaceFirst("[^A-Z ]","").length()!=title.length();
        };

        Function<String,String> titleTransformer = title -> {
            title = title.toUpperCase();
            if(title.startsWith("TECHNOLOGY OF ")) {
                title = title.substring(14)+" TECHNOLOGY";
            }
            return title;
        };

        List<Category> categories = (List<Category>) Database.loadObject(new File(ExtractCategories.TECH_CATEGORIES_FILE));
        categories = categories.stream().filter(c->c.getCategories().stream().noneMatch(category->ExtractCategories.shouldNotMatch.apply(category)))
                .collect(Collectors.toList());

        System.out.println("Size: "+categories.size());


        Graph graph = new BayesianNet();
        categories.forEach(category -> {
            String t = titleTransformer.apply(category.getTitle());
            if(badTitleFunction.apply(t)) {
                return;
            }
            Node node = graph.addBinaryNode(t);
            //graph.connectNodes(node,node);
            category.getCategories().forEach(c->{
                c = titleTransformer.apply(c);
                if(badTitleFunction.apply(c)) {
                    return;
                }
                Node node2 = graph.addBinaryNode(c);
                graph.connectNodes(node2,node);
            });
            //category.getLinks().forEach(l->{
            //    Node node2 = graph.addBinaryNode(l.toUpperCase());
            //    graph.connectNodes(node,node2);
            //});
        });

        List<Node> mainNodes = graph.getAllNodesList();
        mainNodes = mainNodes.stream().sorted((n1,n2)->Integer.compare(n2.getInBound().size(),n1.getInBound().size())).collect(Collectors.toList());
        mainNodes.forEach(node->{
           System.out.println(node.getLabel()+": "+node.getInBound().size());
        });
        System.out.println("Graph size: "+mainNodes.size());

        AtomicLong cnt = new AtomicLong(0);
        AtomicLong valid = new AtomicLong(0);
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(categoriesWithTextFile)));
        Consumer<WikiPage> consumer = page -> {
            String title = titleTransformer.apply(page.getTitle());
            Node node = graph.findNode(title);
            if(node!=null) {
                // write
                try {
                    page.setTitle(title);
                    oos.writeObject(CategoryWithText.create(title, page.getCategories(), page.getLinks(), page.getText()));
                    if (node.getParents().size() > 0) {
                        for(Node parent : node.getParents()) {
                            oos.writeObject(CategoryWithText.create(parent.getLabel(), page.getCategories(), page.getLinks(), page.getText()));
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
                valid.getAndIncrement();
            }
            if(cnt.getAndIncrement()%10000==9999) {
                try {
                    oos.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                }
                System.out.println("Valid "+valid.get()+" out of "+cnt.get());
            }
        };

        FullPageStreamingHandler handler = new FullPageStreamingHandler(consumer);

        WikiXMLParser wxsp;
        try {
            wxsp = WikiXMLParserFactory.getSAXParser(ExtractCategories.WIKI_FILE);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.exit(1);
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

        oos.flush();
        oos.close();

    }
}
