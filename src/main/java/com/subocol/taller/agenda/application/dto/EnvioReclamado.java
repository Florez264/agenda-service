package com.subocol.taller.agenda.application.dto;

import java.util.UUID;

public record EnvioReclamado(NotificacionCita notificacion, UUID token, int intento) {}
