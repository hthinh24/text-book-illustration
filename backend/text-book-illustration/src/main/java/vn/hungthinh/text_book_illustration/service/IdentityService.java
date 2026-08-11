package vn.hungthinh.text_book_illustration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.hungthinh.text_book_illustration.dto.request.IdentityRequest;
import vn.hungthinh.text_book_illustration.dto.response.IdentityResponse;
import vn.hungthinh.text_book_illustration.entity.User;
import vn.hungthinh.text_book_illustration.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;

    @Transactional
    public IdentityResponse getOrCreate(IdentityRequest request) {
        return userRepository.findByEmail(request.email())
                .map(existing -> new IdentityResponse(existing.getId(), existing.getName()))
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(request.email());
                    user.setName(request.name());
                    User saved = userRepository.save(user);
                    return new IdentityResponse(saved.getId(), saved.getName());
                });
    }
}
