package com.fastfile.dto;

import com.fastfile.model.User;

public record UserDTO(Long id, String username, String email, String firstName, String lastName, String userType,
                      long usedStorage) {
    public UserDTO(User user) {
        this(user.getId(), user.getUsername(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getUserType(), user.getUsedStorage());
    }
}