package com.fastfile.service;

import com.fastfile.config.FilesConfig;
import com.fastfile.dto.UserDTO;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;

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
    public long freeLimit;
    @Value("${storage.limits.premium}")
    public long premiumLimit;

    public UserDTO register(User user) throws IOException {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user = userRepository.save(user);
        Files.createDirectories(Paths.get(FilesConfig.FILES_ROOT + "/" + user.getId()));
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

    public Path getMyUserPath(String directory) {
        if (directory == null) directory = "";

        // 🔒 Safety check against unsafe paths.
        Path path = Paths.get(directory);
        if (directory.contains("\u0000") || path.isAbsolute()) {
            throw new IllegalArgumentException("Unsafe path");
        }
        Path normalized = path.normalize();
        for (Path part : normalized) {
            if (part.toString().equals("..")) {
                throw new IllegalArgumentException("Unsafe path");
            }
        }

        Long userId = authService.getMyUserId();
        return Paths.get(FilesConfig.FILES_ROOT + userId + "/" + directory);
    }

    public Path getMyUserPath() {
        return getMyUserPath("");
    }
}
