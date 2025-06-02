// WorkflowController.java
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRepository repo;

    public WorkflowController(WorkflowRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public void create(@RequestBody WorkflowDto dto) {
        repo.save(dto.toEntity());
    }
}

// WorkflowDto.java (Record DTO)
public record WorkflowDto(
    String name,
    String status,
    WorkflowMetadata metadata
) {
    public Workflow toEntity() {
        Workflow w = new Workflow();
        w.setName(name);
        w.setStatus(status);
        w.setMetadata(metadata);
        return w;
    }
}

// Workflow.java (Entity)
import jakarta.persistence.*;

@Entity
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String status;

    @Convert(converter = WorkflowMetadataConverter.class)
    @Column(columnDefinition = "jsonb")
    private WorkflowMetadata metadata;

    // Getters/setters
    public void setName(String name) { this.name = name; }
    public void setStatus(String status) { this.status = status; }
    public void setMetadata(WorkflowMetadata metadata) { this.metadata = metadata; }
}

// WorkflowMetadataConverter.java

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Converter(autoApply = false)
public class WorkflowMetadataConverter implements AttributeConverter<WorkflowMetadata, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(WorkflowMetadata attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialisatie fout", e);
        }
    }

    @Override
    public WorkflowMetadata convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, WorkflowMetadata.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSON deserialisatie fout", e);
        }
    }
}

// WorkflowMetadata.java
import java.util.List;

public record WorkflowMetadata(
    String category,
    List<String> tags
) {}