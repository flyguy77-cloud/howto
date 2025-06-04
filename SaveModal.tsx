import React, { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Stack,
} from "@mui/material";

type SaveWorkflowModalProps = {
  open: boolean;
  onClose: () => void;
  onSave: (workflowInfo: {
    name: string;
    category: string;
    description: string;
  }) => void;
};

export const SaveWorkflowModal = ({
  open,
  onClose,
  onSave,
}: SaveWorkflowModalProps) => {
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [description, setDescription] = useState("");

  const handleSubmit = () => {
    if (!name.trim()) return;

    onSave({
      name: name.trim(),
      category: category.trim(),
      description: description.trim(),
    });

    // reset form
    setName("");
    setCategory("");
    setDescription("");
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Workflow opslaan</DialogTitle>
      <DialogContent>
        <Stack spacing={2} mt={1}>
          <TextField
            label="Workflownaam"
            fullWidth
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <TextField
            label="Categorie"
            fullWidth
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
          <TextField
            label="Beschrijving"
            fullWidth
            multiline
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Annuleren</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={!name.trim()}
        >
          Opslaan
        </Button>
      </DialogActions>
    </Dialog>
  );
};
