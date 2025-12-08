package com.czertainly.rabbitImporter;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import com.czertainly.rabbitImporter.service.ImportDefinitionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RabbitMQProperties.class})
public class RabbitImporterApplication {

    private static final Logger log = LoggerFactory.getLogger(RabbitImporterApplication.class);

    private final ImportDefinitionsService service;

    public RabbitImporterApplication(ImportDefinitionsService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        SpringApplication.run(RabbitImporterApplication.class, args);
    }
}
