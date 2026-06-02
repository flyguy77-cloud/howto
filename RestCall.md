1. Docker image: bare minimum REST runner

FROM alpine:3.20
RUN apk add --no-cache \
    bash \
    curl \
    jq \
    ca-certificates
WORKDIR /work
CMD ["bash"]

Build bijvoorbeeld:

docker build -t harbor.local/workflow/rest-runner:0.1 .

⸻

2. REST node configuratie in backend

Enum uitbreiden

```java
public enum NodeType {
    LOAD_SCRIPT,
    RUN_SCRIPT,
    REST_CALL,
    GENERATE_REPORT,
    END
}
```


DTO/config voor REST node
```java
public record RestNodeConfig(
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParams,
        String body,
        String outputFileName,
        boolean includeAuthToken
) {}
```


Voorbeeld JSON in node parameters:
```json
{
  "method": "GET",
  "url": "https://api.example.nl/data/customers",
  "headers": {
    "Accept": "application/json"
  },
  "queryParams": {
    "year": "2026"
  },
  "outputFileName": "customers.json",
  "includeAuthToken": true
}
```
⸻

3. CommandBuilder voor REST node

Maak een aparte builder, niet in JobExecutionService proppen.
```java
@Component
public class RestCommandBuilder {
    public String build(RestNodeConfig config) {
        String outputFile = "${TEMP_DIR}/" + safeFileName(config.outputFileName());
        StringBuilder cmd = new StringBuilder();
        cmd.append("""
            set -euo pipefail
            mkdir -p "$TEMP_DIR"
            mkdir -p "$(dirname "$JOB_LOG")"
            touch "$JOB_LOG"
            RESPONSE_FILE="%s"
            STATUS_FILE="${TEMP_DIR}/http_status.txt"
            curl -sS -L \\
              -X %s \\
            """.formatted(outputFile, config.method().toUpperCase()));
        if (config.includeAuthToken()) {
            cmd.append("""
              -H "Authorization: Bearer $USER_TOKEN" \\
            """);
        }
        if (config.headers() != null) {
            config.headers().forEach((k, v) ->
                    cmd.append("  -H ")
                       .append(shellQuote(k + ": " + v))
                       .append(" \\\n")
            );
        }
        if (config.body() != null && !config.body().isBlank()) {
            cmd.append("  -H 'Content-Type: application/json' \\\n");
            cmd.append("  --data ").append(shellQuote(config.body())).append(" \\\n");
        }
        String url = appendQueryParams(config.url(), config.queryParams());
        cmd.append("""
              -w "%%{http_code}" \\
              -o "$RESPONSE_FILE" \\
              %s > "$STATUS_FILE" 2>> "$JOB_LOG"
            STATUS=$(cat "$STATUS_FILE")
            echo "[REST] HTTP status: $STATUS" | tee -a "$JOB_LOG"
            echo "[REST] Response written to: $RESPONSE_FILE" | tee -a "$JOB_LOG"
            if [ "$STATUS" -lt 200 ] || [ "$STATUS" -ge 300 ]; then
              echo "[REST] Request failed with status $STATUS" | tee -a "$JOB_LOG"
              exit 1
            fi
            """.formatted(shellQuote(url)));
        return cmd.toString();
    }
    private String appendQueryParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return url + (url.contains("?") ? "&" : "?") + query;
    }
    private String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "response.json";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
```
⸻

4. WorkflowNodeExecutor uitbreiden
```java
@Service
@RequiredArgsConstructor
public class WorkflowNodeExecutor {
    private final JobExecutionService jobExecutionService;
    private final JobCompletionAwaiter awaiter;
    private final ObjectMapper objectMapper;
    public void execute(NodeEntity node, GraphModel graph, UUID workflowExecId) {
        switch (node.getType()) {
            case RUN_SCRIPT -> {
                UUID jobExecId = jobExecutionService.executeRunScript(node, graph, workflowExecId);
                awaiter.await(jobExecId);
            }
            case REST_CALL -> {
                RestNodeConfig config = objectMapper.convertValue(
                        node.getParameters(),
                        RestNodeConfig.class
                );
                UUID jobExecId = jobExecutionService.executeRestCall(
                        node,
                        graph,
                        workflowExecId,
                        config
                );
                awaiter.await(jobExecId);
            }
            case LOAD_SCRIPT -> {
                // no-op
            }
            case END -> {
                // no-op
            }
            default -> throw new IllegalStateException("Unsupported node type: " + node.getType());
        }
    }
}
```
⸻

