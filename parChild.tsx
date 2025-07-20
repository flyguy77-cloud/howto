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
