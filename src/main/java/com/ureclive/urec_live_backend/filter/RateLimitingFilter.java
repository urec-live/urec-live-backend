package com.ureclive.urec_live_backend.filter;

import com.ureclive.urec_live_backend.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RateLimitingFilter implements Filter {

    private final RateLimitingService rateLimitingService;

    public RateLimitingFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Only apply rate limiting to the login endpoint
        if (request.getRequestURI().equals("/api/auth/login") && request.getMethod().equals("POST")) {
            String ip = request.getRemoteAddr();
            Bucket tokenBucket = rateLimitingService.resolveBucket(ip);
            ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(1);

            if (!probe.isConsumed()) {
                response.setStatus(429); // Too Many Requests
                response.getWriter().write("Too many login attempts. Please try again later.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
