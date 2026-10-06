package com.writingassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WritingAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(WritingAssistantApplication.class, args);
    }
}
