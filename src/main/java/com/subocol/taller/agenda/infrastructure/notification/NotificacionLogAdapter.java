package com.subocol.taller.agenda.infrastructure.notification;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.port.NotificacionCitaPort;
import com.subocol.taller.agenda.domain.model.EstadoNotificacion;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificacionLogAdapter implements NotificacionCitaPort {
  public ResultadoEnvio enviar(NotificacionCita n) {
    log.info(
        "WHATSAPP_SIMULADO evento={} cita={} orden={} tipo={} inicio={} asesor={}",
        n.evento(),
        n.citaId(),
        n.ordenId(),
        n.tipoCita(),
        n.inicio(),
        n.asesor());
    return new ResultadoEnvio(EstadoNotificacion.SIMULADA, null);
  }
}
