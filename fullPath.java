1. Datamodel

@Entity
@Table(name = "job_execution")
public class JobExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String jobName;

    @Enumerated(EnumType.STRING)
    private JobExecutionType jobType = JobExecutionType.SCRIPT;

    @Enumerated(EnumType.STRING)
    private JobExecutionStatus status;

    private String message;

    private Instant startedAt;
    private Instant finishedAt;
}

@Entity
@Table(name = "job_execution_log")
public class JobExecutionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_execution_id", nullable = false)
    private JobExecutionEntity jobExecution;

    private Instant timestamp;

    @Column(length = 4000)
    private String message;
}


⸻

2. Repositories

public interface JobExecutionRepository extends JpaRepository<JobExecutionEntity, UUID> {}

public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLogEntity, Long> {
    List<JobExecutionLogEntity> findByJobExecutionIdOrderByTimestampAsc(UUID jobExecutionId);
}


⸻

3. DTO’s

Request DTO (voor uitvoeren)

public record ExecuteJobRequestDto(
    String jobName,
    String scriptContent
) {}

Response DTO (voor status)

public record JobStatusResponseDto(
    UUID id,
    String jobName,
    JobExecutionType jobType,
    JobExecutionStatus status,
    String message,
    Instant startedAt,
    Instant finishedAt
) {}

Response DTO (voor logs)

public record JobLogResponseDto(
    Instant timestamp,
    String message
) {}


⸻

4. Service-interface

public interface JobExecutionService {

    JobStatusResponseDto execute(ExecuteJobRequestDto request);

    JobStatusResponseDto getStatus(UUID jobId);

    List<JobLogResponseDto> getLogs(UUID jobId);
}


⸻

5. Service-implementatie

@Service
@RequiredArgsConstructor
public class JobExecutionServiceImpl implements JobExecutionService {

    private final KubernetesClient client;
    private final JobExecutionRepository jobRepo;
    private final JobMonitor jobMonitor;

    @Value("${kubernetes.client.namespace}")
    private String namespace;

    @Override
    public JobStatusResponseDto execute(ExecuteJobRequestDto request) {
        // Maak record in DB
        var job = new JobExecutionEntity();
        job.setJobName(request.jobName());
        job.setJobType(JobExecutionType.SCRIPT);
        job.setStatus(JobExecutionStatus.PENDING);
        job.setStartedAt(Instant.now());
        jobRepo.save(job);

        String jobName = "job-" + job.getId();

        // Bouw & start Kubernetes Job
        var k8sJob = new JobBuilder()
            .withNewMetadata().withName(jobName).endMetadata()
            .withNewSpec()
                .withNewTemplate()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("runner")
                            .withImage("python:3.11")
                            .withCommand("bash", "-c", request.scriptContent())
                        .endContainer()
                        .withRestartPolicy("Never")
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        client.batch().v1().jobs().inNamespace(namespace).create(k8sJob);

        // Start watchers via monitor (asynchroon door Fabric8)
        jobMonitor.monitor(namespace, jobName, job.getId());

        return new JobStatusResponseDto(
            job.getId(),
            job.getJobName(),
            job.getJobType(),
            job.getStatus(),
            job.getMessage(),
            job.getStartedAt(),
            job.getFinishedAt()
        );
    }

    @Override
    public JobStatusResponseDto getStatus(UUID jobId) {
        return jobRepo.findById(jobId)
                .map(j -> new JobStatusResponseDto(
                        j.getId(), j.getJobName(), j.getJobType(),
                        j.getStatus(), j.getMessage(),
                        j.getStartedAt(), j.getFinishedAt()))
                .orElseThrow(() -> new EntityNotFoundException("Job not found"));
    }

    @Override
    public List<JobLogResponseDto> getLogs(UUID jobId) {
        // eenvoudig append-only ophalen
        return jobRepo.findById(jobId)
                .map(j -> j.getLogs().stream()
                    .map(l -> new JobLogResponseDto(l.getTimestamp(), l.getMessage()))
                    .toList())
                .orElse(List.of());
    }
}

⸻

6. Controller

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobExecutionController {

    private final JobExecutionService jobExecutionService;

    @PostMapping("/execute")
    public ResponseEntity<JobStatusResponseDto> executeJob(@RequestBody ExecuteJobRequestDto request) {
        var response = jobExecutionService.execute(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<JobStatusResponseDto> getStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobExecutionService.getStatus(jobId));
    }

    @GetMapping("/{jobId}/logs")
    public ResponseEntity<List<JobLogResponseDto>> getLogs(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobExecutionService.getLogs(jobId));
    }
}

⸻
1. JobStatusWatcher

@Component
@RequiredArgsConstructor
public class JobStatusWatcher {

