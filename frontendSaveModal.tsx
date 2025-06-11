// Save Modal
import React, { useState } from "react";
import { Dialog, DialogTitle, DialogContent, TextField, DialogActions, Button } from "@mui/material";

type SaveModalProps = {
  open: boolean;
  onClose: () => void;
  onSave: (formData: WorkflowFormData) => void;
};

type WorkflowFormData = {
  name: string;
  description: string;
  category: string;
};

export const SaveModal: React.FC<SaveModalProps> = ({ open, onClose, onSave }) => {
  const [formData, setFormData] = useState<WorkflowFormData>({
    name: "",
    description: "",
    category: ""
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSave = () => {
    onSave(formData);     // ⬅️ Geef data terug aan parent
    setFormData({ name: "", description: "", category: "" }); // reset
    onClose();            // sluit modal
  };

  const handleCancel = () => {
    setFormData({ name: "", description: "", category: "" }); // reset
    onClose(); // sluiten zonder opslaan
  };

  return (
    <Dialog open={open} onClose={handleCancel} fullWidth maxWidth="sm">
      <DialogTitle>Workflow opslaan</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          margin="dense"
          name="name"
          label="Naam"
          type="text"
          fullWidth
          value={formData.name}
          onChange={handleChange}
        />
        <TextField
          margin="dense"
          name="description"
          label="Beschrijving"
          type="text"
          fullWidth
          value={formData.description}
          onChange={handleChange}
        />
        <TextField
          margin="dense"
          name="category"
          label="Categorie"
          type="text"
          fullWidth
          value={formData.category}
          onChange={handleChange}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel}>Annuleren</Button>
        <Button onClick={handleSave} variant="contained">Opslaan</Button>
      </DialogActions>
    </Dialog>
  );
};

// Editor
const [isModalOpen, setIsModalOpen] = useState(false);

const handleSaveModalOpen = () => setIsModalOpen(true);
const handleSaveModalClose = () => setIsModalOpen(false);

const handleSaveWorkflow = (formData: WorkflowFormData) => {
  console.log("Alle data samenvoegen:", {
    ...formData,
    nodes,
    edges
  });
  // 👉 axios.post(...) of andere opslaglogica hier
};

<Button onClick={handleSaveModalOpen}>Open Save Modal</Button>

<SaveModal
  open={isModalOpen}
  onClose={handleSaveModalClose}
  onSave={handleSaveWorkflow}
/>