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

	    public void markFinished(UUID execId) {
        execRepo.findByExecId(execId).ifPresent(exec -> {
            exec.setStatus(WorkflowExecutionStatus.SUCCEEDED);
            exec.setFinishedAt(Instant.now());
            execRepo.save(exec);
        });
    }

    public void markFailed(UUID execId, String reason) {
        execRepo.findByExecId(execId).ifPresent(exec -> {
            exec.setStatus(WorkflowExecutionStatus.FAILED);
            exec.setErrorMessage(reason);
            exec.setFinishedAt(Instant.now());
            execRepo.save(exec);
        });
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
	
	        WorkflowEntity wf = wfRepo.findById(workflowId)
	                .orElseThrow(() -> new IllegalStateException("Workflow not found"));
	
	        GraphModel graph = graphBuilder.build(wf);
	
	        if (graph.getStartNodes().size() != 1) {
	            throw new IllegalStateException("Workflow must have exactly one START node");
	        }
	
	        UUID startNodeId = graph.getStartNodes().get(0);
	
	        BackgroundJob.enqueue(() ->
	                runNode(workflowId, startNodeId, execId)
	        );
	    }

	    public void runNode(UUID workflowId, UUID nodeId, UUID execId) {
	
	        NodeEntity node = nodeRepo.findById(nodeId)
	                .orElseThrow(() -> new IllegalStateException("Node not found"));
	
	        // 1️⃣ execute node (BLOCKING until done)
	        nodeExecutor.execute(node, execId);
	
	        // 2️⃣ load graph again (safe & simple)
	        WorkflowEntity wf = wfRepo.findById(workflowId)
	                .orElseThrow();
	
	        GraphModel graph = graphBuilder.build(wf);
	
	        // 3️⃣ determine next node(s)
	        List<UUID> next = graph.getNext().get(nodeId);
	
	        if (next == null || next.isEmpty()) {
	            return; // END of workflow
	        }
	
	        if (next.size() > 1) {
	            throw new IllegalStateException(
	                    "Parallel nodes not supported in MVP"
	            );
	        }
	
	        UUID nextNodeId = next.get(0);
	
	        // 4️⃣ enqueue next step
	        BackgroundJob.enqueue(() ->
	                runNode(workflowId, nextNodeId, execId)
	        );
	    }
	}


	@Service
	@RequiredArgsConstructor
	public class WorkflowNodeExecutor {
	
	    private final JobExecutionService jobExecService;
	    private final WorkflowExecutionService workflowExecService;
	
	    public void execute(NodeEntity node, UUID workflowExecId) {
	
	        switch (node.getType()) {
	
	            case RUN_SCRIPT -> {
	                RunScriptNodeRequestDto dto =
	                        new RunScriptNodeRequestDto(
	                                node.getScript(),
	                                node.getImage(),
	                                node.getArgs(),
	                                node.getEnv()
	                        );
	
	                jobExecService.executeScriptJobInternal(dto, workflowExecId);
	            }
	
	            case END -> {
	                workflowExecService.markFinished(workflowExecId);
	            }
	
	            case LOAD_SCRIPT, GENERATE_REPORT ->
	                    throw new NodeTypeNotSupportedException(node.getType());
	        }
	    }
	}
	
	@Component
	public class GraphBuilder {
	
	    public GraphModel build(WorkflowEntity wf) {
	
	        Map<UUID, List<UUID>> next = new HashMap<>();
	        Map<UUID, List<UUID>> incoming = new HashMap<>();
	
	        for (NodeEntity node : wf.getNodes()) {
	            next.put(node.getId(), new ArrayList<>());
	            incoming.put(node.getId(), new ArrayList<>());
	        }
	
	        for (EdgeEntity edge : wf.getEdges()) {
	            next.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
	            incoming.get(edge.getTargetNodeId()).add(edge.getSourceNodeId());
	        }
	
	        List<UUID> startNodes = wf.getNodes().stream()
	                .filter(n -> n.getKind() == NodeKind.START)
	                .map(NodeEntity::getId)
	                .toList();
	
	        if (startNodes.isEmpty()) {
	            throw new IllegalStateException("Workflow has no START node");
	        }
	
	        return new GraphModel(
	                Map.copyOf(next),
	                Map.copyOf(incoming),
	                List.copyOf(startNodes)
	        );
	    }
	}


