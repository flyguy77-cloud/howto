1. Datamodel (JPA)

We houden de gesplitste structuur met job_type aan.

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
    private final JobExecutionLogRepository logRepo;
    private final JobStatusService statusService;
    private final JobLogService logService;

    @Value("${kubernetes.client.namespace}")
    private String namespace;

    @Override
    public JobStatusResponseDto execute(ExecuteJobRequestDto request) {
        JobExecutionEntity job = new JobExecutionEntity();
        job.setJobName(request.jobName());
        job.setJobType(JobExecutionType.SCRIPT);
        job.setStatus(JobExecutionStatus.PENDING);
        job.setStartedAt(Instant.now());
        jobRepo.save(job);

        // Bouw en start de Kubernetes Job
        String jobName = "job-" + job.getId();
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

        // Start watchers
        var statusWatcher = new JobStatusWatcher(client, namespace, jobName, job.getId(), statusService);
        var logStreamer = new PodLogStreamer(client, namespace, jobName, job.getId(), logService);
        var monitor = new JobMonitor(statusWatcher, logStreamer);
        monitor.start();

        return mapToDto(job);
    }

    @Override
    public JobStatusResponseDto getStatus(UUID jobId) {
        return jobRepo.findById(jobId)
                .map(this::mapToDto)
                .orElseThrow(() -> new EntityNotFoundException("Job not found"));
    }

    @Override
    public List<JobLogResponseDto> getLogs(UUID jobId) {
        return logRepo.findByJobExecutionIdOrderByTimestampAsc(jobId)
                .stream()
                .map(l -> new JobLogResponseDto(l.getTimestamp(), l.getMessage()))
                .toList();
    }

    private JobStatusResponseDto mapToDto(JobExecutionEntity e) {
        return new JobStatusResponseDto(
            e.getId(),
            e.getJobName(),
            e.getJobType(),
            e.getStatus(),
            e.getMessage(),
            e.getStartedAt(),
            e.getFinishedAt()
        );
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

public final class JobStatusWatcher implements AutoCloseable {

    private final KubernetesClient client;
    private final String namespace;
    private final String jobName;
    private final UUID executionId;
    private final JobStatusService statusService;
    private Watch watch;

    public JobStatusWatcher(KubernetesClient client,
                            String namespace,
                            String jobName,
                            UUID executionId,
                            JobStatusService statusService) {
        this.client = client;
        this.namespace = namespace;
        this.jobName = jobName;
        this.executionId = executionId;
        this.statusService = statusService;
    }

    public void start() {
        this.watch = client.batch().v1().jobs()
            .inNamespace(namespace)
            .withName(jobName)
            .watch(new Watcher<Job>() {
                @Override
                public void eventReceived(Action action, Job job) {
                    if (job.getStatus() == null) return;

                    var status = job.getStatus();
                    var conditions = status.getConditions();

                    JobExecutionStatus mapped = JobExecutionStatus.RUNNING;
                    String msg = null;

                    if (status.getSucceeded() != null && status.getSucceeded() > 0)
                        mapped = JobExecutionStatus.SUCCEEDED;
                    else if (status.getFailed() != null && status.getFailed() > 0)
                        mapped = JobExecutionStatus.FAILED;

                    if (conditions != null && !conditions.isEmpty()) {
                        var last = conditions.get(conditions.size() - 1);
                        msg = last.getMessage();
                        switch (last.getType()) {
                            case "Complete" -> mapped = JobExecutionStatus.SUCCEEDED;
                            case "Failed" -> mapped = JobExecutionStatus.FAILED;
                        }
                    }

                    statusService.updateStatus(executionId, mapped, msg);
                }

                @Override
                public void onClose(WatcherException cause) {
                    if (cause != null) {
                        statusService.updateStatus(executionId, JobExecutionStatus.FAILED,
                                "Watcher closed: " + cause.getMessage());
                    }
                }
            });
    }

    @Override
    public void close() {
        if (watch != null) watch.close();
    }
}

⸻
2. PodLogStreamer

public final class PodLogStreamer implements AutoCloseable {

    private final KubernetesClient client;
    private final String namespace;
    private final String jobName;
    private final UUID executionId;
    private final JobLogService logService;
    private volatile boolean running = false;

    public PodLogStreamer(KubernetesClient client,
                          String namespace,
                          String jobName,
                          UUID executionId,
                          JobLogService logService) {
        this.client = client;
        this.namespace = namespace;
        this.jobName = jobName;
        this.executionId = executionId;
        this.logService = logService;
    }

    public void start() {
        running = true;
        // vind de pod met label "job-name"
        var pods = client.pods()
                .inNamespace(namespace)
                .withLabel("job-name", jobName)
                .list()
                .getItems();

        if (pods == null || pods.isEmpty()) return;

        String podName = pods.get(0).getMetadata().getName();

        // Start de logstream
        try (LogWatch logWatch = client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .watchLog();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(logWatch.getOutput(), StandardCharsets.UTF_8))) {

            String line;
            while (running && (line = reader.readLine()) != null) {
                logService.appendLog(executionId, line);
            }

        } catch (IOException e) {
            logService.appendLog(executionId, "[ERROR] Log stream error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
    }
}

⸻
3. JobMonitor

public final class JobMonitor implements AutoCloseable {

    private final JobStatusWatcher statusWatcher;
    private final PodLogStreamer logStreamer;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public JobMonitor(JobStatusWatcher statusWatcher, PodLogStreamer logStreamer) {
        this.statusWatcher = statusWatcher;
        this.logStreamer = logStreamer;
    }

    public void start() {
        // Start beide watchers asynchroon
        executor.submit(statusWatcher::start);
        executor.submit(logStreamer::start);
    }

    @Override
    public void close() {
        try (statusWatcher; logStreamer) {
            executor.shutdownNow();
        }
    }
}

⸻
4. Integratie in JobExecutionServiceImpl

@Override
public JobStatusResponseDto execute(ExecuteJobRequestDto request) {
    
//Job aanmaken in DB
    JobExecutionEntity job = new JobExecutionEntity();
    job.setJobName(request.jobName());
    job.setJobType(JobExecutionType.SCRIPT);
    job.setStatus(JobExecutionStatus.PENDING);
    job.setStartedAt(Instant.now());
    jobRepo.save(job);

    String k8sJobName = "job-" + job.getId();

    // Bouw Kubernetes Job
    var k8sJob = new JobBuilder()
        .withNewMetadata().withName(k8sJobName).endMetadata()
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

    // Start watchers
    JobStatusWatcher statusWatcher = new JobStatusWatcher(
            client, namespace, k8sJobName, job.getId(), statusService);

    PodLogStreamer logStreamer = new PodLogStreamer(
            client, namespace, k8sJobName, job.getId(), logService);

    JobMonitor monitor = new JobMonitor(statusWatcher, logStreamer);
    monitor.start();

    return mapToDto(job);
}
