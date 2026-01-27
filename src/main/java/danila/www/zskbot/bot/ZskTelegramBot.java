package danila.www.zskbot.bot;


import danila.www.zskbot.db.TrainerEntity;
import danila.www.zskbot.db.TrainerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ZskTelegramBot extends TelegramLongPollingBot {

    private final String username;
    private final TrainerRepository trainerRepository;
    private final AdminGuard adminGuard;

    // мастер добавления/редактирования
    private final Map<Long, AddTrainerFlow.Draft> drafts = new ConcurrentHashMap<>();
    private final Map<Long, String> userGenderFilter = new ConcurrentHashMap<>();

    // приветствие ДО /start (покажем один раз за запуск приложения)
    private final Set<Long> preStartWelcomed = ConcurrentHashMap.newKeySet();

    public ZskTelegramBot(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username,
            TrainerRepository trainerRepository,
            AdminGuard adminGuard
    ) {
        super(token);
        this.username = username;
        this.trainerRepository = trainerRepository;
        this.adminGuard = adminGuard;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                onCallback(update);
                return;
            }

            if (update.hasMessage() && update.getMessage().hasPhoto()) {
                onPhoto(update);
                return;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                onText(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== MENUS =====

    private InlineKeyboardMarkup userMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(InlineKeyboardButton.builder()
                                .text("📋 Список тренеров")
                                .callbackData("menu:trainers")
                                .build())
                ))
                .build();
    }

    private InlineKeyboardMarkup adminMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(InlineKeyboardButton.builder()
                                .text("📋 Список тренеров")
                                .callbackData("menu:trainers")
                                .build()),
                        List.of(InlineKeyboardButton.builder()
                                .text("➕ Добавить тренера")
                                .callbackData("menu:add_trainer")
                                .build())
                ))
                .build();
    }

    private InlineKeyboardMarkup genderFilterKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(
                                InlineKeyboardButton.builder()
                                        .text("👨 Мужчины")
                                        .callbackData("filter:M")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("👩 Женщины")
                                        .callbackData("filter:F")
                                        .build()
                        ),
                        List.of(
                                InlineKeyboardButton.builder()
                                        .text("👥 Все")
                                        .callbackData("filter:ALL")
                                        .build()
                        )
                ))
                .build();
    }


    private void sendMenu(Long chatId) throws TelegramApiException {
        boolean isAdmin = adminGuard.isAdmin(chatId);
        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(username + " Выбери действие 👇")
                .replyMarkup(isAdmin ? adminMenu() : userMenu())
                .build());
    }

    // ===== PRE-START GREETING =====

    private void sendPreStartGreeting(Long chatId) throws TelegramApiException {
        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(
                        "Привет! 👋\n\n" +
                                "Чтобы начать работу с ботом, нажми:\n" +
                                "/start\n\n" +
                                "После этого появится меню и список тренеров 💪"
                )
                .build());
    }

    // ===== TEXT =====

    private void onText(Update update) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        // Приветствие ДО /start (Telegram не даёт боту писать первым — только в ответ)
        if (!"/start".equals(text) && !preStartWelcomed.contains(chatId)) {
            preStartWelcomed.add(chatId);
            sendPreStartGreeting(chatId);
            return;
        }

        switch (text) {
            case "/start" -> {
                KeyboardRow row = new KeyboardRow();
                row.add(new KeyboardButton("📋 Список тренеров"));

                ReplyKeyboardMarkup kb = ReplyKeyboardMarkup.builder()
                        .resizeKeyboard(true)
                        .keyboard(List.of(row))
                        .build();

                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(
                                "Привет! Это ZSK_BOT 👋\n\n" +
                                        "Я помогу тебе выбрать тренера 💪\n\n" +
                                        "Нажми 'Список тренеров'\n\n" +
                                        "И начинай свой путь к телу своей мечты 💪"
                        )
                        .replyMarkup(kb)
                        .build());

                // если юзер сделал /start — больше "pre-start" ему не нужен
                preStartWelcomed.add(chatId);
            }

            case "📋 Список тренеров" -> showTrainers(chatId);

            case "/cancel" -> {
                drafts.remove(chatId);
                reply(chatId, "Ок, отменил.");
                sendMenu(chatId);
            }

            case "/trainers" -> {
                showTrainers(chatId);
                sendMenu(chatId);
            }

            case "/add_trainer" -> {
                if (!adminGuard.isAdmin(chatId)) {
                    reply(chatId, "Не понял 🤔");
                    sendMenu(chatId);
                    return;
                }
                AddTrainerFlow.Draft d = new AddTrainerFlow.Draft();
                d.mode = AddTrainerFlow.Mode.ADD;
                drafts.put(chatId, d);

                reply(chatId, "Добавляем тренера.\nШаг 1/5: Введи имя тренера.\n(Отмена: /cancel)");
            }

            default -> {
                var d = drafts.get(chatId);
                if (d != null) {
                    handleDraftText(chatId, text, d);
                } else {
                    reply(chatId, "Нажми кнопку в меню 👇");
                    sendMenu(chatId);
                }
            }
        }
    }

    private void handleDraftText(Long chatId, String text, AddTrainerFlow.Draft d) throws TelegramApiException {
        boolean isEdit = d.mode == AddTrainerFlow.Mode.EDIT;
        boolean skip = "-".equals(text); // для EDIT: "-" = оставить как есть

        switch (d.step) {
            case NAME -> {
                if (!skip) {
                    if (text.length() < 2) {
                        reply(chatId, "Имя слишком короткое. Введи имя ещё раз.\n(Отмена: /cancel)");
                        return;
                    }
                    d.name = text;
                }
                d.step = AddTrainerFlow.Step.GENDER;

                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("Шаг 2/5: Выбери пол тренера 👇")
                        .replyMarkup(genderKeyboard(isEdit))
                        .build());
            }

            case GENDER -> {
                // fallback: если написал текстом
                String g = text.trim().toLowerCase();

                if (isEdit && "-".equals(g)) {
                    // оставим как есть
                } else if (g.equals("м") || g.equals("m")) {
                    d.gender = "M";
                } else if (g.equals("ж") || g.equals("f")) {
                    d.gender = "F";
                } else {
                    reply(chatId, "Выбери кнопкой 👇 (или напиши М/Ж)");
                    execute(SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("Выбери пол тренера:")
                            .replyMarkup(genderKeyboard(isEdit))
                            .build());
                    return;
                }


                d.step = AddTrainerFlow.Step.DESCRIPTION;
                reply(chatId,
                        (isEdit ? "Редактирование.\n" : "") +
                                "Шаг 3/5: Введи описание.\n" +
                                (isEdit ? "Напиши '-' чтобы оставить старое.\n" : "") +
                                "(Отмена: /cancel)"
                );
            }

            case DESCRIPTION -> {
                if (!skip) {
                    if (text.length() < 5) {
                        reply(chatId, "Описание слишком короткое. Введи ещё раз.\n(Отмена: /cancel)");
                        return;
                    }
                    d.description = text;
                }
                d.step = AddTrainerFlow.Step.USERNAME;
                reply(chatId,
                        "Шаг 4/5: Введи username (без @).\n" +
                                (isEdit ? "Напиши '-' чтобы оставить старый.\n" : "") +
                                "(Отмена: /cancel)"
                );
            }

            case USERNAME -> {
                if (!skip) {
                    String u = text.startsWith("@") ? text.substring(1) : text;
                    if (!u.matches("^[A-Za-z0-9_]{5,32}$")) {
                        reply(chatId, "Username неверный. Только буквы/цифры/_ (5..32), без @.\n(Отмена: /cancel)");
                        return;
                    }
                    d.username = u;
                }

                d.step = AddTrainerFlow.Step.PHOTO;

                if (isEdit) {
                    reply(chatId,
                            "Шаг 5/5: Пришли новое фото тренера (как фото).\n" +
                                    "Или напиши '-' чтобы оставить старое.\n" +
                                    "(Отмена: /cancel)"
                    );
                } else {
                    reply(chatId, "Шаг 4/4: Пришли фото тренера (как фото, не как файл).\n(Отмена: /cancel)");
                }
            }

            case PHOTO -> {
                if (isEdit && skip) {
                    d.keepOldPhoto = true;
                    finishEditTrainer(chatId, d);
                    return;
                }

                reply(chatId,
                        "Жду фото.\n" +
                                (isEdit ? "Или '-' чтобы оставить старое.\n" : "") +
                                "(Отмена: /cancel)"
                );
            }
        }
    }

    // ===== PHOTO (finish add/edit) =====

    private void onPhoto(Update update) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        var d = drafts.get(chatId);
        if (d == null) return;

        if (d.step != AddTrainerFlow.Step.PHOTO) {
            reply(chatId, "Сейчас не шаг с фото. Продолжай /cancel или следуй шагам.");
            return;
        }

        var photos = update.getMessage().getPhoto();
        var best = photos.get(photos.size() - 1);
        String fileId = best.getFileId();

        d.photoFileId = fileId;
        d.keepOldPhoto = false;

        if (d.mode == AddTrainerFlow.Mode.ADD) {
            var e = new TrainerEntity();
            e.setName(d.name);
            e.setDescription(d.description);
            e.setTelegramUsername(d.username);
            e.setPhotoFileId(d.photoFileId);
            e.setGender(d.gender);

            trainerRepository.save(e);
            drafts.remove(chatId);

            reply(chatId, "✅ Тренер добавлен: " + e.getName());
            sendMenu(chatId);
            return;
        }

        // EDIT
        finishEditTrainer(chatId, d);
    }

    private void finishEditTrainer(Long chatId, AddTrainerFlow.Draft d) throws TelegramApiException {
        if (d.trainerId == null) {
            drafts.remove(chatId);
            reply(chatId, "Ошибка: не найден trainerId для редактирования.");
            return;
        }

        var trainerOpt = trainerRepository.findById(d.trainerId);
        if (trainerOpt.isEmpty()) {
            drafts.remove(chatId);
            reply(chatId, "Тренер не найден (возможно уже удалён).");
            return;
        }

        var t = trainerOpt.get();

        // поля могли остаться старыми (если "-" на шаге) — мы меняем только то, что реально пришло
        if (d.name != null) t.setName(d.name);
        if (d.description != null) t.setDescription(d.description);
        if (d.username != null) t.setTelegramUsername(d.username);
        if(d.gender != null) t.setGender(d.gender);

        if (!d.keepOldPhoto && d.photoFileId != null) {
            t.setPhotoFileId(d.photoFileId);
        }

        trainerRepository.save(t);
        drafts.remove(chatId);

        reply(chatId, "✅ Обновил тренера: " + t.getName());
        sendMenu(chatId);
    }

    // ===== CALLBACKS =====

    private void onCallback(Update update) throws TelegramApiException {
        var cb = update.getCallbackQuery();
        Long chatId = cb.getMessage().getChatId();
        String data = cb.getData();

        try {
            if ("menu:trainers".equals(data)) {
                showTrainers(chatId);
                sendMenu(chatId);
                return;
            }

            if ("menu:add_trainer".equals(data)) {
                if (!adminGuard.isAdmin(chatId)) {
                    reply(chatId, "Не понял 🤔");
                    sendMenu(chatId);
                    return;
                }
                AddTrainerFlow.Draft d = new AddTrainerFlow.Draft();
                d.mode = AddTrainerFlow.Mode.ADD;
                drafts.put(chatId, d);

                reply(chatId, "Добавляем тренера.\nШаг 1/4: Введи имя тренера.\n(Отмена: /cancel)");
                return;
            }

            // ===== GENDER FILTER (USER) =====
            if (data != null && data.startsWith("filter:")) {
                String value = data.substring("filter:".length());

                if ("ALL".equals(value)) {
                    userGenderFilter.remove(chatId);
                } else if ("M".equals(value) || "F".equals(value)) {
                    userGenderFilter.put(chatId, value);
                } else {
                    reply(chatId, "Не понял фильтр.");
                    return;
                }

                List<TrainerEntity> trainers = loadTrainers(chatId);
                if (trainers.isEmpty()) {
                    reply(chatId, "Тренеров с таким полом пока нет.");
                    return;
                }

                showTrainerPage(chatId, null, 0);
                return;
            }

            // ===== PAGINATION =====
            // page:trainers:<index>
            if (data != null && data.startsWith("page:trainers:")) {
                int index = parseIndexSafe(data.substring("page:trainers:".length()), 0);
                Integer messageId = cb.getMessage().getMessageId();
                showTrainerPage(chatId, messageId, index);
                return;
            }

            if ("noop".equals(data)) {
                return;
            }

            // ===== UPDATE FLOW (admin only) =====
            if (data != null && data.startsWith("upd:")) {
                if (!adminGuard.isAdmin(chatId)) {
                    reply(chatId, "Не понял 🤔");
                    sendMenu(chatId);
                    return;
                }

                UUID id = UUID.fromString(data.substring("upd:".length()));
                var trainerOpt = trainerRepository.findById(id);
                if (trainerOpt.isEmpty()) {
                    reply(chatId, "Тренер не найден.");
                    return;
                }

                var t = trainerOpt.get();
                AddTrainerFlow.Draft d = new AddTrainerFlow.Draft();
                d.mode = AddTrainerFlow.Mode.EDIT;
                d.trainerId = id;

                // текущие значения (для удобства "-")
                d.name = t.getName();
                d.description = t.getDescription();
                d.username = t.getTelegramUsername();
                d.gender=t.getGender();

                drafts.put(chatId, d);

                reply(chatId,
                        "Редактируем тренера: " + t.getName() + "\n" +
                                "Шаг 1/4: Введи новое имя.\n" +
                                "Или напиши '-' чтобы оставить старое.\n" +
                                "(Отмена: /cancel)"
                );
                return;
            }

            // ===== GENDER BUTTONS =====
            if (data != null && data.startsWith("gender:")) {
                var d = drafts.get(chatId);
                if (d == null || d.step != AddTrainerFlow.Step.GENDER) {
                    reply(chatId, "Сейчас выбор пола не актуален.");
                    return;
                }

                String v = data.substring("gender:".length());

                if ("M".equals(v)) {
                    d.gender = "M";
                } else if ("F".equals(v)) {
                    d.gender = "F";
                } else if ("KEEP".equals(v) && d.mode == AddTrainerFlow.Mode.EDIT) {
                    // ничего не меняем
                } else {
                    reply(chatId, "Не понял выбор пола.");
                    return;
                }

                d.step = AddTrainerFlow.Step.DESCRIPTION;

                reply(chatId,
                        "Шаг 3/5: Введи описание.\n" +
                                (d.mode == AddTrainerFlow.Mode.EDIT ? "Напиши '-' чтобы оставить старое.\n" : "") +
                                "(Отмена: /cancel)"
                );
                return;
            }

            // ===== DELETE FLOW (admin only) =====
            if (data != null && data.startsWith("del:")) {
                if (!adminGuard.isAdmin(chatId)) {
                    reply(chatId, "Не понял 🤔");
                    sendMenu(chatId);
                    return;
                }

                UUID id = UUID.fromString(data.substring("del:".length()));
                var trainer = trainerRepository.findById(id).orElse(null);
                if (trainer == null) {
                    reply(chatId, "Тренер уже удалён или не найден.");
                    sendMenu(chatId);
                    return;
                }

                var yes = InlineKeyboardButton.builder()
                        .text("✅ Да, удалить")
                        .callbackData("delc:" + id)
                        .build();

                var no = InlineKeyboardButton.builder()
                        .text("❌ Отмена")
                        .callbackData("delno:" + id)
                        .build();

                var kb = InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(yes, no)))
                        .build();

                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("Точно удалить тренера: " + trainer.getName() + "?")
                        .replyMarkup(kb)
                        .build());

                return;
            }

            if (data != null && data.startsWith("delc:")) {
                if (!adminGuard.isAdmin(chatId)) {
                    reply(chatId, "Не понял 🤔");
                    sendMenu(chatId);
                    return;
                }

                UUID id = UUID.fromString(data.substring("delc:".length()));

                if (!trainerRepository.existsById(id)) {
                    reply(chatId, "Тренер уже удалён или не найден.");
                    sendMenu(chatId);
                    return;
                }

                trainerRepository.deleteById(id);
                reply(chatId, "🗑 Удалено.");

                showTrainers(chatId);
                sendMenu(chatId);
                return;
            }

            if (data != null && data.startsWith("delno:")) {
                reply(chatId, "Ок, не удаляю.");
                sendMenu(chatId);
                return;
            }

            // ===== PICK TRAINER =====
            if (data != null && data.startsWith("pick:")) {
                UUID id = UUID.fromString(data.substring("pick:".length()));
                var trainer = trainerRepository.findById(id).orElse(null);

                if (trainer == null) {
                    reply(chatId, "Тренер не найден.");
                    sendMenu(chatId);
                    return;
                }

                String u = trainer.getTelegramUsername();
                if (u.startsWith("@")) u = u.substring(1);
                String link = "https://t.me/" + u;

                var btn = InlineKeyboardButton.builder()
                        .text("💬 Перейти в чат с " + trainer.getName())
                        .url(link)
                        .build();

                var kb = InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(btn)))
                        .build();

                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("Вы выбрали тренера: " + trainer.getName())
                        .replyMarkup(kb)
                        .build());

                sendMenu(chatId);
            }

        } finally {
            try {
                execute(AnswerCallbackQuery.builder().callbackQueryId(cb.getId()).build());
            } catch (Exception ignored) {
            }
        }
    }

    // ===== TRAINERS LIST (по одному + редактирование сообщения) =====

    private void showTrainers(Long chatId) throws TelegramApiException {
        // 1) сначала проверяем: есть ли вообще тренеры
        List<TrainerEntity> all = trainerRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        if (all.isEmpty()) {
            reply(chatId, "Пока нет тренеров.");
            return;
        }

        // 2) если есть — предлагаем фильтр пола (а дальше уже покажем карточки)
        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбери тренеров по полу 👇")
                .replyMarkup(genderFilterKeyboard())
                .build());
    }

    private List<TrainerEntity> loadTrainers(Long chatId)  {
        List<TrainerEntity> all =
                trainerRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        String gender = userGenderFilter.get(chatId);
        if (gender == null) return all;

        return all.stream()
                .filter(t -> gender.equalsIgnoreCase(t.getGender()))
                .toList();
    }

    /**
     * messageId == null -> отправляем новую карточку
     * messageId != null -> редактируем существующую (листать по одному)
     */
    private void showTrainerPage(Long chatId, Integer messageId, int index) throws TelegramApiException {
        var trainers = loadTrainers(chatId);

        if (trainers.isEmpty()) {
            reply(chatId, "Пока нет тренеров.");
            return;
        }

        if (index < 0) index = 0;
        if (index >= trainers.size()) index = trainers.size() - 1;

        var t = trainers.get(index);
        InlineKeyboardMarkup kb = trainerCardKeyboard(chatId, t, index, trainers.size());
        String genderText = "";
        if ("M".equalsIgnoreCase(t.getGender())) genderText = "👨 Пол: М\n";
        if ("F".equalsIgnoreCase(t.getGender())) genderText = "👩 Пол: Ж\n";

        String caption = "*" + t.getName() + "*\n" + genderText + t.getDescription();

        if (messageId == null) {
            execute(SendPhoto.builder()
                    .chatId(chatId.toString())
                    .photo(new InputFile(t.getPhotoFileId()))
                    .caption(caption)
                    .parseMode("Markdown")
                    .replyMarkup(kb)
                    .build());
            return;
        }

        InputMediaPhoto media = new InputMediaPhoto();
        media.setMedia(t.getPhotoFileId());
        media.setCaption(caption);
        media.setParseMode("Markdown");

        execute(EditMessageMedia.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .media(media)
                .replyMarkup(kb)
                .build());
    }

    private InlineKeyboardMarkup genderKeyboard(boolean isEdit) {
        InlineKeyboardButton male = InlineKeyboardButton.builder()
                .text("👨 М")
                .callbackData("gender:M")
                .build();

        InlineKeyboardButton female = InlineKeyboardButton.builder()
                .text("👩 Ж")
                .callbackData("gender:F")
                .build();

        if (isEdit) {
            InlineKeyboardButton keep = InlineKeyboardButton.builder()
                    .text("↩️ Оставить старое")
                    .callbackData("gender:KEEP")
                    .build();

            return InlineKeyboardMarkup.builder()
                    .keyboard(List.of(
                            List.of(male, female),
                            List.of(keep)
                    ))
                    .build();
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(male, female)))
                .build();
    }

    private InlineKeyboardMarkup trainerCardKeyboard(Long chatId, TrainerEntity t, int index, int total) {
        var pickBtn = InlineKeyboardButton.builder()
                .text("✅ Выбрать")
                .callbackData("pick:" + t.getId())
                .build();

        boolean isAdmin = adminGuard.isAdmin(chatId);

        String prevData = (index <= 0) ? "noop" : ("page:trainers:" + (index - 1));
        String nextData = (index >= total - 1) ? "noop" : ("page:trainers:" + (index + 1));

        var prevBtn = InlineKeyboardButton.builder().text("⬅️").callbackData(prevData).build();
        var nextBtn = InlineKeyboardButton.builder().text("➡️").callbackData(nextData).build();
        var counter = InlineKeyboardButton.builder().text((index + 1) + "/" + total).callbackData("noop").build();

        if (isAdmin) {
            var updBtn = InlineKeyboardButton.builder()
                    .text("✏️ Обновить")
                    .callbackData("upd:" + t.getId())
                    .build();

            var delBtn = InlineKeyboardButton.builder()
                    .text("🗑 Удалить")
                    .callbackData("del:" + t.getId())
                    .build();

            if (total > 1) {
                return InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(pickBtn),
                                List.of(updBtn),
                                List.of(delBtn),
                                List.of(prevBtn, counter, nextBtn)
                        ))
                        .build();
            }

            return InlineKeyboardMarkup.builder()
                    .keyboard(List.of(
                            List.of(pickBtn),
                            List.of(updBtn),
                            List.of(delBtn)
                    ))
                    .build();
        }

        if (total > 1) {
            return InlineKeyboardMarkup.builder()
                    .keyboard(List.of(
                            List.of(pickBtn),
                            List.of(prevBtn, counter, nextBtn)
                    ))
                    .build();
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(pickBtn)))
                .build();
    }

    private int parseIndexSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // ===== UTIL =====

    private void reply(Long chatId, String text) throws TelegramApiException {
        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }
}