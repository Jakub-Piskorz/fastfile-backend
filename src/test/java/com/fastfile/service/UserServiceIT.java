package com.fastfile.service;

import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class UserServiceIT {

    @Autowired
    UserRepository userRepository;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:latest");

    @BeforeEach
    void setup() {
        final User testUser = new User(
                "test",
                "test@test.com",
                "TestFirstname",
                "TestLastname",
                "secretPassword"
        );
        userRepository.save(testUser);
    }

    @Test
    public void connectionEstablished() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    public void registerUser() {
        final User user = new User(
                "qbek",
                "qbek@test.com",
                "Qbek",
                "Qbeczek",
                "secretPassword"
        );
        final User registeredUser = userRepository.save(user);
        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getUsername()).isEqualTo(user.getUsername());
        assertThat(registeredUser.getPassword()).isEqualTo(user.getPassword());
    }
//
    @Test
    @Sql(scripts = "/schema.sql")
    public void findTestUser() {
        final Optional<User> result = userRepository.findByUsername("test");
        assertThat(result.orElseThrow().getLastName()).isEqualTo("TestLastname");
    }
}