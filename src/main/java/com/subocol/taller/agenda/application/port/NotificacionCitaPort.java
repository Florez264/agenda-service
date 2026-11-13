package com.subocol.taller.agenda.application.port;

import com.subocol.taller.agenda.application.dto.*;

public interface NotificacionCitaPort {
  ResultadoEnvio enviar(NotificacionCita notificacion);
}
