package main.java.predict.tech_tags;

import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLParserFactory;
import main.java.predict.Database;
import main.java.predict.word2vec.FullPageStreamingHandler;
import main.java.util.StopWords;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class ExtractCategories {

    public static final String WIKI_FILE = "/media/ehallmark/tank/enwiki-latest-pages-articles-multistream.xml";
    public static final File TECH_CATEGORIES_WITH_TEXT_FILE = new File("tech_tag_categories_with_text_file.jobj");


    public static final Function<String,Boolean> shouldMatch = category -> {
        category=category.toLowerCase();
        return category.endsWith(" technology")||category.endsWith(" technologies")||category.endsWith(" inventions") || category.contains("science") || category.contains("engineering");
    };

    public static final Function<String,Boolean> shouldNotMatch = category -> {
        if(category.replaceFirst("[1][0-8][0-9]{2}","").length()<category.length()) return true; // match old years
        category=category.toLowerCase();
        return category.endsWith("in science")||category.contains("pseudoscience")||category.contains("poets")||category.contains("playwrights")||category.contains("novelists")||category.startsWith("philosophy of")||category.startsWith("regulation of")||category.contains("fiction")||category.contains("men in")||category.startsWith("history of")||category.contains("prehistor")||category.contains("antiquity")||category.contains("renaissance")||category.contains("middle ages")||category.contains("obsolete")||category.contains("ancient")||category.contains("historical")||category.contains("video games")||category.contains("author")||category.contains("television series")||category.contains("treaties")||category.contains("companies")||category.contains("people") || category.contains("alumni") || category.contains("colleges") || category.contains("universit") || category.contains("places")||category.contains("recipients")||category.contains("winners");
    };

    private static final Set<String> STOP_WORDS = new HashSet<>(StopWords.getStopWords());
    public static final Function<String,Boolean> shouldNotMatchTitle = title -> {
        title = title.toLowerCase();
        String[] words = title.split(" ");
        if(words.length>3) return true;
        else if(words.length==3) {
            if (!(!STOP_WORDS.contains(words[0])&&!STOP_WORDS.contains(words[2])&&STOP_WORDS.contains(words[1]))) {
                return true;
            }
        }
        return title.contains(":") || title.startsWith("regulation of")||title.startsWith("philosophy of")|| title.contains(" in ") || title.endsWith("inventions") || title.startsWith("history of ") || title.endsWith(" musical instruments") || title.endsWith("byte") || title.split(" ").length>3 || title.replaceFirst("[^a-z ]","").length()!=title.length();
    };

    public static final Function<String,String> titleTransformer = title -> {
        title = title.toUpperCase();
        if(title.startsWith("TECHNOLOGY OF ")) {
            title = title.substring(14)+" TECHNOLOGY";
        }
        return title;
    };


    public static void main(String[] args) throws IOException {
        final int minShouldMatch = 1;
        final int minNumWords = 500;
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(TECH_CATEGORIES_WITH_TEXT_FILE)));

        AtomicLong cnt = new AtomicLong(0);
        AtomicLong valid = new AtomicLong(0);
        FullPageStreamingHandler handler = new FullPageStreamingHandler(wikiPage -> {
            if(!shouldNotMatchTitle.apply(wikiPage.getTitle().toLowerCase())&&wikiPage.getCategories().stream().noneMatch(category->shouldNotMatch.apply(category))
                    && wikiPage.getCategories().stream().filter(category->shouldMatch.apply(category)).count()>minShouldMatch) {
                // check valid length of text
                if(wikiPage.getText().split("\\s+",minNumWords+2).length>minNumWords) {
                    valid.getAndIncrement();
                    String title = wikiPage.getTitle();
                    title = titleTransformer.apply(title);
                  //  System.out.println("Found: " + wikiPage.getTitle()+": "+String.join("; ",wikiPage.getCategories()));
                    try {
                        oos.writeObject(CategoryWithText.create(title, wikiPage.getCategories(), wikiPage.getLinks(), wikiPage.getText()));
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Found "+valid.get()+" out of "+cnt.get());
                try {
                    oos.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
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

        oos.flush();
        oos.close();
    }
}
