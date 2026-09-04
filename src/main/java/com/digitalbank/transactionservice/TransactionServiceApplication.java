package com.digitalbank.transactionservice;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import com.digitalbank.transactionservice.risk.ConfiguredTransferRiskEvaluator;
import com.digitalbank.transactionservice.risk.TransferRiskEvaluator;
import com.digitalbank.transactionservice.risk.TransferRiskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(TransferRiskProperties.class)
@SecurityScheme(name = "bearer-jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }

    @Bean
    TransferRiskEvaluator transferRiskEvaluator(TransferRiskProperties properties) {
        return new ConfiguredTransferRiskEvaluator(properties);
    }
}