5. JobExecutionService REST-methode
```java
@Service
@RequiredArgsConstructor
public class JobExecutionService {
    private final RestCommandBuilder restCommandBuilder;
    private final WorkflowExecutionPathResolver pathResolver;
    private final KubernetesClient client;
    public UUID executeRestCall(
            NodeEntity node,
            GraphModel graph,
            UUID workflowExecId,
            RestNodeConfig config
    ) {
        UUID jobExecId = UUID.randomUUID();
        ExecutionPaths paths = pathResolver.resolve(
                node.getTeam(),
                node.getSubteam(),
                workflowExecId,
                jobExecId,
                node.getNodeId(),
                LocalDate.now()
        );
        String command = restCommandBuilder.build(config);
        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName("rest-" + jobExecId)
                .endMetadata()
                .withNewSpec()
                    .withNewTemplate()
                        .withNewSpec()
                            .withServiceAccountName("workflow-executor")
                            .addNewVolume()
                                .withName("shared-volume")
                                .withNewPersistentVolumeClaim()
                                    .withClaimName("isilon-rwx-pvc")
                                .endPersistentVolumeClaim()
                            .endVolume()
                            .addNewContainer()
                                .withName("rest-runner")
                                .withImage("harbor.local/workflow/rest-runner:0.1")
                                .withCommand("bash", "-c")
                                .withArgs(command)
                                .addNewEnv().withName("WORKFLOW_EXEC_ID").withValue(workflowExecId.toString()).endEnv()
                                .addNewEnv().withName("JOB_EXEC_ID").withValue(jobExecId.toString()).endEnv()
                                .addNewEnv().withName("NODE_ID").withValue(node.getNodeId()).endEnv()
                                .addNewEnv().withName("TEMP_ROOT").withValue(paths.tempRoot()).endEnv()
                                .addNewEnv().withName("TEMP_DIR").withValue(paths.tempDir()).endEnv()
                                .addNewEnv().withName("OUTPUT_DIR").withValue(paths.outputDir()).endEnv()
                                .addNewEnv().withName("JOB_LOG").withValue(paths.jobLog()).endEnv()
                                .addNewEnv().withName("PREDECESSOR_NODE_IDS")
                                    .withValue(String.join(",", graph.predecessors(node.getNodeId())))
                                .endEnv()
                                .addNewVolumeMount()
                                    .withName("shared-volume")
                                    .withMountPath("/shared")
                                .endVolumeMount()
                            .endContainer()
                            .withRestartPolicy("Never")
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        client.batch().v1().jobs()
                .inNamespace("workflow-ns")
                .resource(job)
                .create();
        return jobExecId;
    }
}
```
⸻

6. Output op volume

REST-node schrijft dan bijvoorbeeld:
```text
/shared/team/executions/2026-05-19/{workflowExecId}/temp/restNode1/customers.json
/shared/team/executions/2026-05-19/{workflowExecId}/jobs/{jobExecId}.log
```
Een latere R/Python node krijgt via:

INPUT_DIRS=/shared/.../temp/restNode1,/shared/.../temp/restNode2

of leest via:

TEMP_ROOT/{predecessorNodeId}

⸻

7. Frontend node velden

Voor de UI zou ik beginnen met deze velden:

Basis

* Naam
* Method
    * GET
    * POST
    * PUT
    * PATCH
    * DELETE
* URL
* Output file name
    * default: response.json

Request

* Headers
    * key/value editor
* Query parameters
    * key/value editor
* Body
    * JSON editor, alleen tonen bij POST/PUT/PATCH

Auth

* Use workflow owner token
    * boolean
