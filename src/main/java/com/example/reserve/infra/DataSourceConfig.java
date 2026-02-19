package com.example.reserve.infra;

import com.example.reserve.model.ApplicationProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    private final ApplicationProperties props;

    public DataSourceConfig(ApplicationProperties props) {
        this.props = props;
    }

    @Bean
    public DataSource dataSource() {
        var ds = props.datasource();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(ds.url());
        cfg.setUsername(ds.username());
        cfg.setPassword(ds.password());
        cfg.setDriverClassName(ds.driverClassName());

        cfg.setMaximumPoolSize(20);
        cfg.setMinimumIdle(5);
        cfg.setConnectionTimeout(3000);
        cfg.setValidationTimeout(1000);
        cfg.setIdleTimeout(300000);
        cfg.setMaxLifetime(1800000);

        return new HikariDataSource(cfg);
    }

    // todo: 나중에 yml 로
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(false);

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.example.reserve.entity");
        emf.setPersistenceUnitName("mysql");
        emf.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> jpaProps = new HashMap<>();
        jpaProps.put("hibernate.hbm2ddl.auto", "none");          // validate / update / create / none
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        jpaProps.put("hibernate.format_sql", "true");
        jpaProps.put("hibernate.jdbc.time_zone", "Asia/Seoul");

        jpaProps.put("hibernate.jdbc.batch_size", 100);
        jpaProps.put("hibernate.order_inserts", true);
        jpaProps.put("hibernate.generate_statistics", true);
        jpaProps.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        jpaProps.put("hibernate.implicit_naming_strategy",
                "org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl");
        emf.setJpaPropertyMap(jpaProps);
        return emf;
    }

    @Bean
    public JpaTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean emf) {
        assert emf.getObject() != null;
        return new JpaTransactionManager(emf.getObject());
    }
}