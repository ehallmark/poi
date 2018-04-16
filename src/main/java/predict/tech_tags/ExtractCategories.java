package main.java.predict.tech_tags;

import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLParserFactory;
import main.java.predict.Database;
import main.java.predict.word2vec.FullPageStreamingHandler;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class ExtractCategories {

    public static final String WIKI_FILE = "/media/ehallmark/tank/enwiki-latest-pages-articles-multistream.xml";
    public static final String TECH_CATEGORIES_FILE = "technology_categories_list.jobj";


    public static final Function<String,Boolean> shouldMatch = category -> {
        return category.endsWith(" technology")||category.endsWith(" technologies")||category.endsWith(" inventions");// || category.contains("science") || category.contains("engineering") || category.contains("semiconductor")||category.contains("computer")||category.contains("advancement")|| category.contains("chemistry") || category.contains("biology")||category.contains("physic");
    };

    public static final Function<String,Boolean> shouldNotMatch = category -> {
        if(category.replaceFirst("[1][0-8][0-9]{2}","").length()<category.length()) return true; // match old years
        return category.contains("women in")||category.contains("prehistor")||category.contains("antiquity")||category.contains("renaissance")||category.contains("middle ages")||category.contains("obsolete")||category.contains("ancient")||category.contains("historical")||category.contains("video games")||category.contains("television series")||category.contains("treaties")||category.contains("companies")||category.contains("people") || category.contains("alumni") || category.contains("colleges") || category.contains("universit") || category.contains("places")||category.contains("recipients")||category.contains("winners");
    };

    public static final Function<String,Boolean> shouldNotMatchTitle = title -> {
        if(title.split(" ").length>3) return true;
        return title.contains(":") || title.startsWith("history of");
    };

    public static void main(String[] args) {
        final int minShouldMatch = 1;



        List<Category> categories = new ArrayList<>();
        AtomicLong cnt = new AtomicLong(0);
        AtomicLong valid = new AtomicLong(0);
        FullPageStreamingHandler handler = new FullPageStreamingHandler(wikiPage -> {
            if(!shouldNotMatchTitle.apply(wikiPage.getTitle().toLowerCase())&&wikiPage.getCategories().stream().noneMatch(category->shouldNotMatch.apply(category.toLowerCase()))
                    && wikiPage.getCategories().stream().filter(category->shouldMatch.apply(category.toLowerCase())).count()>minShouldMatch) {
                categories.add(Category.create(wikiPage.getTitle(),wikiPage.getCategories(),wikiPage.getLinks()));
                valid.getAndIncrement();
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Found "+valid.get()+" out of "+cnt.get());
            }
        });

        // test
        String match = "1793 introductions";
        System.out.println("Should match "+match+": "+shouldNotMatch.apply(match));
        match = "1993 introductions";
        System.out.println("Should match "+match+": "+shouldNotMatch.apply(match));

        WikiXMLParser wxsp;
        try {
            wxsp = WikiXMLParserFactory.getSAXParser(WIKI_FILE);
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


        Database.saveObject(categories, new File(TECH_CATEGORIES_FILE));

    }
}
