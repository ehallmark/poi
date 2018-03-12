package main.java.nlp.wikipedia.demo;

import main.java.nlp.language.Language;
import main.java.nlp.wikipedia.PageCallbackHandler;
import main.java.nlp.wikipedia.WikiXMLParser;
import main.java.nlp.wikipedia.WikiXMLParserFactory;

import java.net.MalformedURLException;

/**
 * 
 * @author Jason Smith
 *
 */
public class SAXParserDemo {
	/**
	 * @param args
	 */
	public static final String WIKI_FILE = "/home/ehallmark/data/enwiki-latest-pages-articles-multistream.xml";
	public static void main(String[] args) {
		args = new String[]{
				WIKI_FILE
		};
		if(args.length != 1) {
			System.err.println("Usage: Parser <XML-FILE>");
			System.exit(-1);
		}

		DemoSAXHandler handler = new DemoSAXHandler();

        WikiXMLParser wxsp = null;
        try {
            wxsp = WikiXMLParserFactory.getSAXParser(args[0]);
        } catch (MalformedURLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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

		handler.save();
	}
}
