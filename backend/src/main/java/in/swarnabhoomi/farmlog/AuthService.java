package in.swarnabhoomi.farmlog;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService implements ApplicationRunner {
    private static final Set<String> ROLES = Set.of("farmer", "seller", "buyer", "consultant", "admin");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("\\+91[6-9][0-9]{9}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final LoginSessionRepository sessions;
    private final OtpChallengeRepository challenges;
    private final PasswordEncoder passwords;
    private final TwilioSmsService sms;

    @Value("${app.admin.bootstrap-mobile:}") private String bootstrapMobile;
    @Value("${app.admin.bootstrap-password:}") private String bootstrapPassword;

    public AuthService(AppUserRepository users, LoginSessionRepository sessions,
            OtpChallengeRepository challenges, PasswordEncoder passwords, TwilioSmsService sms) {
        this.users = users;
        this.sessions = sessions;
        this.challenges = challenges;
        this.passwords = passwords;
        this.sms = sms;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapMobile.isBlank() && bootstrapPassword.isBlank()) return;
        String mobile = normalizeMobile(bootstrapMobile);
        validatePassword(bootstrapPassword);
        users.findByMobileNumberAndRole(mobile, "admin").orElseGet(() ->
                users.save(new AppUser(mobile, "admin", passwords.encode(bootstrapPassword))));
    }

    @Transactional
    public AuthResponse register(String mobileValue, String password, String roleValue) {
        String role = normalizeRole(roleValue);
        if ("admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "प्रशासक खाते सार्वजनिक sign up मधून तयार करता येत नाही. प्रशासकाने ते सर्व्हरवरून तयार करावे.");
        }
        String mobile = normalizeMobile(mobileValue);
        validatePassword(password);
        if (users.existsByMobileNumberAndRole(mobile, role)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "या मोबाईल क्रमांकासाठी खाते आधीपासून आहे. लॉगिन करा.");
        }
        AppUser user = users.save(new AppUser(mobile, role, passwords.encode(password)));
        return createSession(user);
    }

    @Transactional
    public AuthResponse login(String mobileValue, String password, String roleValue) {
        String role = normalizeRole(roleValue);
        String mobile = normalizeMobile(mobileValue);
        if (password == null || password.isBlank()) throw badRequest("पासवर्ड आवश्यक आहे.");
        AppUser user = users.findByMobileNumberAndRole(mobile, role)
                .filter(candidate -> passwords.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "मोबाईल क्रमांक, खाते प्रकार किंवा पासवर्ड तपासा."));
        return createSession(user);
    }

    @Transactional
    public OtpRequestResponse requestOtp(String mobileValue, String roleValue) {
        String role = normalizeRole(roleValue);
        String mobile = normalizeMobile(mobileValue);
        Instant now = Instant.now();
        if (challenges.existsByMobileNumberAndCreatedAtAfter(mobile, now.minusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "नवीन OTP मागण्यापूर्वी एक मिनिट थांबा.");
        }
        if (!sms.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OTP login सुरू करण्यासाठी सर्व्हरवर SMS सेवा कॉन्फिगर करणे आवश्यक आहे.");
        }
        AppUser user = users.findByMobileNumberAndRole(mobile, role).orElse(null);
        if (user == null) return new OtpRequestResponse("खाते असल्यास OTP पाठवला आहे.");

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        sms.sendOtp(mobile, code);
        challenges.deleteAllByMobileNumberAndRole(mobile, role);
        challenges.save(new OtpChallenge(mobile, role, passwords.encode(code), now.plus(Duration.ofMinutes(5))));
        return new OtpRequestResponse("खाते असल्यास OTP पाठवला आहे.");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse verifyOtp(String mobileValue, String code, String roleValue) {
        String role = normalizeRole(roleValue);
        String mobile = normalizeMobile(mobileValue);
        OtpChallenge challenge = challenges.findTopByMobileNumberAndRoleAndConsumedFalseOrderByCreatedAtDesc(mobile, role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP अवैध किंवा कालबाह्य आहे."));
        if (!challenge.getExpiresAt().isAfter(Instant.now()) || challenge.getAttempts() >= 5) {
            challenge.setConsumed(true);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP अवैध किंवा कालबाह्य आहे.");
        }
        if (code == null || !code.matches("[0-9]{6}") || !passwords.matches(code, challenge.getCodeHash())) {
            challenge.incrementAttempts();
            if (challenge.getAttempts() >= 5) challenge.setConsumed(true);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP अवैध किंवा कालबाह्य आहे.");
        }
        challenge.setConsumed(true);
        AppUser user = users.findByMobileNumberAndRole(mobile, role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP अवैध किंवा कालबाह्य आहे."));
        return createSession(user);
    }

    @Transactional(readOnly = true)
    public AppUser userForToken(String token) {
        if (token == null || token.length() < 32 || token.length() > 128) return null;
        return sessions.findByTokenHashAndExpiresAtAfter(hashToken(token), Instant.now())
                .map(LoginSession::getUser).orElse(null);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && token.length() >= 32 && token.length() <= 128) {
            sessions.deleteById(hashToken(token));
        }
    }

    private AuthResponse createSession(AppUser user) {
        byte[] randomToken = new byte[32];
        RANDOM.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        sessions.save(new LoginSession(hashToken(token), user, expiresAt));
        return new AuthResponse(token, expiresAt, new UserSummary(user.getId(), user.getMobileNumber(), user.getRole()));
    }

    private static String normalizeMobile(String value) {
        if (value == null) throw badRequest("वैध भारतीय मोबाईल क्रमांक टाका.");
        String mobile = toAsciiDigits(value.trim()).replaceAll("[\\s()-]", "");
        if (mobile.matches("[6-9][0-9]{9}")) mobile = "+91" + mobile;
        else if (mobile.matches("91[6-9][0-9]{9}")) mobile = "+" + mobile;
        if (!INDIAN_MOBILE.matcher(mobile).matches()) throw badRequest("वैध १० अंकी भारतीय मोबाईल क्रमांक टाका.");
        return mobile;
    }

    private static String normalizeRole(String value) {
        String role = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!ROLES.contains(role)) throw badRequest("खाते प्रकार निवडा.");
        return role;
    }

    private static String toAsciiDigits(String value) {
        StringBuilder digits = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            digits.append(character >= '०' && character <= '९' ? (char) ('0' + character - '०') : character);
        }
        return digits.toString();
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw badRequest("पासवर्ड किमान ८ अक्षरांचा आणि जास्तीत जास्त ७२ बाइटचा असावा.");
        }
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record AuthResponse(String token, Instant expiresAt, UserSummary user) {}
    public record UserSummary(Long id, String mobileNumber, String role) {}
    public record OtpRequestResponse(String message) {}
}
