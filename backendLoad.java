// WorkflowServiceImpl
public WorkflowResponseDto getWorkflowById(Long id) {
    Workflow wf = repo.findById(id).orElseThrow();

    List<NodeDto> nodes = wf.getNodes().stream()
        .map(NodeDto::fromEntity)
        .toList();

    List<EdgeDto> edges = wf.getEdges().stream()
        .map(EdgeDto::fromEntity)
        .toList();

    return new WorkflowResponseDto(
        wf.getId(),
        wf.getName(),
        wf.getDescription(),
        wf.getMetadata(),
        nodes,
        edges
    );
}

// NodeDto

public record NodeDto(
    String nodeId,
    String type,
    String label,
    double positionX,
    double positionY,
    NodeData data
) {
    public static NodeDto fromEntity(Node node) {
        return new NodeDto(
            node.getNodeId(),
            node.getType(),
            node.getLabel(),
            node.getPositionX(),
            node.getPositionY(),
            node.getData()
        );
    }
}