package com.subocol.taller.agenda.infrastructure.configuration;

import com.subocol.taller.agenda.application.port.*;
import com.subocol.taller.agenda.application.usecase.DespacharNotificacionesUseCase;
import com.subocol.taller.agenda.infrastructure.notification.*;
import java.net.http.HttpClient;
import java.time.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PropiedadesNotificaciones.class)
public class NotificacionesConfiguration {
  @Bean
  NotificacionCitaPort notificador(PropiedadesNotificaciones propiedades, OrdenesPort ordenes) {
    propiedades.validarWhatsApp();
    if (propiedades.getModo() == PropiedadesNotificaciones.Modo.LOG)
      return new NotificacionLogAdapter();
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(5));
    var client =
        RestClient.builder()
            .baseUrl("https://graph.facebook.com")
            .requestFactory(factory)
            .defaultHeaders(h -> h.setBearerAuth(propiedades.getWhatsapp().getAccessToken()))
            .build();
    return new WhatsAppCloudAdapter(client, propiedades.getWhatsapp(), ordenes);
  }

  @Bean
  DespacharNotificacionesUseCase despachar(
      BuzonNotificacionesPort buzon,
      NotificacionCitaPort notificador,
      Clock clock,
      PropiedadesNotificaciones p) {
    return new DespacharNotificacionesUseCase(
        buzon,
        notificador,
        clock,
        p.getLote(),
        p.getMaxIntentos(),
        p.getSegundosReserva(),
        p.getSegundosReintento());
  }
}
