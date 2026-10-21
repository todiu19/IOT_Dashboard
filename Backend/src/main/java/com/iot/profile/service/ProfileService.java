package com.iot.profile.service;

import com.iot.domain.entity.User;
import com.iot.common.dto.response.ProfileResponse;
import com.iot.common.exception.ApiException;
import com.iot.domain.repository.UserRepository;
import com.iot.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ProfileService {

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(String username) {
        return toResponse(findUser(username));
    }

    private User findUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
    }

    public static ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getFullname(),
                user.getUsername(),
                user.getMsv(),
                user.getEmail(),
                user.getGithub(),
                user.getPostman(),
                user.getFigma(),
                user.getPdf()
        );
    }
}
