package com.queueless.security;

import com.queueless.entity.User;
import com.queueless.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String providerId = oAuth2User.getName();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            log.error("OAuth2 provider {} did not return an email address", provider);
            throw new RuntimeException("Email not found from OAuth2 provider");
        }
        email = email.trim().toLowerCase();

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!provider.equals(user.getProvider())) {
                user.setProvider(provider);
                user.setProviderId(providerId);
                userRepository.save(user);
            }
        } else {
            User newUser = User.builder()
                    .email(email)
                    .name(name != null ? name : "Customer")
                    .role(User.Role.CUSTOMER)
                    .provider(provider)
                    .providerId(providerId)
                    .active(true)
                    .build();
            userRepository.save(newUser);
        }

        return oAuth2User;
    }
}
