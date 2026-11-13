package com.subocol.taller.agenda.domain.model;

import com.subocol.taller.agenda.domain.exception.ReglaNegocioException;
import java.time.*;
import java.util.*;

public record Franja(UUID id, String asesor, Instant inicio, Instant fin, int cupo) {
  public Franja {
    Objects.requireNonNull(id);
    Objects.requireNonNull(inicio);
    Objects.requireNonNull(fin);
    if (asesor == null || asesor.isBlank() || asesor.length() > 100)
      throw new IllegalArgumentException("Asesor inválido");
    if (cupo < 1 || cupo > 10)
      throw new IllegalArgumentException("El cupo debe estar entre 1 y 10");
    if (inicio.getNano() != 0
        || inicio.getEpochSecond() % 1800 != 0
        || !fin.equals(inicio.plusSeconds(1800)))
      throw new IllegalArgumentException("Franjas fijas de 30 minutos, alineadas a :00 o :30 UTC");
  }

  public static Franja crear(String asesor, Instant inicio, int cupo, Instant ahora) {
    if (inicio == null || !inicio.isAfter(ahora))
      throw new IllegalArgumentException("La franja debe ser futura");
    return new Franja(UUID.randomUUID(), asesor, inicio, inicio.plusSeconds(1800), cupo);
  }

  public void validarReserva(long ocupados, Instant ahora) {
    if (!inicio.isAfter(ahora)) throw new ReglaNegocioException("La franja ya inició");
    if (ocupados >= cupo) throw new ReglaNegocioException("No hay cupos disponibles en la franja");
  }
}
