package com.fastfile.controller;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.config.FilesConfig;
import com.fastfile.config.GlobalVariables;
import com.fastfile.dto.UserDTO;
import com.fastfile.dto.UserLoginDTO;
import com.fastfile.dto.UserTypeDTO;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import com.fastfile.service.AuthService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import static com.fastfile.IntegrationTestSetup.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Integration test for {@link AuthController}
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerIT {

    @LocalServerPort
    private int port;

    // Bez @Autowired - instancjonujemy w setUpClient()
    private RestClient restClient;

    @Autowired
    private GlobalVariables env;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    // CONFIG
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeTransaction
    void beforeTransactionConfig() throws IOException {
        IntegrationTestSetup.beforeTransactionConfig(jdbcTemplate, userRepository, authService, jwtService);

        // Login and retrieve JWT token
        token = authService.authenticate("testUser", "secretPassword");
    }

    @BeforeEach
    void setUpClient() {
        // Tworzymy lekki klient skierowany na wylosowany port serwera
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void afterEachConfig() throws IOException {
        IntegrationTestSetup.afterEachConfig();
    }

    @AfterAll
    static void afterAllConfig() throws IOException {
        Path testUserDir = Paths.get(FilesConfig.FILES_ROOT, TEST_USER_ID.toString());
        IntegrationTestSetup.afterAllConfig(testUserDir);
    }

    // Helper to inject Authorization header
    private Consumer<HttpHeaders> authHeader(String jwtToken) {
        return headers -> headers.setBearerAuth(jwtToken);
    }
    // END OF CONFIG

    @Transactional
    @Test
    public void registerIT() {
        User newUser = new User("testUser2", "test2@test.com", "testFirstname", "testLastname", env.ffPassword());
        UserDTO user = restClient.post()
                .uri("/auth/register")
                .body(newUser)
                .retrieve()
                .body(UserDTO.class);

        assertThat(user).isNotNull();
        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isNotNull();
        assertThat(user.firstName()).isNotNull();
        assertThat(user.lastName()).isNotNull();

        UserLoginDTO userLoginDTO = new UserLoginDTO("testUser2", env.ffPassword());
        String jwtToken = restClient.post()
                .uri("/auth/login")
                .body(userLoginDTO)
                .retrieve()
                .body(String.class);

        assertThat(jwtToken).isNotNull();
        assertThat(jwtToken.length()).isGreaterThan(0);

        UserDTO userDTO = restClient.get()
                .uri("/auth/user")
                .headers(authHeader(jwtToken))
                .retrieve()
                .body(UserDTO.class);

        assertThat(userDTO).isNotNull();
        assertThat(userDTO.id()).isEqualTo(user.id());
        assertThat(userDTO.email()).isEqualTo(user.email());
        assertThat(userDTO.firstName()).isEqualTo(user.firstName());
        assertThat(userDTO.lastName()).isEqualTo(user.lastName());
    }

    @Transactional
    @Test
    public void loginIT() {
        UserDTO userDTO = restClient.get()
                .uri("/auth/user")
                .headers(authHeader(token))
                .retrieve()
                .body(UserDTO.class);

        assertThat(userDTO).isNotNull();
        assertThat(userDTO.email()).isEqualTo("example@example.com");
        assertThat(userDTO.firstName()).isEqualTo("testFirstname");
        assertThat(userDTO.lastName()).isEqualTo("testLastname");
    }

    @Transactional
    @Test
    public void loginWrongPasswordIT() {
        UserLoginDTO wrongUserLoginDTO = new UserLoginDTO("testUser", "wrongPassword");

        HttpClientErrorException.Forbidden ex = assertThrows(
                HttpClientErrorException.Forbidden.class,
                () -> restClient.post()
                        .uri("/auth/login")
                        .body(wrongUserLoginDTO)
                        .retrieve()
                        .toBodilessEntity()
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Transactional
    @Test
    public void getCurrentUserIT() {
        UserDTO userDTO = restClient.get()
                .uri("/auth/user")
                .headers(authHeader(token))
                .retrieve()
                .body(UserDTO.class);

        assertNotNull(userDTO);
        assertThat(userDTO.username()).isEqualTo("testUser");
        assertThat(userDTO.id()).isEqualTo(TEST_USER_ID);
        assertThat(userDTO.userType()).isEqualTo("free");
    }

    @Transactional
    @Test
    public void deleteMeIT() {
        UserLoginDTO testUserLoginDTO = new UserLoginDTO("testUser", "secretPassword");

        // Delete user
        Boolean deleted = restClient.delete()
                .uri("/auth/delete-me")
                .headers(authHeader(token))
                .retrieve()
                .body(Boolean.class);

        assertThat(deleted).isTrue();

        // Try to log in deleted user
        HttpClientErrorException.Forbidden ex = assertThrows(
                HttpClientErrorException.Forbidden.class,
                () -> restClient.post()
                        .uri("/auth/login")
                        .body(testUserLoginDTO)
                        .retrieve()
                        .toBodilessEntity()
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Transactional
    @Test
    public void setUserTypeIT() {
        // Check if user is of type "free"
        UserDTO userDTO = restClient.get()
                .uri("/auth/user")
                .headers(authHeader(token))
                .retrieve()
                .body(UserDTO.class);

        assertNotNull(userDTO);
        assertThat(userDTO.userType()).isEqualTo("free");

        // Change user type to "premium"
        UserTypeDTO userTypeDTO = new UserTypeDTO("premium");
        restClient.post()
                .uri("/auth/user/set-user-type")
                .headers(authHeader(token))
                .body(userTypeDTO)
                .retrieve()
                .toBodilessEntity();

        // Check if user is of type "premium"
        userDTO = restClient.get()
                .uri("/auth/user")
                .headers(authHeader(token))
                .retrieve()
                .body(UserDTO.class);

        assertNotNull(userDTO);
        assertThat(userDTO.userType()).isEqualTo("premium");

        // Change back user type to "free"
        userTypeDTO = new UserTypeDTO("free");
        restClient.post()
                .uri("/auth/user/set-user-type")
                .headers(authHeader(token))
                .body(userTypeDTO)
                .retrieve()
                .toBodilessEntity();

        // Check if user is of type "free"
        userDTO = restClient.get()
                .uri("/auth/user")
                .headers(authHeader(token))
                .retrieve()
                .body(UserDTO.class);

        assertNotNull(userDTO);
        assertThat(userDTO.userType()).isEqualTo("free");
    }
}