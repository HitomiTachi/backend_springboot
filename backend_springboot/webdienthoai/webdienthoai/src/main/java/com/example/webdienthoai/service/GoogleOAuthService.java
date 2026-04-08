package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.security.JwtUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private static final String PROVIDER_GOOGLE = "GOOGLE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, OAuthState> stateStore = new ConcurrentHashMap<>();

    @Value("${app.oauth.google.enabled:false}")
    private boolean googleOAuthEnabled;

    @Value("${app.oauth.google.client-id:}")
    private String clientId;

    @Value("${app.oauth.google.client-secret:}")
    private String clientSecret;

    @Value("${app.oauth.google.redirect-uri:http://localhost:8080/api/auth/google/callback}")
    private String backendRedirectUri;

    @Value("${app.oauth.google.auth-uri:https://accounts.google.com/o/oauth2/v2/auth}")
    private String authUri;

    @Value("${app.oauth.google.token-uri:https://oauth2.googleapis.com/token}")
    private String tokenUri;

    @Value("${app.oauth.google.userinfo-uri:https://openidconnect.googleapis.com/v1/userinfo}")
    private String userInfoUri;

    @Value("${app.oauth.google.scope:openid email profile}")
    private String scope;

    @Value("${app.oauth.google.state-ttl-seconds:300}")
    private long stateTtlSeconds;

    @Value("${app.oauth.google.frontend-success-url:http://localhost:3000/oauth/google/callback}")
    private String frontendSuccessUrl;

    @Value("${app.oauth.google.frontend-failure-url:http://localhost:3000/login}")
    private String frontendFailureUrl;

    @Value("${app.oauth.google.allowed-redirect-uris:http://localhost:3000/oauth/google/callback,http://127.0.0.1:3000/oauth/google/callback}")
    private String allowedRedirectUris;

    public URI buildGoogleAuthorizationRedirect(String requestedRedirectUri) {
        ensureGoogleOAuthConfigured();
        String state = UUID.randomUUID().toString();
        String frontendRedirect = resolveFrontendRedirectUri(requestedRedirectUri);
        stateStore.put(state, new OAuthState(frontendRedirect, Instant.now().plusSeconds(Math.max(30, stateTtlSeconds))));
        cleanupExpiredStates();

        String query = "client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(backendRedirectUri)
                + "&response_type=code"
                + "&scope=" + enc(scope)
                + "&state=" + enc(state)
                + "&access_type=offline"
                + "&prompt=select_account";
        return URI.create(authUri + "?" + query);
    }

    public URI buildFailureRedirect(String state, String message) {
        OAuthState oauthState = consumeState(state);
        String base = oauthState != null ? oauthState.frontendRedirectUri : frontendFailureUrl;
        return appendQuery(base, "error", message);
    }

    public URI handleGoogleCallback(String code, String state) {
        ensureGoogleOAuthConfigured();
        OAuthState oauthState = consumeState(state);
        if (oauthState == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state không hợp lệ hoặc đã hết hạn");
        }
        if (code == null || code.isBlank()) {
            return appendQuery(oauthState.frontendRedirectUri, "error", "Thiếu authorization code từ Google");
        }

        GoogleTokenResponse tokenResponse = exchangeCodeForToken(code.trim());
        GoogleUserInfo userInfo = fetchUserInfo(tokenResponse.accessToken);
        if (userInfo.email == null || userInfo.email.isBlank()) {
            return appendQuery(oauthState.frontendRedirectUri, "error", "Google không trả về email hợp lệ");
        }
        String email = userInfo.email.trim().toLowerCase();
        User user = upsertGoogleUser(userInfo, email);
        String jwt = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole());
        return appendQuery(oauthState.frontendRedirectUri, "token", jwt);
    }

    private void ensureGoogleOAuthConfigured() {
        if (!googleOAuthEnabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google OAuth đang tắt");
        }
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(backendRedirectUri)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Thiếu cấu hình Google OAuth");
        }
    }

    private GoogleTokenResponse exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", backendRedirectUri);
        form.add("grant_type", "authorization_code");
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUri, new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Google token exchange failed: status={}, body={}", response.getStatusCode(), response.getBody());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể trao đổi token với Google");
            }
            GoogleTokenResponse tokenResponse = objectMapper.readValue(response.getBody(), GoogleTokenResponse.class);
            if (tokenResponse.accessToken == null || tokenResponse.accessToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google không trả access_token");
            }
            return tokenResponse;
        } catch (IOException | RestClientException e) {
            log.warn("Google OAuth token request error", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Lỗi kết nối Google OAuth");
        }
    }

    private GoogleUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    userInfoUri,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Google userinfo failed: status={}, body={}", response.getStatusCode(), response.getBody());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không lấy được thông tin người dùng từ Google");
            }
            return objectMapper.readValue(response.getBody(), GoogleUserInfo.class);
        } catch (IOException | RestClientException e) {
            log.warn("Google OAuth userinfo request error", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Lỗi khi lấy thông tin người dùng Google");
        }
    }

    private User upsertGoogleUser(GoogleUserInfo userInfo, String email) {
        User existingByProvider = null;
        if (!isBlank(userInfo.sub)) {
            existingByProvider = userRepository.findByAuthProviderAndProviderId(PROVIDER_GOOGLE, userInfo.sub.trim()).orElse(null);
        }
        if (existingByProvider != null) {
            syncGoogleProfile(existingByProvider, userInfo);
            if (isBlank(existingByProvider.getRole())) {
                existingByProvider.setRole("customer");
            }
            return userRepository.save(existingByProvider);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .name(resolveDisplayName(userInfo, email))
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role("customer")
                    .passwordChangedAt(Instant.now())
                    .authProvider(PROVIDER_GOOGLE)
                    .providerId(blankToNull(userInfo.sub))
                    .emailVerifiedAt(Boolean.TRUE.equals(userInfo.emailVerified) ? Instant.now() : null)
                    .avatarUrl(blankToNull(userInfo.picture))
                    .build();
            return userRepository.save(user);
        }

        if ("GOOGLE".equalsIgnoreCase(user.getAuthProvider())
                && user.getProviderId() != null
                && !user.getProviderId().equals(blankToNull(userInfo.sub))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã liên kết với tài khoản Google khác");
        }
        user.setAuthProvider(PROVIDER_GOOGLE);
        user.setProviderId(blankToNull(userInfo.sub));
        syncGoogleProfile(user, userInfo);
        if (isBlank(user.getRole())) {
            user.setRole("customer");
        }
        return userRepository.save(user);
    }

    private void syncGoogleProfile(User user, GoogleUserInfo userInfo) {
        if (isBlank(user.getName()) && !isBlank(userInfo.name)) {
            user.setName(userInfo.name.trim());
        }
        if (isBlank(user.getAvatarUrl()) && !isBlank(userInfo.picture)) {
            user.setAvatarUrl(userInfo.picture.trim());
        }
        if (Boolean.TRUE.equals(userInfo.emailVerified) && user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
    }

    private OAuthState consumeState(String state) {
        if (isBlank(state)) return null;
        OAuthState oauthState = stateStore.remove(state.trim());
        if (oauthState == null) return null;
        if (Instant.now().isAfter(oauthState.expiresAt)) return null;
        return oauthState;
    }

    private void cleanupExpiredStates() {
        Instant now = Instant.now();
        stateStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt));
    }

    private String resolveFrontendRedirectUri(String requestedRedirectUri) {
        if (isBlank(requestedRedirectUri)) return frontendSuccessUrl;
        String candidate = requestedRedirectUri.trim();
        String[] allowed = allowedRedirectUris.split(",");
        for (String item : allowed) {
            if (candidate.equals(item.trim())) return candidate;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirectUri không hợp lệ");
    }

    private URI appendQuery(String baseUrl, String key, String value) {
        String sep = baseUrl.contains("?") ? "&" : "?";
        return URI.create(baseUrl + sep + enc(key) + "=" + enc(value == null ? "" : value));
    }

    private String resolveDisplayName(GoogleUserInfo userInfo, String email) {
        if (!isBlank(userInfo.name)) return userInfo.name.trim();
        int at = email.indexOf("@");
        return at > 0 ? email.substring(0, at) : "Người dùng Google";
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record OAuthState(String frontendRedirectUri, Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GoogleTokenResponse {
        @JsonProperty("access_token")
        public String accessToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GoogleUserInfo {
        public String sub;
        public String email;
        @JsonProperty("email_verified")
        public Boolean emailVerified;
        public String name;
        public String picture;
    }
}
