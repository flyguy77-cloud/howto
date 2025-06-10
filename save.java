// WorkflowService.java
@Transactional
public void save(WorkflowRequestDto request) {
    // Map de workflow DTO naar een entity
    WorkflowDto workflowDto = request.workflow();
    Workflow workflow = new Workflow();
    workflow.setName(workflowDto.name());
    workflow.setDescription(workflowDto.description());
    workflow.setMetadata(workflowDto.metadata());

    // Opslaan zodat hij een ID krijgt
    workflowRepository.save(workflow);

    // Nodes koppelen aan deze workflow
    List<Node> nodes = request.nodes().stream()
        .map(dto -> {
            Node node = new Node();
            node.setId(dto.id()); // als String, of genereer UUID als je dat liever hebt
            node.setType(dto.type());
            node.setData(dto.data());
            node.setWorkflow(workflow); // Belangrijk!
            return node;
        }).toList();

    nodeRepository.saveAll(nodes);

    // Edges koppelen aan deze workflow
    List<Edge> edges = request.edges().stream()
        .map(dto -> {
            Edge edge = new Edge();
            edge.setId(dto.id());
            edge.setSource(dto.source());
            edge.setTarget(dto.target());
            edge.setData(dto.data());
            edge.setWorkflow(workflow); // ook hier
            return edge;
        }).toList();

    edgeRepository.saveAll(edges);
}

// Payload
{
  "workflow": {
    "name": "Nieuwe workflow",
    "description": "Beschrijving...",
    "metadata": {
      "category": "default"
    }
  },
  "nodes": [
    { "id": "1", "type": "script", "data": { "label": "Stap 1" } },
    { "id": "2", "type": "logic", "data": { "label": "Stap 2" } }
  ],
  "edges": [
    { "id": "e1-2", "source": "1", "target": "2", "data": {} }
  ]
}

// WorkflowRequestDto
public record WorkflowRequestDto(
    WorkflowDto workflow,
    List<NodeDto> nodes,
    List<EdgeDto> edges
) {}

// WorkflowDto
public record WorkflowDto(
    String name,
    String description,
    WorkflowMetadata metadata
) {}

// WorkflowMetadata
public record WorkflowMetadata(
    String category
) {}

// NodeDto
public record NodeDto(
    String id,
    String type,
    Map<String, Object> data
) {}

// EdgeDto
public record EdgeDto(
    String id,
    String source,
    String target,
    Map<String, Object> data
) {}

// WorkflowController
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<Void> saveWorkflow(@RequestBody WorkflowRequestDto requestDto) {
        workflowService.save(requestDto);
        return ResponseEntity.ok().build();
    }
}