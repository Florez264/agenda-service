package com.subocol.taller.agenda.application.usecase;

import com.subocol.taller.agenda.application.exception.EnvioNotificacionException;
import com.subocol.taller.agenda.application.port.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public final class DespacharNotificacionesUseCase {
  private final BuzonNotificacionesPort buzon;
  private final NotificacionCitaPort proveedor;
  private final Clock clock;
  private final int lote;
  private final int maxIntentos;
  private final int segundosReserva;
  private final int segundosReintento;

  public void ejecutar() {
    for (int i = 0; i < lote; i++) {
      var candidato = buzon.reclamar(clock.instant(), segundosReserva);
      if (candidato.isEmpty()) return;
      var envio = candidato.get();
      if (envio.intento() > maxIntentos) {
        buzon.fallar(envio, "MAX_INTENTOS", clock.instant(), true);
        continue;
      }
      // Never hold a database transaction or the slot lock while making network calls.
      try {
        var resultado = proveedor.enviar(envio.notificacion());
        buzon.completar(envio, resultado);
      } catch (EnvioNotificacionException ex) {
        boolean definitivo = !ex.isReintentable() || envio.intento() >= maxIntentos;
        long espera =
            Math.min(3600L, segundosReintento * (1L << Math.min(10, envio.intento() - 1)));
        buzon.fallar(envio, ex.getMessage(), clock.instant().plusSeconds(espera), definitivo);
        log.warn(
            "Notificación {} evento={} resultado={} código={}",
            envio.notificacion().id(),
            envio.notificacion().evento(),
            definitivo ? "FALLIDA" : "REINTENTO",
            ex.getMessage());
      } catch (RuntimeException ex) {
        // Do not log the exception: HTTP messages may contain credentials or personal data.
        buzon.fallar(
            envio,
            "ERROR_DESPACHO",
            clock.instant().plusSeconds(segundosReintento),
            envio.intento() >= maxIntentos);
        log.error("Error de despacho de notificación {}", envio.notificacion().id());
      }
    }
  }
}
