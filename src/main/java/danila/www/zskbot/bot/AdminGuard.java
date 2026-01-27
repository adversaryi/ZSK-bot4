package danila.www.zskbot.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminGuard {
    private final Set<Long> adminIds;

    public AdminGuard(@Value("${bot.adminIds:}") String raw) {
        this.adminIds = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    public boolean isAdmin(Long chatId) {
        return adminIds.contains(chatId);
    }

    public java.util.Set<Long> getAdminIds() {
        return adminIds;
    }
}
