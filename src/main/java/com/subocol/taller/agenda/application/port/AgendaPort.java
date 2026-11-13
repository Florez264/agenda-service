package com.subocol.taller.agenda.application.port;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.domain.model.*;
import java.time.Instant;
import java.util.UUID;

public interface AgendaPort {
  Franja crearFranja(Franja franja);

  Pagina<FranjaDisponible> listarFranjas(
      String asesor, Instant desde, Instant hasta, Paginacion pagina);

  CitaDetalle reservar(Cita cita);

  CitaDetalle cancelar(UUID id, String motivo, String actor);

  CitaDetalle consultar(UUID id);

  Pagina<CitaDetalle> listar(UUID ordenId, String asesor, EstadoCita estado, Paginacion pagina);
}
