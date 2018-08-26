package main.java.poker;

import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
            throw new RuntimeException("No more cards!");
        }
        Card card = deck.get(deckIdx.get());
        deckIdx.getAndIncrement();
        return card;
    }

    public void dealHands(Card[] givenHand, Card[] flop, Card turn, Card river) {
        shuffleDeck();
        if(givenHand!=null) {
            for(Card card : givenHand) {
                if(!deck.remove(card)) {
                    throw new RuntimeException("Could not find card: "+card.toString());
                }
                deck.add(0, card);
            }
        }
        // add all additional stuff to the end
        {
            if (flop != null) {
                for (Card card : flop) {
                    if (!deck.remove(card)) {
                        throw new RuntimeException("Could not find card: " + card.toString());
                    }
                    deck.add(card);
                }
            }
            if(turn != null) {
                if(!deck.remove(turn)) {
                    throw new RuntimeException("Could not find card: "+turn.toString());
                }
                deck.add(turn);
            }
            if(river != null) {
                if(!deck.remove(river)) {
                    throw new RuntimeException("Could not find card: "+river.toString());
                }
                deck.add(river);
            }
        }
        // now insert into place
        if(flop != null) {
            int indexFlop = numPlayers * 2 + 1;
            for (Card card : flop) {
                if(!deck.remove(card)) {
                    throw new RuntimeException("Could not find card: "+card.toString());
                }
                deck.add(indexFlop, card);
            }
        }
        if(turn != null) {
            int indexTurn = numPlayers * 2 + 1 + 3 + 1;
            if(!deck.remove(turn)) {
                throw new RuntimeException("Could not find card: "+turn.toString());
            }
            deck.add(indexTurn, turn);
        }
        if(river != null) {
            int indexRiver = numPlayers * 2 + 1 + 3 + 1 + 1 + 1;
            if(!deck.remove(river)) {
                throw new RuntimeException("Could not find card: "+river.toString());
            }
            deck.add(indexRiver, river);
        }
        hands = new Hand[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            Hand hand = new Hand();
            hand.cards = new Card[]{nextCard(), nextCard()};
            hands[i] = hand;
        }
        if(deck.size()!=52) {
            throw new RuntimeException("Invalid deck size: "+deck.size());
        }
    }

    public void showFlop() {
        nextCard(); // burn
        flop = new Card[]{nextCard(), nextCard(), nextCard()};
    }

    public void showTurn() {
        nextCard(); // burn
        turn = nextCard();
    }


    public void showRiver() {
        nextCard(); // burn
        river = nextCard();
    }


    // returns the best hand
    public Pair<Hand, Double> simulateHand() {
        Hand bestHand = null;
        double bestScore = 0;
        boolean currentTie = false;
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
                currentTie = Math.abs(score-bestScore) < 0.000001;
                bestScore = score;
                bestHand = new Hand();
                bestHand.cards = hand.cards;
            }
        }
        if (currentTie) {
            System.out.println("TIE!");
            return null;
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


    public int largestNOfAKind(Map<Integer,Long> counts) {
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
        int[] sortedCardNumbers = Stream.of(cards).mapToInt(e -> e.num == 1 ? 14 : e.num).sorted().toArray();
        double highCardBoost = IntStream.range(0, sortedCardNumbers.length).mapToDouble(i -> ((double) sortedCardNumbers[sortedCardNumbers.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
        double highCardBoostStraight = 0d;
        double score = 0d;
        if (straight) {
            // check whether ace is low
            if (counts.containsKey(1) && counts.containsKey(2)) {
                int[] sortedCardNumbersAceLow = Stream.of(cards).mapToInt(e -> e.num).sorted().toArray();
                highCardBoostStraight = IntStream.range(0, sortedCardNumbersAceLow.length).mapToDouble(i -> ((double) sortedCardNumbersAceLow[sortedCardNumbersAceLow.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
            } else {
                highCardBoostStraight = highCardBoost;
            }
        }
        if (flush && straight) {
            // straight flush!
            // check whether ace is high card
            score = Math.max(score, 8d + highCardBoostStraight);
            // highest score
            return score;
        } else if (flush) {
            score = Math.max(score, 5d + highCardBoost);
        } else if (straight) {
            score = Math.max(score, 4d + highCardBoostStraight);
        }
        int largestN = largestNOfAKind(counts);
        if (largestN == 4) {
            // 4 of a kind
            int cardIdx = countToCardIdx.get(4L).get(0);
            if (cardIdx == 1) cardIdx = 14;
            int cardIdx2 = countToCardIdx.get(1L).get(0);
            if (cardIdx2 == 1) cardIdx2 = 14;
            double bonus = ((double) cardIdx) / 15.0 + ((double) cardIdx2) / (15.0 * 15.0);
            score = Math.max(score, 7d + bonus);
            // second highest
            return score;
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
                return score;
            } else {
                // 3 of a kind
                // single pair
                int tripleIdx = countToCardIdx.get(3L).get(0);
                if (tripleIdx == 1) tripleIdx = 14;
                int[] sortedNonPairs = countToCardIdx.get(1L).stream().mapToInt(e -> e == 1L ? 14 : e).sorted().toArray();
                double nonPairBoost = IntStream.range(0, sortedNonPairs.length).mapToDouble(i -> ((double) sortedNonPairs[sortedNonPairs.length - 1 - i]) / Math.pow(15.0, i + 1)).sum();
                double bonus = ((double) tripleIdx) / 15.0 + nonPairBoost / 15.0;
                score = Math.max(score, 3d + bonus);
                return score;
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
    public static boolean threeCardHandsAreEqual(Hand h1, Hand h2) {
        return (h1.cards[0].equals(h2.cards[0])&&h1.cards[1].equals(h2.cards[1])&&h1.cards[2].equals(h2.cards[2])) ||
                (h1.cards[0].equals(h2.cards[1])&&h1.cards[1].equals(h2.cards[0])&&h1.cards[2].equals(h2.cards[2])) ||
                (h1.cards[0].equals(h2.cards[2])&&h1.cards[1].equals(h2.cards[0])&&h1.cards[2].equals(h2.cards[1])) ||
                (h1.cards[0].equals(h2.cards[0])&&h1.cards[1].equals(h2.cards[2])&&h1.cards[2].equals(h2.cards[1])) ||
                (h1.cards[0].equals(h2.cards[1])&&h1.cards[1].equals(h2.cards[2])&&h1.cards[2].equals(h2.cards[0])) ||
                (h1.cards[0].equals(h2.cards[2])&&h1.cards[1].equals(h2.cards[1])&&h1.cards[2].equals(h2.cards[0]));
    }

    public static double probabilityWinningHand(Hand hand, Card[] flop, Card turn, Card river, int numPlayers, int numSimulations) {
        AtomicLong wins = new AtomicLong(0L);
        AtomicLong relevantTotal = new AtomicLong(0L);
        int numThreads = 12;
        ExecutorService service = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numSimulations; i++) {
            service.execute(()-> {
                Simulation simulation = new Simulation(numPlayers);
                simulation.dealHands(hand.cards, flop, turn, river);
                simulation.showFlop();
                simulation.showTurn();
                simulation.showRiver();
                Pair<Hand, Double> best = simulation.simulateHand();
                boolean foundShow = true;
                if(best!=null) {
                    Card[] flopActual = simulation.flop;
                    Card turnActual = simulation.turn;
                    Card riverActual = simulation.river;
                    if (flop != null) {
                        foundShow = false;
                        Hand flopHand = new Hand();
                        flopHand.cards = flop;
                        Hand flopActualHand = new Hand();
                        flopActualHand.cards = flopActual;
                        if (threeCardHandsAreEqual(flopHand, flopActualHand)) {
                            foundShow = true;
                        }
                    }
                    if (foundShow && turn != null) {
                        foundShow = false;
                        if (turn.equals(turnActual)) {
                            foundShow = true;
                        }
                    }
                    if (foundShow && river != null) {
                        foundShow = false;
                        if (river.equals(riverActual)) {
                            foundShow = true;
                        }
                    }
                }
                if (foundShow) {
                    relevantTotal.getAndIncrement();
                    if (best!= null && twoCardHandsAreEqual(hand, best.getFirst())) {
                        // close enough
                        wins.getAndIncrement();
                    }
                } else {
                   System.out.println("NOT FOUND");
                }
            });
        }
        service.shutdown();
        try {
            service.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println("Relevant total: "+relevantTotal);
        if (relevantTotal.get()==0) {
            return 0;
        } else {
            return ((double) wins.get()) / relevantTotal.get();
        }
    }

    public static void main(String[] args) {
        Hand hand = new Hand();
        Card flop1 = new Card();
        Card flop2 = new Card();
        Card flop3 = new Card();
        flop1.num=2;
        flop2.num=3;
        flop3.num=2;
        flop1.suit='H';
        flop2.suit='S';
        flop3.suit='S';
        Card[] flop = new Card[]{
                flop1,
                flop2,
                flop3,
        };
        Card turn = new Card();
        turn.num = 8;
        turn.suit = 'S';
        Card river = new Card();
        river.num = 12;
        river.suit = 'S';
        turn = null;
        river = null;
        //flop = null;
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

                            for (int numSimulations : Arrays.asList(1000)) {
                                double prob = probabilityWinningHand(hand, flop, turn, null,5, numSimulations);
                                System.out.println("Prob " + hand.toString() + " (n=" + numSimulations + "): " + prob);
                            }
                        }
                    }
                }
            }
        }
    }
}
