package com.queueless.security;

import com.queueless.dto.Dtos.AuthResponse;
import com.queueless.entity.User;
import com.queueless.repository.UserRepository;
import com.queueless.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${app.oauth-success-redirect:http://localhost:5173/oauth2/callback}")
    private String oauthSuccessRedirect;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, @Lazy AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found after OAuth login"));

        AuthResponse authResponse = authService.oauthLogin(user, clientIp(request), request.getHeader("User-Agent"));

        String targetUrl = oauthSuccessRedirect
                + "?token=" + encode(authResponse.getAccessToken())
                + "&refreshToken=" + encode(authResponse.getRefreshToken());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String clientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf == null || xf.isBlank()) return request.getRemoteAddr();
        return xf.split(",")[0].trim();
    }
}
