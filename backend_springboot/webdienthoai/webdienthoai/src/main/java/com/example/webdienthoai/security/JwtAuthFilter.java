package com.example.webdienthoai.security;

import com.example.webdienthoai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtUtil.getEmailFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        String role = userRepository.findByEmail(email)
            .map(u -> u.getRole() != null ? u.getRole() : jwtUtil.getRoleFromToken(token))
            .orElseGet(() -> jwtUtil.getRoleFromToken(token));
        String roleValue = role != null ? role.trim().toUpperCase(Locale.ROOT) : "CUSTOMER";
        var auth = new UsernamePasswordAuthenticationToken(
            new UserPrincipal(userId, email, roleValue.toLowerCase(Locale.ROOT)),
                null,
            List.of(new SimpleGrantedAuthority("ROLE_" + roleValue)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}
