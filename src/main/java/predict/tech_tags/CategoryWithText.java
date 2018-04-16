package main.java.predict.tech_tags;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

public class CategoryWithText extends Category {
    private static final long serialVersionUID = 1L;
    @Getter @Setter
    private String text;
    private CategoryWithText(String title, Set<String> categories, Set<String> links, String text) {
        super(title,categories,links);
        this.text=text;

    }

    public static CategoryWithText create(String title, Set<String> categories, Set<String> links, String text) {
        return new CategoryWithText(title,categories,links,text);
    }
}
