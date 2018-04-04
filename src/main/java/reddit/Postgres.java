package main.java.reddit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class Postgres {
    private static final String dbUrl = "jdbc:postgresql://localhost/reddit?user=postgres&password=password&tcpKeepAlive=true";
    private static Connection conn;
    static {
        resetConn();
    }

    public static synchronized void resetConn() {
        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(false);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void commit() throws SQLException {
        conn.commit();
    }

    private static final AtomicLong ingestCnt = new AtomicLong(0);
    private static PreparedStatement ps;
    public synchronized static void ingest(Comment comment) throws SQLException {
        if(ps==null) {
            ps = conn.prepareStatement("insert into comments (id,parent_id,subreddit_id,link_id,text,score,ups,author,controversiality) values (?,?,?,?,?,?,?,?,?) on conflict (id) do nothing");
        }
        ps.setString(1,comment.getId());
        ps.setString(2,comment.getParent_id());
        ps.setString(3,comment.getSubreddit_id());
        ps.setString(4,comment.getLink_id());
        ps.setString(5,comment.getBody());
        ps.setInt(6,comment.getScore());
        ps.setInt(7,comment.getUps());
        ps.setString(8,comment.getAuthor());
        ps.setInt(9,comment.getControversiality());
        ps.executeUpdate();
        if(ingestCnt.getAndIncrement()%10000==9999) {
            commit();
        }
    }

    public static void iterate(Consumer<Comment> consumer) throws SQLException {
        //PreparedStatement ps = conn.prepareStatement("select c1.id,c1.parent_id,c1.subreddit_id,c1.link_id,c1.text,c1.score,c1.ups,c1.author,c1.controversiality,c2.text as parent_text from comments as c1 join comments as c2 on ('t1_'||c2.id=c1.parent_id)");
        PreparedStatement ps = conn.prepareStatement("select c1.id,c1.parent_id,c1.subreddit_id,c1.link_id,c1.text,c1.score,c1.ups,c1.author,c1.controversiality from comments as c1");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        Map<String,Comment> messageCacheF = new HashMap<>();
        Map<String,Comment> messageCacheR = new HashMap<>();
        List<String> cachedF = new LinkedList<>();
        List<String> cachedR = new LinkedList<>();
        final int maxCacheSize = 100000;
        long seen = 0;
        while(rs.next()) {
            Comment comment = new Comment();
            comment.setId(rs.getString(1));
            comment.setParent_id(rs.getString(2));
            comment.setSubreddit_id(rs.getString(3));
            comment.setLink_id(rs.getString(4));
            comment.setBody(rs.getString(5));
            comment.setScore(rs.getInt(6));
            comment.setUps(rs.getInt(7));
            comment.setAuthor(rs.getString(8));
            comment.setControversiality(rs.getInt(9));
            {
                if (messageCacheF.containsKey(comment.getParent_id())) {
                    comment.setParent_body(messageCacheF.get(comment.getParent_id()).getBody());
                }
                String typeIdF = "t1_" + comment.getId();
                cachedF.add(typeIdF);
                messageCacheF.put(typeIdF,comment);
                if(cachedF.size()>maxCacheSize) {
                    messageCacheF.remove(cachedF.remove(0));
                }
            }
            {
                String typeIdR = "t1_" + comment.getId();
                if (messageCacheR.containsKey(typeIdR)) {
                    Comment childComment = messageCacheR.get(typeIdR);
                    childComment.setParent_body(comment.getBody());
                    consumer.accept(childComment);
                }
                cachedR.add(comment.getParent_id());
                messageCacheR.put(comment.getParent_id(),comment);
                if(cachedR.size()>maxCacheSize) {
                    messageCacheR.remove(cachedR.remove(0));
                }
            }
            if(comment.getParent_body()!=null) {
                consumer.accept(comment);
            }

            if(seen%50000==49999) {
                System.gc();
                System.out.println("Cached "+seen);
            }
            seen++;
        }
        rs.close();
        ps.close();
    }

    public static void iterateNoParents(Consumer<Comment> consumer, int sampling) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("select c1.id,c1.parent_id,c1.subreddit_id,c1.link_id,c1.text,c1.score,c1.ups,c1.author,c1.controversiality from comments as c1");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        while(rs.next()&&(sampling<=0||cnt.get()<sampling)) {
            Comment comment = new Comment();
            comment.setId(rs.getString(1));
            comment.setParent_id(rs.getString(2));
            comment.setSubreddit_id(rs.getString(3));
            comment.setLink_id(rs.getString(4));
            comment.setBody(rs.getString(5));
            comment.setScore(rs.getInt(6));
            comment.setUps(rs.getInt(7));
            comment.setAuthor(rs.getString(8));
            comment.setControversiality(rs.getInt(9));
            consumer.accept(comment);
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Iterated over: "+cnt.get()+" out of "+sampling);
            }
        }
        rs.close();
        ps.close();
    }

    public static void iterateForControversiality(Consumer<Comment> consumer, int sampling) throws SQLException {
        PreparedStatement ps1 = conn.prepareStatement("select id,parent_id,subreddit_id,link_id,text,score,ups,author,controversiality from comments where controversiality=0");
        PreparedStatement ps2 = conn.prepareStatement("select id,parent_id,subreddit_id,link_id,text,score,ups,author,controversiality from comments where controversiality=1");
        ps1.setFetchSize(100);
        ps2.setFetchSize(100);
        ResultSet rs1 = ps1.executeQuery();
        ResultSet rs2 = ps2.executeQuery();
        final Random rand = new Random(2352);
        AtomicLong cnt = new AtomicLong(0);
        int numControversial = 0;
        ResultSet rs = (rand.nextBoolean()?rs1:rs2);
        while(rs.next()&&(sampling<=0||cnt.get()<sampling)) {
            if(rs==rs2) numControversial++;
            Comment comment = new Comment();
            comment.setId(rs.getString(1));
            comment.setParent_id(rs.getString(2));
            comment.setSubreddit_id(rs.getString(3));
            comment.setLink_id(rs.getString(4));
            comment.setBody(rs.getString(5));
            comment.setScore(rs.getInt(6));
            comment.setUps(rs.getInt(7));
            comment.setAuthor(rs.getString(8));
            comment.setControversiality(rs.getInt(9));
            consumer.accept(comment);
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Iterated over: "+cnt.get()+" out of "+sampling+". Num controversial: "+numControversial);
            }
            rs = (rand.nextBoolean()?rs1:rs2);
        }
        rs1.close();
        rs2.close();
        ps1.close();
        ps2.close();
    }
}
