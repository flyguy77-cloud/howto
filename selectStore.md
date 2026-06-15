### Verrijk node met een selected prop
```typescript
Node {
  id
  type
  position
  data
  selected  // <-- toevoegen aan de frondent react node
}
```

```typescript
const flowNodes = workflow.nodes.map((node) => ({
  id: node.nodeId,
  type: node.type,
  position: {
    x: node.positionX,
    y: node.positionY,
  },
  data: node.data,

  // alleen frontend UI-state
  selected: node.nodeId === selectedNodeId,  // <--- toevoeging
}));
```

### Zustand store maken

```typescript
// shared/stores/workflowUiStore.ts  <<--- locatie store

import { create } from "zustand";
import { persist } from "zustand/middleware";

type WorkflowUiState = {
  selectedNodeId: string | null;
  setSelectedNodeId: (id: string | null) => void;
};

export const useWorkflowUiStore = create<WorkflowUiState>()(
  persist(
    (set) => ({
      selectedNodeId: null,
      setSelectedNodeId: (id) => set({ selectedNodeId: id }),
    }),
    {
      name: "workflow-editor-ui",
    }
  )
);
```

#### In de code worden de nodes gefiltered
```typescript
const nodes = workflow.nodes.map(...) //<--- vervang

const flowNodes = workflow.nodes.map((node) =>  //<--- met
  toReactFlowNode(node, selectedNodeId)
);
```

#### in features/workflow-editor/mappers/toReactFlowNode.ts
```typescript
import type { Node } from "@xyflow/react";

const selectedNodeId =
  useWorkflowEditorUiStore.getState().selectedNodeId;

export function toReactFlowNode(
  workflowNode: WorkflowNodeDto,
  selectedNodeId: string | null
): Node {
  return {
    id: workflowNode.nodeId,
    type: workflowNode.type,
    position: {
      x: workflowNode.positionX,
      y: workflowNode.positionY,
    },
    data: workflowNode.data,
    selected: workflowNode.nodeId === selectedNodeId,
  };
}
```

### Flow.tsx

```typescript
import { ReactFlow, useOnSelectionChange } from "@xyflow/react";
import { useCallback } from "react";
import { useWorkflowUiStore } from "@/shared/stores/workflowUiStore"; // <-- call de store

function NodeSelectionListener() {
  const selectNode = useWorkflowEditorUiStore((state) => state.selectNode);
  const handleSelectionChange = useCallback(
    ({ nodes }) => {
      selectNode(nodes[0]?.id ?? null);
    },
    [selectNode]
  );
  useOnSelectionChange({
    onChange: handleSelectionChange,
  });
  return null;
}

const nodes = workflow.nodes.map((node) => ({
  ...mapBackendNodeToFlowNode(node),
  selected: node.nodeId === selectedNodeId,
}));

<ReactFlow
  nodes={nodes}
  edges={edges}
  onNodesChange={onNodesChange}
  onEdgesChange={onEdgesChange}
  nodeTypes={nodeTypes}
  elementsSelectable  // <--- toevoeging
>
  <NodeSelectionListener /> // <-- call de SelectionListener functie
</ReactFlow>
```

### In BaseNode

```typescript
export function BaseNode({ data, selected }: NodeProps) {
  return (
    <Box
      sx={{
        border: selected ? "2px solid #1976d2" : "1px solid #ccc",
        boxShadow: selected ? 4 : 1,
        borderRadius: 2,
        backgroundColor: "background.paper",
      }}
    >
      {data.label}
    </Box>
  );
}
```







