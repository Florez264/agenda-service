package com.subocol.taller.agenda.application.dto;

import com.subocol.taller.agenda.domain.model.*;
import java.time.Instant;
import java.util.UUID;

public record NotificacionCita(
    UUID id,
    UUID citaId,
    UUID ordenId,
    EventoNotificacion evento,
    TipoCita tipoCita,
    String asesor,
    Instant inicio) {}
