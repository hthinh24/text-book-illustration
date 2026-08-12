package vn.hungthinh.text_book_illustration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.hungthinh.text_book_illustration.dto.request.IdentityRequest;
import vn.hungthinh.text_book_illustration.dto.response.IdentityResponse;
import vn.hungthinh.text_book_illustration.entity.User;
import vn.hungthinh.text_book_illustration.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityService {

    private final UserRepository userRepository;

    @Transactional
    public IdentityResponse getOrCreate(IdentityRequest request) {
        log.info("[Service] Starting getOrCreate identity: email={}", request.email());
        return userRepository.findByEmail(request.email())
                .map(existing -> {
                    log.info("[Service] Found existing user: email={}, userId={}", existing.getEmail(), existing.getId());
                    return new IdentityResponse(existing.getId(), existing.getEmail(), existing.getName());
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(request.email());
                    user.setName(request.name());
                    User saved = userRepository.save(user);
                    log.info("[Service] Created new user: email={}, userId={}", saved.getEmail(), saved.getId());
                    return new IdentityResponse(saved.getId(), saved.getEmail(), saved.getName());
                });
    }
}
