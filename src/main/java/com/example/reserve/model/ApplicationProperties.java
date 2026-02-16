package com.example.reserve.model;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "application")
public record ApplicationProperties(Datasource datasource) {

    public record Datasource(
            String driverClassName,
            String url,
            String username,
            String password
    ) {}
}