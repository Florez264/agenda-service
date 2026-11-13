package com.subocol.taller.agenda.infrastructure.integration;

import com.subocol.taller.agenda.application.port.OrdenesPort;
import com.subocol.taller.agenda.domain.exception.*;
import com.subocol.taller.agenda.infrastructure.web.response.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.*;

@RequiredArgsConstructor
public class OrdenesHttpAdapter implements OrdenesPort {
  private final RestClient client;

  public record Resumen(UUID id, String estado, String telefono) {}

  public String consultarEstado(UUID id) {
    return consultar(id).estado();
  }

  public String consultarTelefono(UUID id) {
    var telefono = consultar(id).telefono();
    if (telefono == null || telefono.isBlank()) throw new ServicioNoDisponibleException();
    return telefono;
  }

  private Resumen consultar(UUID id) {
    try {
      var respuesta =
          client
              .get()
              .uri("/internal/ordenes/{id}", id)
              .retrieve()
              .body(new ParameterizedTypeReference<ApiResponse<Resumen>>() {});
      if (respuesta == null
          || respuesta.status() != 200
          || respuesta.data() == null
          || !id.equals(respuesta.data().id())
          || respuesta.data().estado() == null
          || !java.util.Set.of(
                  "REGISTRADA",
                  "EN_DIAGNOSTICO",
                  "PENDIENTE_AUTORIZACION",
                  "EN_REPARACION",
                  "REPARADA",
                  "CERRADA")
              .contains(respuesta.data().estado())) throw new ServicioNoDisponibleException();
      return respuesta.data();
    } catch (HttpClientErrorException.NotFound e) {
      throw new RecursoNoEncontradoException("Orden no encontrada");
    } catch (RestClientException e) {
      throw new ServicioNoDisponibleException();
    }
  }
}
