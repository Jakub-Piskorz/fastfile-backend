package com.fastfile.config;

import com.fastfile.auth.JwtAuthenticationFilter;
import com.fastfile.auth.JwtService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    private static final String[] PUBLIC_PATHS_SECURITY = {"/", "/auth/login", "/auth/register", "/swagger-ui/**", "/v3/api-docs/**"};
    private static final List<String> PUBLIC_PATHS_FILTER = List.of("/auth/login", "/auth/register", "/swagger-ui", "/v3/api-docs");


    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Spring boot generates random password if I don't define it. Not necessary but annoying if absent.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((a) -> a
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(PUBLIC_PATHS_SECURITY).permitAll() // not authenticated endpoints
                        .anyRequest().authenticated() // authenticated endpoints
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, PUBLIC_PATHS_FILTER), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}