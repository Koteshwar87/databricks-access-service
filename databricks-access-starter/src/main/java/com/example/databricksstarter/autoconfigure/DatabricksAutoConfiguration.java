package com.example.databricksstarter.autoconfigure;

import com.example.databricksstarter.health.DatabricksHealthIndicator;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

@Slf4j
@AutoConfiguration(after = { DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class })
@ConditionalOnProperty(prefix = "app.databricks", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DatabricksProperties.class)
public class DatabricksAutoConfiguration {

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "databricksDataSource")
    @ConfigurationProperties("app.databricks.hikari")
    public HikariDataSource databricksDataSource(DatabricksProperties props) {
        String maskedUrl = props.getJdbcUrl()
                .replaceAll("OAuth2Secret=[^;]*;", "OAuth2Secret=***;");
        log.info("Configuring Databricks DataSource (OAuth M2M, url={})", maskedUrl);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(props.getJdbcUrl());
        ds.setDriverClassName("com.databricks.client.jdbc.Driver");
        ds.setPoolName("databricks-hikari-pool");
        // Production-tuned defaults. Any of these — and any other HikariCP property —
        // is overridable via app.databricks.hikari.* (bound by @ConfigurationProperties above).
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(60_000);
        ds.setIdleTimeout(120_000);
        ds.setMaxLifetime(3_300_000);   // just under the ~60-min OAuth M2M token TTL
        ds.setAutoCommit(false);
        ds.setConnectionTestQuery("SELECT 1");
        ds.setValidationTimeout(10_000);
        ds.setKeepaliveTime(180_000);
        ds.setLeakDetectionThreshold(60_000);
        return ds;
    }

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "databricksJdbcTemplate")
    public JdbcTemplate databricksJdbcTemplate(
            @Qualifier("databricksDataSource") DataSource databricksDataSource,
            DatabricksProperties props) {
        JdbcTemplate jdbc = new JdbcTemplate(databricksDataSource);
        jdbc.setQueryTimeout(props.getQueryTimeoutSeconds());
        return jdbc;
    }

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "databricksJdbcClient")
    public JdbcClient databricksJdbcClient(
            @Qualifier("databricksJdbcTemplate") JdbcTemplate databricksJdbcTemplate) {
        return JdbcClient.create(databricksJdbcTemplate);
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "databricks")
    public HealthIndicator databricks(
            @Qualifier("databricksJdbcTemplate") JdbcTemplate databricksJdbcTemplate) {
        return new DatabricksHealthIndicator(databricksJdbcTemplate);
    }
}
