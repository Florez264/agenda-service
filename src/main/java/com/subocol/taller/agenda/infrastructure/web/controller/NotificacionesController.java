package com.subocol.taller.agenda.infrastructure.web.controller;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.port.BuzonNotificacionesPort;
import com.subocol.taller.agenda.domain.model.EstadoNotificacion;
import com.subocol.taller.agenda.infrastructure.web.response.*;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionesController {
  private final BuzonNotificacionesPort buzon;
  private final Clock clock;

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<Pagina<NotificacionDetalle>>> listar(
      @RequestParam(required = false) UUID citaId,
      @RequestParam(required = false) EstadoNotificacion estado,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponses.ok(buzon.listar(citaId, estado, new Paginacion(page, size)));
  }

  public record Reintentada(boolean update, UUID id, EstadoNotificacion estado, String message) {}

  @PatchMapping("/{id}/reintentar")
  public ResponseEntity<ApiResponse<Reintentada>> reintentar(@PathVariable UUID id) {
    var n = buzon.reintentar(id, clock.instant());
    return ApiResponses.ok(
        new Reintentada(true, n.id(), n.estado(), "Notificación pendiente de reintento"));
  }
}
