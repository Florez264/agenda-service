package com.subocol.taller.agenda.infrastructure.web.controller;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.usecase.AgendaUseCase;
import com.subocol.taller.agenda.domain.model.*;
import com.subocol.taller.agenda.infrastructure.web.response.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgendaController {
  private final AgendaUseCase agenda;

  public record CrearFranjaRequest(@NotNull Instant inicio, @Min(1) @Max(10) int cupo) {}

  public record AgendarRequest(
      @NotNull UUID ordenId, @NotNull UUID franjaId, @NotNull TipoCita tipo) {}

  public record CancelarRequest(@NotBlank @Size(max = 500) String motivo) {}

  public record FranjaCreada(
      UUID id, String asesor, Instant inicio, Instant fin, int cupo, String message) {}

  public record CitaCreada(
      UUID id, UUID ordenId, UUID franjaId, EstadoCita estado, String message) {}

  public record CitaCancelada(boolean update, UUID id, EstadoCita estado, String message) {}

  @PostMapping("/franjas/crear")
  public ResponseEntity<ApiResponse<FranjaCreada>> crear(
      @Valid @RequestBody CrearFranjaRequest request, Principal principal) {
    var f = agenda.crearFranja(principal.getName(), request.inicio(), request.cupo());
    return ApiResponses.created(
        new FranjaCreada(
            f.id(), f.asesor(), f.inicio(), f.fin(), f.cupo(), "Franja creada correctamente"));
  }

  @GetMapping("/franjas/listar")
  public ResponseEntity<ApiResponse<Pagina<FranjaDisponible>>> franjas(
      @RequestParam(required = false) String asesor,
      @RequestParam(required = false) Instant desde,
      @RequestParam(required = false) Instant hasta,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponses.ok(agenda.franjas(asesor, desde, hasta, new Paginacion(page, size)));
  }

  @PostMapping("/citas/agendar")
  public ResponseEntity<ApiResponse<CitaCreada>> agendar(
      @Valid @RequestBody AgendarRequest request, Principal principal) {
    var c =
        agenda.agendar(request.ordenId(), request.franjaId(), request.tipo(), principal.getName());
    return ApiResponses.created(
        new CitaCreada(
            c.id(), c.ordenId(), c.franjaId(), c.estado(), "Cita agendada correctamente"));
  }

  @PatchMapping("/citas/{id}/cancelar")
  public ResponseEntity<ApiResponse<CitaCancelada>> cancelar(
      @PathVariable UUID id, @Valid @RequestBody CancelarRequest request, Principal principal) {
    var c = agenda.cancelar(id, request.motivo(), principal.getName());
    return ApiResponses.ok(
        new CitaCancelada(true, c.id(), c.estado(), "Cita cancelada correctamente"));
  }

  @GetMapping("/citas/consultar/{id}")
  public ResponseEntity<ApiResponse<CitaDetalle>> consultar(@PathVariable UUID id) {
    return ApiResponses.ok(agenda.consultar(id));
  }

  @GetMapping("/citas/listar")
  public ResponseEntity<ApiResponse<Pagina<CitaDetalle>>> listar(
      @RequestParam(required = false) UUID ordenId,
      @RequestParam(required = false) String asesor,
      @RequestParam(required = false) EstadoCita estado,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponses.ok(agenda.listar(ordenId, asesor, estado, new Paginacion(page, size)));
  }
}
