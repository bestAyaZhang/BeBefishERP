package com.bebefish.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BebefishErpApplication {
    public static void main(String[] args) {
        SpringApplication.run(BebefishErpApplication.class, args);
    }
}
