import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowService workflowService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetWorkflowById_ReturnsWorkflowResponseDto() throws Exception {
        // Arrange
        WorkflowMetadata metadata = new WorkflowMetadata("analytics", List.of("tag1"));

        NodeDto nodeDto = new NodeDto("node1", "typeA", "label", 100.0, 200.0, null);
        EdgeDto edgeDto = new EdgeDto("edge1", "node1", "node2", "default");

        WorkflowResponseDto mockResponse = new WorkflowResponseDto(
                1L,
                "Test Workflow",
                "Test Description",
                metadata,
                List.of(nodeDto),
                List.of(edgeDto)
        );

        Mockito.when(workflowService.getWorkflowById(anyLong()))
                .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/api/workflows/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test Workflow"))
                .andExpect(jsonPath("$.metadata.category").value("analytics"))
                .andExpect(jsonPath("$.nodes[0].nodeId").value("node1"))
                .andExpect(jsonPath("$.edges[0].edgeId").value("edge1"));
    }
}
