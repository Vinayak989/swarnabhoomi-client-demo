package in.swarnabhoomi.farmlog;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@RequestBody(required = false) RegisterRequest request) {
        return authService.register(request == null ? null : request.mobileNumber(),
                request == null ? null : request.password(), request == null ? null : request.role());
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@RequestBody(required = false) PasswordLoginRequest request) {
        return authService.login(request == null ? null : request.mobileNumber(),
                request == null ? null : request.password(), request == null ? null : request.role());
    }

    @PostMapping("/otp/request")
    public AuthService.OtpRequestResponse requestOtp(@RequestBody(required = false) RolePhoneRequest request) {
        return authService.requestOtp(request == null ? null : request.mobileNumber(),
                request == null ? null : request.role());
    }

    @PostMapping("/otp/verify")
    public AuthService.AuthResponse verifyOtp(@RequestBody(required = false) OtpVerifyRequest request) {
        return authService.verifyOtp(request == null ? null : request.mobileNumber(),
                request == null ? null : request.code(), request == null ? null : request.role());
    }

    @GetMapping("/me")
    public AuthService.UserSummary me(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        return new AuthService.UserSummary(user.getId(), user.getMobileNumber(), user.getRole());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> authError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null ? "विनंती पूर्ण करता आली नाही." : exception.getReason()));
    }

    public record RegisterRequest(String mobileNumber, String password, String role) {}
    public record PasswordLoginRequest(String mobileNumber, String password, String role) {}
    public record RolePhoneRequest(String mobileNumber, String role) {}
    public record OtpVerifyRequest(String mobileNumber, String code, String role) {}
}
