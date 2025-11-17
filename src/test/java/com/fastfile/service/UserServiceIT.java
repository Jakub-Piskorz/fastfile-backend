package com.fastfile.service;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Optional;

import static com.fastfile.IntegrationTestSetup.*;
import static org.assertj.core.api.Assertions.assertThat;

// Integration test for {@link UserService}
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    UserService userService;

    // CONFIG
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;

    @BeforeTransaction
    void beforeTransactionConfig() throws IOException {
        IntegrationTestSetup.beforeTransactionConfig(jdbcTemplate, userRepository, authService, jwtService);
    }

    @AfterEach
    void afterEachConfig() throws IOException {
        IntegrationTestSetup.afterEachConfig();
    }

    @AfterAll
    static void afterAllConfig() throws IOException {
        IntegrationTestSetup.afterAllConfig();
    }
    // END OF CONFIG

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
    @Transactional
    public void me() {
        User me = userService.getMe();
        assertThat(me).isNotNull();
        assertThat(me.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(me.getId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    public void findTestUser() {
        final Optional<User> result = userRepository.findByUsername(TEST_USERNAME);
        assertThat(result.orElseThrow().getLastName()).isEqualTo("testLastname");
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