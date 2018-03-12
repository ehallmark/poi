package main.java.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Countries {
    public static List<String> getCountries() {
        List<String> states = Collections.synchronizedList(new ArrayList<>(50));
        try(BufferedReader reader = new BufferedReader(new FileReader(new File("countries.csv")))) {
            states.addAll(reader.lines().map(line->line.replace("\"","").trim()).collect(Collectors.toList()));
        } catch(Exception e) {
            e.printStackTrace();
        }
        return states;
    }
}
