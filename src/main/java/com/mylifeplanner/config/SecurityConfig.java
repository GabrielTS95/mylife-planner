package com.mylifeplanner.config;

import com.mylifeplanner.repository.AppUserRepository;
import com.mylifeplanner.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> {
            if (!AccountService.USERNAME.equals(username)) throw new UsernameNotFoundException("Usuario no encontrado");
            var account = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
            return User.withUsername(account.getUsername()).password(account.getPasswordHash()).roles("OWNER").build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()
                        .anyRequest().hasRole("OWNER"))
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true)
                        .failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .build();
    }
}
