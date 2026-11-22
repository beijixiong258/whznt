package com.whznt.config;

import com.whznt.service.AccountService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfig {

    @Bean
    @DependsOnDatabaseInitialization
    InitializingBean bootstrapAdmin(AccountService accounts,
                                    @Value("${whznt.bootstrap-admin.phone:}") String phone,
                                    @Value("${whznt.bootstrap-admin.password:}") String password) {
        return () -> {
            if (phone.isBlank() && password.isEmpty()) {
                return;
            }
            if (phone.isBlank() || password.isEmpty()) {
                throw new IllegalStateException("Configure both the bootstrap administrator phone and password.");
            }
            accounts.provisionAdmin(phone, password);
        };
    }
}
