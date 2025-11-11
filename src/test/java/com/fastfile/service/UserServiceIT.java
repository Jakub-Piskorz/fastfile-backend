package com.fastfile.service;

import com.fastfile.auth.JwtService;
import com.fastfile.dto.UserDTO;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserServiceIT {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserService userService;

    @Autowired
    AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    TestRestTemplate restTemplate;

    private static Long TEST_USER_ID;
    private static final String TEST_USERNAME = "qbek";
    private static final String TEST_PASSWORD = "secretPassword";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:latest").withReuse(true);

    @BeforeEach
    void setup() throws IOException {
        final User testUser = new User(
                TEST_USERNAME,
                "test@test.com",
                "TestFirstname",
                "TestLastname",
                TEST_PASSWORD
        );
        UserDTO userDTO = userService.register(testUser);
        TEST_USER_ID = testUser.getId();

        // SecurityContext for service tests
        String jwtToken = authService.authenticate(TEST_USERNAME, TEST_PASSWORD);
        Claims claims = jwtService.extractClaims(jwtToken);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDTO.id(), null, List.of());
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    public void connectionEstablished() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    @Transactional
    public void registerUser() {
        final User user = new User(
                "qbek2",
                "qbek@test.com",
                "Qbek",
                "Qbeczek",
                TEST_PASSWORD
        );
        final User registeredUser = userRepository.save(user);
        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getUsername()).isEqualTo(user.getUsername());
        assertThat(registeredUser.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    public void me() {
        User me = userService.getMe();
        assertThat(me).isNotNull();
        assertThat(me.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(me.getId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    public void findTestUser() {
        final Optional<User> result = userRepository.findByUsername(TEST_USERNAME);
        assertThat(result.orElseThrow().getLastName()).isEqualTo("TestLastname");
    }

    @Test
    @Transactional
    void userTypes() {
        User user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUserType()).isEqualTo("free");

        boolean result = userService.updateMyUserType("premium");
        assertThat(result).isTrue();
        user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUserType()).isEqualTo("premium");

        result = userService.updateMyUserType("incorrect-type");
        assertThat(result).isFalse();
        user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUserType()).isEqualTo("premium");
    }

    @Test
    @Transactional
    void usedStorage() {
        User me = userService.getMe();
        assertThat(me.getUserType()).isEqualTo("free");
        assertThat(me.getUsedStorage()).isEqualTo(0L);
        assertThat(userService.getMyUsedStorage()).isEqualTo(0L);
        assertThat(userService.getMyUserStorageLimit()).isEqualTo(userService.freeLimit);

        userService.updateMyUserType("premium");
        assertThat(userService.getMyUsedStorage()).isEqualTo(0L);
        me =  userService.getMe();
        assertThat(me.getUserType()).isEqualTo("premium");
        assertThat(userService.getMyUserStorageLimit()).isEqualTo(userService.premiumLimit);
    }
}