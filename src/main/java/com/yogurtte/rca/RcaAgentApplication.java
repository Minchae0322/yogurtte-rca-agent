package com.yogurtte.rca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RcaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RcaAgentApplication.class, args);
    }
}
