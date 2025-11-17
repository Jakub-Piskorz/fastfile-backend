package com.fastfile;

import com.fastfile.auth.JwtService;
import com.fastfile.repository.UserRepository;
import com.fastfile.service.AuthService;
import io.jsonwebtoken.Claims;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fastfile.service.FileSystemService.FILES_ROOT;

public class IntegrationTestSetup {

    public static final Long TEST_USER_ID = -1L;
    public static final Path TEST_USER_DIR = Paths.get(FILES_ROOT, TEST_USER_ID.toString());
    public static final String TEST_USERNAME = "testUser";
    public static final String TEST_PASSWORD = "secretPassword";

    public static void beforeTransactionConfig(JdbcTemplate jdbcTemplate, UserRepository userRepository, AuthService authService, JwtService jwtService) throws IOException {
        // Insert test user, if it doesn't exist.
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

        // Log in and setup SecurityContext
        String jwtToken = authService.authenticate("testUser", "secretPassword");
        Claims claims = jwtService.extractClaims(jwtToken);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, List.of());
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void afterEachConfig() throws IOException {
        // Clean user directory
        if (Files.exists(TEST_USER_DIR)) {
            FileUtils.cleanDirectory(TEST_USER_DIR.toFile());
        }
    }

    public static void afterAllConfig() throws IOException {
        // Remove user directory with everything inside
        if (Files.exists(TEST_USER_DIR)) {
            try (Stream<Path> filesStream = Files.walk(TEST_USER_DIR)) {
                filesStream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            boolean deleted = file.delete();
                            if (!deleted) {
                                throw new RuntimeException("Failed to delete file: " + file);
                            }
                        });
            }
        }
        SecurityContextHolder.clearContext();
    }
}
