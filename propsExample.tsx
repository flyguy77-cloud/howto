// Editor
import { useState } from "react";
import { Flow } from "./components/Flow";
import { Sidebar } from "./components/Sidebar";
import { SaveModal } from "./components/SaveModal";
import { workflowApi } from "./api/workflowApi";
import { Node, Edge } from "./model/types";

export const EditorPage = () => {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [showSaveModal, setShowSaveModal] = useState(false);

  const handleSave = async () => {
    await workflowApi.save({
      name: "My Workflow",
      metadata: { category: "default" },
      nodes,
      edges,
    });
    setShowSaveModal(false);
  };

  const handleLoad = async () => {
    const workflow = await workflowApi.load(1);
    setNodes(workflow.nodes);
    setEdges(workflow.edges);
  };

  return (
    <>
      <Flow
        nodes={nodes}
        edges={edges}
        onNodesChange={setNodes}
        onEdgesChange={setEdges}
      />
      <Sidebar
        onSaveClick={() => setShowSaveModal(true)}
        onLoadClick={handleLoad}
      />
      {showSaveModal && (
        <SaveModal
          onClose={() => setShowSaveModal(false)}
          onSave={handleSave}
        />
      )}
    </>
  );
};

// Flow
import ReactFlow, { applyNodeChanges, applyEdgeChanges } from "@xyflow/react";
import { Node, Edge, NodeChange, EdgeChange } from "../model/types";

type Props = {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: (nodes: Node[]) => void;
  onEdgesChange: (edges: Edge[]) => void;
};

export const Flow = ({ nodes, edges, onNodesChange, onEdgesChange }: Props) => {
  const handleNodeChange = (changes: NodeChange[]) => {
    const removedIds = changes
      .filter((c) => c.type === "remove")
      .map((c) => c.id);
    onEdgesChange(
      edges.filter(
        (e) => !removedIds.includes(e.source) && !removedIds.includes(e.target)
      )
    );
    onNodesChange(applyNodeChanges(changes, nodes));
  };

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={handleNodeChange}
      onEdgesChange={(changes) =>
        onEdgesChange(applyEdgeChanges(changes, edges))
      }
    />
  );
};

// SideBar
type SidebarProps = {
  onSaveClick: () => void;
  onLoadClick: () => void;
};

export const Sidebar = ({ onSaveClick, onLoadClick }: SidebarProps) => (
  <div>
    <button onClick={onSaveClick}>Save</button>
    <button onClick={onLoadClick}>Load</button>
  </div>
);

// SaveModal
type SaveModalProps = {
  onClose: () => void;
  onSave: () => void;
};

export const SaveModal = ({ onClose, onSave }: SaveModalProps) => (
  <div className="modal">
    <h2>Opslaan?</h2>
    <button onClick={onSave}>Opslaan</button>
    <button onClick={onClose}>Annuleren</button>
  </div>
);

// WorkflowApi
import axios from "axios";
import { Workflow } from "../model/types";

export const workflowApi = {
  save: (workflow: Workflow) => axios.post("/api/workflow", workflow),
  load: (id: number): Promise<Workflow> =>
    axios.get(`/api/workflow/${id}`).then((res) => res.data),
};

// Types

export type Node = {
  id: string;
  type: string;
  data: any;
};

export type Edge = {
  id: string;
  source: string;
  target: string;
  data?: any;
};

export type Workflow = {
  id?: number;
  name: string;
  metadata: { category: string };
  nodes: Node[];
  edges: Edge[];
};
