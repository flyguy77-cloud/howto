@Component
public class WorkflowExecutionPathResolver {

    public ExecutionPaths resolve(
            String team,
            String subteam,
            UUID workflowExecutionId,
            LocalDate date
    ) {
        String baseDate = date.toString();

        String executionDir = String.format(
                "/shared/%s/executies/%s/%s",
                team, baseDate, workflowExecutionId
        );

        String tempDir = String.format(
                "/shared/%s/temp/%s/%s",
                team, baseDate, workflowExecutionId
        );

        String outputDir = String.format(
                "/shared/%s/outputs/%s/%s/%s",
                team, subteam, baseDate, workflowExecutionId
        );

        String executionLog = executionDir + "/executie.log";

        return new ExecutionPaths(
                executionDir,
                tempDir,
                outputDir,
                executionLog
        );
    }
}

/////////

public record ExecutionPaths(
        String executionDir,
        String tempDir,
        String outputDir,
        String executionLog
) {}


////////

@Service
@RequiredArgsConstructor
public class JobExecutionService {

    private final WorkflowExecutionPathResolver pathResolver;

    public UUID executeScriptJobInternal(
            RunScriptNodeRequestDto dto,
            UUID workflowExecId
    ) {
        UUID jobExecId = UUID.randomUUID();

        ExecutionPaths paths = pathResolver.resolve(
                dto.team(),
                dto.subteam(),
                workflowExecId,
                LocalDate.now()
        );

        Job job = buildKubernetesJob(dto, workflowExecId, jobExecId, paths);

        client.batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .resource(job)
                .create();

        return jobExecId;
    }
}



///////

private Job buildKubernetesJob(
        RunScriptNodeRequestDto dto,
        UUID workflowExecId,
        UUID jobExecId,
        ExecutionPaths paths
) {
    return new JobBuilder()
            .withNewMetadata()
                .withName("job-" + jobExecId)
            .endMetadata()
            .withNewSpec()
                .withNewTemplate()
                    .withNewSpec()
                        .addNewVolume()
                            .withName("shared-volume")
                            .withNewPersistentVolumeClaim()
                                .withClaimName("workflow-shared-pvc")
                            .endPersistentVolumeClaim()
                        .endVolume()
                        .addNewVolume()
                            .withName("scratch-volume")
                            .withNewEmptyDir()
                            .endEmptyDir()
                        .endVolume()
                        .addNewContainer()
                            .withName("runner")
                            .withImage(dto.image())
                            .withCommand("/usr/local/bin/entrypoint.sh", dto.scriptPath())
                            .addNewVolumeMount()
                                .withName("shared-volume")
                                .withMountPath("/shared")
                            .endVolumeMount()
                            .addNewVolumeMount()
                                .withName("scratch-volume")
                                .withMountPath("/work")
                            .endVolumeMount()
                            .addNewEnv().withName("WORKFLOW_EXEC_ID").withValue(workflowExecId.toString()).endEnv()
                            .addNewEnv().withName("JOB_EXEC_ID").withValue(jobExecId.toString()).endEnv()
                            .addNewEnv().withName("EXECUTION_DIR").withValue(paths.executionDir()).endEnv()
                            .addNewEnv().withName("TEMP_DIR").withValue(paths.tempDir()).endEnv()
                            .addNewEnv().withName("OUTPUT_DIR").withValue(paths.outputDir()).endEnv()
                            .addNewEnv().withName("EXECUTION_LOG").withValue(paths.executionLog()).endEnv()
                        .endContainer()
                        .withRestartPolicy("Never")
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
}

////

// entrypoint.sh
// mkdir -p "$EXECUTION_DIR"
// mkdir -p "$TEMP_DIR"
// mkdir -p "$OUTPUT_DIR"
// touch "$EXECUTION_LOG"
// echo "[entrypoint] start job ${JOB_EXEC_ID}" >> "$EXECUTION_LOG"


// /shared/{team}/executies/{date}/{workflowExecId}/workflow.log
// /shared/{team}/temp/{date}/{workflowExecId}/...
// /shared/{team}/outputs/{subteam}/{date}/{workflowExecId}/...
// /shared/{team}/executies/{date}/{workflowExecId}/jobs/{jobExecId}.log

// Voorstel
/shared/{team}/executions/{date}/{workflowExecId}/
    workflow.log
    jobs/
        {jobExecId}.log
    temp/
        {nodeId}/
            datasource-a.parquet
            datasource-b.parquet
    outputs/
        {subteam}/
            report.pdf
            report.html
            report_metadata.json


@Component
public class WorkflowExecutionPathResolver {

    public ExecutionPaths resolve(
            String team,
            String subteam,
            UUID workflowExecId,
            UUID jobExecId,
            String nodeId,
            LocalDate date
    ) {
        String base = String.format(
                "/shared/%s/executions/%s/%s",
                team,
                date,
                workflowExecId
        );

        String workflowLog = base + "/workflow.log";
        String jobLog = base + "/jobs/" + jobExecId + ".log";
        String tempRoot = base + "/temp";
        String tempDir = tempRoot + "/" + nodeId;
        String outputDir = base + "/outputs/" + subteam;

        return new ExecutionPaths(
                base,
                workflowLog,
                jobLog,
                tempRoot,
                tempDir,
                outputDir
        );
    }
}


public record ExecutionPaths(
        String workflowRoot,
        String workflowLog,
        String jobLog,
        String tempRoot,
        String tempDir,
        String outputDir
) {}


ExecutionPaths paths = pathResolver.resolve(
        dto.team(),
        dto.subteam(),
        workflowExecId,
        jobExecId,
        dto.nodeId(),
        LocalDate.now()
);


.addNewEnv().withName("WORKFLOW_ROOT").withValue(paths.workflowRoot()).endEnv()
.addNewEnv().withName("WORKFLOW_LOG").withValue(paths.workflowLog()).endEnv()
.addNewEnv().withName("JOB_LOG").withValue(paths.jobLog()).endEnv()
.addNewEnv().withName("TEMP_ROOT").withValue(paths.tempRoot()).endEnv()
.addNewEnv().withName("TEMP_DIR").withValue(paths.tempDir()).endEnv()
.addNewEnv().withName("OUTPUT_DIR").withValue(paths.outputDir()).endEnv()

ENTRYPOINT.sh
mkdir -p "$(dirname "$WORKFLOW_LOG")"
mkdir -p "$(dirname "$JOB_LOG")"
mkdir -p "$TEMP_DIR"
mkdir -p "$OUTPUT_DIR"

touch "$WORKFLOW_LOG"
touch "$JOB_LOG"

echo "[entrypoint] job started: $JOB_EXEC_ID" >> "$JOB_LOG"
echo "[entrypoint] workflow started: $WORKFLOW_EXEC_ID" >> "$WORKFLOW_LOG"


List<String> command = List.of(
    "bash",
    "-c",
    "mkdir -p $(dirname $JOB_LOG) && Rscript " + scriptPath + " 2>&1 | tee -a $JOB_LOG"
);

<runtime> <script> 2>&1 | tee -a "$JOB_LOG"
