package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class GafferEntity {
    @JsonProperty("group")
    private String group;

    @JsonProperty("vertex")
    private String vertex;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    public GafferEntity() {
    }

    public GafferEntity(String group, String vertex, Map<String, Object> properties) {
        this.group = group;
        this.vertex = vertex;
        this.properties = properties;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVertex() {
        return vertex;
    }

    public void setVertex(String vertex) {
        this.vertex = vertex;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "GafferEntity{" +
                "group='" + group + '\'' +
                ", vertex='" + vertex + '\'' +
                ", properties=" + properties +
                '}';
    }
}
