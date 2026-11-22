package com.whznt.config;

import com.whznt.entity.Account.Role;
import com.whznt.mapper.AccountMapper;
import com.whznt.service.AccountService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurity(HttpSecurity http, AccountMapper accounts, AccountService users,
                                      PasswordEncoder encoder) throws Exception {
        http.securityMatcher("/api/admin/**")
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                        .anyRequest().hasRole(Role.ADMIN.name()));
        return sessionSecurity(http, accounts, users, encoder, Role.ADMIN, "/api/admin/auth");
    }

    @Bean
    SecurityFilterChain workspaceSecurity(HttpSecurity http, AccountMapper accounts, AccountService users,
                                          PasswordEncoder encoder) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .anyRequest().hasRole(Role.USER.name()));
        return sessionSecurity(http, accounts, users, encoder, Role.USER, "/api/auth");
    }

    private SecurityFilterChain sessionSecurity(HttpSecurity http, AccountMapper accounts, AccountService users,
                                                PasswordEncoder encoder, Role role, String authPath) throws Exception {
        var provider = new DaoAuthenticationProvider(phone -> {
            var user = users.loadUserByUsername(phone);
            if (user.role() != role) {
                throw new UsernameNotFoundException("手机号或密码有误。");
            }
            return user;
        });
        provider.setPasswordEncoder(encoder);
        return http.authenticationManager(new ProviderManager(provider))
                .addFilterBefore(new AccountSessionFilter(accounts), AuthorizationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(login -> login
                        .loginProcessingUrl(authPath + "/login")
                        .usernameParameter("phone")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) ->
                                reply(response, 401, "手机号或密码有误。")))
                .logout(logout -> logout
                        .logoutUrl(authPath + "/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                reply(response, 401, "请登录后继续。"))
                        .accessDeniedHandler((request, response, exception) ->
                                reply(response, 403, exception instanceof CsrfException
                                        ? "请求已失效，请刷新页面后重试。" : "当前账号没有访问权限。")))
                .build();
    }

    private static void reply(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
