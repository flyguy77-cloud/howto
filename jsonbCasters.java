// Optie 1 - Pas de Converter klasse aan
@Converter(autoApply = false)
public class WorkflowMetadataConverter implements AttributeConverter<WorkflowMetadata, PGobject> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public PGobject convertToDatabaseColumn(WorkflowMetadata attribute) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb"); // <-- dit is de sleutel
            jsonObject.setValue(mapper.writeValueAsString(attribute));
            return jsonObject;
        } catch (Exception e) {
            throw new IllegalStateException("Kon metadata niet serialiseren naar jsonb", e);
        }
    }

    @Override
    public WorkflowMetadata convertToEntityAttribute(PGobject dbData) {
        try {
            return mapper.readValue(dbData.getValue(), WorkflowMetadata.class);
        } catch (Exception e) {
            throw new IllegalStateException("Kon metadata niet deserialiseren", e);
        }
    }
}

// Workflow klasse blijft gelijk
@Convert(converter = WorkflowMetadataConverter.class)
@Column(name = "metadata", columnDefinition = "jsonb")
private WorkflowMetadata metadata;

-----------------
// Optie 2 - Pas de Workflow klasse aan
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

@Entity
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String status;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private WorkflowMetadata metadata;

    // getters/setters
}

<dependency>
  <groupId>com.vladmihalcea</groupId>
  <artifactId>hibernate-types-60</artifactId>
  <version>2.21.1</version> <!-- check for latest -->
</dependency>

-------------------------
// Optie 3 - Gebruik de Columntranform annotatie (VOORKEUR)
@Column(name = "metadata", columnDefinition = "jsonb")
@ColumnTransformer(write = "?::jsonb")
@Convert(converter = WorkflowMetadataConverter.class)
private WorkflowMetadata metadata;

// Let wel op dat de ID's niet meer goed gegenereert worden. Dit moet aangepast worden@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workflow_seq") <-- SEQUENCE!
@SequenceGenerator(name = "workflow_seq", sequenceName = "workflow_id_seq", allocationSize = 1)
private Long id;