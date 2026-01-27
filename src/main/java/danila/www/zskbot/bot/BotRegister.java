package danila.www.zskbot.bot;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotRegister {

    private final ZskTelegramBot bot;

    public BotRegister(ZskTelegramBot bot) {
        this.bot = bot;
    }

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
            System.out.println("✅ BOT REGISTERED: " + bot.getBotUsername());
        } catch (Exception e) {
            System.err.println("❌ Bot register failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
