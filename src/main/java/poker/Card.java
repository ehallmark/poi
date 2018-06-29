package main.java.poker;

import java.util.Objects;

public class Card {
    int num;
    char suit;
    @Override
    public String toString() {
        return String.valueOf(num)+String.valueOf(suit);
    }
    @Override
    public boolean equals(Object other) {
        if(other instanceof Card) {
            return ((Card) other).suit==suit && ((Card) other).num==num;
        }
        return false;
    }
    @Override
    public int hashCode() {
        return Objects.hash(suit,num);
    }
}
