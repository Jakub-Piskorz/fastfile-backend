package com.fastfile.service;

import com.fastfile.dto.UserDTO;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.fastfile.service.FileSystemService.FILES_ROOT;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Value("${storage.limits.free}")
    private long freeLimit;
    @Value("${storage.limits.premium}")
    private long premiumLimit;

    public UserDTO register(User user) throws IOException {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user = userRepository.save(user);
        Files.createDirectories(Paths.get(FILES_ROOT + "/" + user.getId()));
        return new UserDTO(user);
    }

    public User getMe() {
        Long myUserId = authService.getMyUserId();
        return userRepository.findById(myUserId).orElse(null);
    }

    public long getUserStorageLimit(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        boolean isUserPremium = Objects.equals(user.getUserType(), "premium");

        return isUserPremium ? premiumLimit : freeLimit;
    }
    public long getMyUserStorageLimit() {
        return getUserStorageLimit(getMe().getId());
    }

    public boolean updateUserType(Long userId, String newUserType) {
        if (!Pattern.matches("^(free|premium)$", newUserType)) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        user.setUserType(newUserType);
        userRepository.save(user);

        return true;
    }
    public boolean updateMyUserType(String newUserType) {
        return updateUserType(getMe().getId(), newUserType);
    }

    public long getUsedStorage(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user.getUsedStorage();
    }

    public long getMyUsedStorage() {
        return getUsedStorage(getMe().getId());
    }

}
