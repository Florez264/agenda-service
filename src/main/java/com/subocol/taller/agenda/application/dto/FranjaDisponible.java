package com.subocol.taller.agenda.application.dto;

import java.time.Instant;
import java.util.UUID;

public record FranjaDisponible(
    UUID id,
    String asesor,
    Instant inicio,
    Instant fin,
    int cupo,
    long reservadas,
    long disponibles) {}
