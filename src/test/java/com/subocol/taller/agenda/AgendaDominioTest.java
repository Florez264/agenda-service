package com.subocol.taller.agenda;

import static org.assertj.core.api.Assertions.*;

import com.subocol.taller.agenda.domain.exception.*;
import com.subocol.taller.agenda.domain.model.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgendaDominioTest {
  @Test
  void franjaValidaReglasSinSpring() {
    var ahora = Instant.parse("2030-01-01T00:00:00Z");
    var f = Franja.crear("asesor", ahora.plusSeconds(1800), 1, ahora);
    assertThatThrownBy(() -> f.validarReserva(1, ahora)).isInstanceOf(ReglaNegocioException.class);
    assertThatThrownBy(() -> f.validarReserva(0, f.inicio()))
        .isInstanceOf(ReglaNegocioException.class);
  }

  @Test
  void cancelacionExigeMotivoYNoPuedeRepetirse() {
    var ahora = Instant.parse("2030-01-01T00:00:00Z");
    var c = Cita.reservar(UUID.randomUUID(), UUID.randomUUID(), TipoCita.INGRESO, "asesor", ahora);
    assertThatThrownBy(() -> c.cancelar(" ", "asesor", ahora))
        .isInstanceOf(IllegalArgumentException.class);
    var cancelada = c.cancelar("Cambio de fecha", "asesor", ahora);
    assertThat(cancelada.getEstado()).isEqualTo(EstadoCita.CANCELADA);
    assertThat(c.getEstado()).isEqualTo(EstadoCita.RESERVADA);
    assertThatThrownBy(() -> cancelada.cancelar("Otra vez", "asesor", ahora))
        .isInstanceOf(ReglaNegocioException.class);
  }
}
