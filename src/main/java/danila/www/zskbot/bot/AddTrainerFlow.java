package danila.www.zskbot.bot;

import java.util.UUID;

public class AddTrainerFlow {

    public enum Step { NAME, DESCRIPTION, USERNAME, PHOTO,GENDER }

    public enum Mode { ADD, EDIT }

    public static class Draft {
        public Step step = Step.NAME;
        public Mode mode = Mode.ADD;

        // для EDIT
        public UUID trainerId;

        // данные
        public String name;
        public String gender;
        public String description;
        public String username;

        // фото
        public String photoFileId;       // новое фото (если прислали)
        public boolean keepOldPhoto = true; // если true — оставляем старое
    }
}

