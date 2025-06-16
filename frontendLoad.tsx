// modal component LoadWorkflowModal / Dialog
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from "@mui/material";
import { useEffect, useState } from "react";

interface LoadWorkflowDialogProps {
  open: boolean;
  onClose: () => void;
  onLoad: (workflowId: number) => void;
}

export const LoadWorkflowDialog: React.FC<LoadWorkflowDialogProps> = ({
  open,
  onClose,
  onLoad,
}) => {
  const [selectedId, setSelectedId] = useState<number | "">("");
  const [availableWorkflows, setAvailableWorkflows] = useState<
    { id: number; name: string }[]
  >([]);

  useEffect(() => {
    if (open) {
      fetch("/api/workflows") // ← Pas deze endpoint aan indien nodig
        .then((res) => res.json())
        .then((data) => setAvailableWorkflows(data))
        .catch((err) => console.error("Error loading workflows", err));
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Selecteer een workflow</DialogTitle>
      <DialogContent>
        <FormControl fullWidth margin="normal">
          <InputLabel id="workflow-select-label">Workflow</InputLabel>
          <Select
            labelId="workflow-select-label"
            value={selectedId}
            onChange={(e) => setSelectedId(Number(e.target.value))}
          >
            {availableWorkflows.map((wf) => (
              <MenuItem key={wf.id} value={wf.id}>
                {wf.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Annuleren</Button>
        <Button
          onClick={() => {
            onLoad(Number(selectedId));
            onClose();
          }}
          disabled={selectedId === ""}
          variant="contained"
        >
          Laden
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
