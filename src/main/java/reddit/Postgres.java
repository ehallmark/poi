package main.java.reddit;

import java.sql.*;
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
    public static void ingest(Comment comment) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("insert into comments (id,parent_id,subreddit_id,link_id,text,score,ups,author,controversiality) values (?,?,?,?,?,?,?,?,?) on conflict (id) do nothing");
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
        ps.close();
    }

    public static void iterate(Consumer<Comment> consumer) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("select c1.id,c1.parent_id,c1.subreddit_id,c1.link_id,c1.text,c1.score,c1.ups,c1.author,c1.controversiality,c2.text as parent_text from comments as c1 join comments as c2 on ('t1_'||c2.id=c1.parent_id)");
        ResultSet rs = ps.executeQuery();
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
            comment.setParent_body(rs.getString(10));
            consumer.accept(comment);
        }
        rs.close();
        ps.close();
    }
}
