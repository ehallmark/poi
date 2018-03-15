package main.java.reddit;

import lombok.Getter;
import lombok.Setter;

public class Comment {
    @Getter @Setter
    private String id;
    @Getter @Setter
    private String parent_id;
    @Getter @Setter
    private String subreddit_id;
    @Getter @Setter
    private String link_id;
    @Getter @Setter
    private String body;
    @Getter @Setter
    private int controversiality;
    @Getter @Setter
    private int ups;
    @Getter @Setter
    private int score;
    @Getter @Setter
    private String author;
    @Getter @Setter
    private String parent_body;

    @Override
    public String toString() {
        return "Comment "+id+"\n\tAuthor "+author+"\n\tParent "+parent_id+"\n\tSubreddit "+subreddit_id+"\n\tLink "+link_id+"\n\tBody: "+body+"\n\tControversial "+controversiality+"\n\tUps "+ups+"\n\tScore "+score;
    }
}