    private final KubernetesClient client;
    private final JobStatusService statusService;

    public Watch start(String namespace, String jobName, UUID execId) {
        return client.batch().v1().jobs()
            .inNamespace(namespace)
            .withName(jobName)
            .watch(new Watcher<Job>() {
                @Override
                public void eventReceived(Action action, Job job) {
                    if (job == null || job.getStatus() == null) return;

                    var jobStatus = job.getStatus();
                    var conditions = jobStatus.getConditions();
                    var mapped = JobExecutionStatus.RUNNING;
                    String msg = null;

                    if (jobStatus.getSucceeded() != null && jobStatus.getSucceeded() > 0)
                        mapped = JobExecutionStatus.SUCCEEDED;
                    else if (jobStatus.getFailed() != null && jobStatus.getFailed() > 0)
                        mapped = JobExecutionStatus.FAILED;

                    if (conditions != null && !conditions.isEmpty()) {
                        var last = conditions.get(conditions.size() - 1);
                        msg = last.getMessage();
                        switch (last.getType()) {
                            case "Failed" -> mapped = JobExecutionStatus.FAILED;
                            case "Complete" -> mapped = JobExecutionStatus.SUCCEEDED;
                        }
                    }

                    statusService.updateStatus(execId, mapped, msg);
                }

                @Override
                public void onClose(WatcherException cause) {
                    if (cause != null) {
                        statusService.updateStatus(execId, JobExecutionStatus.FAILED,
                                "Watcher closed: " + cause.getMessage());
                    }
                }
            });
    }
}

⸻
2. PodLogStreamer

@Component
@RequiredArgsConstructor
public class PodLogStreamer {

    private final KubernetesClient client;
    private final JobLogService logService;

    public void start(String namespace, String jobName, UUID execId) {
        var pods = client.pods()
                .inNamespace(namespace)
                .withLabel("job-name", jobName)
                .list()
                .getItems();

        if (pods == null || pods.isEmpty()) {
            logService.appendLog(execId, "[WARN] No pods found for job " + jobName);
            return;
        }

        var podName = pods.get(0).getMetadata().getName();

        try (LogWatch lw = client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .watchLog();
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(lw.getOutput(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                logService.appendLog(execId, line);
            }

        } catch (IOException e) {
            logService.appendLog(execId, "[ERROR] Log streaming failed: " + e.getMessage());
        }
    }
}

⸻
3. JobMonitor
// coordinator zonder java threads
@Component
@RequiredArgsConstructor
public class JobMonitor {

    private final JobStatusWatcher statusWatcher;
    private final PodLogStreamer logStreamer;

    public void monitor(String namespace, String jobName, UUID execId) {
        try (Watch watch = statusWatcher.start(namespace, jobName, execId)) {
            logStreamer.start(namespace, jobName, execId);
        } catch (Exception e) {
            // log error, maar watchers worden netjes gesloten
        }
    }
}

⸻


src/
 └── main/
      ├── java/
      │    └── net/test/workflow/
      │         ├── controller/
      │         │    └── JobExecutionController.java
      │         │
      │         ├── dto/
      │         │    ├── request/
      │         │    │    └── ExecuteJobRequestDto.java
      │         │    └── response/
      │         │         ├── JobStatusResponseDto.java
      │         │         └── JobLogResponseDto.java
      │         │
      │         ├── entity/
      │         │    ├── JobExecutionEntity.java
      │         │    └── JobExecutionLogEntity.java
      │         │
      │         ├── enums/
      │         │    ├── JobExecutionStatus.java
      │         │    └── JobExecutionType.java
      │         │
      │         ├── repository/
      │         │    ├── JobExecutionRepository.java
      │         │    └── JobExecutionLogRepository.java
      │         │
      │         ├── service/
      │         │    ├── JobExecutionService.java
      │         │    ├── JobExecutionServiceImpl.java
      │         │    ├── JobStatusService.java
      │         │    ├── JobLogService.java
      │         │    └── impl/
      │         │         ├── JobStatusServiceImpl.java
      │         │         └── JobLogServiceImpl.java
      │         │
      │         ├── kubernetes/
      │         │    ├── watcher/
      │         │    │    ├── JobStatusWatcher.java
      │         │    │    └── PodLogStreamer.java
      │         │    └── monitor/
      │         │         └── JobMonitor.java
      │         │
      │         ├── config/
      │         │    ├── GitLabConfig.java
      │         │    └── KubernetesConfig.java
      │         │
      │         └── util/
      │              └── JobMapper.java
      │
      └── resources/
           ├── application.yml
           └── db/migration/
                ├── V10__create_job_execution_tables.sql
                ├── V11__add_job_type_column.sql
                └── V12__seed_data.sql (optioneel)
