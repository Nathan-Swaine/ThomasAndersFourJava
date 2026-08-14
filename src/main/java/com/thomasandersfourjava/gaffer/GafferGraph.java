package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class GafferGraph {
    @JsonProperty("entities")
    private List<GafferEntity> entities;

    @JsonProperty("edges")
    private List<GafferEdge> edges;

    public GafferGraph() {
    }

    public GafferGraph(List<GafferEntity> entities, List<GafferEdge> edges) {
        this.entities = entities;
        this.edges = edges;
    }

    public List<GafferEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<GafferEntity> entities) {
        this.entities = entities;
    }

    public List<GafferEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<GafferEdge> edges) {
        this.edges = edges;
    }

    @Override
    public String toString() {
        return "GafferGraph{" +
                "entities=" + entities.size() +
                ", edges=" + edges.size() +
                '}';
    }
}
