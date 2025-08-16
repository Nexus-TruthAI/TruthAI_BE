package jpabasic.truthaiserver.controller;

import jakarta.annotation.PostConstruct;
import jpabasic.truthaiserver.dto.TokenDto;
import jpabasic.truthaiserver.service.AuthService;
import jpabasic.truthaiserver.service.LoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jpabasic.truthaiserver.dto.GoogleInfoDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final AuthService authService;
    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody TokenDto dto, HttpSession session) {
        String token = dto.getToken(); // ✅ 인스턴스에서 꺼냄
        log.info("받은 토큰: {}", token);
        GoogleInfoDto authenticate = authService.authenticate(token);
        Map<String, String> tokens = loginService.processUserLogin(authenticate);
        session.setAttribute("accessToken", tokens.get("accessToken"));
        session.setAttribute("refreshToken", tokens.get("refreshToken"));
        log.info("유저 정보: {}", authenticate);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

}
