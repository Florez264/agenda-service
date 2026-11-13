package com.subocol.taller.agenda.infrastructure.notification;

import com.subocol.taller.agenda.application.usecase.DespacharNotificacionesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "taller.notificaciones.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotificacionesWorker {
  private final DespacharNotificacionesUseCase despachar;

  @Scheduled(
      fixedDelayString = "${taller.notificaciones.poll-delay-ms:30000}",
      initialDelayString = "${taller.notificaciones.initial-delay-ms:10000}")
  public void procesar() {
    try {
      despachar.ejecutar();
    } catch (RuntimeException ex) {
      log.error(
          "No fue posible procesar el buzón de notificaciones; se conservarán los pendientes");
    }
  }
}