* eventueel later:
    * auth mode:
        * none
        * bearer user token
        * api key
        * basic — liever niet voor MVP

Advanced

* timeout seconds
* follow redirects
* expected status codes
    * default: 200-299

⸻

8. Voorbeeld React model
```javascript
export type RestNodeData = {
  label: string;
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  url: string;
  headers: Record<string, string>;
  queryParams: Record<string, string>;
  body?: string;
  outputFileName: string;
  includeAuthToken: boolean;
  timeoutSeconds?: number;
};
```

# FROM HERE

### Ontwerp
```text
Repository ophalen       -> WorkflowRunner
Node type dispatch       -> WorkflowNodeExecutor
REST config mapping      -> WorkflowNodeExecutor
K8s Job bouwen/starten   -> JobExecutionService
```

### Request DTO vanuit frontend / Swagger

#### node parameters
```java
public record RestNodeParameters(
        String endpointPath,
        List<String> kenmerken,
        Long timeFrom,
        Long timeTo
) {}
```

### RunNode:
```java
public void runNode(UUID workflowId, UUID workflowExecId, String nodeId) {

    NodeEntity node = nodeRepo.findByNodeId(nodeId)
            .orElseThrow(() -> new IllegalStateException("Node not found: " + nodeId));

    WorkflowEntity wf = workflowRepo.findById(workflowId)
            .orElseThrow();

    GraphModel graph = graphBuilder.build(wf);

    nodeExecutor.execute(node, graph, workflowExecId);

    List<String> next = graph.successors(nodeId);

    if (next.isEmpty()) {
        markWorkflowSucceeded(workflowExecId);
        return;
    }

    BackgroundJob.enqueue(() ->
            runNode(workflowId, workflowExecId, next.get(0))
    );
}
```

### WorkflowNodeExecutor
```java
case REST_CALL -> {
    RestNodeConfigDto config = objectMapper.convertValue(
            node.getParameters(),
            RestNodeConfigDto.class
    );

    UUID jobExecId = jobExecutionService.executeRestCall(
            node,
            graph,
            workflowExecId,
            config
    );

    awaiter.await(jobExecId);
}
```

### request-body DTO
```java
public record RestCallBodyDto(
        List<String> kenmerken,
        Long timeFrom,
        Long timeTo
) {}
```

### RestCommandBuilder
```java
public String build(RestNodeConfigDto config) {
    RestCallBodyDto body = new RestCallBodyDto(
            config.kenmerken(),
            config.timeFrom(),
            config.timeTo()
    );

    String bodyJson = objectMapper.writeValueAsString(body);

    return """
        set -euo pipefail

        mkdir -p "$TEMP_DIR"
        mkdir -p "$(dirname "$JOB_LOG")"
        touch "$JOB_LOG"

        curl -sS -L --fail-with-body \\
          -X %s \\
          -H "Content-Type: application/json" \\
          -H "Accept: application/json" \\
          %s \\
          --data %s \\
          -o "$TEMP_DIR/%s" \\
          "$REST_BASE_URL%s" \\
          2>&1 | tee -a "$JOB_LOG"
        """.formatted(
            config.method(),
            config.useBearerToken() ? "-H \"Authorization: Bearer $USER_TOKEN\"" : "",
            shellQuote(bodyJson),
            safeFileName(config.outputFileName()),
            config.endpointPath()
        );
}
```

#### NodeExecutor
```java
case REST_CALL -> {
    RestNodeConfigDto config = objectMapper.convertValue(
            node.getParameters(),
            RestNodeConfigDto.class
    );

    UUID jobExecId = jobExecutionService.executeRestCall(
            node,
            graph,
            workflowExecId,
            config
    );

    awaiter.await(jobExecId);
}
```

#### Flow
```text
WorkflowRunner
  -> nodeRepo.findByNodeId(nodeId)
  -> WorkflowNodeExecutor.execute(node, graph, workflowExecId)
  -> executor leest node.parameters
  -> RestNodeConfigDto
  -> JobExecutionService bouwt K8s Job
```
