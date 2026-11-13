package com.subocol.taller.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.exception.EnvioNotificacionException;
import com.subocol.taller.agenda.application.port.*;
import com.subocol.taller.agenda.application.usecase.*;
import com.subocol.taller.agenda.domain.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class NotificacionesIntegrationTest {
  @Autowired AgendaUseCase agenda;
  @Autowired DespacharNotificacionesUseCase despachar;
  @Autowired BuzonNotificacionesPort buzon;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean OrdenesPort ordenes;
  @MockitoBean Clock clock;
  @MockitoBean NotificacionCitaPort proveedor;
  final Instant ahora = Instant.parse("2030-01-01T10:00:00Z");

  @BeforeEach
  void limpiar() {
    when(clock.instant()).thenReturn(ahora);
    when(ordenes.consultarEstado(any())).thenReturn("REGISTRADA");
    jdbc.update("delete from notificacion_cita");
    jdbc.update("delete from cita");
    jdbc.update("delete from franja");
  }

  CitaDetalle crear() {
    var f = agenda.crearFranja("asesor", ahora.plusSeconds(3600), 1);
    return agenda.agendar(UUID.randomUUID(), f.id(), TipoCita.INGRESO, "asesor");
  }

  long count(String tabla) {
    return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
  }

  NotificacionDetalle unica() {
    return buzon.listar(null, null, new Paginacion(0, 20)).content().getFirst();
  }

  @Test
  void agendarYCancelarEncolanSinEnviarDentroDeLaPeticion() {
    var c = crear();
    assertThat(count("cita")).isEqualTo(1);
    assertThat(unica().evento()).isEqualTo(EventoNotificacion.AGENDADA);
    agenda.cancelar(c.id(), "Cambio de fecha", "asesor");
    assertThat(count("notificacion_cita")).isEqualTo(2);
    assertThatThrownBy(() -> agenda.cancelar(c.id(), "Otra vez", "asesor"))
        .isInstanceOf(com.subocol.taller.agenda.domain.exception.ReglaNegocioException.class);
    assertThat(count("notificacion_cita")).isEqualTo(2);
    verifyNoInteractions(proveedor);
  }

  @Test
  void falloTransitorioConservaCitaYReintentaTrasLaEspera() {
    var c = crear();
    when(proveedor.enviar(any())).thenThrow(new EnvioNotificacionException("META_HTTP_503", true));
    despachar.ejecutar();
    assertThat(agenda.consultar(c.id()).estado()).isEqualTo(EstadoCita.RESERVADA);
    assertThat(unica().estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
    assertThat(unica().intentos()).isEqualTo(1);
    despachar.ejecutar();
    verify(proveedor, times(1)).enviar(any());
    when(clock.instant()).thenReturn(ahora.plusSeconds(31));
    doReturn(new ResultadoEnvio(EstadoNotificacion.ACEPTADA, "wamid.prueba"))
        .when(proveedor)
        .enviar(any());
    despachar.ejecutar();
    assertThat(unica().estado()).isEqualTo(EstadoNotificacion.ACEPTADA);
    assertThat(unica().mensajeProveedorId()).isEqualTo("wamid.prueba");
    assertThat(unica().intentos()).isEqualTo(2);
  }

  @Test
  void tokenInvalidoNoSeReintentaAutomaticamenteYSePuedeReencolar() throws Exception {
    crear();
    when(proveedor.enviar(any())).thenThrow(new EnvioNotificacionException("META_HTTP_401", false));
    despachar.ejecutar();
    assertThat(unica().estado()).isEqualTo(EstadoNotificacion.FALLIDA);
    when(clock.instant()).thenReturn(ahora.plusSeconds(3600));
    despachar.ejecutar();
    verify(proveedor, times(1)).enviar(any());
    mvc.perform(
            patch("/api/notificaciones/" + unica().id() + "/reintentar")
                .with(user("asesor").roles("ASESOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.update").value(true));
    doReturn(new ResultadoEnvio(EstadoNotificacion.ACEPTADA, "wamid.nuevo"))
        .when(proveedor)
        .enviar(any());
    despachar.ejecutar();
    assertThat(unica().estado()).isEqualTo(EstadoNotificacion.ACEPTADA);
  }

  @Test
  void noSeSuperaElMaximoDeTresIntentos() {
    crear();
    when(proveedor.enviar(any())).thenThrow(new EnvioNotificacionException("META_HTTP_429", true));
    for (int i = 0; i < 5; i++) {
      when(clock.instant()).thenReturn(ahora.plusSeconds(i * 3600));
      despachar.ejecutar();
    }
    verify(proveedor, times(3)).enviar(any());
    assertThat(unica().estado()).isEqualTo(EstadoNotificacion.FALLIDA);
    assertThat(unica().intentos()).isEqualTo(3);
  }

  @Test
  void cancelacionEsperaLaNotificacionAnteriorDeLaMismaCita() {
    var c = crear();
    agenda.cancelar(c.id(), "Cambio", "asesor");
    when(proveedor.enviar(any())).thenThrow(new EnvioNotificacionException("META_HTTP_503", true));
    despachar.ejecutar();
    verify(proveedor, times(1)).enviar(any());
    when(clock.instant()).thenReturn(ahora.plusSeconds(31));
    doReturn(new ResultadoEnvio(EstadoNotificacion.SIMULADA, null)).when(proveedor).enviar(any());
    despachar.ejecutar();
    var captor = ArgumentCaptor.forClass(NotificacionCita.class);
    verify(proveedor, times(3)).enviar(captor.capture());
    assertThat(captor.getAllValues().stream().map(NotificacionCita::evento))
        .containsExactly(
            EventoNotificacion.AGENDADA,
            EventoNotificacion.AGENDADA,
            EventoNotificacion.CANCELADA_MANUAL);
  }

  @Test
  void dosDespachadoresReclamanUnaSolaVezYElTokenAnteriorNoConfirmaReservaNueva() throws Exception {
    crear();
    var inicio = new CountDownLatch(1);
    List<Optional<EnvioReclamado>> respuestas;
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Optional<EnvioReclamado>> claim =
          () -> {
            inicio.await();
            return buzon.reclamar(ahora, 60);
          };
      var a = pool.submit(claim);
      var b = pool.submit(claim);
      inicio.countDown();
      respuestas = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    }
    assertThat(respuestas.stream().filter(Optional::isPresent).count()).isEqualTo(1);
    var viejo = respuestas.stream().flatMap(Optional::stream).findFirst().orElseThrow();
    assertThat(buzon.reclamar(ahora.plusSeconds(59), 60)).isEmpty();
    var nuevo = buzon.reclamar(ahora.plusSeconds(61), 60).orElseThrow();
    buzon.completar(viejo, new ResultadoEnvio(EstadoNotificacion.ACEPTADA, "wamid.viejo"));
    assertThat(unica().estado()).isEqualTo(EstadoNotificacion.EN_PROCESO);
    buzon.completar(nuevo, new ResultadoEnvio(EstadoNotificacion.ACEPTADA, "wamid.nuevo"));
    assertThat(unica().mensajeProveedorId()).isEqualTo("wamid.nuevo");
  }

  @Test
  void rollbackRevierteCitaYNotificacionJuntas() {
    var f = agenda.crearFranja("asesor", ahora.plusSeconds(3600), 1);
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .execute(
                        status -> {
                          agenda.agendar(UUID.randomUUID(), f.id(), TipoCita.INGRESO, "asesor");
                          assertThat(count("cita")).isEqualTo(1);
                          assertThat(count("notificacion_cita")).isEqualTo(1);
                          throw new IllegalStateException("Revertir prueba");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("cita")).isZero();
    assertThat(count("notificacion_cita")).isZero();
  }

  @Test
  void consultaPaginadaConContratoYPermisos() throws Exception {
    var c = crear();
    mvc.perform(get("/api/notificaciones/listar")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notificaciones/listar").with(user("servicio").roles("SERVICIO")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/notificaciones/listar")
                .param("citaId", c.id().toString())
                .param("size", "1")
                .with(user("asesor").roles("ASESOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.data.content[0].accessToken").doesNotExist());
  }

  @Test
  void noSeReenviaUnAgendamientoObsoletoTrasCancelarLaCita() throws Exception {
    var c = crear();
    agenda.cancelar(c.id(), "Cambio", "asesor");
    when(proveedor.enviar(any())).thenThrow(new EnvioNotificacionException("META_HTTP_401", false));
    despachar.ejecutar();
    var agendada =
        buzon.listar(c.id(), EstadoNotificacion.FALLIDA, new Paginacion(0, 20)).content().stream()
            .filter(n -> n.evento() == EventoNotificacion.AGENDADA)
            .findFirst()
            .orElseThrow();
    mvc.perform(
            patch("/api/notificaciones/" + agendada.id() + "/reintentar")
                .with(user("asesor").roles("ASESOR")))
        .andExpect(status().isConflict());
  }
}
