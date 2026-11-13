package com.subocol.taller.agenda.infrastructure.web.response;

public record ApiResponse<T>(int status, T data) {}
