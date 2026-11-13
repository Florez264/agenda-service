package com.subocol.taller.agenda.application.port;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.domain.model.EstadoNotificacion;
import java.time.Instant;
import java.util.*;

public interface BuzonNotificacionesPort {
  Optional<EnvioReclamado> reclamar(Instant ahora, int segundosReserva);

  void completar(EnvioReclamado envio, ResultadoEnvio resultado);

  void fallar(EnvioReclamado envio, String codigo, Instant siguienteIntento, boolean definitivo);

  Pagina<NotificacionDetalle> listar(UUID citaId, EstadoNotificacion estado, Paginacion pagina);

  NotificacionDetalle reintentar(UUID id, Instant ahora);
}
