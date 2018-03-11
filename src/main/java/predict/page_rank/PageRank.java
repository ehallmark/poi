package main.java.predict.page_rank;

import com.google.common.util.concurrent.AtomicDouble;
import main.java.graphical_modeling.model.learning.algorithms.LearningAlgorithm;
import main.java.graphical_modeling.model.nodes.Node;
import main.java.graphical_modeling.util.ObjectIO;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/21/17.
 */
public class PageRank extends RankGraph<String> {
    protected double currentScore;

    public PageRank(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, double damping) {
        super(labelToCitationLabelsMap, null,damping);
    }

    @Override
    public LearningAlgorithm getLearningAlgorithm() {
        return new Algorithm();
    }

    protected double rankValue(Node node) {
        return (1d-damping)/nodes.size() + damping * node.getNeighbors().stream().mapToDouble(neighbor->{
            Float rank = rankTable.get(neighbor.getLabel());
            if(rank==null) return 0d;
            if(neighbor.getNeighbors().size()>0) {
                return (double)rank/neighbor.getNeighbors().size();
            } else return 0d;
        }).sum();
    }

    @Override
    protected void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> labels) {
        rankTable=Collections.synchronizedMap(new HashMap<>(labelToCitationLabelsMap.size()));
        System.out.println("Adding initial nodes...");
        AtomicInteger cnt = new AtomicInteger(0);
        labelToCitationLabelsMap.entrySet().stream().forEach(e->{
            String label = e.getKey();
            Collection<String> citations = e.getValue();
            Node labelNode = graph.addBinaryNode(label);
            citations.forEach(citation->{
                if(citation==null) return;
                Node citationNode = graph.addBinaryNode(citation);
                graph.connectNodes(labelNode,citationNode);
            });
            if(cnt.getAndIncrement()%100000==99999) {
                System.out.println("Added: "+cnt.get()+" nodes");
            }
        });
        nodes=graph.getAllNodesList();
        this.nodes.forEach(node->rankTable.put(node.getLabel(),1f/nodes.size()));
        System.out.println("Done.");
    }


    public class Algorithm implements LearningAlgorithm {
        @Override
        public boolean runAlgorithm() {
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicDouble delta = new AtomicDouble(0d);
            nodes.stream().forEach(node -> {
                double rank = rankValue(node);
                double prior = rankTable.get(node.getLabel());
                rankTable.put(node.getLabel(), (float) rank);
                delta.getAndAdd(Math.abs(rank-prior));
                if(cnt.getAndIncrement()%10000==0) System.out.println("Updated scores of "+cnt.get()+" patents so far. Score="+(delta.get()/cnt.get()));
            });
            currentScore = delta.get()/nodes.size();
            return currentScore  < 0.0000001/nodes.size();
        }

        @Override
        public double computeCurrentScore() {
            return currentScore;
        }
    }

}
