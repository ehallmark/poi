package main.java.gis;

import au.com.bytecode.opencsv.CSVWriter;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import main.java.predict.Database;
import main.java.predict.PointOfInterest;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateShapefiles {
    public static final File poiShapeFile = new File("poi_shape_file.shp");


    /**
     * Here is how you can use a SimpleFeatureType builder to create the schema for your shapefile
     * dynamically.
     * <p>
     * This method is an improvement on the code used in the main method above (where we used
     * DataUtilities.createFeatureType) because we can set a Coordinate Reference System for the
     * FeatureType and a a maximum field length for the 'name' field dddd
     */
    private static SimpleFeatureType createFeatureType() {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Location");
        builder.setCRS(DefaultGeographicCRS.WGS84); // <- Coordinate reference system

        // add attributes in order
        builder.add("the_geom", Point.class);
        builder.length(30).add("Name", String.class); // <- 15 chars width for name field
        builder.add("number",Integer.class);

        // build the type
        final SimpleFeatureType LOCATION = builder.buildFeatureType();

        return LOCATION;
    }

    private static double toRangeRadian(double in) {
        return toRange(in, -Math.PI, Math.PI);
    }

    private static double toRange(double in, double start, double end) {
        if(in>=start&&in<=end) return in;
        double period = end - start;
        while(in < start) {
            in+=period;
        }
        while(in > end) {
            in-=period;
        }
        return in;
    }

    public static void createShapefile(List<PointOfInterest> pointOfInterests, File newFile) throws Exception {
        /*
         * We use the DataUtilities class to create a FeatureType that will describe the data in our
         * shapefile.
         *
         * See also the createFeatureType method below for another, more flexible approach.
         */
        final SimpleFeatureType TYPE =createFeatureType();
        System.out.println("TYPE:"+TYPE);

        /*
         * A list to collect features as we create them.
         */
        List<SimpleFeature> features = new ArrayList<>();

        /*
         * GeometryFactory will be used to create the geometry attribute of each feature,
         * using a Point object for the location.
         */
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

        //MapContent map = new MapContent();
        //Layer layer = DrawShapefiles.addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-americas.shp/groads-v1-americas.shp"));
        //layer.getBounds()

        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("/home/ehallmark/Downloads/poi.csv")));
        writer.write("Name,LatitudeRad,LongitudeRad,Links,Categories\n");
        writer.flush();
        pointOfInterests.forEach(poi->{
            double latitude = toRangeRadian(poi.getLatitude());
            double longitude = toRangeRadian(poi.getLongitude());
            String name = poi.getTitle().replace("\"", "");
            int number = poi.getLinks()==null?0:poi.getLinks().size();
            String categories = poi.getCategories()==null ? "": String.join("; ",poi.getCategories()).replace("\"","");
            String links = poi.getLinks()==null ? "": String.join("; ",poi.getLinks()).replace("\"","");
            try {
                System.out.println("POI: "+name);
                writer.write("\""+name+"\","+latitude+","+longitude+",\""+links+"\",\""+categories+"\"\n");
                writer.flush();
            } catch(Exception e) {
                e.printStackTrace();
            }

            /* Longitude (= x coord) first ! */
            Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

            featureBuilder.add(point);
            featureBuilder.add(name);
            featureBuilder.add(number);
            SimpleFeature feature = featureBuilder.buildFeature(null);
            features.add(feature);
        });
        writer.flush();
        writer.close();

        /*
         * Get an output file name and create the new shapefile
         */

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<>();
        params.put("url", newFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

        /*
         * TYPE is used as a template to describe the file contents
         */
        newDataStore.createSchema(TYPE);

        /*
         * Write the features to the shapefile
         */
        Transaction transaction = new DefaultTransaction("create");

        String typeName = newDataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
        SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
        /*
         * The Shapefile format has a couple limitations:
         * - "the_geom" is always first, and used for the geometry attribute name
         * - "the_geom" must be of type Point, MultiPoint, MuiltiLineString, MultiPolygon
         * - Attribute names are limited in length
         * - Not all data types are supported (example Timestamp represented as Date)
         *
         * Each data store has different limitations so check the resulting SimpleFeatureType.
         */
        System.out.println("SHAPE:"+SHAPE_TYPE);

        if (featureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            /*
             * SimpleFeatureStore has a method to add features from a
             * SimpleFeatureCollection object, so we use the ListFeatureCollection
             * class to wrap our list of features.
             */
            SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch (Exception problem) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            System.exit(0); // success!
        } else {
            System.out.println(typeName + " does not support read/write access");
            System.exit(1);
        }
    }


    public static void main(String[] args) throws Exception {
        Database database = new Database(Database.load(Database.labeledDataFile));
        database.init(true);
        createShapefile(database.getPois(),poiShapeFile);
    }
}
