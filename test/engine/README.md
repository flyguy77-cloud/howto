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
	            throw new IllegalStateException(
	                    "Workflow must have exactly one START node"
	            );
	        }
	
	        UUID startNodeId = graph.getStartNodes().get(0);
	
	        UUID firstExecutable =
	                findNextExecutableNode(startNodeId, graph, wf);
	
	        if (firstExecutable == null) {
	            throw new IllegalStateException(
	                    "No executable node reachable from START"
	            );
	        }
	
	        enqueueNode(workflowId, firstExecutable, execId);
	    }

	
	    private void enqueueNode(UUID workflowId, UUID nodeId, UUID execId) {
	        BackgroundJob.enqueue(() ->
	                runNode(workflowId, nodeId, execId)
	        );
	    }


		public void runNode(UUID workflowId, UUID nodeId, UUID execId) {
	
	        NodeEntity node = nodeRepo.findById(nodeId)
	                .orElseThrow(() -> new IllegalStateException("Node not found"));
	
	        // 1. execute current node (blocking)
	        nodeExecutor.execute(node, execId);
	
	        // 2. reload graph (safe & stateless)
	        WorkflowEntity wf = wfRepo.findById(workflowId)
	                .orElseThrow();
	
	        GraphModel graph = graphBuilder.build(wf);
	
	        // 3. find next executable node
	        UUID nextExecutable =
	                findNextExecutableNode(nodeId, graph, wf);
	
	        if (nextExecutable == null) {
	            return; // workflow finished (END node was executed)
	        }
	
	        enqueueNode(workflowId, nextExecutable, execId);
	    }
}

		HELPERS:
		
		private boolean isExecutable(NodeEntity node) {
    		return switch (node.getType()) {
        case RUN_SCRIPT, END -> true;
        default -> false; // START, LOAD_SCRIPT, etc.
    	};
	  }


		private UUID findNextExecutableNode(
	        UUID fromNodeId,
	        GraphModel graph,
	        WorkflowEntity wf
		) {
	    UUID current = fromNodeId;
	
	    while (true) {
	
	        List<UUID> next = graph.getNext().get(current);
	
	        if (next == null || next.isEmpty()) {
	            return null; // einde van de workflow
	        }
	
	        if (next.size() > 1) {
	            throw new IllegalStateException(
	                    "Parallel branches not supported in MVP"
	            );
	        }
	
	        UUID candidateId = next.get(0);
	
	        NodeEntity candidate = wf.getNodes().stream()
	                .filter(n -> n.getId().equals(candidateId))
	                .findFirst()
	                .orElseThrow(() ->
	                        new IllegalStateException("Node not found: " + candidateId)
	                );
	
	        if (isExecutable(candidate)) {
	            return candidateId;
	        }
	
	        // skip non-executable node (bv. LOAD_SCRIPT)
	        current = candidateId;
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


	public class GraphModel {

    private final Map<UUID, List<UUID>> next;
    private final List<UUID> startNodes;

    public GraphModel(
            Map<UUID, List<UUID>> next,
            List<UUID> startNodes
    ) {
        this.next = next;
        this.startNodes = startNodes;
    }

    public Map<UUID, List<UUID>> getNext() {
        return next;
    }

    public List<UUID> getStartNodes() {
        return startNodes;
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

	@Component
	@RequiredArgsConstructor
	public class PollingJobCompletionAwaiter
	        implements JobCompletionAwaiter {
	
	    private final JobExecutionRepository jobRepo;
	
	    @Override
	    public void await(UUID execId) {
	
	        while (true) {
	            JobExecutionStatus status = jobRepo.findById(execId)
	                    .map(JobExecutionEntity::getStatus)
	                    .orElseThrow();
	
	            if (status == JobExecutionStatus.SUCCEEDED) {
	                return;
	            }
	
	            if (status == JobExecutionStatus.FAILED) {
	                throw new IllegalStateException(
	                        "Job failed: " + execId
	                );
	            }
	
	            try {
	                Thread.sleep(1_000); // 1s is prima
	            } catch (InterruptedException e) {
	                Thread.currentThread().interrupt();
	                throw new IllegalStateException("Interrupted", e);
	            }
	        }
	    }
}
	
	@Service
	@RequiredArgsConstructor
	public class WorkflowNodeExecutor {
	
	    private final JobExecutionService jobExecutionService;
	    private final JobCompletionAwaiter awaiter;
	
	    public void execute(NodeEntity node,
	                        UUID workflowExecId,
	                        boolean sequential) {
	
	        UUID jobExecId = workflowExecId; // MVP: 1-op-1
	
	        switch (node.getType()) {
	
	            case RUN_SCRIPT -> {
	                RunScriptNodeRequestDto dto =
	                        RunScriptNodeRequestDto.from(node);
	
	                jobExecutionService.executeScriptJobInternal(dto, jobExecId);
	
	                if (sequential) {
	                    awaiter.await(jobExecId);
	                }
	            }
	
	            case INLINE_SCRIPT -> {
	                InlineScriptNodeRequestDto dto =
	                        InlineScriptNodeRequestDto.from(node);
	
	                jobExecutionService.executeInlineScript(dto, jobExecId);
	
	                if (sequential) {
	                    awaiter.await(jobExecId);
	                }
	            }
	
	            case END -> {
	                // mark workflow finished (later)
	            }
	
	            default -> throw new IllegalStateException(
	                    "Unsupported node type: " + node.getType()
	            );
	        }
	    }
}
