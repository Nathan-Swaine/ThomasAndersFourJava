package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class GafferEdge {
    @JsonProperty("group")
    private String group;

    @JsonProperty("source")
    private String source;

    @JsonProperty("destination")
    private String destination;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    public GafferEdge() {
    }

    public GafferEdge(String group, String source, String destination, Map<String, Object> properties) {
        this.group = group;
        this.source = source;
        this.destination = destination;
        this.properties = properties;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "GafferEdge{" +
                "group='" + group + '\'' +
                ", source='" + source + '\'' +
                ", destination='" + destination + '\'' +
                ", properties=" + properties +
                '}';
    }
}
