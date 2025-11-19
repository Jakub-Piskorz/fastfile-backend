package com.fastfile.controller;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.config.GlobalVariables;
import com.fastfile.dto.UserDTO;
import com.fastfile.dto.UserTypeDTO;
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
import org.springframework.http.*;
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

    private HttpEntity<Void> httpRequestEntity;

    @BeforeTransaction
    void beforeTransactionConfig() throws IOException {
        IntegrationTestSetup.beforeTransactionConfig(jdbcTemplate, userRepository, authService, jwtService);

        // Login and set http request entity
        String jwtToken = authService.authenticate("testUser", "secretPassword");
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtToken);
        httpRequestEntity = new HttpEntity<>(headers);
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

    @Transactional
    @Test
    public void registerIT() {
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

        HttpEntity<Void> customRequestEntity = new HttpEntity<>(headers);

        UserDTO userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, customRequestEntity, UserDTO.class).getBody();
        assertThat(userDTO).isNotNull();
        assert userDTO != null;
        assertThat(userDTO.id()).isEqualTo(user.id());
        assertThat(userDTO.email()).isEqualTo(user.email());
        assertThat(userDTO.firstName()).isEqualTo(user.firstName());
        assertThat(userDTO.lastName()).isEqualTo(user.lastName());
    }

    @Transactional
    @Test
    public void loginIT() {
        UserDTO userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, httpRequestEntity, UserDTO.class).getBody();
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
        ResponseEntity<UserDTO> response = restTemplate.exchange("/auth/user", HttpMethod.GET, httpRequestEntity, UserDTO.class);
        assertNotNull(response.getBody());
        UserDTO userDTO = response.getBody();
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
        Boolean deleted = restTemplate.exchange("/auth/delete-me", HttpMethod.DELETE, httpRequestEntity, Boolean.class).getBody();
        assertThat(deleted).isTrue();

        // Try to log in deleted user
        String jwtToken = restTemplate.postForObject("/auth/login", testUserLoginDTO, String.class);
        assertThat(jwtToken).isNull();
    }

    @Transactional
    @Test
    public void setUserTypeIT() {
        // Check if user is of type "free"
        UserDTO userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, httpRequestEntity, UserDTO.class).getBody();
        assertNotNull(userDTO);
        assertThat(userDTO.userType()).isEqualTo("free");

        // Change user type to "premium"
        UserTypeDTO userTypeDTO = new UserTypeDTO("premium");
        HttpEntity<UserTypeDTO> customRequestEntity = new HttpEntity<>(userTypeDTO, httpRequestEntity.getHeaders());
        ResponseEntity<String> response = restTemplate.exchange("/auth/user/set-user-type", HttpMethod.POST, customRequestEntity, String.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Check if user is of type "premium"
        userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, httpRequestEntity, UserDTO.class).getBody();
        assertNotNull(userDTO);
        assertThat(userDTO.userType()).isEqualTo("premium");

        // Change back user type to "free"
        userTypeDTO = new UserTypeDTO("free");
        customRequestEntity = new HttpEntity<>(userTypeDTO, httpRequestEntity.getHeaders());
        response = restTemplate.exchange("/auth/user/set-user-type", HttpMethod.POST, customRequestEntity, String.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Check if user is of type "free"
        userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, httpRequestEntity, UserDTO.class).getBody();
        assertNotNull(userDTO);
        assertThat(userDTO.userType()).isEqualTo("free");
    }
}