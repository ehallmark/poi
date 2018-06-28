package main.java.predict.tech_tags;

import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLParserFactory;
import main.java.predict.word2vec.FullPageStreamingHandler;
import main.java.util.StopWords;

import java.io.*;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class ExtractCategories {

    public static final String WIKI_FILE = "/home/ehallmark/data/enwiki-latest-pages-articles-multistream.xml";
    public static final File TECH_CATEGORIES_WITH_TEXT_FILE = new File("tech_tag_categories_with_text_file.jobj");

    public static final Function<String,Boolean> shouldMatch = category -> {
        category=category.toLowerCase();
        return category.contains("technology")||category.contains("internet of things")||category.contains("access control")||category.contains("mobile computers")||category.contains("wearable computers") || category.contains("mixed reality")||category.endsWith("distribution")||category.endsWith("services")||category.endsWith(" systems")||category.startsWith("applications of")||category.endsWith(" techniques")||category.endsWith(" advertising")||category.endsWith(" reality")||category.endsWith(" processes")||category.endsWith(" equipment")|| category.endsWith(" infrastructure")||category.endsWith(" standards")||category.contains("computation")||category.contains("computing")||category.endsWith(" electronics")||category.contains("technologies")||category.endsWith(" inventions") || category.contains("encoding") || category.contains("decodings") || category.endsWith(" instruments") ||category.contains("science") || category.contains("engineering");
    };

    public static final Function<String,Boolean> shouldNotMatch = category -> {
        if(category.replaceFirst("[1][0-9][0-8][0-9]","").length()<category.length()||category.replaceFirst("[1][0-8][0-9]{2}","").length()<category.length()) return true; // match old years
        category=category.toLowerCase();
        return category.contains(" parks ")||category.contains("prayer")||category.contains("inhibitors")||category.contains("bdsm")||category.contains("tradename")||category.contains("woodwind")||category.startsWith("climate of")||category.contains("data formats")||category.contains("weapons")||category.startsWith("primitive ")||category.contains("character sets")||category.contains("medieval")||category.contains("television systems")||category.endsWith(" education")||category.contains("theology")||category.contains("software")||category.contains("slang")||category.contains("political")||category.contains("tunnels")||category.contains("sports originating")||category.contains("writing systems")||category.contains("musical instruments")||category.contains("phobias")||category.contains("discontinued")||category.contains("baroque")||category.contains("classical")||category.contains("orchestral")||category.contains("music instruments")||category.contains("string instruments")||category.contains("organized crime")||category.contains("alchem")||category.contains("cuisine")||category.endsWith("civilizations")||category.endsWith("occupations")||category.contains("celestial")||category.contains("sailing")||category.startsWith("activism")|| category.startsWith("culture jamming") || category.endsWith("competitions")||category.endsWith("anthems")||category.contains("products")||category.contains("religio")||category.contains("scientists")||category.endsWith(" deaths")||category.endsWith(" births")||category.contains("world war")||category.contains("nazi")||category.contains("astronomical object")|| category.endsWith("in science")||category.contains("establishments")||category.contains("genital")||category.contains("sexual")||category.contains("providers")||category.contains("pseudoscience")||category.contains("poets")||category.contains("playwrights")||category.contains("novelists")||category.startsWith("philosophy of")||category.startsWith("regulation of")||category.contains("fiction")||category.contains("men in")||category.endsWith(" languages")||category.startsWith("history of")||category.contains("prehistor")||category.contains("antiquity")||category.contains("renaissance")||category.contains("middle ages")||category.contains("obsolete")||category.contains("ancient")||category.contains("historical")||category.contains("video games")||category.contains("author")||category.contains("television series")||category.contains("treaties")||category.contains("companies")||category.contains("people") || category.contains("alumni") || category.contains("colleges") || category.contains("universit") || category.contains(" places ")||category.startsWith(" places")||category.contains("recipients")||category.contains("winners");
    };

    private static final Set<String> STOP_WORDS = new HashSet<>(StopWords.getStopWords());
    public static final Function<String,Boolean> shouldNotMatchTitle = title -> {
        title = title.toLowerCase();
        String[] words = title.split(" ");
        if(words.length>3) return true;
        else if(words.length==3) {
            if(!words[1].equals("aerial")) {
                if (words[1].equals("it")||!(!STOP_WORDS.contains(words[0]) && !STOP_WORDS.contains(words[2]) && STOP_WORDS.contains(words[1]))) {
                    return true;
                }
            }
        }
        return title.equals("attention")||title.contains("paradox")|| title.contains("experiment") || title.contains("sexism")||title.contains("racism")||title.startsWith("formal ")||title.startsWith("books ")||title.equals("open problems")||title.startsWith("science")||title.endsWith(" of science")||title.equals("formal methods")||title.contains("mitsubishi") || title.contains("acquisitions")||title.contains(":") || title.contains("russian") || title.startsWith("regulation of")||title.startsWith("philosophy of")|| title.contains(" in ") || title.endsWith("inventions") || title.startsWith("history of ") || title.endsWith(" musical instruments") || title.endsWith("byte") || title.split(" ").length>3 || title.replaceFirst("[^a-z ]","").length()!=title.length();
    };

    public static final Function<String,String> titleTransformer = title -> {
        title = title.toUpperCase();
        if(title.endsWith(" TECHNOLOGIES")) {
            title = title.substring(0,title.length()-3)+"Y"; // convert technologies to technology
        }
        if(title.startsWith("TECHNOLOGY OF ")) {
            title = title.substring(14)+" TECHNOLOGY";
        }
        if(title.endsWith(" TERMINOLOGY")) {
            title = title.substring(0,title.length()-" TERMINOLOGY".length())+ " TECHNOLOGY";
        }
        if(title.startsWith("APPLICATIONS OF ")) {
            title = title.substring("APPLICATIONS OF".length(),title.length()).trim();
        }
        if(title.startsWith("APPLICATION OF ")) {
            title = title.substring("APPLICATION OF".length(),title.length()).trim();
        }
        return title;
    };


    public static void main(String[] args) throws IOException {
        final int minShouldMatch = 1;
        final int minChars = 10000;
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(TECH_CATEGORIES_WITH_TEXT_FILE)));

        AtomicLong cnt = new AtomicLong(0);
        AtomicLong valid = new AtomicLong(0);
        FullPageStreamingHandler handler = new FullPageStreamingHandler(wikiPage -> {
            boolean should = false;
            if(wikiPage.getTitle().toLowerCase().contains("internet of things")) {
                // FLAG
                System.out.println("CATEGORIES FOR IOT: "+wikiPage.getTitle()+": "+String.join("; ",wikiPage.getCategories()));
                should=true;
            } else if(wikiPage.getTitle().toLowerCase().contains("home automation")) {
                System.out.println("CATEGORIES FOR HOME AUTOMATION: "+wikiPage.getTitle()+": "+String.join("; ",wikiPage.getCategories()));
                should=true;
            } else if(wikiPage.getTitle().toLowerCase().contains("connected car")) {
                System.out.println("CATEGORIES FOR CONNECTED CAR: "+wikiPage.getTitle()+": "+String.join("; ",wikiPage.getCategories()));
                should=true;
            }
            if(!shouldNotMatchTitle.apply(wikiPage.getTitle())&&wikiPage.getCategories().stream().noneMatch(category->shouldNotMatch.apply(category))
                    && wikiPage.getCategories().stream().filter(category->shouldMatch.apply(category)).count()>=minShouldMatch) {
                // check valid length of text

                if(wikiPage.getText().length()>minChars) {
                    valid.getAndIncrement();
                    String title = wikiPage.getTitle();
                    title = titleTransformer.apply(title);
                  if(valid.get()%10==9)  System.out.println("Found: " + wikiPage.getTitle()+": "+String.join("; ",wikiPage.getCategories()));
                    try {
                        oos.writeObject(CategoryWithText.create(title, wikiPage.getCategories(), wikiPage.getLinks(), wikiPage.getText()));
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            } else if(should) {
                System.out.println("DID NOT MATCH: "+wikiPage.getTitle());
                System.out.println("CATEGORIES: "+String.join("; ",wikiPage.getCategories()));
                //System.exit(1);
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
        System.out.println("Should not match "+match+": "+shouldNotMatch.apply(match));
        match = "1993 introductions";
        System.out.println("Should not match "+match+": "+shouldNotMatch.apply(match));

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
