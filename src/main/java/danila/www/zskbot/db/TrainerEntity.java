package danila.www.zskbot.db;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "trainers")

public class TrainerEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "telegram_username", nullable = false)
    private String telegramUsername;

    @Column(name = "photo_file_id", nullable = false, columnDefinition = "text")
    private String photoFileId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "gender")
    private String gender;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }


}
