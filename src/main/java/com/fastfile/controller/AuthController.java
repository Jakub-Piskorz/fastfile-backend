package com.fastfile.controller;

import com.fastfile.dto.UserDTO;
import com.fastfile.dto.UserTypeDTO;
import com.fastfile.dto.UserLoginDTO;

import com.fastfile.model.User;
import com.fastfile.service.AuthService;
import com.fastfile.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@RequestBody User user) throws IOException {
        return new ResponseEntity<>(userService.register(user), HttpStatus.OK);
    }

    @PostMapping("/login")
    public String login(@RequestBody UserLoginDTO user) {
        return authService.authenticate(user.login(), user.password());
    }
    @GetMapping("/user")
    public UserDTO getCurrentUser() {
        var user = userService.getMe();
        return new UserDTO(user);
    }

    @PostMapping("/user/set-user-type")
    public ResponseEntity<String> setUserType(@RequestBody UserTypeDTO userType) {
        boolean succeeded = userService.UpdateMyUserType(userType.userType());
        if (succeeded) {
            return new ResponseEntity<>("Successfully updated user to: " + userType.userType(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Couldn't update user to: " + userType.userType(), HttpStatus.BAD_REQUEST);
        }
    }
}
