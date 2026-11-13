package com.subocol.taller.agenda.application.dto;

import com.subocol.taller.agenda.domain.model.*;
import java.time.Instant;
import java.util.UUID;

public record NotificacionDetalle(
    UUID id,
    UUID citaId,
    EventoNotificacion evento,
    EstadoNotificacion estado,
    int intentos,
    Instant creada,
    Instant siguienteIntento,
    String codigoError,
    String mensajeProveedorId) {}
