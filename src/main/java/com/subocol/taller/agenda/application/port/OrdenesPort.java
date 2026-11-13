package com.subocol.taller.agenda.application.port;

import java.util.UUID;

public interface OrdenesPort {
  String consultarEstado(UUID ordenId);

  String consultarTelefono(UUID ordenId);
}
