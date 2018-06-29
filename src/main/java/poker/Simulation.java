package main.java.poker;

import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Simulation {
    private static final Random rand = new Random(2352);
    private final int numPlayersStart;
    private List<Card> deck;
    private AtomicInteger deckIdx;
    private int numPlayers;
    private Hand[] hands;
    private Card[] flop;
    private Card turn;
    private Card river;

    public Simulation(int numPlayersStart) {
        this.numPlayersStart = numPlayersStart;
        this.numPlayers = numPlayersStart;
        deckIdx = new AtomicInteger(0);
        deck = new ArrayList<>(52);
        for (char c : new char[]{'C', 'D', 'H', 'S'}) {
            for (int j = 1; j <= 13; j++) {
                Card card = new Card();
                card.num = j;
                card.suit = c;
                deck.add(card);
            }
        }
    }

    public void shuffleDeck() {
        deckIdx.set(0);
        Collections.shuffle(deck, rand);
    }

    public Card nextCard() {
        if (deckIdx.get() >= deck.size()) {
            shuffleDeck();
        }
        Card card = deck.get(deckIdx.get());
        deckIdx.getAndIncrement();
        return card;
    }

    public void dealHands() {
        shuffleDeck();
        hands = new Hand[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            Hand hand = new Hand();
            hand.cards = new Card[]{nextCard(), nextCard()};
            hands[i] = hand;
        }
    }

    public void showFlop() {
        nextCard(); // burn
        flop = new Card[]{nextCard(), nextCard(), nextCard()};
    }

    public void showTurn() {
        nextCard();
        turn = nextCard();
    }


    public void showRiver() {
        nextCard();
        river = nextCard();
    }


    // returns the best hand
    public Pair<Hand, Double> simulateHand() {
        Hand bestHand = null;
        double bestScore = 0;
        for (Hand hand : hands) {
            Card[] cards = new Card[]{hand.cards[0], hand.cards[1], flop[0], flop[1], flop[2]};
            Card[] cardsClone = cards.clone();
            double score = scoreHand(cards);
            for (int i = 0; i < cards.length; i++) {
                // add in turn card
                cards[i] = turn;
                for (int j = 0; j < cards.length; j++) {
                    if ((i == 0 && j == 1) || (i == 1 && j == 0)) continue;
                    cards[j] = river;
                    score = Math.max(scoreHand(cards), score);
                    if (i == j) {
                        cards[i] = turn;
                    } else {
                        cards[i] = cardsClone[j];
                    }
                }
                cards[i] = cardsClone[i];
            }
            if (score > bestScore) {
                bestScore = score;
                bestHand = new Hand();
                bestHand.cards = hand.cards;
            }
        }
        //System.out.println("Best hand "+bestScore+": "+bestHand);
        return new Pair<>(bestHand, bestScore);
    }


    public boolean containsFlush(Card[] cards) {
        Set<Character> suits = new HashSet<>();
        for (Card card : cards) {
            suits.add(card.suit);
        }
        return suits.size() == 1;
    }

    public boolean containsStraight(Card[] cards) {
        int min = 14;
        int max = 0;
        Set<Integer> nums = new HashSet<>();
        for (Card card : cards) {
            if (card.num < min) {
                min = card.num;
            }
            if (card.num > max) {
                max = card.num;
            }
            nums.add(card.num);
        }
        boolean straight = max - min == 4 && nums.size() == 5;
        if (!straight) {
            // need to check aces over kings
            min = 14;
            max = 0;
            nums = new HashSet<>();
            for (Card card : cards) {
                int cardNum = card.num;
                if (cardNum == 1) cardNum = 14;
                if (cardNum < min) {
                    min = cardNum;
                }
                if (cardNum > max) {
                    max = cardNum;
                }
                nums.add(cardNum);
            }
            straight = max - min == 4 && nums.size() == 5;
        }
        return straight;
    }


    public int largestNOfAKind(Card[] cards) {
        Map<Integer, Long> counts = Stream.of(cards).collect(Collectors.groupingBy(e -> e.num, Collectors.counting()));
        return counts.values().stream().mapToInt(d -> d.intValue()).max().orElse(1);
    }

    public boolean containsFullHouse(Card[] cards, Map<Integer, Long> counts) {
        return counts.size() == 2 && Arrays.asList(2L, 3L).contains(counts.get(cards[0].num));
    }

    public boolean containsTwoPair(Map<Integer, Long> counts) {
        return counts.size() == 3 && counts.values().stream().mapToInt(d -> d.intValue()).max().orElse(1) == 2;
    }


    public double scoreHand(Card[] cards) {
        if (cards.length != 5) throw new RuntimeException("Can only score 5 cards at a time.");
        boolean flush = containsFlush(cards);
        boolean straight = containsStraight(cards);
        Map<Integer, Long> counts = Stream.of(cards).collect(Collectors.groupingBy(e -> e.num, Collectors.counting()));
        Map<Long, List<Integer>> countToCardIdx = counts.entrySet().stream().collect(Collectors.groupingBy(e -> e.getValue(), Collectors.mapping(e -> e.getKey(), Collectors.toCollection(ArrayList::new))));
        int[] sortedCardNumbers = counts.keySet().stream().mapToInt(e -> e == 1 ? 14 : e).sorted().toArray();
        double highCardBoost = IntStream.range(0, sortedCardNumbers.length).mapToDouble(i -> ((double) sortedCardNumbers[sortedCardNumbers.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
        double highCardBoostStraight = 0d;
        double score = 0d;
        if (straight) {
            // check whether ace is low
            if (counts.containsKey(1) && counts.containsKey(2)) {
                int[] sortedCardNumbersAceLow = counts.keySet().stream().mapToInt(e -> e).sorted().toArray();
                highCardBoostStraight = IntStream.range(0, sortedCardNumbersAceLow.length).mapToDouble(i -> ((double) sortedCardNumbersAceLow[sortedCardNumbersAceLow.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
            } else {
                highCardBoostStraight = highCardBoost;
            }
        }
        if (flush && straight) {
            // straight flush!
            // check whether ace is high card
            score = Math.max(score, 8d + highCardBoostStraight);
        } else if (flush) {
            score = Math.max(score, 5d + highCardBoost);
        } else if (straight) {
            score = Math.max(score, 4d + highCardBoostStraight);
        }
        int largestN = largestNOfAKind(cards);
        if (largestN == 4) {
            // 4 of a kind
            int cardIdx = countToCardIdx.get(4L).get(0);
            if (cardIdx == 1) cardIdx = 14;
            int cardIdx2 = countToCardIdx.get(1L).get(0);
            if (cardIdx2 == 1) cardIdx2 = 14;
            double bonus = ((double) cardIdx) / 15.0 + ((double) cardIdx2) / (15.0 * 15.0);
            score = Math.max(score, 7d + bonus);
        } else if (largestN == 3) {
            // check for full house
            boolean fullHouse = containsFullHouse(cards, counts);
            if (fullHouse) {
                int cardIdx = countToCardIdx.get(3L).get(0);
                if (cardIdx == 1) cardIdx = 14;
                int cardIdx2 = countToCardIdx.get(2L).get(0);
                if (cardIdx2 == 1) cardIdx2 = 14;
                double bonus = ((double) cardIdx) / 15.0 + ((double) cardIdx2) / (15.0 * 15.0);
                score = Math.max(score, 6d + bonus);

            } else {
                // 3 of a kind
                // single pair
                int tripleIdx = countToCardIdx.get(3L).get(0);
                if (tripleIdx == 1) tripleIdx = 14;
                int[] sortedNonPairs = countToCardIdx.get(1L).stream().mapToInt(e -> e == 1L ? 14 : e).sorted().toArray();
                double nonPairBoost = IntStream.range(0, sortedNonPairs.length).mapToDouble(i -> ((double) sortedNonPairs[sortedNonPairs.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
                double bonus = ((double) tripleIdx) / 15.0 + nonPairBoost / 15.0;
                score = Math.max(score, 3d + bonus);
            }
        } else if (largestN == 2) {
            // check 2 pair
            boolean twoPair = containsTwoPair(counts);
            if (twoPair) {
                // two pair
                List<Integer> twoPairs = countToCardIdx.get(2L);
                int cardIdx = twoPairs.get(0);
                if (cardIdx == 1) cardIdx = 14;
                int cardIdx2 = twoPairs.get(1);
                if (cardIdx2 == 1) cardIdx2 = 14;
                int cardIdx3 = countToCardIdx.get(1L).get(0);
                if (cardIdx3 == 1) cardIdx3 = 14;
                double bonus = ((double) Math.max(cardIdx, cardIdx2)) / 15.0 + ((double) Math.min(cardIdx, cardIdx2)) / (15.0 * 15.0) + ((double) cardIdx3) / (15.0 * 15.0 * 15.0);
                score = Math.max(score, 2d + bonus);
            } else {
                // single pair
                int pairIdx = countToCardIdx.get(2L).get(0);
                if (pairIdx == 1) pairIdx = 14;
                int[] sortedNonPairs = countToCardIdx.get(1L).stream().mapToInt(e -> e == 1 ? 14 : e).sorted().toArray();
                double nonPairBoost = IntStream.range(0, sortedNonPairs.length).mapToDouble(i -> ((double) sortedNonPairs[sortedNonPairs.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
                double bonus = ((double) pairIdx) / 15.0 + nonPairBoost / 15.0;
                score = Math.max(score, 1d + bonus);
            }
        } else {
            score = Math.max(score, highCardBoost);
        }

        return score;
    }

    public static boolean twoCardHandsAreEqual(Hand h1, Hand h2) {
        return (h1.cards[0].equals(h2.cards[0]) && h1.cards[1].equals(h2.cards[1])) || (h1.cards[0].equals(h2.cards[1]) && h1.cards[1].equals(h2.cards[0]));
    }

    public static double probabilityWinningHand(Hand hand, int numPlayers, int numSimulations) {
        Simulation simulation = new Simulation(numPlayers);
        int wins = 0;
        int relevantTotal = 0;
        for (int i = 0; i < numSimulations; i++) {
            simulation.dealHands();
            boolean foundHand = false;
            for (Hand h : simulation.hands) {
                if (twoCardHandsAreEqual(h, hand)) {
                    foundHand = true;
                    break;
                }
            }
            if (foundHand) {
                relevantTotal++;
                simulation.showFlop();
                simulation.showTurn();
                simulation.showRiver();
                Pair<Hand, Double> best = simulation.simulateHand();
                if (twoCardHandsAreEqual(hand, best.getFirst())) {
                    // close enough
                    wins++;
                }
            }
        }
        if (relevantTotal == 0) {
            return 0;
        } else {
            return ((double) wins) / relevantTotal;
        }
    }

    public static void main(String[] args) {
        Hand hand = new Hand();
        for (int i = 1; i <= 13; i++) {
            for (char c : new char[]{'C'}) {
                for (int j = 1; j <= 13; j++) {
                    for (char h : new char[]{'C', 'S', 'H', 'D'}) {
                        if(i!=j||c!=h) {
                            Card card1 = new Card();
                            Card card2 = new Card();
                            card1.num = i;
                            card2.num = j;
                            card1.suit = c;
                            card2.suit = h;
                            hand.cards = new Card[]{
                                    card1,
                                    card2
                            };

                            for (int numSimulations : Arrays.asList(500000)) {
                                double prob = probabilityWinningHand(hand, 4, numSimulations);
                                System.out.println("Prob " + hand.toString() + " (n=" + numSimulations + "): " + prob);
                            }
                        }
                    }
                }
            }
        }
    }
}
