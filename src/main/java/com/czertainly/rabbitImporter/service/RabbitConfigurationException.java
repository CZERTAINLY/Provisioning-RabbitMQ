package com.czertainly.rabbitImporter.service;

public class RabbitConfigurationException extends RuntimeException {

    public RabbitConfigurationException(String message) {
        super(message);
    }

    public RabbitConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

}
