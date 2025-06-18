const [workflows, setWorkflows] = useState<WorkflowMap[]>([]);

// geen await icm .then gebruiken
export const UseGetAllWorkflows = async (): Promise<WorkflowMap[]> => {
  const res = await axios.get<WorkflowMap[]>("/api/workflows");
  return res.data;
};

// of,
return axios.get<WorkflowMap[]>("/api/workflows").then((response) => response.data);


useEffect(() => {
  if (!open) return;

  const fetchData = async () => {
    try {
      const data = await UseGetAllWorkflows(); // ✅ wacht netjes
      setWorkflows(data);
    } catch (error) {
      console.error("Fout bij ophalen workflows:", error);
    }
  };

  fetchData();
}, [open]);

// debugging
const data = await UseGetAllWorkflows();
console.log("🔍 typeof data:", typeof data);
console.log("🔍 isArray:", Array.isArray(data));
console.log("🔍 example item:", data[0]);


// modal component LoadWorkflowModal / Dialog
import React, { useEffect, useState } from "react";
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Select, MenuItem, CircularProgress
} from "@mui/material";

interface WorkflowInfo {
  id: number;
  name: string;
}

interface LoadWorkflowModalProps {
  open: boolean;
  onClose: () => void;
  onLoad: (workflow: any) => void;
}

export const LoadWorkflowModal: React.FC<LoadWorkflowModalProps> = ({ open, onClose, onLoad }) => {
  const [workflows, setWorkflows] = useState<WorkflowInfo[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  // Haal lijst met workflows op bij openen modal
  useEffect(() => {
    if (open) {
      fetch("/api/workflows")
        .then((res) => res.json())
        .then(setWorkflows)
        .catch((err) => console.error("Laden workflows mislukt", err));
    }
  }, [open]);

  // Als gebruiker een workflow selecteert en op 'Laden' klikt
  const handleLoadClick = async () => {
    if (!selectedId) return;

    setLoading(true);
    try {
      const response = await fetch(`/api/workflows/${selectedId}`);
      const data = await response.json();
      onLoad(data);  // Geef data terug aan parent (Editor)
      onClose();     // Sluit modal
    } catch (err) {
      console.error("Fout bij ophalen van workflow:", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Workflow laden</DialogTitle>
      <DialogContent>
        {workflows.length === 0 ? (
          <p>Geen workflows beschikbaar</p>
        ) : (
          <Select
            value={selectedId || ""}
            onChange={(e) => setSelectedId(Number(e.target.value))}
            fullWidth
          >
            {workflows.map((wf) => (
              <MenuItem key={wf.id} value={wf.id}>
                {wf.name}
              </MenuItem>
            ))}
          </Select>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Annuleren</Button>
        <Button onClick={handleLoadClick} disabled={!selectedId || loading}>
          {loading ? <CircularProgress size={20} /> : "Laden"}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// Drag and drop Sidebar
import { useState } from "react";
import { LoadWorkflowDialog } from "@/features/workflow/load/ui/LoadWorkflowDialog";

export const DragAndDropSidebar = ({
  onWorkflowLoad,
}: {
  onWorkflowLoad: (data: any) => void;
}) => {
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  const handleLoadWorkflow = async (workflowId: number) => {
    const response = await fetch(`/api/workflows/${workflowId}`);
    const data = await response.json();
    onWorkflowLoad(data); // bijvoorbeeld door te mappen naar nodes/edges
  };

  return (
    <>
      <button onClick={() => setIsDialogOpen(true)}>Load Workflow</button>
      <LoadWorkflowDialog
        open={isDialogOpen}
        onClose={() => setIsDialogOpen(false)}
        onLoad={handleLoadWorkflow}
      />
    </>
  );
};

// Canvas update Editor
<DragAndDropSidebar
  onWorkflowLoad={(workflowData) => {
    setNodes(workflowData.nodes);
    setEdges(workflowData.edges);
    // setWorkflowMeta(workflowData.workflow) eventueel ook
  }}
/>;

///////   new

const Editor = () => {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [loadModalOpen, setLoadModalOpen] = useState(false);

  const handleWorkflowLoad = (loadedNodes: Node[], loadedEdges: Edge[]) => {
    setNodes(loadedNodes);
    setEdges(loadedEdges);
  };

  return (
    <>
      <Sidebar onLoadClick={() => setLoadModalOpen(true)} />
      <LoadModal
        open={loadModalOpen}
        onClose={() => setLoadModalOpen(false)}
        onWorkflowLoad={handleWorkflowLoad} // 👈 callback meegeven
      />
      <FlowCanvas nodes={nodes} edges={edges} />
    </>
  );
};


const LoadModal = ({ open, onClose, onWorkflowLoad }: {
  open: boolean;
  onClose: () => void;
  onWorkflowLoad: (nodes: Node[], edges: Edge[]) => void;
}) => {
  const [workflows, setWorkflows] = useState<WorkflowInfo[]>([]);
  const [selectedWorkflow, setSelectedWorkflow] = useState<WorkflowInfo | null>(null);

  const handleSelect = async (workflowId: number) => {
    const response = await axios.get(`/api/workflows/${workflowId}`);
    const { nodes, edges } = response.data;
    onWorkflowLoad(nodes, edges); // 👈 Terug naar Editor
    onClose(); // Modal sluiten
  };

  return (
    <Dialog open={open} onClose={onClose}>
      {/* lijst workflows met selectie */}
      {/* button om handleSelect te triggeren */}
    </Dialog>
  );
};
