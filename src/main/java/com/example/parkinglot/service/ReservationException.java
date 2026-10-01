package com.example.parkinglot.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ReservationException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ReservationException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

}
