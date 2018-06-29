package main.java.poker;

import java.util.Arrays;

public class Hand {
    Card[] cards;
    @Override
    public String toString() {
        return Arrays.toString(cards);
    }
}
