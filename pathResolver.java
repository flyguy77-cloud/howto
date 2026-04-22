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

=========================



Ja — dit is eigenlijk de mooiste “keep it simple” variant, en eerlijk: voor jouw use-case is dit waarschijnlijk precies goed.

Je gaat dan naar:

Graph bepaalt volgorde
Directory-structuur bepaalt dataflow
Geen manifests, geen extra tooling, geen dependencies

En je platform blijft super licht.

⸻

🔥 Het concept in 1 zin

Elke node schrijft naar:

TEMP_ROOT/{nodeId}/...

En een volgende node leest simpelweg:

TEMP_ROOT/{predecessorNodeId}/*

Klaar.

⸻

🧠 Wat verandert er architectonisch?

❌ Wat je NIET meer nodig hebt

* manifest.json
* write_manifest.py
* artifact detectie
* extra tooling in images

✅ Wat je WEL nodig hebt

* consistente directory conventie
* graph → predecessors bepalen
* backend → juiste env vars meegeven

⸻

📁 Definitieve directory structuur

/shared/{team}/executions/{date}/{workflowExecId}/
    temp/
        node1/
            customers.csv
        node2/
            orders.csv
        node3/
            revenue.csv
    outputs/
        {subteam}/
            report.pdf

⸻

🧩 Stap 1 — PathResolver aanpassen

Je hoeft alleen temp-root + node-specifieke dir te hebben.

public record ExecutionPaths(
        String workflowRoot,
        String jobLog,
        String tempRoot,
        String tempDir,
        String outputDir
) {}
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
        String workflowRoot = String.format(
                "/shared/%s/executions/%s/%s",
                team,
                date,
                workflowExecId
        );
        String tempRoot = workflowRoot + "/temp";
        String tempDir = tempRoot + "/" + nodeId;
        String jobLog = workflowRoot + "/jobs/" + jobExecId + ".log";
        String outputDir = workflowRoot + "/outputs/" + subteam;
        return new ExecutionPaths(
                workflowRoot,
                jobLog,
                tempRoot,
                tempDir,
                outputDir
        );
    }
}

⸻

⚙️ Stap 2 — CommandBuilder (simpel!)

Geen manifest, geen detectie, alleen:

* mkdirs
* script run
* logging

@Component
public class ScriptCommandBuilder {
    public String build(String interpreter, String scriptPath) {
        return """
            set -euo pipefail
            mkdir -p "$TEMP_DIR"
            mkdir -p "$OUTPUT_DIR"
            mkdir -p "$(dirname "$JOB_LOG")"
            touch "$JOB_LOG"
            %s %s 2>&1 | tee -a "$JOB_LOG"
            EXIT_CODE=${PIPESTATUS[0]}
            exit $EXIT_CODE
            """.formatted(interpreter, shellEscape(scriptPath));
    }
    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}

⸻

🧠 Stap 3 — Graph → predecessors gebruiken

Je had al:

graph.predecessors(nodeId)

Die gebruik je nu om env vars te vullen.

⸻

🚀 Stap 4 — JobExecutionService aanpassen

Belangrijk: je geeft nu alleen directories door.

String predecessorNodeIds = String.join(",", graph.predecessors(nodeId));
.addNewEnv().withName("TEMP_ROOT").withValue(paths.tempRoot()).endEnv()
.addNewEnv().withName("TEMP_DIR").withValue(paths.tempDir()).endEnv()
.addNewEnv().withName("OUTPUT_DIR").withValue(paths.outputDir()).endEnv()
.addNewEnv().withName("JOB_LOG").withValue(paths.jobLog()).endEnv()
.addNewEnv().withName("PREDECESSOR_NODE_IDS").withValue(predecessorNodeIds).endEnv()

⸻

🧠 Stap 5 — Consumer node (node4)

Nu komt de magie: geen manifest nodig.

Node4 weet:

PREDECESSOR_NODE_IDS=node1,node2,node3
TEMP_ROOT=/shared/.../temp

Dus in script:

Python voorbeeld

import os
from pathlib import Path
temp_root = Path(os.environ["TEMP_ROOT"])
preds = os.environ["PREDECESSOR_NODE_IDS"].split(",")
input_files = []
for node_id in preds:
    node_dir = temp_root / node_id
    if node_dir.exists():
        input_files.extend(node_dir.glob("*"))
print("Input files:", input_files)

⸻

R voorbeeld

temp_root <- Sys.getenv("TEMP_ROOT")
preds <- strsplit(Sys.getenv("PREDECESSOR_NODE_IDS"), ",")[[1]]
input_files <- c()
for (node in preds) {
  node_dir <- file.path(temp_root, node)
  if (dir.exists(node_dir)) {
    files <- list.files(node_dir, full.names = TRUE)
    input_files <- c(input_files, files)
  }
}
print(input_files)

⸻

💡 Bonus: helper env var (nog makkelijker)

Je kunt het nog makkelijker maken:

String inputDirs = graph.predecessors(nodeId).stream()
        .map(pred -> paths.tempRoot() + "/" + pred)
        .collect(Collectors.joining(","));

Dan geef je mee:

.addNewEnv().withName("INPUT_DIRS").withValue(inputDirs).endEnv()

Dan hoeft script alleen:

dirs = os.environ["INPUT_DIRS"].split(",")

⸻

🧠 Waarom dit echt goed is

✔ Geen platform-lekkage

Scripts hoeven alleen:

* naar TEMP_DIR schrijven
* of uit INPUT_DIRS lezen

✔ Geen extra tooling

* geen Python helper nodig
* geen JSON parsing
* geen manifests

✔ Supersimpel debuggen

Je kunt letterlijk kijken:

ls /shared/.../temp/node1

✔ Perfect voor jouw use-case

CSV → R → PDF = werkt perfect

⸻

⚠️ Wanneer dit stuk kan gaan

Wees eerlijk — dit model heeft grenzen:

1. Als nodes veel verschillende outputs maken

→ geen onderscheid zonder conventie

2. Als je filtering wilt

→ bv alleen .csv vs .parquet

3. Parallel execution + naming collisions

→ los je op met nodeId dir (heb je al 👍)

⸻

🔥 Mijn eerlijke oordeel

Voor jouw platform:

dit is de beste MVP-architectuur

Waarom:

* simpel
* robuust
* geen over-engineering
* geen extra dependencies
* sluit perfect aan op jouw Kubernetes + RWX setup

⸻

🎯 Samenvatting

Wat je doet

* Node schrijft naar:

TEMP_ROOT/{nodeId}/

* Backend geeft:

PREDECESSOR_NODE_IDS
TEMP_ROOT

* Consumer node leest:

TEMP_ROOT/{predecessor}/*

Wat je niet meer nodig hebt

* manifests
* artifact detection
* helper scripts
* extra runtimes
