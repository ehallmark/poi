package main.java.patents;

import main.java.word2vec.Word2VecTextFolder;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import org.deeplearning4j.models.word2vec.Word2Vec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Stream;

public class IngestGooglePatentsToWord2VecDir {
    static Map<String,Object> parseLine(String line) {
        return Document.parse(line);
    }

    public static void main(String[] args) throws Exception {

        File dir = new File("/media/ehallmark/tank/google-big-query/patents/");
        Word2VecTextFolder.openAll();

        Set<String> familyIds = Collections.synchronizedSet(new HashSet<>());
        Stream.of(dir.listFiles()).parallel().forEach(file->{
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(file))))) {
                reader.lines().forEach(line->{
                    if(line==null||line.isEmpty()) return;
                    Map<String,Object> json = parseLine(line);
                    //System.out.println("Line: "+line);
                    String country = (String)json.get("country_code");
                    if(country==null) return;
                    if(!country.equals("US")) return;
                    String familyId = (String)json.get("family_id");
                    if(familyId==null) return;
                    if(familyIds.contains(familyId)) return;
                    List<Map<String,Object>> abstractLocalized = (List<Map<String,Object>>)json.get("abstract_localized");
                    List<Map<String,Object>> claimsLocalized = (List<Map<String,Object>>)json.get("claims_localized");
                    List<Map<String,Object>> descriptionLocalized = (List<Map<String,Object>>)json.get("description_localized");
                    Arrays.asList(abstractLocalized,claimsLocalized,descriptionLocalized).forEach(list->{
                        if(list==null) return;
                        for(Map<String,Object> map : list) {
                            if(map.get("language").equals("en")) {
                                String text = (String)map.get("text");
                                if(text==null) continue;
                                Stream.of(text.split("\\n")).forEach(sentence->{
                                    String validChars = sentence.replaceAll("[^a-z ]","");
                                    if(validChars.length()>Math.round(0.75f*sentence.length())) {
                                        String[] words = sentence.split("\\s+");
                                        if (words.length < 10) return;
                                        Word2VecTextFolder.consume(sentence);
                                    }
                                });
                                break;
                            }
                        }
                    });
                    familyIds.add(familyId);
                });
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        Word2VecTextFolder.closeAll();
    }
}
