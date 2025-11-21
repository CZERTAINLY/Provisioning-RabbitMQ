package com.czertainly.rabbitImporter;

import com.czertainly.rabbitImporter.service.RabbitImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
public class RabbitImporterApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RabbitImporterApplication.class);

    private final RabbitImportService service;

    public RabbitImporterApplication(RabbitImportService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        SpringApplication.run(RabbitImporterApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            service.importDefinitions();
            log.info("RabbitMQ import completed successfully.");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
            log.error("Import failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
