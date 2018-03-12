package main.java.predict;

import lombok.Getter;

import java.util.Collection;
import java.util.Set;

public class PointOfInterest {
    private double latitude;
    private double longitude;
    @Getter
    private String title;
    @Getter
    private Collection<String> links;
    @Getter
    private Collection<String> categories;
    public PointOfInterest(double latitude, double longitude, String title, Collection<String> links, Collection<String> categories) {
        this.latitude=latitude;
        this.longitude=longitude;
        this.categories=categories;
        this.title=title;
        this.links=links;
    }

    public double haversineDistance(PointOfInterest other) {
        final double phi1 = latitude;
        final double phi2 = other.latitude;
        final double lambda1 = longitude;
        final double lambda2 = other.longitude;

        return haversine(phi2-phi1)+Math.cos(phi1)*Math.cos(phi2)*haversine(lambda2-lambda1);
    }

    private static double haversine(double x) {
        return (1d-Math.cos(x))/2d;
    }

}
