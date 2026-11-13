package com.subocol.taller.agenda.infrastructure.notification;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.exception.EnvioNotificacionException;
import com.subocol.taller.agenda.application.port.*;
import com.subocol.taller.agenda.domain.exception.*;
import com.subocol.taller.agenda.domain.model.*;
import com.subocol.taller.agenda.infrastructure.configuration.PropiedadesNotificaciones;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.*;

@RequiredArgsConstructor
public class WhatsAppCloudAdapter implements NotificacionCitaPort {
  private final RestClient client;
  private final PropiedadesNotificaciones.WhatsApp config;
  private final OrdenesPort ordenes;

  public record Mensaje(String id) {}

  public record Respuesta(List<Mensaje> messages) {}

  public ResultadoEnvio enviar(NotificacionCita n) {
    String telefono;
    try {
      telefono =
          config.getTestRecipient().isBlank()
              ? ordenes.consultarTelefono(n.ordenId())
              : config.getTestRecipient();
    } catch (RecursoNoEncontradoException ex) {
      throw new EnvioNotificacionException("ORDEN_NO_ENCONTRADA", false);
    } catch (ServicioNoDisponibleException ex) {
      throw new EnvioNotificacionException("ORDENES_NO_DISPONIBLE", true);
    }
    var plantilla =
        n.evento() == EventoNotificacion.AGENDADA ? config.getAgendada() : config.getCancelada();
    Map<String, Object> template = new LinkedHashMap<>();
    template.put("name", plantilla.getNombre());
    template.put("language", Map.of("code", plantilla.getIdioma()));
    if (!plantilla.getParametros().isEmpty()) {
      var parametros =
          plantilla.getParametros().stream()
              .map(p -> Map.of("type", "text", "text", valor(p, n)))
              .toList();
      template.put("components", List.of(Map.of("type", "body", "parameters", parametros)));
    }
    var payload =
        Map.of(
            "messaging_product",
            "whatsapp",
            "to",
            normalizar(telefono),
            "type",
            "template",
            "template",
            template);
    try {
      var respuesta =
          client
              .post()
              .uri(
                  "/{version}/{phoneId}/messages",
                  config.getApiVersion(),
                  config.getPhoneNumberId())
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(Respuesta.class);
      if (respuesta == null
          || respuesta.messages() == null
          || respuesta.messages().isEmpty()
          || respuesta.messages().getFirst().id() == null
          || respuesta.messages().getFirst().id().isBlank()
          || respuesta.messages().getFirst().id().length() > 300)
        throw new EnvioNotificacionException("RESPUESTA_META_INVALIDA", true);
      // Accepted by Meta is not a delivery receipt. A webhook would be needed to confirm delivery.
      return new ResultadoEnvio(EstadoNotificacion.ACEPTADA, respuesta.messages().getFirst().id());
    } catch (RestClientResponseException ex) {
      int code = ex.getStatusCode().value();
      throw new EnvioNotificacionException(
          "META_HTTP_" + code, code == 408 || code == 429 || code >= 500);
    } catch (RestClientException ex) {
      throw new EnvioNotificacionException("META_CONEXION", true);
    }
  }

  private String normalizar(String telefono) {
    if (telefono == null) throw new EnvioNotificacionException("TELEFONO_INVALIDO", false);
    String limpio = telefono.strip();
    boolean internacional = limpio.startsWith("+");
    limpio = limpio.replace("+", "");
    if (!internacional && limpio.length() == 10) limpio = config.getCountryCode() + limpio;
    if (!limpio.matches("[1-9][0-9]{6,14}"))
      throw new EnvioNotificacionException("TELEFONO_INVALIDO", false);
    return limpio;
  }

  private String valor(String campo, NotificacionCita n) {
    var fecha = n.inicio().atZone(ZoneId.of(config.getTimeZone()));
    return switch (campo) {
      case "CITA_ID" -> n.citaId().toString();
      case "ORDEN_ID" -> n.ordenId().toString();
      case "FECHA" -> fecha.format(DateTimeFormatter.ISO_LOCAL_DATE);
      case "HORA" -> fecha.format(DateTimeFormatter.ofPattern("HH:mm"));
      case "TIPO_CITA" -> n.tipoCita().name();
      case "EVENTO" -> n.evento().name();
      case "ASESOR" -> n.asesor();
      default -> throw new EnvioNotificacionException("PARAMETRO_PLANTILLA_INVALIDO", false);
    };
  }
}
