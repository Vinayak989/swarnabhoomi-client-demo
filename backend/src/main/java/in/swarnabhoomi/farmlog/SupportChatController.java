package in.swarnabhoomi.farmlog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/support")
public class SupportChatController {
    private final SupportThreadRepository threads;
    private final SupportMessageRepository messages;
    private final AppUserRepository users;
    private final AccountProfileRepository profiles;

    public SupportChatController(SupportThreadRepository threads, SupportMessageRepository messages,
            AppUserRepository users, AccountProfileRepository profiles) {
        this.threads = threads;
        this.messages = messages;
        this.users = users;
        this.profiles = profiles;
    }

    @GetMapping("/unread")
    @Transactional(readOnly = true)
    public UnreadCount unread(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if ("admin".equals(user.getRole())) {
            return new UnreadCount(messages.countBySenderRoleNotAndReadAtIsNull("admin"));
        }
        requireParticipant(user);
        return new UnreadCount(threads.findByParticipantUserId(user.getId())
                .map(thread -> messages.countByThreadIdAndSenderRoleAndReadAtIsNull(thread.getId(), "admin"))
                .orElse(0L));
    }

    @GetMapping("/my")
    @Transactional
    public ChatView myChat(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireParticipant(user);
        SupportThread thread = threads.findByParticipantUserId(user.getId()).orElse(null);
        if (thread == null) return new ChatView(null, user.getRole(), List.of());
        messages.markRead(thread.getId(), "admin", Instant.now());
        return chatView(thread);
    }

    @PostMapping("/my/messages")
    @Transactional
    public MessageView sendMyMessage(@RequestBody SendMessage request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireParticipant(user);
        String body = cleanBody(request);
        SupportThread thread = threads.findByParticipantUserId(user.getId())
                .orElseGet(() -> threads.save(new SupportThread(user.getId(), user.getRole())));
        SupportMessage message = messages.save(new SupportMessage(thread.getId(), user.getId(), user.getRole(), body));
        thread.touch();
        threads.save(thread);
        return messageView(message);
    }

    @GetMapping("/admin/threads")
    @Transactional(readOnly = true)
    public Page<ThreadSummary> adminThreads(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        Page<SupportThread> result = threads.findAllByOrderByUpdatedAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))));
        List<Long> userIds = result.getContent().stream().map(SupportThread::getParticipantUserId).toList();
        Map<Long, AppUser> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, account -> account));
        Map<Long, AccountProfile> profileByUserId = userIds.isEmpty() ? Map.of()
                : profiles.findAllByUserIds(userIds).stream()
                        .collect(Collectors.toMap(AccountProfile::getUserId, profile -> profile));
        return result.map(thread -> {
            AppUser participant = userById.get(thread.getParticipantUserId());
            AccountProfile profile = profileByUserId.get(thread.getParticipantUserId());
            SupportMessage last = messages.findTopByThreadIdOrderByIdDesc(thread.getId()).orElse(null);
            String fallback = switch (thread.getParticipantRole()) {
                case "seller" -> "विक्रेता";
                case "buyer" -> "खरेदीदार";
                default -> "शेतकरी";
            };
            String name = profile != null && profile.getFullName() != null && !profile.getFullName().isBlank()
                    ? profile.getFullName()
                    : fallback + " #" + thread.getParticipantUserId();
            return new ThreadSummary(thread.getId(), thread.getParticipantUserId(),
                    thread.getParticipantRole(), name,
                    participant == null ? null : participant.getMobileNumber(),
                    last == null ? "" : last.getBody(),
                    last == null ? thread.getCreatedAt() : last.getCreatedAt(),
                    messages.countByThreadIdAndSenderRoleAndReadAtIsNull(
                            thread.getId(), thread.getParticipantRole()));
        });
    }

    @GetMapping("/admin/threads/{id}")
    @Transactional
    public ChatView adminChat(@PathVariable Long id,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        SupportThread thread = getThread(id);
        messages.markRead(thread.getId(), thread.getParticipantRole(), Instant.now());
        return chatView(thread);
    }

    @PostMapping("/admin/threads/{id}/messages")
    @Transactional
    public MessageView sendAdminMessage(@PathVariable Long id, @RequestBody SendMessage request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        SupportThread thread = getThread(id);
        SupportMessage message = messages.save(new SupportMessage(thread.getId(), user.getId(), "admin",
                cleanBody(request)));
        thread.touch();
        threads.save(thread);
        return messageView(message);
    }

    private ChatView chatView(SupportThread thread) {
        return new ChatView(thread.getId(), thread.getParticipantRole(),
                messages.findByThreadIdOrderByIdAsc(thread.getId()).stream()
                        .map(SupportChatController::messageView).toList());
    }

    private SupportThread getThread(Long id) {
        return threads.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "संभाषण सापडले नाही."));
    }

    private static String cleanBody(SendMessage request) {
        String body = request == null || request.body() == null ? "" : request.body().trim();
        if (body.isEmpty() || body.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संदेश १ ते २००० अक्षरांचा असावा.");
        }
        return body;
    }

    private static void requireParticipant(AppUser user) {
        if (!"farmer".equals(user.getRole()) && !"seller".equals(user.getRole())
                && !"buyer".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ही सुविधा शेतकरी, विक्रेता व खरेदीदारांसाठी आहे.");
        }
    }

    private static void requireAdmin(AppUser user) {
        if (!"admin".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "प्रशासक प्रवेश आवश्यक आहे.");
        }
    }

    public record SendMessage(String body) {}
    public record UnreadCount(long count) {}
    public record MessageView(Long id, Long senderUserId, String senderRole, String body,
            Instant createdAt, Instant readAt) {}
    public record ChatView(Long threadId, String participantRole, List<MessageView> messages) {}
    public record ThreadSummary(Long threadId, Long participantUserId, String participantRole,
            String participantName, String participantMobileNumber, String lastMessage,
            Instant lastMessageAt, long unreadCount) {}

    private static MessageView messageView(SupportMessage message) {
        return new MessageView(message.getId(), message.getSenderUserId(), message.getSenderRole(),
                message.getBody(), message.getCreatedAt(), message.getReadAt());
    }
}
