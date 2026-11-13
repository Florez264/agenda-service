package com.subocol.taller.agenda.application.dto;

import com.subocol.taller.agenda.domain.model.*;
import java.time.Instant;
import java.util.UUID;

public record CitaDetalle(
    UUID id,
    UUID ordenId,
    UUID franjaId,
    String asesor,
    Instant inicio,
    Instant fin,
    TipoCita tipo,
    EstadoCita estado,
    String creadaPor,
    Instant fechaCreacion,
    String motivoCancelacion,
    String canceladaPor,
    Instant fechaCancelacion) {
  public static CitaDetalle desde(Cita c, Franja f) {
    return new CitaDetalle(
        c.getId(),
        c.getOrdenId(),
        c.getFranjaId(),
        f.asesor(),
        f.inicio(),
        f.fin(),
        c.getTipo(),
        c.getEstado(),
        c.getCreadaPor(),
        c.getFechaCreacion(),
        c.getMotivoCancelacion(),
        c.getCanceladaPor(),
        c.getFechaCancelacion());
  }
}
