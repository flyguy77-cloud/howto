// saveWorkflow.tsx
import { Node, Edge } from "@/entities/workflow";
import { WorkflowDto } from "@/shared/api";

export const saveWorkflow = async (
  dto: WorkflowDto & { nodes: Node[]; edges: Edge[] }
): Promise<string> => {
  const response = await fetch("/api/workflows", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dto),
  });

  if (!response.ok) throw new Error("Opslaan mislukt");

  const result = await response.json();
  return result.id;
};

// loadWorkflow.ts
import { Node, Edge } from "@/entities/workflow";

export interface LoadedWorkflow {
  id: string;
  name: string;
  status: string;
  metadata: any;
  nodes: Node[];
  edges: Edge[];
}

export const loadWorkflow = async (id: string): Promise<LoadedWorkflow> => {
  const response = await fetch(`/api/workflows/${id}`);
  if (!response.ok) throw new Error("Laden mislukt");

  return await response.json();
};

// ui.tsx (in pages/WorkflowEditor)
import { saveWorkflow } from "@/features/workflow/save/model/saveWorkflow";
import { loadWorkflow } from "@/features/workflow/load/model/loadWorkflow";

const handleSave = async () => {
  const id = await saveWorkflow({ name, status, metadata, nodes, edges });
  alert(`Workflow opgeslagen met ID: ${id}`);
};

const handleLoad = async () => {
  const data = await loadWorkflow("abc-123");
  setNodes(data.nodes);
  setEdges(data.edges);
};

// src/
// ├── entities/
// │   └── workflow/               # Workflow object zelf (types, model, state)
// │       └── model/
// │           └── types.ts
// │           └── selectors.ts
// ├── features/
// │   └── workflow/
// │       ├── save/
// │       │   └── model/
// │       │       └── saveWorkflow.ts      <-- hier komt handler
// │       └── load/
// │           └── model/
// │               └── loadWorkflow.ts     <-- hier komt handler
// ├── pages/
// │   └── WorkflowEditor/
// │       └── ui.tsx                       <-- gebruikt handlers via import
