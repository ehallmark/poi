package main.java.gis;
import java.io.File;
import java.util.Iterator;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

/**
 * Prompts the user for a shapefile and displays the contents on the screen in a map frame.
 * <p>
 * This is the GeoTools Quickstart application used in documentationa and tutorials. *
 */
public class DrawShapefiles {

    /**
     * GeoTools Quickstart demo application. Prompts the user for a shapefile and displays its
     * contents on the screen in a map frame
     */
    public static Layer addLayerToMap(MapContent map, File file) throws Exception {
        FileDataStore store = FileDataStoreFinder.getDataStore(file);
        if(store==null) return null;
        SimpleFeatureSource featureSource = store.getFeatureSource();
        Style style = SLD.createSimpleStyle(featureSource.getSchema());
        Layer layer = new FeatureLayer(featureSource, style);
        map.addLayer(layer);
        return layer;
    }

    public static void main(String[] args) throws Exception {
        MapContent map = new MapContent();
        map.setTitle("Title");

        addLayerToMap(map,CreateShapefiles.poiShapeFile);
        /*addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-americas.shp/groads-v1-americas.shp"));
        addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-europe.shp/groads-v1-europe.shp"));
        addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-africa.shp/groads-v1-africa.shp"));
        addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-oceania-west.shp/groads-v1-oceania-west.shp"));
        addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-oceania-east.shp/groads-v1-oceania-east.shp"));
        addLayerToMap(map,new File("/home/ehallmark/Downloads/groads-v1-asia.shp/groads-v1-asia.shp"));*/


        /*SimpleFeatureIterator featureIterator = featureSource.getFeatures().features();
        while(featureIterator.hasNext()) {
            SimpleFeature feature = featureIterator.next();
            System.out.println("ID: "+feature.getID());
            System.out.println("Type: "+feature.getType());
            System.out.println("Feature Type: "+feature.getFeatureType());
            System.out.println("Attributes: "+feature.getAttributes());
        }*/

        // Now display the map
        JMapFrame.showMap(map);
    }

}