package danila.www.zskbot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
@Slf4j
public class SelfPingJob {

    @Value("${app.public-url:}")
    private String publicUrl;

    private final HttpClient client = HttpClient.newHttpClient();

    // каждые 5 минут
    @Scheduled(fixedDelayString = "${app.self-ping-ms:300000}")
    public void ping() {
        if (publicUrl == null || publicUrl.isBlank()) return;

        try {
            String url = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/health"))
                    .GET()
                    .build();

            client.send(req, HttpResponse.BodyHandlers.discarding());
          log.info("✅ self-ping ok: " + url);
        } catch (Exception e) {
            log.error("⚠️ self-ping failed: " + e.getMessage());
        }
    }
}
