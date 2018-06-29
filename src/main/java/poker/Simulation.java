package main.java.poker;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Simulation {
    private static final Random rand = new Random(2352);
    private final int numPlayersStart;
    private List<Card> deck;
    private AtomicInteger deckIdx;
    private int numPlayers;
    public Simulation(int numDecks, int numPlayersStart) {
        this.numPlayersStart=numPlayersStart;
        this.numPlayers=numPlayersStart;
        deckIdx = new AtomicInteger(0);
        deck = new ArrayList<>(52*numDecks);
        for(int i = 0; i < numDecks; i++) {
            for(int j = 1; j <= 13; j++) {
                for(char c : new char[]{'c', 'd', 'h', 's'}) {
                    Card card = new Card();
                    card.num=j;
                    card.suit=c;
                    deck.add(card);
                }
            }
        }
    }

    public void shuffleDeck() {
        deckIdx.set(0);
        Collections.shuffle(deck, rand);
    }

    public Card nextCard() {
        if(deckIdx.get()>=deck.size()) {
            shuffleDeck();
        }
        Card card = deck.get(deckIdx.get());
        deckIdx.getAndIncrement();
        return card;
    }

    public Hand[] simulateHand() {
        shuffleDeck();
        Hand[] hands = new Hand[numPlayers];
        for(int i = 0; i < numPlayers; i++) {
            Hand hand = new Hand();
            hand.cards = new Card[]{nextCard(), nextCard()};
            hands[i]=hand;
        }
        return hands;
    }



    class Hand {
        Card[] cards;
        @Override
        public String toString() {
            return Arrays.toString(cards);
        }
    }

    class Card {
        int num;
        char suit;
        @Override
        public String toString() {
            return String.valueOf(num)+String.valueOf(suit).toUpperCase();
        }
    }


    public static void main(String[] args) {
        Simulation simulation = new Simulation(4, 5);
        Hand[] hands = simulation.simulateHand();
        for(Hand hand : hands) {
            System.out.println("Hand: "+hand);
        }

    }
}
