package main.java.predict;

import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.primitives.Pair;

import java.io.*;
import java.util.*;
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
    public Database(Map<String,Map<String,Object>> data) {
        this.data=data;
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
                if (category.startsWith(prefix)) {
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

    public static void main(String[] args) {
        Map<String,Map<String,Object>> data = load(dataFile);
        Database database = new Database(data);
        database.init();

        double portlandLat = 45d+31d/60+12d/3600;
        double portlandLong = -(122d+40d/60+55d/3600);


        Map<String,Collection<String>> touristAttractionsToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> hospitalToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> schoolToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> museumToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> unincorporatedCommunityToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> townshipToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> nationalRegisterHouseToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> nationalRegisterPlaceToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> parksToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> airportsToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> historicLandmarksToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> mountainToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> railwayToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> cityToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> buildingsAndStructuresToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> populatedPlaceToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> countyToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> districtToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> villageToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> municipalityToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> formerMunicipalityToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> provinceToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> churchToLocationsMap = Collections.synchronizedMap(new HashMap<>());
        database.getPois().parallelStream().forEach(poi->{
           if(poi.getCategories()!=null) {
               extractLocationCategories(poi,Arrays.asList("Tourist attractions in","Tourist attractions of","Tourist attractions"),touristAttractionsToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Cities in","Cities of","Towns of","Towns in","Cities and towns in","Cities and towns of"),cityToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Townships of","Townships for","Townships in"),townshipToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Unincorporated communities of","Unincorporated communities for","Unincorporated communities in"),unincorporatedCommunityToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Houses on the National Register of Historic Places in", "Houses on the National Register of Historic Places for"),nationalRegisterHouseToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Counties in","Counties of","Counties for"),countyToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Hospitals of","Hospitals in","Hospitals for"),hospitalToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Private schools in","Private schools for","Private schools of","Public schools in","Schools in","Public schools for","Public schools of"),schoolToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("National Historic Landmarks of", "National Historic Landmarks in", "National Historic Landmarks for"),historicLandmarksToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("National Register of Historic Places in"),nationalRegisterPlaceToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Museums in","Art museums in","History museums in","Art history museums in"),museumToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Airports in"),airportsToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Provinces in","Provinces of", "Provinces for"),provinceToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Buildings and structures in"),buildingsAndStructuresToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Villages in","Villages of"),villageToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Districts in","Districts of", "Districts for"),districtToLocationsMap);
               extractLocationCategories(poi,Collections.singletonList("Populated places in"),populatedPlaceToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Churches in","Grade I listed churches in","Roman Catholic churches in","Lutheran churches in","Baptist churches in"),churchToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Railway stations for","Railway stations in","Train stations in","Train stations of","Metro stations in"),railwayToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("State parks of","National parks of","City parks of", "State parks in", "National parks in","City parks in"),parksToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Mountains of","Mountains in","Mountain ranges of","Mountain ranges in"),mountainToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Municipalities for", "Municipalities of", "Municipalities in"),municipalityToLocationsMap);
               extractLocationCategories(poi,Arrays.asList("Former municipalities of","Former municipalities for", "Former municipalities in"),formerMunicipalityToLocationsMap);
           }
        });
        database.getPois().parallelStream().forEach(poi->{
            if(poi.getCategories()!=null) {
                if(Arrays.asList(churchToLocationsMap,nationalRegisterPlaceToLocationsMap,museumToLocationsMap,schoolToLocationsMap,historicLandmarksToLocationsMap,unincorporatedCommunityToLocationsMap,airportsToLocationsMap,townshipToLocationsMap,nationalRegisterHouseToLocationsMap,parksToLocationsMap,mountainToLocationsMap,railwayToLocationsMap,cityToLocationsMap,buildingsAndStructuresToLocationsMap,touristAttractionsToLocationsMap,countyToLocationsMap,provinceToLocationsMap,villageToLocationsMap,districtToLocationsMap,populatedPlaceToLocationsMap,municipalityToLocationsMap,formerMunicipalityToLocationsMap).stream()
                        .noneMatch(map->map.containsKey(poi.getTitle()))) {
                    System.out.println("Missing "+poi.getTitle()+": "+poi.getCategories());
                }
            }
        });
        System.out.println("Num cities: "+cityToLocationsMap.size());
        System.out.println("Num historic landmarks: "+historicLandmarksToLocationsMap.size());
        System.out.println("Num national places: "+nationalRegisterPlaceToLocationsMap.size());
        System.out.println("Num churches: "+churchToLocationsMap.size());
        System.out.println("Num townships: "+townshipToLocationsMap.size());
        System.out.println("Num mountains: "+mountainToLocationsMap.size());
        System.out.println("Num schools: "+schoolToLocationsMap.size());
        System.out.println("Num hospitals: "+hospitalToLocationsMap.size());
        System.out.println("Num unincorporated communities: "+unincorporatedCommunityToLocationsMap.size());
        System.out.println("Num parks: "+parksToLocationsMap.size());
        System.out.println("Num national houses: "+nationalRegisterHouseToLocationsMap.size());
        System.out.println("Num railways: "+railwayToLocationsMap.size());
        System.out.println("Num attractions: "+touristAttractionsToLocationsMap.size());
        System.out.println("Num buildings: "+buildingsAndStructuresToLocationsMap.size());
        System.out.println("Num counties: "+countyToLocationsMap.size());
        System.out.println("Num villages: "+villageToLocationsMap.size());
        System.out.println("Num airports: "+airportsToLocationsMap.size());
        System.out.println("Num districts: "+districtToLocationsMap.size());
        System.out.println("Num provinces: "+provinceToLocationsMap.size());
        System.out.println("Num populated places: "+populatedPlaceToLocationsMap.size());
        System.out.println("Num municipalities: "+municipalityToLocationsMap.size());
        System.out.println("Num former municipalities: "+formerMunicipalityToLocationsMap.size());

        //Map<String,Collection<String>> groupedPopulatedPlaces = groupMaps(populatedPlaceToLocationsMap,Arrays.asList(cityToLocationsMap,touristAttractionsToLocationsMap,countyToLocationsMap,villageToLocationsMap,districtToLocationsMap,villageToLocationsMap,municipalityToLocationsMap,formerMunicipalityToLocationsMap));
        //System.out.println("Matched grouped places: "+groupedPopulatedPlaces.size());

        //database.setPois(database.getPois().stream().filter(poi->airportsToLocationsMap.containsKey(poi.getTitle())).collect(Collectors.toList()));
        //System.out.println("POIs: "+String.join("\n",database.closestPois(portlandLat,portlandLong,30,false).stream().map(e->e.getTitle()+": "+e.getCategories()).collect(Collectors.toList())));
    }
}
