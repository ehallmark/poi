package main.java.poker;

import javax.smartcardio.Card;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Simulation {
    private static final Random rand = new Random(2352);
    private final int numPlayersStart;
    private List<Card> deck;
    private AtomicInteger deckIdx;
    private int numPlayers;
    public Simulation(int numPlayersStart) {
        this.numPlayersStart=numPlayersStart;
        this.numPlayers=numPlayersStart;
        deckIdx = new AtomicInteger(0);
        deck = new ArrayList<>(52);
        for(char c : new char[]{'C', 'D', 'H', 'S'}) {
            for(int j = 1; j <= 13; j++) {
                Card card = new Card();
                card.num=j;
                card.suit=c;
                deck.add(card);
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
        nextCard(); // burn
        Card[] flop = new Card[]{nextCard(), nextCard(), nextCard()};
        nextCard(); // burn
        Card turn = nextCard();
        nextCard(); // burn
        Card river = nextCard();

        return hands;
    }


    public boolean containsFlush(Card[] cards) {
        Set<Character> suits = new HashSet<>();
        for(Card card : cards) {
            suits.add(card.suit);
        }
        return suits.size()==1;
    }

    public boolean containsStraight(Card[] cards) {
        int min = 14;
        int max = 0;
        Set<Integer> nums = new HashSet<>();
        for(Card card : cards) {
            if(card.num<min) {
                min = card.num;
            }
            if(card.num>max) {
                max = card.num;
            }
            nums.add(card.num);
        }
        boolean straight = max-min==5&&nums.size()==5;
        if(!straight) {
            // need to check aces over kings
            min = 14;
            max = 0;
            nums = new HashSet<>();
            for(Card card : cards) {
                int cardNum = card.num;
                if(cardNum==1) cardNum=14;
                if(cardNum<min) {
                    min = cardNum;
                }
                if(cardNum>max) {
                    max = cardNum;
                }
                nums.add(cardNum);
            }
            straight = max-min==5&&nums.size()==5;
        }
        return straight;
    }



    public int largestNOfAKind(Card[] cards) {
        Map<Integer,Long> counts = Stream.of(cards).collect(Collectors.groupingBy(e->e.num, Collectors.counting()));
        return counts.values().stream().mapToInt(d->d.intValue()).max().orElse(1);
    }

    public boolean containsFullHouse(Card[] cards) {
        Map<Integer,Long> counts = Stream.of(cards).collect(Collectors.groupingBy(e->e.num, Collectors.counting()));
        return counts.size()==2 && Arrays.asList(2,3).contains(counts.get(cards[0].num));
    }

    public boolean containsTwoPair(Card[] cards) {
        Map<Integer,Long> counts = Stream.of(cards).collect(Collectors.groupingBy(e->e.num, Collectors.counting()));
        return counts.size()==3 && counts.values().stream().mapToInt(d->d.intValue()).max().orElse(1)==2;
    }



    public double scoreHand(Card[] cards) {
        if(cards.length!=5) throw new RuntimeException("Can only score 5 cards at a time.");
        boolean flush = containsFlush(cards);
        boolean straight = containsStraight(cards);
        double score = 0d;
        if(flush && straight) {
            // straight flush!
            score = Math.max(score, 8d);
        } else if (flush) {
            score = Math.max(score, 5d);
        } else if (straight) {
            score = Math.max(score, 4d);
        }
        int largestN  = largestNOfAKind(cards);
        if(largestN==4) {
            // 4 of a kind
            score = Math.max(score, 7d);
        } else if (largestN==3) {
            // check for full house
            boolean fullHouse = containsFullHouse(cards);
            if(fullHouse) {
                score = Math.max(score, 6d);

            } else {
                // 3 of a kind
                score = Math.max(score, 3d);
            }
        } else if (largestN==2) {
            // check 2 pair
            boolean twoPair = containsTwoPair(cards);
            if(twoPair) {
                // two pair
                score = Math.max(score, 2d);
            } else {
                // single pair
                score = Math.max(score, 1d);
            }
        } else {
            score = Math.max(score, 0d);
        }

        return score;
    }



    public static double probabilityOfHand()


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
            return String.valueOf(num)+String.valueOf(suit);
        }
    }

    public static void main(String[] args) {
        Simulation simulation = new Simulation(5);
        Hand[] hands = simulation.simulateHand();
        for(Hand hand : hands) {
            System.out.println("Hand: "+hand);
        }

    }
}
