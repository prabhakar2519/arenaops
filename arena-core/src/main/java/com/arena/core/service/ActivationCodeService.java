package com.arena.core.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class ActivationCodeService {

  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final SecureRandom secureRandom = new SecureRandom();

  public String generateCode() {
    return "ARENA-" + randomChunk(5) + "-" + randomChunk(5);
  }

  public String hashCode(String code) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(normalize(code).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public String normalize(String code) {
    return code == null ? "" : code.trim().toUpperCase();
  }

  private String randomChunk(int length) {
    StringBuilder value = new StringBuilder();
    for (int i = 0; i < length; i++) {
      value.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
    }
    return value.toString();
  }
}
