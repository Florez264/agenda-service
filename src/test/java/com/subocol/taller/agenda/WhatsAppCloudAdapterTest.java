package com.subocol.taller.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.exception.EnvioNotificacionException;
import com.subocol.taller.agenda.application.port.OrdenesPort;
import com.subocol.taller.agenda.domain.model.*;
import com.subocol.taller.agenda.infrastructure.configuration.PropiedadesNotificaciones;
import com.subocol.taller.agenda.infrastructure.notification.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WhatsAppCloudAdapterTest {
  final OrdenesPort ordenes = mock(OrdenesPort.class);

  NotificacionCita n(EventoNotificacion e) {
    return new NotificacionCita(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        e,
        TipoCita.INGRESO,
        "asesor",
        Instant.parse("2030-01-02T14:00:00Z"));
  }

  PropiedadesNotificaciones.WhatsApp config() {
    var c = new PropiedadesNotificaciones.WhatsApp();
    c.setPhoneNumberId("123456");
    return c;
  }

  @Test
  void reproduceHelloWorldEnUsSinParametrosYNormalizaTelefono() {
    var c = config();
    var n = n(EventoNotificacion.AGENDADA);
    when(ordenes.consultarTelefono(n.ordenId())).thenReturn("3001234567");
    var builder =
        RestClient.builder()
            .baseUrl("https://graph.facebook.com")
            .defaultHeaders(h -> h.setBearerAuth("token-falso-de-prueba"));
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://graph.facebook.com/v25.0/123456/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token-falso-de-prueba"))
        .andExpect(
            content()
                .json(
                    """
                    {"messaging_product":"whatsapp","to":"573001234567","type":"template","template":{"name":"hello_world","language":{"code":"en_US"}}}
                    """))
        .andRespond(
            withSuccess(
                "{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"573001234567\",\"wa_id\":\"573001234567\"}],\"messages\":[{\"id\":\"wamid.test\",\"message_status\":\"accepted\"}]}",
                MediaType.APPLICATION_JSON));
    var resultado = new WhatsAppCloudAdapter(builder.build(), c, ordenes).enviar(n);
    assertThat(resultado.estado()).isEqualTo(EstadoNotificacion.ACEPTADA);
    assertThat(resultado.mensajeProveedorId()).isEqualTo("wamid.test");
    server.verify();
  }

  @Test
  void plantillaDeCancelacionIncluyeParametrosDeCitaEnOrden() {
    var c = config();
    c.setTestRecipient("+573009876543");
    c.getCancelada().setNombre("cita_cancelada");
    c.getCancelada().setIdioma("es");
    c.getCancelada().setParametros(List.of("CITA_ID", "FECHA", "HORA", "EVENTO"));
    var n = n(EventoNotificacion.CANCELADA_MANUAL);
    var builder = RestClient.builder().baseUrl("https://graph.facebook.com");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(anything())
        .andExpect(jsonPath("$.template.name").value("cita_cancelada"))
        .andExpect(
            jsonPath("$.template.components[0].parameters[0].text").value(n.citaId().toString()))
        .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("2030-01-02"))
        .andExpect(jsonPath("$.template.components[0].parameters[2].text").value("09:00"))
        .andExpect(
            jsonPath("$.template.components[0].parameters[3].text").value("CANCELADA_MANUAL"))
        .andRespond(
            withSuccess("{\"messages\":[{\"id\":\"wamid.test\"}]}", MediaType.APPLICATION_JSON));
    new WhatsAppCloudAdapter(builder.build(), c, ordenes).enviar(n);
    verifyNoInteractions(ordenes);
    server.verify();
  }

  @Test
  void clasificaErroresSinExponerCuerpoNiCredenciales() {
    for (HttpStatus status :
        List.of(
            HttpStatus.UNAUTHORIZED,
            HttpStatus.BAD_REQUEST,
            HttpStatus.TOO_MANY_REQUESTS,
            HttpStatus.SERVICE_UNAVAILABLE)) {
      var c = config();
      c.setTestRecipient("573001234567");
      var builder = RestClient.builder().baseUrl("https://graph.facebook.com");
      var server = MockRestServiceServer.bindTo(builder).build();
      server
          .expect(anything())
          .andRespond(withStatus(status).body("token-secreto-detalle-interno"));
      assertThatThrownBy(
              () ->
                  new WhatsAppCloudAdapter(builder.build(), c, ordenes)
                      .enviar(n(EventoNotificacion.AGENDADA)))
          .isInstanceOfSatisfying(
              EnvioNotificacionException.class,
              ex -> {
                assertThat(ex.getMessage()).isEqualTo("META_HTTP_" + status.value());
                assertThat(ex.isReintentable())
                    .isEqualTo(status.value() == 429 || status.is5xxServerError());
              });
      server.verify();
    }
  }

  @Test
  void numeroInvalidoNoLlamaAMeta() {
    var c = config();
    c.setTestRecipient("incorrecto");
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    assertThatThrownBy(
            () ->
                new WhatsAppCloudAdapter(builder.build(), c, ordenes)
                    .enviar(n(EventoNotificacion.AGENDADA)))
        .isInstanceOfSatisfying(
            EnvioNotificacionException.class, ex -> assertThat(ex.isReintentable()).isFalse());
    server.verify();
  }

  @Test
  void modoLogFuncionaSinCredencialesYModoRealLasExige() {
    var p = new PropiedadesNotificaciones();
    assertThatCode(p::validarWhatsApp).doesNotThrowAnyException();
    assertThat(new NotificacionLogAdapter().enviar(n(EventoNotificacion.AGENDADA)).estado())
        .isEqualTo(EstadoNotificacion.SIMULADA);
    p.setModo(PropiedadesNotificaciones.Modo.WHATSAPP);
    assertThatThrownBy(p::validarWhatsApp).isInstanceOf(IllegalStateException.class);
    p.getWhatsapp().setPhoneNumberId("123");
    p.getWhatsapp().setAccessToken("token-falso");
    assertThatCode(p::validarWhatsApp).doesNotThrowAnyException();
    p.getWhatsapp().getAgendada().setParametros(List.of("DESCONOCIDO"));
    assertThatThrownBy(p::validarWhatsApp).isInstanceOf(IllegalStateException.class);
  }
}
