const updateField = (key: string, newValue: any) => {
  setNodes((nodes) =>
    nodes.map((node) => {
      if (node.id !== selectedNodeId) return node;

      return {
        ...node,
        data: {
          ...node.data,
          fields: {
            ...node.data.fields,
            integration: {
              ...node.data.fields?.integration,
              gitlab: {
                ...node.data.fields?.integration?.gitlab,
                [key]: newValue, // Bijvoorbeeld: key = "file", value = { id, name, path }
              },
            },
          },
        },
      };
    })
  );
};

onSelect={(file) => {
  updateField("file", file); // Alleen dit!
  setModalOpen(false);
}}



// parent
const [modalOpen, setModalOpen] = useState(false);
const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  const handleMenuOpen = (event: React.MouseEvent<HTMLButtonElement>) => {
    setMenuAnchor(event.currentTarget);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const handleModalOpen = () => {
    setMenuAnchor(null);
    setModalOpen(true);
  };


// child

type MyModalProps = {
  anchorEl: HTMLElement | null;
  onMenuClose: () => void;
  modalOpen: boolean;
  onModalClose: () => void;
  onModalOpen: () => void;
};

export const MyModal = ({
  anchorEl,
  onMenuClose,
  modalOpen,
  onModalClose,
  onModalOpen,
}: MyModalProps)

const menuOpen = Boolean(anchorEl);

      <Menu anchorEl={anchorEl} open={menuOpen} onClose={onMenuClose}>

<Dialog open={modalOpen} onClose={onModalClose}>
