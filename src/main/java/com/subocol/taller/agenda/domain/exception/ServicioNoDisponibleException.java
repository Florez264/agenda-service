package com.subocol.taller.agenda.domain.exception;

public class ServicioNoDisponibleException extends RuntimeException {
  public ServicioNoDisponibleException() {
    super("No fue posible verificar la orden; intente más tarde");
  }
}
