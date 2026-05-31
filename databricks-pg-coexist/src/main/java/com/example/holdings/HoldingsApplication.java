package com.example.holdings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HoldingsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoldingsApplication.class, args);
    }
}
