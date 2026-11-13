package com.subocol.taller.agenda.application.usecase;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.port.*;
import com.subocol.taller.agenda.domain.exception.ReglaNegocioException;
import com.subocol.taller.agenda.domain.model.*;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AgendaUseCase {
  private final AgendaPort agenda;
  private final OrdenesPort ordenes;
  private final Clock clock;

  public Franja crearFranja(String asesor, Instant inicio, int cupo) {
    return agenda.crearFranja(Franja.crear(asesor, inicio, cupo, clock.instant()));
  }

  public Pagina<FranjaDisponible> franjas(
      String asesor, Instant desde, Instant hasta, Paginacion pagina) {
    if (desde == null) desde = clock.instant();
    if (hasta == null) hasta = desde.plus(Duration.ofDays(7));
    if (!hasta.isAfter(desde) || Duration.between(desde, hasta).compareTo(Duration.ofDays(31)) > 0)
      throw new IllegalArgumentException("Rango de fechas máximo de 31 días");
    return agenda.listarFranjas(asesor, desde, hasta, pagina);
  }

  public CitaDetalle agendar(UUID ordenId, UUID franjaId, TipoCita tipo, String actor) {
    var resumen = ordenes.consultarResumen(ordenId);
    var estado = resumen.estado();
    if ("CERRADA".equals(estado))
      throw new ReglaNegocioException("No se pueden agendar citas para una orden cerrada");
    if (tipo == TipoCita.ENTREGA && !"REPARADA".equals(estado))
      throw new ReglaNegocioException("Una cita de entrega requiere una orden reparada");
    agenda.prepararOrden(ordenId);
    return agenda.reservar(
        Cita.reservar(ordenId, franjaId, tipo, actor, clock.instant()), resumen.version());
  }

  public CitaDetalle cancelar(UUID id, String motivo, String actor) {
    return agenda.cancelar(id, motivo, actor);
  }

  public CitaDetalle consultar(UUID id) {
    return agenda.consultar(id);
  }

  public Pagina<CitaDetalle> listar(
      UUID ordenId, String asesor, EstadoCita estado, Paginacion pagina) {
    return agenda.listar(ordenId, asesor, estado, pagina);
  }
}
