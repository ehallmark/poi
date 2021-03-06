package main.java.nlp.wikipedia;

import main.java.nlp.language.Language;
import main.java.nlp.wikipedia.WikiPage;
/**
 * 
 * Interface to allow streamed processing of pages. 
 * This allows a SAX style processing of Wikipedia XML files. 
 * The registered callback is executed on each page
 * element in the XML file. 
 * <p>
 * Using callbacks will consume lesser memory, an useful feature for large
 * dumps like English and German.
 * 	
 * @author Delip Rao
 * @see WikiPage
 *
 */

public interface PageCallbackHandler {
	/**
	 * This is the callback method that should be implemented before
	 * registering with <code>WikiXMLDOMParser</code>
	 * @param page a wikipedia page object
	 * @see   WikiPage
	 */
	public void process(WikiPage page);

}
