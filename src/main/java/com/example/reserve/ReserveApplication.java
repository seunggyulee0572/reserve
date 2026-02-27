package com.example.reserve;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

import java.util.TimeZone;

@ConfigurationPropertiesScan
@SpringBootApplication( exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
} )
public class ReserveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReserveApplication.class, args);
    }

    @PostConstruct
    public void setUtcTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
