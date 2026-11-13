package com.subocol.taller.agenda.infrastructure.integration;

import com.subocol.taller.agenda.application.port.OrdenesPort;
import com.subocol.taller.agenda.infrastructure.persistence.AgendaPersistenceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "taller.cierres.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CierresOrdenWorker {
  private final OrdenesPort ordenes;
  private final AgendaPersistenceAdapter agenda;

  @Scheduled(
      fixedDelayString = "${taller.cierres.poll-delay-ms:15000}",
      initialDelayString = "${taller.cierres.initial-delay-ms:10000}")
  public void procesar() {
    try {
      for (var cierre : ordenes.cierresPendientes()) {
        agenda.prepararOrden(cierre.ordenId());
        agenda.cancelarPorCierre(cierre.ordenId(), cierre.version());
        ordenes.confirmarCierre(cierre.id());
      }
    } catch (RuntimeException ex) {
      log.warn("Sincronización de cierres pendiente; se reintentará sin perder eventos");
    }
  }
}
