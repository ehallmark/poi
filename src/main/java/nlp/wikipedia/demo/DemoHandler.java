package main.java.nlp.wikipedia.demo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import main.java.nlp.wikipedia.InfoBox;
import main.java.nlp.wikipedia.PageCallbackHandler;
import main.java.nlp.wikipedia.WikiPage;
import org.nd4j.linalg.primitives.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very simple callback for demo.
 * 
 * @author Delip Rao
 * @see PageCallbackHandler
 *
 */

public class DemoHandler implements PageCallbackHandler {
	private static final File dataFile = new File("company_data.jobj");

	private static final String CATEGORIES = "categories";
	private static final String SUBSIDIARIES = "subsidiaries";
	private static final String PARENT = "parent";
	private static final String TITLE = "title";
	private static final AtomicLong total = new AtomicLong(0);
	private static final AtomicLong cnt = new AtomicLong(0);
	private static final AtomicLong sCnt = new AtomicLong(0);
	private static final AtomicLong pCnt = new AtomicLong(0);

	private Map<String,Map<String,Object>> dataMap = Collections.synchronizedMap(new HashMap<>());
	private static InfoBox tryGetInfoBox(WikiPage page) {
		try {
			return page.getInfoBox();
		} catch(Exception e) {
			//e.printStackTrace();
			return null;
		}
	}

	public void save() {
		try(ObjectOutputStream bos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile)))) {
			bos.writeObject(dataMap);
			bos.flush();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}


	private static Map<String,String> parseMap(String infoBoxDump) {
		if(infoBoxDump.length()<=4) return Collections.emptyMap();
		Map<String,String> results = Collections.synchronizedMap(new HashMap<>());
		Stream.of(infoBoxDump.split("\\s\\|"))
				.map(str->{
					String[] keyValuePair = str.split(" = ",2);
					if(keyValuePair.length<2) return null;
					//System.out.println("1: "+keyValuePair[0].trim()+", 2: "+keyValuePair[1].trim());
					return new Pair<>(keyValuePair[0].trim().toLowerCase(),keyValuePair[1].trim());
				}).filter(e->e!=null).forEach(e->results.put(e.getKey(),e.getValue()));
		return results;
	}

	public void process(WikiPage page) {
		InfoBox box = tryGetInfoBox(page);
		if(box!=null&&!box.isEmpty()) {
			String info = box.dumpRaw();
			//  System.out.println("Info box: "+info);
			Map<String,String> infoMap = parseMap(info);
			if(infoMap.containsKey("parent")||infoMap.containsKey("subsidiaries")) {
				String subsidiaries = infoMap.get("subsidiaries");
				String parent = infoMap.get("parent");

				if(cnt.getAndIncrement()%1000==999) {
					System.out.println("Found "+cnt.get()+" valid results out of "+total.get()+". Unique: "+dataMap.size()+"\nSubsids: "+sCnt.get()+". Parents: "+pCnt.get());
				}

				Map<String, Object> map = Collections.synchronizedMap(new HashMap<>());
				map.put(TITLE, page.getTitle());
				map.put(CATEGORIES, Collections.synchronizedList(new ArrayList<>(page.getCategories())));

				if(parent!=null) {
					pCnt.getAndIncrement();
					map.put(PARENT, parent);
				}
				if(subsidiaries!=null) {
					sCnt.getAndIncrement();
					map.put(SUBSIDIARIES,subsidiaries);
				}

				dataMap.put(page.getTitle(), map);
			}
		}
		total.getAndIncrement();
	}

}
