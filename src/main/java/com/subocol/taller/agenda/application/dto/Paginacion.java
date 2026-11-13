package com.subocol.taller.agenda.application.dto;

public record Paginacion(int page, int size) {
  public Paginacion {
    if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Paginación inválida, tamaño entre 1 y 50");
  }
}
