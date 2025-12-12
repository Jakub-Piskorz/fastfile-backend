package com.fastfile.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(JwtService jwtService, List<String> publicPaths) {
        this.jwtService = jwtService;
        this.publicPaths = publicPaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Extract the token from the Authorization header
        String token = request.getHeader("Authorization");


        if (token != null && token.startsWith("Bearer ")) {
            try {
                token = token.substring(7);  // Remove the "Bearer " prefix
                final Claims claims = jwtService.extractClaims(token);
                final String username = claims.getSubject();

                // If valid, set authentication
                if (jwtService.isTokenValid(token, username)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            Collections.emptyList());
                    authentication.setDetails(claims);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.warn("Invalid JWT token: " + token);
                }

            } catch (Exception e) {
                // Handle invalid token or other errors
                SecurityContextHolder.clearContext();
                logger.warn("JWT missing or malformed for request to " + request.getRequestURI());
                logger.warn(e.getMessage());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
        }
        filterChain.doFilter(request, response);
    }
}
