package com.subocol.taller.agenda.application.port;

import java.util.UUID;

public interface OrdenesPort {
  record ResumenEstado(String estado, long version) {}

  record Cierre(UUID id, UUID ordenId, long version) {}

  ResumenEstado consultarResumen(UUID ordenId);

  java.util.List<Cierre> cierresPendientes();

  void confirmarCierre(UUID id);

  String consultarEstado(UUID ordenId);

  String consultarTelefono(UUID ordenId);
}
