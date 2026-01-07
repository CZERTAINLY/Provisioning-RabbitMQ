package com.czertainly.rabbitImporter;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RabbitMQProperties.class})
public class RabbitMqBootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMqBootstrapApplication.class, args);
    }
}
