package main.java.predict.tech_tags;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

public class Category implements Serializable {
    private static final long serialVersionUID = 1L;
    @Getter @Setter
    private String title;
    @Getter @Setter
    private Set<String> categories;
    @Getter @Setter
    private Set<String> links;

    protected Category(String title, Set<String> categories, Set<String> links) {
        this.title=title;
        this.categories=categories;
        this.links=links;
    }

    public static Category create(String title, Set<String> categories, Set<String> links) {
        return new Category(title,categories,links);
    }
}
