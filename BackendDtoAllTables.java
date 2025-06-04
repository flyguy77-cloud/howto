// Frontend

class Workflow {
  Long id;
  String name;
  String description;
  WorkflowMetadata metadata; // bijv. category, tags
  List<Node> nodes;
  List<Edge> edges;
}

const handleSaveModalSubmit = (form: {
  name: string;
  category: string;
  description: string;
}) => {
  const payload = {
    name: form.name,
    description: form.description,
    metadata: { category: form.category },
    nodes,
    edges
  };

  workflowApi.save(payload)
    .then(() => setShowSaveModal(false))
    .catch((err) => console.error('Fout bij opslaan:', err));
};

save: (workflow: Workflow) => axios.post('/api/workflows', workflow)

// Backend

public record WorkflowDto(
    String name,
    String description,
    WorkflowMetadata metadata,
    List<NodeDto> nodes,
    List<EdgeDto> edges
) {}

public record NodeDto(
    String id,
    String type,
    Map<String, Object> data
) {}

public record EdgeDto(
    String id,
    String source,
    String target,
    Map<String, Object> data
) {}

@Entity
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workflow_seq")
    @SequenceGenerator(name = "workflow_seq", sequenceName = "workflow_id_seq", allocationSize = 1)
    private Long id;

    private String name;
    private String description;

    @Column(columnDefinition = "jsonb")
    @Convert(converter = WorkflowMetadataConverter.class)
    private WorkflowMetadata metadata;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Node> nodes = new ArrayList<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Edge> edges = new ArrayList<>();
}

@Entity
public class Node {
    @Id
    private String id;

    private String type;

    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    @ManyToOne
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;
}

@Entity
public class Edge {
    @Id
    private String id;

    private String source;
    private String target;

    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    @ManyToOne
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;
}

public Workflow mapDtoToEntity(WorkflowDto dto) {
    Workflow wf = new Workflow();
    wf.setName(dto.name());
    wf.setDescription(dto.description());
    wf.setMetadata(dto.metadata());

    List<Node> nodes = dto.nodes().stream().map(n -> {
        Node node = new Node();
        node.setId(n.id());
        node.setType(n.type());
        node.setData(n.data());
        node.setWorkflow(wf);
        return node;
    }).toList();

    List<Edge> edges = dto.edges().stream().map(e -> {
        Edge edge = new Edge();
        edge.setId(e.id());
        edge.setSource(e.source());
        edge.setTarget(e.target());
        edge.setData(e.data());
        edge.setWorkflow(wf);
        return edge;
    }).toList();

    wf.setNodes(nodes);
    wf.setEdges(edges);

    return wf;
}
