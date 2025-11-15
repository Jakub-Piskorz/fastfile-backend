package com.fastfile.controller;

import com.fastfile.config.GlobalVariables;
import com.fastfile.dto.UserDTO;
import com.fastfile.model.User;
import com.fastfile.dto.UserLoginDTO;
import com.fastfile.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static com.fastfile.service.FileSystemService.FILES_ROOT;
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

    @Autowired
    private UserRepository userRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest").withReuse(true);


    // Dependencies for @BeforeEach
    private static final Long TEST_USER_ID = -1L;
    private static final Path TEST_USER_DIR = Paths.get(FILES_ROOT, TEST_USER_ID.toString());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeTransaction
    void setup() throws IOException {

        // Add test user if it doesn't exist.
        if (userRepository.findById(TEST_USER_ID).isEmpty()) {
            Resource resource = new ClassPathResource("schema.sql");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                String sql = reader.lines().collect(Collectors.joining("\n"));
                jdbcTemplate.execute(sql);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load schema.sql", e);
            }
        }

        // Create user folder
        if (Files.notExists(TEST_USER_DIR)) {
            Files.createDirectories(TEST_USER_DIR);
        }
    }

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