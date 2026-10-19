package com.urbantransport.fleet.client;

public record IdentityCreateUserRequest(
        String email,
        String password,
        String fullName) {
}
