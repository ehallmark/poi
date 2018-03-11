package main.java.nlp.wikipedia.demo;

import main.java.nlp.wikipedia.InfoBox;
import main.java.nlp.wikipedia.PageCallbackHandler;
import main.java.nlp.wikipedia.WikiPage;
import main.java.predict.Database;
import org.nd4j.linalg.primitives.AtomicBoolean;
import org.nd4j.linalg.primitives.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static main.java.predict.Database.*;
/**
 * An even simpler callback demo.
 *
 * @author Jason Smith
 * @see PageCallbackHandler
 *
 */

public class DemoSAXHandler implements PageCallbackHandler {

    private static final AtomicLong total = new AtomicLong(0);
    private static final AtomicLong cnt = new AtomicLong(0);

    private Map<String,Map<String,Object>> dataMap = Collections.synchronizedMap(new HashMap<>());
    private static InfoBox tryGetInfoBox(WikiPage page) {
        try {
            return page.getInfoBox();
        } catch(Exception e) {
            //e.printStackTrace();
            return null;
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
                    return new Pair<>(keyValuePair[0].trim(),keyValuePair[1].trim());
                }).filter(e->e!=null).forEach(e->results.put(e.getKey(),e.getValue()));
        return results;
    }

    public void save() {
        Database.saveObject(dataMap,dataFile);
    }

	public void process(WikiPage page) {
        InfoBox box = tryGetInfoBox(page);
        if(box!=null&&!box.isEmpty()) {
            String info = box.dumpRaw();
          //  System.out.println("Info box: "+info);
            Map<String,String> infoMap = parseMap(info);
            if(infoMap.containsKey("coordinates")) {
                Pair<Double,Double> coordinates = parseCoordinates(infoMap.get("coordinates"));
                if(coordinates!=null) {
                    if(cnt.getAndIncrement()%1000==999) {
                        System.out.println("Found "+cnt.get()+" with coordinates out of "+total.get()+". Unique: "+dataMap.size());
                    };
                    Map<String, Object> map = Collections.synchronizedMap(new HashMap<>());
                    map.put(TITLE, page.getTitle());
                    map.put(LINKS, Collections.synchronizedList(new ArrayList<>(page.getLinks())));
                    map.put(CATEGORIES, Collections.synchronizedList(new ArrayList<>(page.getCategories())));
                    map.put(COORDINATES, coordinates);
                    dataMap.put(page.getTitle(), map);
                }
            }
        }
        total.getAndIncrement();
    }

    private Pair<Double,Double> parseCoordinates(String coordinates) {
        Pair<Double,Double> pair = parseCoordinatesHelper(coordinates);
        if(pair!=null) {
            // check range
            if(pair.getFirst()<-360 || pair.getFirst() > 360) {
                //System.out.println("Latitude: "+pair.getFirst());
                return null;//throw new RuntimeException("Invalid latitude");
            } else if(pair.getSecond()<-360 || pair.getSecond() > 360) {
                //System.out.println("Longitude: "+pair.getSecond());
                return null;//throw new RuntimeException("Invalid longitude");
            }
        }
        return pair;
    }
    private Pair<Double,Double> parseCoordinatesHelper(String coordinates) {
        // returns lat/long
        try {
            if (coordinates.length() > 2) {
                if (!coordinates.contains("{{")) {
                    String[] parts = coordinates.split(",", 2);
                    //System.out.println("Parts: " + Arrays.toString(parts));
                    if (parts.length == 1) {
                        // make it length 2
                        String part = parts[0];
                        if(part.contains(",")) {
                            parts = part.split(",",2);
                        } else if(part.contains(" ")) {
                            parts = part.split(" ",2);
                        } else return null;
                    }
                    String part1 = parts[0].trim();
                    String part2 = parts[1].trim();
                    int sign1 = 1;
                    int sign2 = 1;
                    if(part1.endsWith("S")) {
                        sign1 = -1;
                    } else if(!part1.endsWith("N")) return null;
                    if(part2.endsWith("W")) {
                        sign2 = -1;
                    } else if(!part2.endsWith("E")) return null;
                    part1 = part1.replace(" ","").replace("S","").replace("N","");
                    part2 = part2.replace(" ","").replace("E","").replace("W","");
                    return new Pair<>(
                            Double.valueOf(part1)*sign1,
                            Double.valueOf(part2)*sign2
                    );
                } else {
                    String[] parts = coordinates.split("\\|");
                    //System.out.println("Parts: " + Arrays.toString(parts));
                    if (parts.length == 1) {
                        // make it length 2
                        String part = parts[0];
                        if(part.contains(",")) {
                            parts = part.split(",",2);
                        } else if(part.contains(" ")) {
                            parts = part.split(" ",2);
                        }
                    }
                    if (parts.length > 1) {
                        double nsVal = 0d;
                        double ewVal = 0d;
                        boolean isDMS = false;
                        for(int i = 0; i < parts.length; i++) {
                            String part = parts[i].replace("}}","");
                            if(part.equals("N")||part.equals("E")||part.equals("S")||part.equals("W")) {
                                isDMS = true;
                                break;
                            }
                        }
                        if(!isDMS) {
                            for(int i = 0; i < parts.length; i++) {
                                String part = parts[i].trim();
                                int sign;
                                if(part.endsWith("N") || part.endsWith("E")) {
                                    sign = 1;
                                } else if(part.endsWith("W")||part.endsWith("S")) {
                                    sign = -1;
                                } else return null;

                                part = part.substring(0, part.length() - 1);
                                String numeric = part.replace(" ", "");
                                if (numeric.length() > 0) {
                                    if (nsVal == 0) {
                                        nsVal = Double.valueOf(numeric)*sign;
                                    } else {
                                        ewVal = Double.valueOf(numeric)*sign;
                                    }
                                } else if (nsVal > 0) break;

                            }
                        } else {
                            int denom = 1;
                            boolean ns = true;
                            for (int i = 0; i < parts.length; i++) {
                                String part = parts[i];
                                if (part.contains("{{")) continue;
                                if (part.contains("}}")) part = part.replace("}}", "");
                                if (part.equals("N") || part.equals("S")) {
                                    ns = false;
                                    denom = 1;
                                    if (part.equals("S")) {
                                        nsVal *= -1;
                                    }
                                } else if (part.equals("E") || part.equals("W")) {
                                    if (part.equals("W")) {
                                        ewVal *= -1;
                                    }
                                    break;
                                } else {
                                    double v = Double.valueOf(part);
                                    if (ns) {
                                        nsVal += v / denom;
                                    } else {
                                        ewVal += v / denom;
                                    }
                                    denom *= 60;
                                }
                            }
                        }
                        return new Pair<>(nsVal, ewVal);
                    }
                }
            }
        } catch (Exception e) {
           // e.printStackTrace();
        }
        return null;
    }

}
