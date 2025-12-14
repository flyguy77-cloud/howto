Dit is <span style="color:red">rood</span> en dit is <span style="color:#00aa00">groen</span>.
  
    @Service
    @RequiredArgsConstructor
    public class JobExecutionService {
    public UUID executeScriptJob(RunScriptNodeRequestDto dto) {
        UUID execId = UUID.randomUUID();

        executeScriptJobInternal(dto, execId);

        return execId;
    }


    public void executeScriptJobInternal(
            RunScriptNodeRequestDto dto,
            UUID execId) {

        // 1. JobExecutionEntity aanmaken / opslaan
        JobExecutionEntity job = new JobExecutionEntity();
        job.setId(execId);
        job.setStatus(JobExecutionStatus.PENDING);
        jobRepo.save(job);

        // 2. Kubernetes Job bouwen
        Job k8sJob = buildKubernetesJob(dto, execId);

        // 3. Job starten
        client.batch().v1().jobs()
              .inNamespace(namespace)
              .resource(k8sJob)
              .create();

        // 4. Watchers starten
        jobMonitor.start(
                namespace,
                k8sJob.getMetadata().getName(),
                execId,
                k8sJob.getMetadata().getUid()
        );
    }
  }

  @Service
  @RequiredArgsConstructor
  public class WorkflowNodeExecutor {

    private final JobExecutionService jobExecutionService;

    public void execute(NodeEntity node, UUID workflowExecId) {

        // DTO wordt hier gewoon gebouwd
        // geen magie, geen mapper vereist
        RunScriptNodeRequestDto dto = new RunScriptNodeRequestDto(
                node.getScript(),
                node.getImage(),
                node.getArgs(),
                node.getEnv()
        );

        // BELANGRIJK:
        // - execId komt van workflow
        // - GEEN nieuwe execId generatie
        jobExecutionService.executeScriptJobInternal(dto, workflowExecId);
    }
  }

  @Service
  @RequiredArgsConstructor

	public class WorkflowScheduleSyncService {
    private final WorkflowRepository workflowRepo;
    private final JobScheduler jobScheduler;
    private final WorkflowExecutionService executionService;

    public void syncAll() {

        var workflows = workflowRepo.findAllWithEnabledSchedule();

        for (WorkflowEntity wf : workflows) {
            var schedule = wf.getSchedule();
            if (schedule == null || !schedule.isEnabled()) continue;

            String recurringId = "workflow-" + wf.getId();

            jobScheduler.scheduleRecurrently(
                    recurringId,
                    schedule.getCron(),
                    () -> executionService.startExecution(wf.getId())
            );
        }
    }
	}


	@Service
	@RequiredArgsConstructor

	public class WorkflowExecutionService {
    private final WorkflowExecutionRepository execRepo;
    private final WorkflowRunner workflowRunner;

    public UUID startExecution(UUID workflowId) {

        UUID execId = UUID.randomUUID();

        var entity = new WorkflowExecutionEntity();
        entity.setExecId(execId);
        entity.setWorkflowId(workflowId);
        entity.setStatus(WorkflowExecutionStatus.PENDING);
        entity.setStartedAt(Instant.now());

        execRepo.save(entity);

        BackgroundJob.enqueue(() ->
                workflowRunner.runWorkflow(workflowId, execId)
        );

        return execId;
    }
	}



	@Service
	@RequiredArgsConstructor

	public class WorkflowRunner {
    private final WorkflowRepository wfRepo;
    private final WorkflowNodeRepository nodeRepo;
    private final GraphBuilder graphBuilder;
    private final WorkflowNodeExecutor nodeExecutor;

    public void runWorkflow(UUID workflowId, UUID execId) {

        WorkflowEntity wf = wfRepo.findById(workflowId).orElseThrow();
        GraphModel graph = graphBuilder.build(wf);

        UUID startNodeId = graph.getStartNodes().get(0);

        BackgroundJob.enqueue(() ->
                runNode(workflowId, startNodeId, execId)
        );
    }

    public void runNode(UUID workflowId, UUID nodeId, UUID execId) {
        NodeEntity node = nodeRepo.findById(nodeId).orElseThrow();

        nodeExecutor.execute(node, execId);

        WorkflowEntity wf = wfRepo.findById(workflowId).orElseThrow();
        GraphModel graph = graphBuilder.build(wf);

        List<UUID> next = graph.getNext().get(nodeId);
        if (next == null || next.isEmpty()) return; // END

        UUID nextNodeId = next.get(0); // MVP: sequentieel

        BackgroundJob.enqueue(() ->
                runNode(workflowId, nextNodeId, execId)
        );
    }
	}


	@Service
	@RequiredArgsConstructor

	public class WorkflowNodeExecutor {
    private final JobExecutionService jobExecService;

    public void execute(NodeEntity node, UUID workflowExecId, UUID workflowId) {
        WorkflowNodeExecutionRequest req =
                new WorkflowNodeExecutionRequest(
                        workflowExecId,
                        workflowId,
                        node.getId(),
                        node.getType(),
                        node.getParameters()
                );

        switch (node.getType()) {
            case LOAD_SCRIPT -> jobExecService.executeLoadScript(req);
            case RUN_SCRIPT  -> jobExecService.executeRunScript(req);
            case GENERATE_REPORT -> jobExecService.executeReport(req);
            case END -> { /* noop */ }
        }
    }
	}

	@Component

	public class GraphBuilder {

    public GraphModel build(WorkflowEntity wf) {

        Map<UUID, List<UUID>> nextMap = new HashMap<>();

        for (NodeEntity node : wf.getNodes()) {
            List<UUID> nextIds = wf.getEdges().stream()
                    .filter(e -> e.getSourceNodeId().equals(node.getId()))
                    .map(EdgeEntity::getTargetNodeId)
                    .toList();

            nextMap.put(node.getId(), nextIds);
        }

        List<UUID> startNodes = wf.getNodes().stream()
                .filter(n -> n.getKind() == NodeKind.START)
                .map(NodeEntity::getId)
                .toList();

        return new GraphModel(nextMap, startNodes);
    }
	}


