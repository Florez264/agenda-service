package com.subocol.taller.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.subocol.taller.agenda.domain.exception.*;
import com.subocol.taller.agenda.infrastructure.integration.OrdenesHttpAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OrdenesHttpAdapterTest {
  @Test
  void consultaContratoInternoConAutenticacion() {
    UUID id = UUID.randomUUID();
    var builder =
        RestClient.builder()
            .baseUrl("http://ordenes")
            .defaultHeaders(h -> h.setBasicAuth("agenda-internal", "secreto"));
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ordenes/internal/ordenes/" + id))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Basic YWdlbmRhLWludGVybmFsOnNlY3JldG8="))
        .andRespond(
            withSuccess(
                "{\"status\":200,\"data\":{\"id\":\""
                    + id
                    + "\",\"estado\":\"REPARADA\",\"version\":3}}",
                MediaType.APPLICATION_JSON));
    assertThat(new OrdenesHttpAdapter(builder.build()).consultarEstado(id)).isEqualTo("REPARADA");
    server.verify();
  }

  @Test
  void traduce404SinFiltrarDetallesInternos() {
    var builder = RestClient.builder().baseUrl("http://ordenes");
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
    assertThatThrownBy(
            () -> new OrdenesHttpAdapter(builder.build()).consultarEstado(UUID.randomUUID()))
        .isInstanceOf(RecursoNoEncontradoException.class);
    server.verify();
  }

  @Test
  void errorRemotoYContratoDesconocidoNoPermitenAgendar() {
    for (String body :
        java.util.List.of(
            "{\"status\":200,\"data\":null}",
            "{\"status\":500,\"data\":null}",
            "json incorrecto")) {
      var builder = RestClient.builder().baseUrl("http://ordenes");
      var server = MockRestServiceServer.bindTo(builder).build();
      server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThatThrownBy(
              () -> new OrdenesHttpAdapter(builder.build()).consultarEstado(UUID.randomUUID()))
          .isInstanceOf(ServicioNoDisponibleException.class);
      server.verify();
    }
    var builder = RestClient.builder().baseUrl("http://ordenes");
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    assertThatThrownBy(
            () -> new OrdenesHttpAdapter(builder.build()).consultarEstado(UUID.randomUUID()))
        .isInstanceOf(ServicioNoDisponibleException.class);
    server.verify();
  }

  @Test
  void consultaTelefonoSoloMedianteContratoInterno() {
    UUID id = UUID.randomUUID();
    var builder = RestClient.builder().baseUrl("http://ordenes");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ordenes/internal/ordenes/" + id))
        .andRespond(
            withSuccess(
                """
                {"status":200,"data":{"id":"%s","estado":"REGISTRADA","telefono":"+573001234567","version":0}}
                """
                    .formatted(id),
                MediaType.APPLICATION_JSON));
    assertThat(new OrdenesHttpAdapter(builder.build()).consultarTelefono(id))
        .isEqualTo("+573001234567");
    server.verify();
  }
}
