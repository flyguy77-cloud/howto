import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    @Test
    void testGetWorkflowById_returnsCorrectWorkflowResponseDto() {
        // Arrange
        WorkflowMetadata metadata = new WorkflowMetadata("analytics", List.of("default"));

        Node node = new Node();
        node.setNodeId("n1");
        node.setType("typeA");
        node.setLabel("Label");
        node.setPositionX(10.0);
        node.setPositionY(20.0);
        node.setData(null);

        Edge edge = new Edge();
        edge.setEdgeId("e1");
        edge.setSource("n1");
        edge.setTarget("n2");
        edge.setLabel("default");

        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Mijn workflow");
        workflow.setDescription("Beschrijving");
        workflow.setMetadata(metadata);
        workflow.setNodes(List.of(node));
        workflow.setEdges(List.of(edge));

        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        // Act
        WorkflowResponseDto responseDto = workflowService.getWorkflowById(1L);

        // Assert
        assertEquals(1L, responseDto.id());
        assertEquals("Mijn workflow", responseDto.name());
        assertEquals("Beschrijving", responseDto.description());
        assertEquals("analytics", responseDto.metadata().category());
        assertEquals(List.of("default"), responseDto.metadata().tags());

        assertEquals(1, responseDto.nodes().size());
        assertEquals("n1", responseDto.nodes().get(0).nodeId());
        assertEquals("typeA", responseDto.nodes().get(0).type());

        assertEquals(1, responseDto.edges().size());
        assertEquals("e1", responseDto.edges().get(0).edgeId());
        assertEquals("n1", responseDto.edges().get(0).source());

        verify(workflowRepository, times(1)).findById(1L);
    }

    @Test
    void testGetWorkflowById_whenNotFound_thenThrowsException() {
        // Arrange
        when(workflowRepository.findById(2L)).thenReturn(Optional.empty());

        // Act + Assert
        EntityNotFoundException thrown = assertThrows(EntityNotFoundException.class, () ->
                workflowService.getWorkflowById(2L)
        );

        assertEquals("Workflow niet gevonden", thrown.getMessage());
    }
}
