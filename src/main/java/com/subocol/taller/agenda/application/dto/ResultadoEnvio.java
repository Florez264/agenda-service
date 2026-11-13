package com.subocol.taller.agenda.application.dto;

import com.subocol.taller.agenda.domain.model.EstadoNotificacion;

public record ResultadoEnvio(EstadoNotificacion estado, String mensajeProveedorId) {
  public ResultadoEnvio {
    if (estado != EstadoNotificacion.SIMULADA && estado != EstadoNotificacion.ACEPTADA)
      throw new IllegalArgumentException("Resultado inválido");
  }
}
