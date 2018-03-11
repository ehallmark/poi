package main.java.predict.page_rank;

import main.java.graphical_modeling.model.graphs.BayesianNet;
import main.java.graphical_modeling.model.graphs.Graph;
import main.java.graphical_modeling.model.learning.algorithms.LearningAlgorithm;
import main.java.graphical_modeling.model.nodes.Node;
import lombok.Getter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 4/21/17.
 */
public abstract class RankGraph<K> {
    protected Graph graph;
    protected List<Node> nodes;
    protected double damping;
    @Getter
    protected Map<K,Float> rankTable;

    protected RankGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels, double damping, Graph graph) {
        System.out.println("Initializing RankGraph of type: "+this.getClass().getName());
        if(damping<0||damping>1) throw new RuntimeException("Illegal damping constant");
        this.graph=graph;
        this.damping=damping;
        this.initGraph(labelToCitationLabelsMap,importantLabels);
        System.out.println("Finished "+this.getClass().getName());
    }
    // default
    protected RankGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels, double damping) {
        this(labelToCitationLabelsMap,importantLabels,damping,new BayesianNet());
    }

    protected abstract void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels);

    protected abstract LearningAlgorithm getLearningAlgorithm();

    public void solve(int numEpochs) {
        graph.applyLearningAlgorithm(getLearningAlgorithm(),numEpochs);
    }

}
