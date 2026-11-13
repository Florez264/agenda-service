package com.subocol.taller.agenda.infrastructure.configuration;

import com.subocol.taller.agenda.application.port.*;
import com.subocol.taller.agenda.application.usecase.AgendaUseCase;
import com.subocol.taller.agenda.infrastructure.integration.OrdenesHttpAdapter;
import java.net.http.HttpClient;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AgendaConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OrdenesPort ordenes(
      @Value("${taller.ordenes.url}") String url,
      @Value("${taller.internal.usuario}") String usuario,
      @Value("${taller.internal.password}") String password) {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(3));
    return new OrdenesHttpAdapter(
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeaders(h -> h.setBasicAuth(usuario, password))
            .build());
  }

  @Bean
  AgendaUseCase agenda(AgendaPort agenda, OrdenesPort ordenes, Clock clock) {
    return new AgendaUseCase(agenda, ordenes, clock);
  }
}
