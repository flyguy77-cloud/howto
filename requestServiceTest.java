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
    void testSaveWorkflow_successfullySavesAndReturnsResponse() {
        // Arrange
        WorkflowMetadata metadata = new WorkflowMetadata("analytics", List.of("default"));

        NodeDto nodeDto = new NodeDto("n1", "typeA", "Label", 10.0, 20.0, null);
        EdgeDto edgeDto = new EdgeDto("e1", "n1", "n2", "default");

        WorkflowRequestDto requestDto = new WorkflowRequestDto(
                "Mijn workflow",
                "Een beschrijving",
                metadata,
                List.of(nodeDto),
                List.of(edgeDto)
        );

        Workflow savedWorkflow = new Workflow();
        savedWorkflow.setId(42L);
        savedWorkflow.setName(requestDto.name());
        savedWorkflow.setDescription(requestDto.description());
        savedWorkflow.setMetadata(metadata);
        savedWorkflow.setNodes(List.of());
        savedWorkflow.setEdges(List.of());

        when(workflowRepository.save(any())).thenReturn(savedWorkflow);

        // Act
        WorkflowResponseDto response = workflowService.saveWorkflow(requestDto);

        // Assert
        assertEquals(42L, response.id());
        assertEquals("Mijn workflow", response.name());
        assertEquals("Een beschrijving", response.description());
        assertEquals("analytics", response.metadata().category());
        assertEquals(List.of("default"), response.metadata().tags());

        verify(workflowRepository, times(1)).save(any(Workflow.class));
    }

    @Test
    void testGetWorkflowById_whenNotFound_thenThrowsException() {
        // Arrange
        Long id = 999L;
        when(workflowRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException thrown = assertThrows(EntityNotFoundException.class, () -> {
            workflowService.getWorkflowById(id);
        });

        assertEquals("Workflow niet gevonden", thrown.getMessage());
    }
}
