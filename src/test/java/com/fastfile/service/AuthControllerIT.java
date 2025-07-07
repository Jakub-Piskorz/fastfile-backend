package com.fastfile.service;

import com.fastfile.config.GlobalVariables;
import com.fastfile.dto.UserDTO;
import com.fastfile.model.User;
import com.fastfile.model.UserLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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


    @Test
    public void userAuthIT() {
        UserDTO user = restTemplate.postForObject(
                "/auth/register",
                new User(env.ffUsername(), "test@test.com", "testFirstname", "testLastname", env.ffPassword()),
                UserDTO.class);
        assertThat(user).isNotNull();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isNotNull();
        assertThat(user.getFirstName()).isNotNull();
        assertThat(user.getLastName()).isNotNull();

        UserLogin userLogin = new UserLogin(env.ffUsername(), env.ffPassword());
        String jwtToken = restTemplate.postForObject("/auth/login", userLogin, String.class);
        assertThat(jwtToken.length()).isGreaterThan(0);


        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        UserDTO userDTO = restTemplate.exchange("/auth/user", HttpMethod.GET, requestEntity, UserDTO.class).getBody();
        assertThat(userDTO).isNotNull();
        assert userDTO != null;
        assertThat(userDTO.getId()).isEqualTo(user.getId());
        assertThat(userDTO.getEmail()).isEqualTo(user.getEmail());
        assertThat(userDTO.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(userDTO.getLastName()).isEqualTo(user.getLastName());
    }
}