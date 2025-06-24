const filteredWorkflows = workflows.filter((workflow) => {
  if (user.role === "ADMIN") return true;
  return workflow.metadata?.owner === user.username;
});

const userHasWorkflows = filteredWorkflows.length > 0;

{userHasWorkflows ? (
  filteredWorkflows.map((workflow) => (
    <WorkflowCard key={workflow.id} workflow={workflow} />
  ))
) : (
  <Typography variant="body1" color="textSecondary">
    Je hebt nog geen workflows aangemaakt.
  </Typography>
)}
