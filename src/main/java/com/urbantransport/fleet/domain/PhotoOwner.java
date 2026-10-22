package com.urbantransport.fleet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PhotoOwner {
    BUS("buses"),
    DRIVER("drivers");

    private final String prefix;
}
