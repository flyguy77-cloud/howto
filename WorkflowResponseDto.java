// WorkflowResponseDto
public record WorkflowResponseDto(
    Long id,
    String name,
    String description,
    WorkflowMetadata metadata,
    List<NodeDto> nodes,
    List<EdgeDto> edges
) {}

// WorkflowResponseDto
public WorkflowResponseDto getWorkflowById(Long id) {
    Workflow workflow = workflowRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Workflow niet gevonden"));

    List<NodeDto> nodeDtos = workflow.getNodes().stream()
        .map(node -> new NodeDto(
            node.getNodeId(),
            node.getType(),
            node.getLabel(),
            node.getPositionX(),
            node.getPositionY()
        )).toList();

    List<EdgeDto> edgeDtos = workflow.getEdges().stream()
        .map(edge -> new EdgeDto(
            edge.getSourceId(),
            edge.getTargetId(),
            edge.getLabel()
        )).toList();

    return new WorkflowResponseDto(
        workflow.getId(),
        workflow.getName(),
        workflow.getDescription(),
        workflow.getMetadata(),
        nodeDtos,
        edgeDtos
    );
}

com.example.myapp
│
├── controller
│   └── WorkflowController.java
│
├── service
│   └── WorkflowService.java
│
├── dto
│   ├── request
│   │   └── WorkflowRequestDto.java
│   ├── response
│   │   └── WorkflowResponseDto.java
│   ├── NodeDto.java
│   └── EdgeDto.java
│
├── domain (of model/entity)
│   └── Workflow.java
│   └── Node.java
│   └── Edge.java
│
└── repository
    └── WorkflowRepository.java

// Editor.tsx
useEffect(() => {
  const loadWorkflow = async () => {
    const response = await axios.get(`/api/workflows/${workflowId}`);
    const { name, description, metadata, nodes, edges } = response.data;

    setWorkflowData({ name, description, metadata });
    setNodes(nodes.map(node => ({ id: node.nodeId, ... })));
    setEdges(edges.map(edge => ({ id: edge.source + "-" + edge.target, ... })));
  };

  loadWorkflow();
}, [workflowId]);