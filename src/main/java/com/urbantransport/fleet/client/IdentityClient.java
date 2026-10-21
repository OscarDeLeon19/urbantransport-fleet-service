package com.urbantransport.fleet.client;

public interface IdentityClient {

    IdentityUserResponse createDriverUser(IdentityCreateUserRequest request);
}
