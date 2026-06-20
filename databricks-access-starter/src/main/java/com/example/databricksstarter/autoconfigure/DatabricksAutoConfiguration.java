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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.databricks", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DatabricksProperties.class)
public class DatabricksAutoConfiguration {

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "databricksDataSource")
    public DataSource databricksDataSource(DatabricksProperties props) {
        String maskedUrl = props.getJdbcUrl()
                .replaceAll("PWD=[^;]*;", "PWD=***;")
                .replaceAll("OAuth2Secret=[^;]*;", "OAuth2Secret=***;");
        log.info("Configuring Databricks DataSource (auth={}, url={})",
                props.isOAuthMode() ? "OAuth M2M" : "PAT", maskedUrl);

        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("databricks-hikari-pool");
        ds.setJdbcUrl(props.getJdbcUrl());
        DatabricksProperties.HikariSettings h = props.getHikari();
        ds.setMaximumPoolSize(h.getMaximumPoolSize());
        ds.setMinimumIdle(h.getMinimumIdle());
        ds.setConnectionTimeout(h.getConnectionTimeout());
        ds.setIdleTimeout(h.getIdleTimeout());
        ds.setMaxLifetime(h.getMaxLifetime());
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

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "databricks")
    public HealthIndicator databricks(
            @Qualifier("databricksJdbcTemplate") JdbcTemplate databricksJdbcTemplate) {
        return new DatabricksHealthIndicator(databricksJdbcTemplate);
    }
}
