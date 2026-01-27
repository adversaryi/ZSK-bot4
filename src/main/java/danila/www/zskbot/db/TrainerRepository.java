package danila.www.zskbot.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TrainerRepository extends JpaRepository <TrainerEntity, UUID> {

}
