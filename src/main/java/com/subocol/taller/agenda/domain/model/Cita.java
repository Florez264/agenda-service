package com.subocol.taller.agenda.domain.model;

import com.subocol.taller.agenda.domain.exception.ReglaNegocioException;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Cita {
  private final UUID id;
  private final UUID ordenId;
  private final UUID franjaId;
  private final TipoCita tipo;
  private final EstadoCita estado;
  private final String creadaPor;
  private final Instant fechaCreacion;
  private final String motivoCancelacion;
  private final String canceladaPor;
  private final Instant fechaCancelacion;

  public static Cita reservar(
      UUID ordenId, UUID franjaId, TipoCita tipo, String actor, Instant ahora) {
    Objects.requireNonNull(ordenId);
    Objects.requireNonNull(franjaId);
    Objects.requireNonNull(tipo);
    Objects.requireNonNull(ahora);
    if (actor == null || actor.isBlank() || actor.length() > 100)
      throw new IllegalArgumentException("Actor inválido");
    return new Cita(
        UUID.randomUUID(),
        ordenId,
        franjaId,
        tipo,
        EstadoCita.RESERVADA,
        actor,
        ahora,
        null,
        null,
        null);
  }

  public Cita cancelar(String motivo, String actor, Instant ahora) {
    if (estado != EstadoCita.RESERVADA)
      throw new ReglaNegocioException("La cita ya está cancelada");
    if (motivo == null || motivo.isBlank() || motivo.strip().length() > 500)
      throw new IllegalArgumentException("Motivo requerido, máximo 500 caracteres");
    if (actor == null || actor.isBlank() || actor.length() > 100)
      throw new IllegalArgumentException("Actor inválido");
    return new Cita(
        id,
        ordenId,
        franjaId,
        tipo,
        EstadoCita.CANCELADA,
        creadaPor,
        fechaCreacion,
        motivo.strip(),
        actor,
        ahora);
  }

  public static Cita reconstituir(
      UUID id,
      UUID ordenId,
      UUID franjaId,
      TipoCita tipo,
      EstadoCita estado,
      String actor,
      Instant fecha,
      String motivo,
      String cancela,
      Instant fechaCancela) {
    return new Cita(
        id, ordenId, franjaId, tipo, estado, actor, fecha, motivo, cancela, fechaCancela);
  }
}
