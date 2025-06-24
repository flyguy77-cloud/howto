// UseEffect lostrekken

const [workflows, setWorkflows] = useState<WorkflowMap[]>([]);

const fetchWorkflows = async () => {
  try {
    const response = await axios.get<WorkflowMap[]>("/api/workflows");
    setWorkflows(response.data);
  } catch (error) {
    console.error("Fout bij ophalen workflows:", error);
  }
};

useEffect(() => {
  fetchWorkflows();
}, []);

const handleDelete = async (id: number) => {
  await axios.delete(`/api/workflows/${id}`);
  await fetchWorkflows(); // opnieuw ophalen = rerender
};

{workflows.map((workflow) => (
  <WorkflowCard
    key={workflow.id}
    workflow={workflow}
    onDelete={handleDelete}
  />
))}
