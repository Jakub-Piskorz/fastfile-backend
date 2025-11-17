package com.fastfile.controller;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.config.GlobalVariables;
import com.fastfile.dto.UserDTO;
import com.fastfile.model.User;
import com.fastfile.dto.UserLoginDTO;
import com.fastfile.repository.UserRepository;
import com.fastfile.service.AuthService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static com.fastfile.IntegrationTestSetup.TEST_USER_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Integration test for {@link AuthController}
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerIT {

    @Autowired
    TestRestTemplate restTemplate;

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

    HttpEntity<Void> loginAndGetEntity() {
        UserLoginDTO testUserLoginDTO = new UserLoginDTO("testUser", "secretPassword");
        String jwtToken = restTemplate.postForObject("/auth/login", testUserLoginDTO, String.class);
        assertThat(jwtToken).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtToken);
        return new HttpEntity<>(headers);
    }


    @Transactional
    @Test
    public void userAuthIT() {
        UserDTO user = restTemplate.postForObject(
                "/auth/register",
                new User(env.ffUsername(), "test@test.com", "testFirstname", "testLastname", env.ffPassword()),
                UserDTO.class);
        assertThat(user).isNotNull();
        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isNotNull();
        assertThat(user.firstName()).isNotNull();
        assertThat(user.lastName()).isNotNull();

        UserLoginDTO userLoginDTO = new UserLoginDTO(env.ffUsername(), env.ffPassword());
        String jwtToken = restTemplate.postForObject("/auth/login", userLoginDTO, String.class);
        assertThat(jwtToken.length()).isGreaterThan(0);


        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        UserDTO userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, requestEntity, UserDTO.class).getBody();
        assertThat(userDTO).isNotNull();
        assert userDTO != null;
        assertThat(userDTO.id()).isEqualTo(user.id());
        assertThat(userDTO.email()).isEqualTo(user.email());
        assertThat(userDTO.firstName()).isEqualTo(user.firstName());
        assertThat(userDTO.lastName()).isEqualTo(user.lastName());
    }

    @Transactional
    @Test
    public void testUserLoginIT() {
        var requestEntity = loginAndGetEntity();

        UserDTO userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, requestEntity, UserDTO.class).getBody();
        assertThat(userDTO).isNotNull();
        assert userDTO != null;

        assertThat(userDTO.email()).isEqualTo("example@example.com");
        assertThat(userDTO.firstName()).isEqualTo("testFirstname");
        assertThat(userDTO.lastName()).isEqualTo("testLastname");
    }

    @Transactional
    @Test
    public void loginWrongPasswordIT() {
        UserLoginDTO wrongUserLoginDTO = new UserLoginDTO("testUser", "wrongPassword");
        String jwtToken = restTemplate.postForObject("/auth/login", wrongUserLoginDTO, String.class);
        assertThat(jwtToken).isNull();
    }

    @Transactional
    @Test
    public void getCurrentUserIT() {
        var requestEntity = loginAndGetEntity();

        ResponseEntity<UserDTO> response = restTemplate.exchange("/auth/user", HttpMethod.GET, requestEntity, UserDTO.class);
        assertNotNull(response.getBody());
        UserDTO userDTO = response.getBody();
        assertNotNull(userDTO);
        assertThat(userDTO.username()).isEqualTo("testUser");
        assertThat(userDTO.id()).isEqualTo(TEST_USER_ID);
        assertThat(userDTO.userType()).isEqualTo("free");
    }

    @Transactional
    @Test
    public void deleteUserIT() {
        var requestEntity = loginAndGetEntity();
        UserLoginDTO testUserLoginDTO = new UserLoginDTO("testUser", "secretPassword");

        // Delete user
        Boolean deleted = restTemplate.exchange("/auth/delete-me", HttpMethod.DELETE, requestEntity, Boolean.class).getBody();
        assertThat(deleted).isTrue();

        // Try to log in deleted user
        String jwtToken = restTemplate.postForObject("/auth/login", testUserLoginDTO, String.class);
        assertThat(jwtToken).isNull();
    }

    @Transactional
    @Test
    public void changeUserTypeIt() {}
}