package com.fastfile.model;

public record FileMetadata(String name, long size, long lastModified, String type, String path, boolean hasFiles) {}