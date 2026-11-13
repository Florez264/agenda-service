package com.subocol.taller.agenda.application.exception;

import lombok.Getter;

@Getter
public final class EnvioNotificacionException extends RuntimeException {
  private final boolean reintentable;

  public EnvioNotificacionException(String codigo, boolean reintentable) {
    super(codigo);
    this.reintentable = reintentable;
  }
}
