package br.com.tutoria.configs;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// "Agora" no fuso da faculdade. O servidor na nuvem roda em UTC: sem isto,
// as 22h de Brasilia o sistema ja acharia que e o dia seguinte.
// Como bean, os testes podem trocar o relogio por um fixo.
@Configuration
public class RelogioConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:America/Sao_Paulo}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }
}
