package br.com.tutoria.configs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.tutoria.Security.JWTAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import br.com.tutoria.Security.JWTAuthorizationFilter;
import br.com.tutoria.Security.JWTUtil;
import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.enums.UsuarioStatusEnum;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final String[] PUBLIC_MATCHERS = {
            "/"
    };

    private static final String[] PUBLIC_MATCHERS_POST = {
            "/usuario",
            "/user",
            "/login"
    };

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig)
            throws Exception {

        return authConfig.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        // Por padrao o Spring confere o status da conta ANTES da senha. Invertemos a ordem:
        // quem erra a senha recebe so "Credenciais invalidas" e nao descobre se o
        // e-mail esta cadastrado nem se o cadastro esta pendente ou recusado.
        provider.setPreAuthenticationChecks(usuario -> { });
        provider.setPostAuthenticationChecks(SecurityConfig::verificarStatusDaConta);
        return provider;
    }

    private static void escreverErro(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                new ObjectMapper().writeValueAsString(Map.of("status", status, "message", message)));
    }

    private static void verificarStatusDaConta(UserDetails usuario) {
        if (usuario.isEnabled()) {
            return;
        }
        UsuarioStatusEnum status = ((UserSpringSecurity) usuario).getStatus();
        throw new DisabledException(status == UsuarioStatusEnum.RECUSADO
                ? "Seu cadastro foi recusado. Procure a coordenação."
                : "Seu cadastro está aguardando a aprovação de um administrador.");
    }

    @Bean
        public SecurityFilterChain filterChain(
                HttpSecurity http,
                AuthenticationManager authenticationManager,
                JWTUtil jwtUtil,
                UserDetailsService userDetailsService)
                throws Exception {

    JWTAuthenticationFilter jwtAuthFilter =
            new JWTAuthenticationFilter(
                    authenticationManager,
                    jwtUtil);

    JWTAuthorizationFilter jwtAuthzFilter =
            new JWTAuthorizationFilter(
                    jwtUtil,
                    userDetailsService);

    http
        // Usa o bean corsConfigurationSource definido abaixo
        .cors(cors -> {})
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_MATCHERS_POST).permitAll()
                .requestMatchers(PUBLIC_MATCHERS).permitAll()
                .anyRequest().authenticated()
        )
        .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        // Sem isto, o Spring responde 403 vazio a quem nao esta logado. Com 401,
        // o frontend reconhece a sessao expirada e volta para a tela de login.
        .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> escreverErro(response,
                        HttpServletResponse.SC_UNAUTHORIZED, "Sessao expirada ou ausente: faca login novamente"))
                .accessDeniedHandler((request, response, e) -> escreverErro(response,
                        HttpServletResponse.SC_FORBIDDEN, "Voce nao tem permissao para realizar esta acao"))
        )
        .addFilterAt(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
        )
        .addFilterBefore(
                jwtAuthzFilter,
                UsernamePasswordAuthenticationFilter.class
        );

                return http.build();
        }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {

        List<String> origens = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}