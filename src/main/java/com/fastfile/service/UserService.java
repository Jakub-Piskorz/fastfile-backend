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

import static com.fastfile.service.FileService.FILES_ROOT;

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
        var myUserId = authService.getMyUserId();
        return userRepository.findById(Long.parseLong(myUserId)).orElse(null);
    }

    public long getMyUserStorageLimit() {
        User me = getMe();
        boolean isUserPremium = Objects.equals(me.getUserType(), "premium");

        return isUserPremium ? premiumLimit : freeLimit;
    }

    public boolean UpdateMyUserType(String newUserType) {
        if (!Pattern.matches("^(free|premium)$", newUserType)) {
            return false;
        }
        User me = getMe();
        me.setUserType(newUserType);
        userRepository.save(me);

        return true;
    }

    public long getMyUserStorage() {
        User me = getMe();

        if (me == null) {
            throw new RuntimeException("User not found");
        }
        return me.getUsedStorage();
    }

}
