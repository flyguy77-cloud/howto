// APP
import "./App.css";
import {Box} from "@mui/material";
import {WorkflowList, type Workflow} from "./components/WorkflowList";

function App() {
    const workflows: Workflow[] = [
        {id: "1", name: "Workflow A", status: "Running", date: "01-05-2026"},
        {id: "2", name: "Workflow B", status: "Running", date: "01-04-2026"},
        {id: "3", name: "Workflow C", status: "Failed", date: "02-05-2026"},
    ];

    return (
        <Box
            sx={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr 1fr",
                gridTemplateRows: "1fr auto",
                gap: 3,
                p: 3,
                background:
                    "linear-gradient(135deg, #eef7ff 0%, #f7fff4 45%, #fff7ed 100%)",
            }}
        >
            <WorkflowList
                workflows={workflows}
                onCreate={() => console.log("New workflow")}
                onStart={(workflow) => console.log("Start", workflow)}
                onStop={(workflow) => console.log("Stop", workflow)}
                onEdit={(workflow) => console.log("Edit", workflow)}
                onDelete={(workflow) => console.log("Delete", workflow)}
                onMore={(workflow) => console.log("More", workflow)}
            />
        </Box>
    );
}

export default App;

// WorkflowList

import {
    Avatar,
    Box,
    Button,
    Chip,
    IconButton,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    Paper,
    Stack,
    Tooltip,
    Typography,
} from "@mui/material";

import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import StopIcon from "@mui/icons-material/Stop";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import AccountTreeIcon from "@mui/icons-material/AccountTree";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import AddIcon from "@mui/icons-material/Add";

export type WorkflowStatus = "Running" | "Failed" | "Stopped";

export type Workflow = {
    id: string;
    name: string;
    status: WorkflowStatus;
    date: string;
};

type WorkflowListProps = {
    workflows: Workflow[];
    onCreate?: () => void;
    onStart?: (workflow: Workflow) => void;
    onStop?: (workflow: Workflow) => void;
    onEdit?: (workflow: Workflow) => void;
    onDelete?: (workflow: Workflow) => void;
    onMore?: (workflow: Workflow) => void;
};

function getStatusColor(status: WorkflowStatus): "success" | "error" | "default" {
    if (status === "Running") return "success";
    if (status === "Failed") return "error";
    return "default";
}

export function WorkflowList({
                                 workflows,
                                 onCreate,
                                 onStart,
                                 onStop,
                                 onEdit,
                                 onDelete,
                                 onMore,
                             }: WorkflowListProps) {
    return (
        <Paper
            elevation={3}
            sx={{
                gridColumn: "1 / 4",
                borderRadius: 5,
                overflow: "hidden",
                backgroundColor: "rgba(255,255,255,0.85)",
                backdropFilter: "blur(8px)",
            }}
        >
            <Box
                sx={{
                    px: 3,
                    py: 2,
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    borderBottom: "1px solid",
                    borderColor: "divider",
                }}
            >
                <Box>
                    <Typography variant="h6" sx={{fontWeight: 700}}>
                        Workflows
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Start, stop, bewerk of verwijder workflows
                    </Typography>
                </Box>

                <Button
                    variant="contained"
                    startIcon={<AddIcon/>}
                    onClick={onCreate}
                    sx={{
                        borderRadius: 3,
                        textTransform: "none",
                        fontWeight: 600,
                        px: 2.5,
                        background: "linear-gradient(135deg, #1976d2, #42a5f5)",
                        "&:hover": {
                            background: "linear-gradient(135deg, #1565c0, #1e88e5)",
                        },
                    }}
                >
                    New workflow
                </Button>
            </Box>

            <List disablePadding>
                {workflows.map((workflow) => (
                    <ListItem
                        key={workflow.id}
                        divider
                        sx={{
                            cursor: "pointer",
                            px: 3,
                            py: 1.5,
                            transition: "all 0.2s ease",
                            "&:hover": {
                                backgroundColor: "rgba(25, 118, 210, 0.08)",
                                transform: "translateX(4px)",
                            },
                        }}
                        secondaryAction={
                            <Stack direction="row" spacing={0.5}>
                                <Tooltip title="Start">
                                    <IconButton
                                        edge="end"
                                        onClick={(event) => {
                                            event.stopPropagation();
                                            onStart?.(workflow);
                                        }}
                                    >
                                        <PlayArrowIcon/>
                                    </IconButton>
                                </Tooltip>

                                <Tooltip title="Stop">
                                    <IconButton
                                        edge="end"
                                        onClick={(event) => {
                                            event.stopPropagation();
                                            onStop?.(workflow);
                                        }}
                                    >
                                        <StopIcon/>
                                    </IconButton>
                                </Tooltip>

                                <Tooltip title="Bewerken">
                                    <IconButton
                                        edge="end"
                                        onClick={(event) => {
                                            event.stopPropagation();
                                            onEdit?.(workflow);
                                        }}
                                    >
                                        <EditIcon/>
                                    </IconButton>
                                </Tooltip>

                                <Tooltip title="Verwijderen">
                                    <IconButton
                                        edge="end"
                                        onClick={(event) => {
                                            event.stopPropagation();
                                            onDelete?.(workflow);
                                        }}
                                    >
                                        <DeleteIcon/>
                                    </IconButton>
                                </Tooltip>

                                <Tooltip title="Meer">
                                    <IconButton
                                        edge="end"
                                        onClick={(event) => {
                                            event.stopPropagation();
                                            onMore?.(workflow);
                                        }}
                                    >
                                        <MoreVertIcon/>
                                    </IconButton>
                                </Tooltip>
                            </Stack>
                        }
                    >
                        <ListItemAvatar>
                            <Avatar
                                sx={{
                                    backgroundColor:
                                        workflow.status === "Running"
                                            ? "success.light"
                                            : workflow.status === "Failed"
                                                ? "orange"
                                                : "grey.200",
                                    color:
                                        workflow.status === "Running"
                                            ? "success.dark"
                                            : workflow.status === "Failed"
                                                ? "error.dark"
                                                : "text.secondary",
                                }}
                            >
                                <AccountTreeIcon/>
                            </Avatar>
                        </ListItemAvatar>

                        <ListItemText
                            primary={
                                <Stack direction="row" spacing={1.5} sx={{alignItems: "center"}}>
                                    <Typography sx={{fontWeight: 700}}>{workflow.name}</Typography>
                                    <Chip
                                        label={workflow.status}
                                        size="small"
                                        color={getStatusColor(workflow.status)}
                                        variant="outlined"
                                    />
                                </Stack>
                            }
                            secondary={`Laatst uitgevoerd · ${workflow.date}`}
                        />
                    </ListItem>
                ))}
            </List>
        </Paper>
    );
}

export default WorkflowList;
