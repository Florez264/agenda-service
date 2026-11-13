package com.subocol.taller.agenda.infrastructure.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.ZoneId;
import java.util.*;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "taller.notificaciones")
public class PropiedadesNotificaciones {
  public enum Modo {
    LOG,
    WHATSAPP
  }

  @NotNull private Modo modo = Modo.LOG;

  @Min(1)
  @Max(10)
  private int lote = 5;

  @Min(1)
  @Max(10)
  private int maxIntentos = 3;

  @Min(30)
  @Max(300)
  private int segundosReserva = 60;

  @Min(10)
  @Max(3600)
  private int segundosReintento = 30;

  @Min(1000)
  private long pollDelayMs = 30000;

  @Min(0)
  private long initialDelayMs = 10000;

  @Valid @NotNull private WhatsApp whatsapp = new WhatsApp();

  @Getter
  @Setter
  public static class WhatsApp {
    private String apiVersion = "v25.0";
    private String phoneNumberId = "";
    private String accessToken = "";
    private String testRecipient = "";
    private String countryCode = "57";
    private String timeZone = "America/Bogota";
    @Valid private Plantilla agendada = new Plantilla();
    @Valid private Plantilla cancelada = new Plantilla();
  }

  @Getter
  @Setter
  public static class Plantilla {
    private String nombre = "hello_world";
    private String idioma = "en_US";
    private List<String> parametros = new ArrayList<>();
  }

  public void validarWhatsApp() {
    if (modo != Modo.WHATSAPP) return;
    if (!whatsapp.apiVersion.matches("v[0-9]+[.][0-9]+")
        || !whatsapp.phoneNumberId.matches("[0-9]+")
        || whatsapp.accessToken.isBlank())
      throw new IllegalStateException(
          "Configura WHATSAPP_API_VERSION, WHATSAPP_PHONE_NUMBER_ID y WHATSAPP_ACCESS_TOKEN");
    if (!whatsapp.countryCode.matches("[1-9][0-9]{0,3}"))
      throw new IllegalStateException("WHATSAPP_DEFAULT_COUNTRY_CODE inválido");
    ZoneId.of(whatsapp.timeZone);
    Set<String> permitidos =
        Set.of("CITA_ID", "ORDEN_ID", "FECHA", "HORA", "TIPO_CITA", "EVENTO", "ASESOR");
    for (var p : List.of(whatsapp.agendada, whatsapp.cancelada)) {
      if (!p.nombre.matches("[a-z0-9_]+")
          || !p.idioma.matches("[a-z]{2}(_[A-Z]{2})?")
          || p.parametros == null
          || !permitidos.containsAll(p.parametros))
        throw new IllegalStateException(
            "Plantilla WhatsApp inválida: revisar nombre, idioma y parámetros");
    }
  }
}
