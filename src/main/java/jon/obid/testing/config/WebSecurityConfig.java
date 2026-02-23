package jon.obid.testing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
    httpSecurity
        .authorizeRequests(
            authorize ->
                authorize
                    .antMatchers(HttpMethod.GET, "/api/books")
                    .permitAll()
                    .antMatchers(HttpMethod.GET, "/api/books/reviews")
                    .permitAll()
                    .antMatchers("/api/**")
                    .authenticated()
                    .requestMatchers(org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.to(org.springframework.boot.actuate.health.HealthEndpoint.class))
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .sessionManagement(
            sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(new CustomAuthenticationConverter())));

    return httpSecurity.build();
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
