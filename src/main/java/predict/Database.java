package main.java.predict;

import lombok.Getter;
import lombok.Setter;
import main.java.graphical_modeling.model.graphs.BayesianNet;
import main.java.graphical_modeling.model.graphs.Graph;
import main.java.graphical_modeling.model.nodes.Node;
import main.java.util.Countries;
import main.java.util.States;
import org.nd4j.linalg.primitives.Pair;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Database {
    public static final String LINKS = "links";
    public static final String CATEGORIES = "categories";
    public static final String COORDINATES = "coordinates";
    public static final String TITLE = "title";
    public static final File dataFile = new File("coordinate_data.jobj");
    @Getter
    private Map<String,Map<String,Object>> data;
    @Getter @Setter
    private List<PointOfInterest> pois;
    private Map<String,PointOfInterest> titleToPOIMap;
    public Database(Map<String,Map<String,Object>> data) {
        this.data=data;
    }

    public PointOfInterest getPoi(String label) {
        return titleToPOIMap.get(label);
    }

    public void init() {
        System.out.println("Initializing pois...");
        this.pois = Collections.synchronizedList(new ArrayList<>(data.size()));
        data.entrySet().parallelStream().forEach(e->{
            String title = e.getKey();
            Pair<Double,Double> coordinates = (Pair<Double,Double>)e.getValue().get(COORDINATES);
            if(coordinates==null) return;
            Collection<String> links = (Collection<String>)e.getValue().getOrDefault(LINKS,Collections.emptyList());
            Collection<String> categories = (Collection<String>)e.getValue().getOrDefault(CATEGORIES,Collections.emptyList());
            pois.add(new PointOfInterest(degreesToRads(coordinates.getFirst()),degreesToRads(coordinates.getSecond()),title,links,categories));
        });
        this.titleToPOIMap=Collections.synchronizedMap(new HashMap<>());
        this.pois.parallelStream().forEach(poi->{
            titleToPOIMap.put(poi.getTitle(),poi);
        });
        System.out.println("Finished initializing pois.");
    }

    public List<PointOfInterest> closestPois(double latitude, double longitude, int limit, boolean isRadian) {
        if(!isRadian) {
            latitude = degreesToRads(latitude);
            longitude = degreesToRads(longitude);
        }
        final PointOfInterest fake = new PointOfInterest(latitude,longitude,null,null, null);
        return pois.parallelStream().map(poi->new Pair<>(poi,poi.haversineDistance(fake)))
                .sorted(Comparator.comparing(e->e.getSecond()))
                .limit(limit)
                .map(e->e.getFirst())
                .collect(Collectors.toList());
    }

    private static double degreesToRads(double d) {
        return d*Math.PI/180d;
    }

    public static Map<String,Map<String,Object>> load(File file) {
        try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return (Map<String,Map<String,Object>>) ois.readObject();
        }catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void saveObject(Object obj, File file) {
        try(ObjectOutputStream bos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            bos.writeObject(obj);
            bos.flush();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }
    private static void extractLocationCategories(PointOfInterest poi, List<String> prefixes, Map<String,Collection<String>> map) {
        extractLocationCategories(poi,prefixes,map,false);
    }
    private static void extractLocationCategories(PointOfInterest poi, List<String> prefixes, Map<String,Collection<String>> map, boolean print) {
        List<String> locations = Collections.synchronizedList(new ArrayList<>());
        for(String category : poi.getCategories()) {
            for(String prefix : prefixes) {
                if (category.toLowerCase().startsWith(prefix.toLowerCase())) {
                    locations.add(category.substring(prefix.length()).trim());
                    break;
                }
            }
        }
        if(locations.size()>0) {
            if(print) {
                System.out.println(poi.getTitle() + ": " + locations);
            }
            map.put(poi.getTitle(),locations);
        }
    }

    private static Map<String,Collection<String>> groupMaps(Map<String,Collection<String>> map, List<Map<String,Collection<String>>> otherMaps) {
        Map<String,Collection<String>> ret = Collections.synchronizedMap(new HashMap<>());
        List<Map<String,Collection<String>>> invertedOthers = otherMaps.parallelStream().map(m->{
            Map<String,Collection<String>> inverted = Collections.synchronizedMap(new HashMap<>());
            m.forEach((k,v)->{
                v.forEach(x->{
                    inverted.putIfAbsent(x,Collections.synchronizedSet(new HashSet<>()));
                    inverted.get(x).add(k);
                });
            });
            return inverted;
        }).collect(Collectors.toList());
        map.entrySet().parallelStream().forEach(e->{
            Collection<String> links = invertedOthers.stream().flatMap(obj->{
                return e.getValue().stream().flatMap(v->obj.getOrDefault(v,Collections.emptyList()).stream()).filter(v->v!=null);
            }).filter(v->v!=null).distinct().collect(Collectors.toList());
            if(links.size()>0) {
                ret.put(e.getKey(),links);
                System.out.println(e.getKey()+": "+links);
            }
        });
        return ret;
    }

    private static Collection<String> getChildrenFor(Node node) {
        Collection<String> collection = Collections.synchronizedCollection(new HashSet<>());
        getChildrenForHelper(node,collection);
        return collection;
    }

    private static void getChildrenForHelper(Node node, Collection<String> collection) {
        if(node.getChildren().isEmpty()) return;
        collection.addAll(node.getChildren().stream().map(n->n.getLabel()).collect(Collectors.toSet()));
        node.getChildren().forEach(child->getChildrenForHelper(child,collection));
    }

    public static void main(String[] args) {
        Map<String,Map<String,Object>> data = load(dataFile);
        Database database = new Database(data);
        database.init();

        double portlandLat = 45d+31d/60+12d/3600;
        double portlandLong = -(122d+40d/60+55d/3600);

        Map<String,Collection<String>> radioStationToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> censusDesignatedPlaceLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> formerPopulatedPlaceToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> touristAttractionsToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> sculptureToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> stadiumToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> glacierToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> lighthouseToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> hospitalToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> schoolToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> museumToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> nationalRegisterHouseToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> nationalRegisterPlaceToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> parksToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> airportsToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> historicLandmarksToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> mountainToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> railwayToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> buildingsAndStructuresToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> populatedPlaceToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> churchToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> natureReserveToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> waterfallToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> damLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> bodyOfWaterToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> protectedAreaToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> forestToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> wildernessAreaToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> hotelToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> bridgeToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> islandToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        database.getPois().parallelStream().forEach(poi->{
           if(poi.getCategories()!=null) {
               extractLocationCategories(poi,Arrays.asList("Province of","Populated places in","Tourist attractions in","Tourist attractions of","Tourist attractions"),touristAttractionsToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Unincorporated communities of","Unincorporated communities for","Unincorporated communities in","Provinces in","Provinces of", "Provinces for","Districts of","Districts for","Districts in","Municipalities for", "Municipalities of", "Municipalities in","Counties in","Counties of","Counties for","Hamlets in","Townships in","Townships for","Rural localities in","Rural localities of","Neighborhoods in","Neighbourhoods in","Neighbourhoods of","Neighborhoods of","Boroughs in","Boroughs of","Suburbs of","Suburbs in","Localities of","Localities in","Hamlets in","Hamlets of","Villages in","Villages of","Cities in","Cities of","Towns of","Towns in","Cities and towns in","Cities and towns of"),populatedPlaceToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Houses on the National Register of Historic Places in", "Houses on the National Register of Historic Places for"),nationalRegisterHouseToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Hospitals of","Hospitals in","Hospitals for"),hospitalToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Private schools in","Elementary schools in","Jesuit high schools in","Universities and colleges in","Law schools in","Universities in","Colleges in","High schools in","Private schools for","Private schools of","Public schools in","Schools in","Public schools for","Public schools of"),schoolToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("National Historic Landmarks of", "National Historic Landmarks in", "National Historic Landmarks for"),historicLandmarksToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("National Register of Historic Places in"),nationalRegisterPlaceToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Museums in","Art museums in","History museums in","Art history museums in"),museumToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Airports in"),airportsToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Buildings and structures in"),buildingsAndStructuresToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Churches in","Grade II listed churches in","Congressional churches in","Grade I listed churches in","Roman Catholic churches in","Lutheran churches in","Baptist churches in"),churchToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Railway stations for","Railway stations in","Train stations in","Train stations of","Metro stations in"),railwayToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("State parks of","National parks of","City parks of", "State parks in", "National parks in","City parks in"),parksToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Mountains of","Volcanoes of","Volcanoes in","Mountains in","Mountain ranges of","Mountain ranges in"),mountainToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Nature reserves of","Nature reserves for","Nature reserves in"),natureReserveToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Waterfalls of","Waterfalls in"),waterfallToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Dams in","Dams of"),damLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Bodies of water of"),bodyOfWaterToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Glaciers of"),glacierToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Lighthouses in","Lighthouses of"),lighthouseToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Protected areas of","Protected areas in"),protectedAreaToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Islands of","Uninhabited islands of"),islandToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Forests of","National forests of","Rainforests of","National rainforests of"),forestToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Wilderness areas of","Wilderness areas in"),wildernessAreaToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Bridges in","Bridges over","Road bridges in","Pedestrian bridges in","Railway bridges in","Truss bridges in","Steel bridges in","Wooden bridges in"),bridgeToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Hotels in"),hotelToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Radio stations in"),radioStationToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Census-designated places in"),censusDesignatedPlaceLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Collage football venues","Rugby union stadiums in","Sports venues in","Multi-purpose stadiums in","Baseball venues in","Indoor arenas in","Football venues in","Soccer venues in"),stadiumToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Glass sculptures in","Fiberglass scultures in","Clay sculptures in","Porcelain sculptures in","Wooden sculptures in","Concrete sculptures in","Sculptures in","Steel sculptures in","Stone sculptures in","Marble sculptures in","Granite sculptures in","Bronze sculptures in","Outdoor sculptures in"),sculptureToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Former populated places in","Former populated places of","Former municipalities of","Former municipalities for", "Former municipalities in"),formerPopulatedPlaceToLocationsMap);
           }
        });

        final List<Map<String,Collection<String>>> allDataMaps = Arrays.asList(
                sculptureToLocationsMap,radioStationToLocationsMap,formerPopulatedPlaceToLocationsMap,
                islandToLocationsMap,bridgeToLocationsMap,hotelToLocationsMap,
                forestToLocationsMap,protectedAreaToLocationsMap,hotelToLocationsMap,
                stadiumToLocationsMap,lighthouseToLocationsMap,glacierToLocationsMap,
                bodyOfWaterToLocationsMap,waterfallToLocationsMap,
                damLocationsMap,natureReserveToLocationsMap,churchToLocationsMap,
                nationalRegisterPlaceToLocationsMap,museumToLocationsMap,schoolToLocationsMap,
                historicLandmarksToLocationsMap,airportsToLocationsMap,
                nationalRegisterHouseToLocationsMap,parksToLocationsMap,
                mountainToLocationsMap,railwayToLocationsMap,
                buildingsAndStructuresToLocationsMap,touristAttractionsToLocationsMap,
                censusDesignatedPlaceLocationsMap,
                populatedPlaceToLocationsMap
        );

        AtomicLong missing = new AtomicLong(0);
        final long total = database.getPois().size();
        final Map<String,Long> counts = Collections.synchronizedMap(new HashMap<>());
        database.getPois().parallelStream().forEach(poi->{
            if(poi.getCategories()!=null) {
                if(allDataMaps.stream()
                        .noneMatch(map->map.containsKey(poi.getTitle()))) {
                    //System.out.println("Missing "+poi.getTitle()+": "+poi.getCategories());
                    missing.getAndIncrement();
                    poi.getCategories().forEach(category->{
                        synchronized (counts) {
                            counts.put(category,counts.getOrDefault(category,0L)+1L);
                        }
                    });
                }
            }
        });

        Graph placeGraph = new BayesianNet();
        populatedPlaceToLocationsMap.entrySet().forEach(e->{
            String place = e.getKey();
            Collection<String> within = e.getValue();
            Node placeNode = placeGraph.addBinaryNode(place);
            within.forEach(w->{
                Node other = placeGraph.addBinaryNode(w);
                placeGraph.connectNodes(other,placeNode);
                //System.out.println(other.getLabel()+" -> "+placeNode.getLabel());
            });
        });

        Set<String> states = Collections.synchronizedSet(States.getStates().stream().map(s->s.toUpperCase()).collect(Collectors.toSet()));
        Map<String,Collection<String>> stateLinksMap = Collections.synchronizedMap(new HashMap<>(states.size()));
        Set<String> stateLinks = Collections.synchronizedSet(new HashSet<>());
        states.forEach(state->{
            Node node = placeGraph.findNode(state);
            if(node!=null) {
                Collection<String> related = getChildrenFor(node);
                if (related.size() > 0) {
                    System.out.println("State "+state+": "+String.join("; ",related));
                    stateLinksMap.put(state, related);
                    stateLinks.addAll(related);
                }
            }
        });

        final List<Map<String,Collection<String>>> nonPlaceMaps = Collections.synchronizedList(new ArrayList<>(allDataMaps));
        nonPlaceMaps.remove(populatedPlaceToLocationsMap);
        
        Collection<String> poiToLocations = Collections.synchronizedSet(new HashSet<>());
        AtomicLong cnt = new AtomicLong(0);
        final long totalCnt = nonPlaceMaps.stream().mapToLong(n->n.size()).sum();
        database.setPois(database.getPois().stream().filter(poi->populatedPlaceToLocationsMap.containsKey(poi.getTitle())).collect(Collectors.toList()));

        nonPlaceMaps.parallelStream().forEach(map->{
            map.forEach((title,v)->{
                poiToLocations.add(title);
                if(cnt.getAndIncrement()%100000==99999) {
                    System.out.println("Found "+cnt.get()+" out of "+totalCnt);
                }
            });
        });

        System.out.println("Pois matched: "+poiToLocations.size());
        System.out.println("States matched: "+stateLinksMap.size()+ " out of "+states.size());
        System.out.println("State links: "+stateLinks.size());

        Set<String> statesCopy = new HashSet<>(states);
        statesCopy.removeAll(stateLinksMap.keySet());
        System.out.println("Missing states: "+String.join("; ",statesCopy));
        //System.out.println("Top missing tags: "+String.join("\n",counts.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(100)
        //.map(e->e.getKey()+": "+e.getValue()).collect(Collectors.toList())));
        System.out.println("Num missing: "+missing.get()+" out of "+total);
        System.out.println("Num census designated places: "+censusDesignatedPlaceLocationsMap.size());
        System.out.println("Num former populated places: "+formerPopulatedPlaceToLocationsMap.size());
        System.out.println("Num stadiums: "+stadiumToLocationsMap.size());
        System.out.println("Num sculptures: "+sculptureToLocationsMap.size());
        System.out.println("Num islands: "+islandToLocationsMap.size());
        System.out.println("Num radio stations: "+radioStationToLocationsMap.size());
        System.out.println("Num protected areas: "+protectedAreaToLocationsMap.size());
        System.out.println("Num bridges: "+bridgeToLocationsMap.size());
        System.out.println("Num hotels: "+hotelToLocationsMap.size());
        System.out.println("Num forests: "+forestToLocationsMap.size());
        System.out.println("Num wilderness areas: "+wildernessAreaToLocationsMap.size());
        System.out.println("Num waterfalls: "+waterfallToLocationsMap.size());
        System.out.println("Num dams: "+damLocationsMap.size());
        System.out.println("Num glaciers: "+glacierToLocationsMap.size());
        System.out.println("Num lighthouses: "+lighthouseToLocationsMap.size());
        System.out.println("Num bodies of water: "+bodyOfWaterToLocationsMap.size());
        System.out.println("Num historic landmarks: "+historicLandmarksToLocationsMap.size());
        System.out.println("Num national places: "+nationalRegisterPlaceToLocationsMap.size());
        System.out.println("Num churches: "+churchToLocationsMap.size());
        System.out.println("Num mountains: "+mountainToLocationsMap.size());
        System.out.println("Num schools: "+schoolToLocationsMap.size());
        System.out.println("Num hospitals: "+hospitalToLocationsMap.size());
        System.out.println("Num parks: "+parksToLocationsMap.size());
        System.out.println("Num national houses: "+nationalRegisterHouseToLocationsMap.size());
        System.out.println("Num railways: "+railwayToLocationsMap.size());
        System.out.println("Num attractions: "+touristAttractionsToLocationsMap.size());
        System.out.println("Num buildings: "+buildingsAndStructuresToLocationsMap.size());
        System.out.println("Num airports: "+airportsToLocationsMap.size());
        System.out.println("Num nature reserves: "+natureReserveToLocationsMap.size());
        System.out.println("Num populated places: "+populatedPlaceToLocationsMap.size());

        //Map<String,Collection<String>> groupedPopulatedPlaces = groupMaps(populatedPlaceToLocationsMap,Arrays.asList(cityToLocationsMap,touristAttractionsToLocationsMap,countyToLocationsMap,villageToLocationsMap,districtToLocationsMap,villageToLocationsMap,municipalityToLocationsMap,formerMunicipalityToLocationsMap));
        //System.out.println("Matched grouped places: "+groupedPopulatedPlaces.size());

  }
}
