package jpabasic.truthaiserver.config;

import jpabasic.truthaiserver.jwt.JwtFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@Configuration
@Slf4j
public class SecurityConfig {
    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // api 테스트 위해서 모든 권한 열어둠
                        .anyRequest().permitAll()
                        // 실제 배포 시 swagger랑 로그인만 열어둠
//                        .requestMatchers("/api/auth", "/swagger-ui/**").permitAll()
//                        .anyRequest().authenticated()
                )

                .csrf(AbstractHttpConfigurer::disable)
                .headers((headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                )
                .build();
    }
}
