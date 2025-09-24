package com.fastfile.dto;

import java.util.List;

public record PrivateFileLinkDTO(String filePath, List<String> emails) {
}
