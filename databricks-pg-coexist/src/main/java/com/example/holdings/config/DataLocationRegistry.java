package com.example.holdings.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties("app.holdings.routing")
public class DataLocationRegistry {

    public enum DataLocation { DATABRICKS, POSTGRES }

    private Map<String, DataLocation> datasets = new HashMap<>();

    public DataLocation locationOf(String dataset) {
        DataLocation loc = datasets.get(dataset);
        if (loc == null) {
            throw new IllegalArgumentException("Unknown dataset: " + dataset
                    + ". Configured datasets: " + datasets.keySet());
        }
        return loc;
    }
}
