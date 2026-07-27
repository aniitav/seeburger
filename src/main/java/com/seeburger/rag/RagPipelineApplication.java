package com.seeburger.rag;

import com.seeburger.rag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class RagPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagPipelineApplication.class, args);
    }
}
