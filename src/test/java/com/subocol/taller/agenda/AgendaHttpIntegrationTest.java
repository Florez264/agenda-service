package com.subocol.taller.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.subocol.taller.agenda.application.port.OrdenesPort;
import com.subocol.taller.agenda.domain.exception.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "taller.cors.allowed-origins=http://localhost:4200")
@AutoConfigureMockMvc
class AgendaHttpIntegrationTest {
  @Test
  void corsPermitePreflightAngularSinAutenticacion() throws Exception {
    mvc.perform(options("/api/prueba")
        .header("Origin", "http://localhost:4200")
        .header("Access-Control-Request-Method", "PATCH")
        .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  @Test
  void corsRechazaOrigenExternoYRutasInternas() throws Exception {
    mvc.perform(options("/api/prueba")
        .header("Origin", "https://externo.example")
        .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(options("/internal/ordenes/1")
        .header("Origin", "http://localhost:4200")
        .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void corsMantieneAutenticacionEnSolicitudesReales() throws Exception {
    mvc.perform(get("/api/prueba").header("Origin", "http://localhost:4200"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean OrdenesPort ordenes;
  @MockitoBean Clock clock;

  @BeforeEach
  void preparar() {
    when(clock.instant()).thenReturn(Instant.parse("2030-01-01T10:00:00Z"));
    when(ordenes.consultarEstado(any())).thenReturn("REGISTRADA");
    jdbc.update("delete from notificacion_cita");
    jdbc.update("delete from cita");
    jdbc.update("delete from franja");
  }

  String franja(int cupo) throws Exception {
    return JsonPath.read(
        mvc.perform(
                post("/api/franjas/crear")
                    .with(user("asesor").roles("ASESOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"inicio\":\"2030-01-02T14:00:00Z\",\"cupo\":" + cupo + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.asesor").value("asesor"))
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.data.id");
  }

  String body(String franja, UUID orden) {
    return "{\"ordenId\":\"" + orden + "\",\"franjaId\":\"" + franja + "\",\"tipo\":\"INGRESO\"}";
  }

  String agendar(String f, UUID orden) throws Exception {
    return JsonPath.read(
        mvc.perform(
                post("/api/citas/agendar")
                    .with(user("asesor").roles("ASESOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(f, orden)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.data.id");
  }

  @Test
  void agendaConsultaFiltraCancelaYLiberarCupo() throws Exception {
    var f = franja(1);
    UUID orden = UUID.randomUUID();
    String id = agendar(f, orden);
    mvc.perform(get("/api/citas/consultar/" + id).with(user("asesor").roles("ASESOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ordenId").value(orden.toString()))
        .andExpect(jsonPath("$.data.creadaPor").value("asesor"));
    mvc.perform(
            get("/api/citas/listar")
                .param("ordenId", orden.toString())
                .param("estado", "RESERVADA")
                .with(user("asesor").roles("ASESOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(f, UUID.randomUUID())))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/citas/" + id + "/cancelar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Cliente solicita otro día\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.estado").value("CANCELADA"));
    mvc.perform(
            patch("/api/citas/" + id + "/cancelar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Repetición\"}"))
        .andExpect(status().isConflict());
    agendar(f, UUID.randomUUID());
    mvc.perform(get("/api/franjas/listar").with(user("asesor").roles("ASESOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].reservadas").value(1))
        .andExpect(jsonPath("$.data.content[0].disponibles").value(0));
  }

  @Test
  void dosSolicitudesSimultaneasNoSobrevendenUltimoCupo() throws Exception {
    String f = franja(1);
    var inicio = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> reservar =
          () -> {
            inicio.await();
            return mvc.perform(
                    post("/api/citas/agendar")
                        .with(user("asesor").roles("ASESOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(f, UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var a = pool.submit(reservar);
      var b = pool.submit(reservar);
      inicio.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(
            jdbc.queryForObject("select count(*) from cita where estado='RESERVADA'", Long.class))
        .isEqualTo(1);
  }

  @Test
  void franjasNoSeSolapanYValidanHorarioYCupo() throws Exception {
    franja(2);
    mvc.perform(
            post("/api/franjas/crear")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"2030-01-02T14:00:00Z\",\"cupo\":3}"))
        .andExpect(status().isConflict());
    for (String inicio : List.of("2030-01-02T14:15:00Z", "2029-01-01T14:00:00Z"))
      mvc.perform(
              post("/api/franjas/crear")
                  .with(user("asesor").roles("ASESOR"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"inicio\":\"" + inicio + "\",\"cupo\":1}"))
          .andExpect(status().isBadRequest());
    mvc.perform(get("/api/franjas/listar").param("size", "51").with(user("asesor").roles("ASESOR")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/franjas/listar")
                .param("desde", "2030-01-01T00:00:00Z")
                .param("hasta", "2030-03-01T00:00:00Z")
                .with(user("asesor").roles("ASESOR")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rechazaOrdenCerradaInexistenteYCaidaSinReservar() throws Exception {
    String f = franja(1);
    UUID orden = UUID.randomUUID();
    when(ordenes.consultarEstado(orden)).thenReturn("CERRADA");
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(f, orden)))
        .andExpect(status().isConflict());
    when(ordenes.consultarEstado(orden))
        .thenThrow(new RecursoNoEncontradoException("Orden no encontrada"));
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(f, orden)))
        .andExpect(status().isNotFound());
    doThrow(new ServicioNoDisponibleException()).when(ordenes).consultarEstado(orden);
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(f, orden)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503));
    assertThat(jdbc.queryForObject("select count(*) from cita", Long.class)).isZero();
  }

  @Test
  void entregaRequiereOrdenReparada() throws Exception {
    String f = franja(1);
    UUID orden = UUID.randomUUID();
    String entrega = body(f, orden).replace("INGRESO", "ENTREGA");
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(entrega))
        .andExpect(status().isConflict());
    when(ordenes.consultarEstado(orden)).thenReturn("REPARADA");
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(entrega))
        .andExpect(status().isCreated());
  }

  @Test
  void seguridadValidacionYRecursosAusentesMantienenContrato() throws Exception {
    mvc.perform(get("/api/citas/listar"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(get("/api/citas/listar").with(user("interno").roles("SERVICIO")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(
            get("/api/citas/consultar/" + UUID.randomUUID()).with(user("asesor").roles("ASESOR")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/citas/agendar")
                .with(user("asesor").roles("ASESOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
