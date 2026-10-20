package com.hytech.training.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.domain.RecruitmentModels.RecruitmentState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Repository
public final class RecruitmentStateRepository {
    private final ObjectMapper mapper;
    private final Path path;

    public RecruitmentStateRepository(ObjectMapper mapper,
            @Value("${recruitment.data-file:data/recruitment-state.json}") String dataFile) {
        this.mapper = mapper;
        this.path = Path.of(dataFile).toAbsolutePath().normalize();
    }

    public synchronized RecruitmentState load() {
        if (!Files.exists(path)) return null;
        try { return mapper.readValue(path.toFile(), RecruitmentState.class); }
        catch (IOException ex) { throw new IllegalStateException("無法讀取招募資料：" + path, ex); }
    }

    public synchronized RecruitmentState save(RecruitmentState state) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), "recruitment-state-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException unsupportedAtomicMove) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            return state;
        } catch (IOException ex) { throw new IllegalStateException("無法保存招募資料：" + path, ex); }
    }
}
