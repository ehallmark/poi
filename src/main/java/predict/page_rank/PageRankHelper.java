package main.java.predict.page_rank;

import main.java.predict.Database;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/24/17.
 */
public class PageRankHelper {
    private static final File pageRankMapFile = new File("page_rank_map.jobj");

    // run sim rank algorithm
    public static void main(String[] args) {
        long t1 = System.currentTimeMillis();

        final Database database = new Database(Database.load(Database.labeledDataFile));
        database.init(true);

        Map<String,Collection<String>> linkMap = Collections.synchronizedMap(new HashMap<>());
        database.getPois().forEach(poi->{
            if(poi.getLinks()!=null) {
                linkMap.put(poi.getTitle(), poi.getLinks());
            } else {
                linkMap.put(poi.getTitle(), Collections.emptyList());
            }
        });

        PageRank algorithm = new PageRank(linkMap,0.75);
        algorithm.solve(25);
        System.out.println("Finished algorithm");
        Map<String,Float> rankTable = algorithm.getRankTable();

        List<String> titles = Collections.synchronizedList(new ArrayList<>(linkMap.keySet()));

        System.out.println("Saving page rank evaluator...");
        Map<String,Number> pageRankMap = getDataMap(titles,rankTable);
        System.out.println("Rank Table size: "+pageRankMap.size());
        long t2 = System.currentTimeMillis();
        System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");

        Database.saveObject(pageRankMap,pageRankMapFile);
    }

    private static Map<String,Number> getDataMap(Collection<String> items, Map<String,Float> rankTable) {
        Map<String,Number> data = Collections.synchronizedMap(new HashMap<>());
        items.parallelStream().forEach(item->{
            if(item!=null) {
                Float rank = rankTable.get(item);
                if (rank != null) {
                    data.put(item, rank.doubleValue());
                }
            }
        });
        return data;
    }
}
