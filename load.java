// ResponseDto

public record WorkflowResponseDto(
    WorkflowDto workflow,
    List<NodeDto> nodes,
    List<EdgeDto> edges
) {}

// WorkflowController
@GetMapping("/{id}")
public ResponseEntity<WorkflowResponseDto> getWorkflow(@PathVariable Long id) {
    WorkflowResponseDto responseDto = workflowService.findById(id);
    return ResponseEntity.ok(responseDto);
}

// WorkflowService
public WorkflowResponseDto findById(Long id) {
    Workflow workflow = workflowRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Workflow niet gevonden"));

    // Map naar DTO
    WorkflowDto workflowDto = new WorkflowDto(
        workflow.getId(),
        workflow.getName(),
        workflow.getDescription(),
        workflow.getMetadata()
    );

    List<NodeDto> nodes = workflow.getNodes().stream()
        .map(n -> new NodeDto(n.getId(), n.getType(), n.getData()))
        .toList();

    List<EdgeDto> edges = workflow.getEdges().stream()
        .map(e -> new EdgeDto(e.getId(), e.getSource(), e.getTarget(), e.getData()))
        .toList();

    return new WorkflowResponseDto(workflowDto, nodes, edges);
}

// Frontend
const handleSelectWorkflow = (workflowId: number) => {
  axios.get(`/api/workflows/${workflowId}`).then(res => {
    setCanvasData(res.data); // of split naar setNodes/setEdges/setWorkflowData
  });
};

// Lijst met selecteerbare workflows
<List>
  {workflows.map((wf) => (
    <ListItem
      key={wf.id}
      button
      onClick={() => handleSelectWorkflow(wf.id)}
    >
      <ListItemText
        primary={wf.name}
        secondary={wf.description}
      />
    </ListItem>
  ))}
</List>

// Backend Endpoint voor lijst
@GetMapping
public ResponseEntity<List<WorkflowSummaryDto>> listWorkflows() {
    List<Workflow> workflows = workflowRepository.findAll();
    List<WorkflowSummaryDto> dtos = workflows.stream()
        .map(wf -> new WorkflowSummaryDto(wf.getId(), wf.getName(), wf.getDescription()))
        .toList();

    return ResponseEntity.ok(dtos);
}
