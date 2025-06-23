import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApiClientTest {

    private MockWebServer mockWebServer;
    private WorkflowApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();

        client = new WorkflowApiClient(baseUrl); // jouw custom client class
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testGetWorkflowById() throws Exception {
        // Mock respons voor de server
        String json = """
        {
          "id": 1,
          "name": "Mock Workflow",
          "description": "Van de fake server",
          "metadata": {
            "category": "default"
          },
          "nodes": [],
          "edges": []
        }
        """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json"));

        // Client test uitvoeren
        WorkflowResponseDto response = client.getWorkflowById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Mock Workflow");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }
}
