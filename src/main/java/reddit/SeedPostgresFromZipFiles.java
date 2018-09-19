package main.java.reddit;

import com.google.gson.Gson;
import main.java.util.ZipStream;

import java.io.BufferedReader;
import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SeedPostgresFromZipFiles {
    private static String datePadding(int monthValue) {
        String m = String.valueOf(monthValue);
        if(m.length()<2) m="0"+m;
        return m;
    }

    public static void main(String[] args) {
        final boolean debug = false;

        // download missing files from http://files.pushshift.io/reddit/comments/
        LocalDate startDate = //LocalDate.of(2005,1,1);
            LocalDate.of(2012,1,1);
        final String urlPrefix = "http://files.pushshift.io/reddit/comments/";
        List<LocalDate> dates = Collections.synchronizedList(new ArrayList<>());
        while(startDate.isBefore(LocalDate.now())) {
            dates.add(startDate);
            startDate = startDate.plusMonths(1);
        }

        dates.forEach(date->{
            String suffix = "RC_"+date.getYear()+"-"+datePadding(date.getMonthValue())+".bz2";
            String urlStr = urlPrefix+suffix;
            try (BufferedReader reader = ZipStream.getCompressedBufferedReaderForInputStream(new URL(urlStr).openStream())) {
                System.out.println("Starting file: "+urlStr);
                reader.lines().forEach(line->{
                    Comment comment = new Gson().fromJson(line,Comment.class);
                    if(comment!=null) {
                        if(debug) {
                            System.out.println("Comment: "+comment.toString());
                        } else {
                            if(comment.getBody()!=null&&!comment.getBody().startsWith("[")) {
                                try {
                                    Postgres.ingest(comment);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    System.out.println("Error ingesting comment: " + comment.toString());
                                }
                            }
                        }
                    }
                });

                System.out.println("Successfully completed file: "+urlStr);
                Postgres.commit();

            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }
}
