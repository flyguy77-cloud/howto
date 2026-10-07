## Frontend Invitation hierarchy
```
src/
├── entities/
│   └── workflow-invitation/
│       ├── api/
│       │   └── workflowInvitationApi.ts
│       ├── model/
│       │   └── workflowInvitation.types.ts
│       └── ui/
│           └── InvitationStatusBadge.tsx
│
├── features/
│   ├── invite-workflow-member/
│   │   ├── api/
│   │   ├── model/
│   │   └── ui/
│   │       └── InviteWorkflowMemberDialog.tsx
│   │
│   ├── accept-workflow-invitation/
│   │   ├── api/
│   │   ├── model/
│   │   └── ui/
│   │       └── AcceptWorkflowInvitation.tsx
│   │
│   └── manage-workflow-members/
│       └── ui/
│           └── WorkflowMembersPanel.tsx
│
└── pages/
    └── workflow-invitation/
        └── WorkflowInvitationPage.tsx
```
### Types
```typescript
export type WorkflowRole =
  | "EDITOR"
  | "VIEWER";

export type InvitationStatus =
  | "PENDING"
  | "ACCEPTED"
  | "DECLINED"
  | "REVOKED"
  | "EXPIRED";

export interface WorkflowInvitation {
  id: number;
  workflowId: number;

  invitedUserId: string;
  invitedBy: string;

  role: WorkflowRole;
  status: InvitationStatus;

  createdAt: string;
  expiresAt?: string;
  acceptedAt?: string;
}
```
Request
```typescript
export interface CreateWorkflowInvitationRequest {
  userId: string;
  role: WorkflowRole;
}
```
Member
```typescript
export interface WorkflowMember {
  id: number;
  userId: string;
  role: WorkflowRole;
  createdAt: string;
  createdBy: string;
}
```
### API laag
```typescript
export const createWorkflowInvitation = async (
  workflowId: number,
  request: CreateWorkflowInvitationRequest,
): Promise<WorkflowInvitation> => {
  const response = await api.post(
    `/workflows/${workflowId}/invitations`,
    request,
  );

  return response.data;
};
```
Accept
```typescript
export const acceptWorkflowInvitation = async (
  invitationId: number,
): Promise<WorkflowMember> => {
  const response = await api.post(
    `/workflow-invitations/${invitationId}/accept`,
  );

  return response.data;
};
```
decline
```typescript
export const declineWorkflowInvitation = async (
  invitationId: number,
): Promise<WorkflowInvitation> => {
  const response = await api.post(
    `/workflow-invitations/${invitationId}/decline`,
  );

  return response.data;
};
```
revoke
```typescript
export const revokeWorkflowInvitation = async (
  invitationId: number,
): Promise<void> => {
  await api.delete(
    `/workflow-invitations/${invitationId}`,
  );
};
```
### Invite aanmaken
```
Workflow
   ↓
[ Delen ]
   ↓
InviteWorkflowMemberDialog
   ↓
Gebruiker selecteren
Rol kiezen
   ↓
POST invitation
   ↓
toon uitnodigingsstatus
```
```typescript
interface InviteWorkflowMemberDialogProps {
  workflowId: number;
  open: boolean;
  onClose: () => void;
}

export const InviteWorkflowMemberDialog = ({
  workflowId,
  open,
  onClose,
}: InviteWorkflowMemberDialogProps) => {
  const [userId, setUserId] = useState("");
  const [role, setRole] =
    useState<WorkflowRole>("EDITOR");

  const handleInvite = async () => {
    await createWorkflowInvitation(
      workflowId,
      {
        userId,
        role,
      },
    );

    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose}>
      {/* user selection */}
      {/* role selection */}
      {/* submit */}
    </Dialog>
  );
};
```

### Invite link
```
/app/invitations/123
```
route
```typescript
<Route
  path="/invitations/:invitationId"
  element={<WorkflowInvitationPage />}
/>
```
```typescript
export const WorkflowInvitationPage = () => {
  const { invitationId } = useParams();

  if (!invitationId) {
    return <NotFound />;
  }

  return (
    <AcceptWorkflowInvitation
      invitationId={Number(invitationId)}
    />
  );
};
```
### Invite laden
```typescript
export const getWorkflowInvitation = async (
  invitationId: number,
): Promise<WorkflowInvitation> => {
  const response = await api.get(
    `/workflow-invitations/${invitationId}`,
  );

  return response.data;
};
```
### Accept component
```typescript
interface AcceptWorkflowInvitationProps {
  invitationId: number;
}

export const AcceptWorkflowInvitation = ({
  invitationId,
}: AcceptWorkflowInvitationProps) => {
  const [invitation, setInvitation] =
    useState<WorkflowInvitation | null>(null);

  const [loading, setLoading] =
    useState(true);

  useEffect(() => {
    getWorkflowInvitation(invitationId)
      .then(setInvitation)
      .finally(() => setLoading(false));
  }, [invitationId]);

  const handleAccept = async () => {
    const member =
      await acceptWorkflowInvitation(invitationId);

    // bijvoorbeeld redirect:
    navigate(`/workflows/${invitation!.workflowId}`);
  };

  const handleDecline = async () => {
    await declineWorkflowInvitation(invitationId);

    navigate("/workflows");
  };

  if (loading) {
    return <CircularProgress />;
  }

  if (!invitation) {
    return <NotFound />;
  }

  return (
    <InvitationCard
      invitation={invitation}
      onAccept={handleAccept}
      onDecline={handleDecline}
    />
  );
};
```
### mail link
```java
String inviteUrl =
    frontendBaseUrl
        + "/invitations/"
        + invitation.getId()
        + "?token="
        + URLEncoder.encode(
            token,
            StandardCharsets.UTF_8
        );
```
```typescript
<Route
  path="/invitations/:invitationId"
  element={<WorkflowInvitationPage />}
/>
```
X-Invitation-Token: ...

### Status afhandeling
```typescript
switch (invitation.status) {
  case "PENDING":
    // buttons tonen
    break;

  case "ACCEPTED":
    // "Je hebt deze uitnodiging al geaccepteerd"
    break;

  case "DECLINED":
    // "Je hebt deze uitnodiging geweigerd"
    break;

  case "REVOKED":
    // "Deze uitnodiging is ingetrokken"
    break;

  case "EXPIRED":
    // "Deze uitnodiging is verlopen"
    break;
}
```
# Backend
```java
@Entity
public class WorkflowInvitation {

    private Long id;

    private Workflow workflow;

    private String invitedUserId;

    private String invitedBy;

    @Enumerated(EnumType.STRING)
    private WorkflowRole role;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status;

    private String tokenHash;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant acceptedAt;
}
```

```java
public interface WorkflowInvitationService {

    WorkflowInvitationDto invite(
        Long workflowId,
        CreateWorkflowInvitationRequest request,
        CurrentUser currentUser
    );

    WorkflowMemberDto accept(
        Long invitationId,
        CurrentUser currentUser
    );

    WorkflowInvitationDto decline(
        Long invitationId,
        CurrentUser currentUser
    );

    void revoke(
        Long invitationId,
        CurrentUser currentUser
    );

    WorkflowInvitationDto getInvitation(
        Long invitationId,
        CurrentUser currentUser
    );

    List<WorkflowInvitationDto> getWorkflowInvitations(
        Long workflowId,
        CurrentUser currentUser
    );
}
```
## Token generatie
```java
private String generateInvitationToken() {
    byte[] bytes = new byte[32];

    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextBytes(bytes);

    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes);
}
```
## implementatie
```java
@Service
@RequiredArgsConstructor
@Transactional
public class WorkflowInvitationServiceImpl
        implements WorkflowInvitationService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowInvitationRepository invitationRepository;
    private final WorkflowMemberRepository memberRepository;

    private final WorkflowAuthorizationService authorizationService;
    private final WorkflowInvitationMapper invitationMapper;
    private final WorkflowMemberMapper memberMapper;

    @Override
    public WorkflowInvitationDto invite(
            Long workflowId,
            CreateWorkflowInvitationRequest request,
            CurrentUser currentUser) {

        authorizationService.requireManageMembers(
            workflowId,
            currentUser
        );

        Workflow workflow = workflowRepository
            .findById(workflowId)
            .orElseThrow(() ->
                new WorkflowNotFoundException(workflowId)
            );

        boolean alreadyMember =
            memberRepository.existsByWorkflowIdAndUserId(
                workflowId,
                request.userId()
            );

        if (alreadyMember) {
            throw new WorkflowMemberAlreadyExistsException(
                request.userId()
            );
        }

        boolean pendingInvitationExists =
            invitationRepository
                .existsByWorkflowIdAndInvitedUserIdAndStatus(
                    workflowId,
                    request.userId(),
                    InvitationStatus.PENDING
                );

        if (pendingInvitationExists) {
            throw new WorkflowInvitationAlreadyExistsException(
                request.userId()
            );
        }

        WorkflowInvitation invitation =
            WorkflowInvitation.builder()
                .workflow(workflow)
                .invitedUserId(request.userId())
                .invitedBy(currentUser.id())
                .role(request.role())
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        return invitationMapper.toDto(
            invitationRepository.save(invitation)
        );
    }

    @Override
    public WorkflowMemberDto accept(
            Long invitationId,
            CurrentUser currentUser) {

        WorkflowInvitation invitation =
            getPendingInvitation(invitationId);

        requireInvitedUser(
            invitation,
            currentUser
        );

        WorkflowMember member =
            WorkflowMember.builder()
                .workflow(invitation.getWorkflow())
                .userId(currentUser.id())
                .role(invitation.getRole())
                .createdAt(Instant.now())
                .createdBy(invitation.getInvitedBy())
                .build();

        WorkflowMember savedMember =
            memberRepository.save(member);

        invitation.setStatus(
            InvitationStatus.ACCEPTED
        );

        invitation.setAcceptedAt(
            Instant.now()
        );

        invitationRepository.save(invitation);

        return memberMapper.toDto(savedMember);
    }

    @Override
    public WorkflowInvitationDto decline(
            Long invitationId,
            CurrentUser currentUser) {

        WorkflowInvitation invitation =
            getPendingInvitation(invitationId);

        requireInvitedUser(
            invitation,
            currentUser
        );

        invitation.setStatus(
            InvitationStatus.DECLINED
        );

        return invitationMapper.toDto(
            invitationRepository.save(invitation)
        );
    }

    @Override
    public void revoke(
            Long invitationId,
            CurrentUser currentUser) {

        WorkflowInvitation invitation =
            getPendingInvitation(invitationId);

        authorizationService.requireManageMembers(
            invitation.getWorkflow().getId(),
            currentUser
        );

        invitation.setStatus(
            InvitationStatus.REVOKED
        );

        invitationRepository.save(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowInvitationDto getInvitation(
            Long invitationId,
            CurrentUser currentUser) {

        WorkflowInvitation invitation =
            invitationRepository
                .findById(invitationId)
                .orElseThrow(() ->
                    new WorkflowInvitationNotFoundException(
                        invitationId
                    )
                );

        boolean invitedUser =
            invitation.getInvitedUserId()
                .equals(currentUser.id());

        boolean canManage =
            authorizationService.canManageMembers(
                invitation.getWorkflow().getId(),
                currentUser
            );

        if (!invitedUser && !canManage) {
            throw new AccessDeniedException(
                "No access to this invitation"
            );
        }

        return invitationMapper.toDto(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowInvitationDto> getWorkflowInvitations(
            Long workflowId,
            CurrentUser currentUser) {

        authorizationService.requireManageMembers(
            workflowId,
            currentUser
        );

        return invitationRepository
            .findAllByWorkflowId(workflowId)
            .stream()
            .map(invitationMapper::toDto)
            .toList();
    }

    private WorkflowInvitation getPendingInvitation(
            Long invitationId) {

        WorkflowInvitation invitation =
            invitationRepository
                .findById(invitationId)
                .orElseThrow(() ->
                    new WorkflowInvitationNotFoundException(
                        invitationId
                    )
                );

        if (invitation.getStatus()
                != InvitationStatus.PENDING) {

            throw new InvalidWorkflowInvitationStateException(
                invitation.getStatus()
            );
        }

        return invitation;
    }

    private void requireInvitedUser(
            WorkflowInvitation invitation,
            CurrentUser currentUser) {

        if (!invitation.getInvitedUserId()
                .equals(currentUser.id())) {

            throw new AccessDeniedException(
                "Invitation does not belong to current user"
            );
        }
    }
}
```
## repository
```java
public interface WorkflowInvitationRepository
        extends JpaRepository<WorkflowInvitation, Long> {

    boolean existsByWorkflowIdAndInvitedUserIdAndStatus(
        Long workflowId,
        String invitedUserId,
        InvitationStatus status
    );

    List<WorkflowInvitation> findAllByWorkflowId(
        Long workflowId
    );
}
```
members
```java
public interface WorkflowMemberRepository
        extends JpaRepository<WorkflowMember, Long> {

    boolean existsByWorkflowIdAndUserId(
        Long workflowId,
        String userId
    );
}
```

## Mail service
pom.xml
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```


application.yml
```yaml
spring:
  mail:
    host: smtp.internal.example
    port: 25
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
```


```java
public interface MailService {
    void sendWorkflowInvitation(
        String recipient,
        String workflowName,
        String invitationUrl
    );
}
```


```java
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Override
    public void sendWorkflowInvitation(
            String recipient,
            String workflowName,
            String invitationUrl) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Uitnodiging voor workflow");

        message.setText("""
            Je bent uitgenodigd om mee te werken aan workflow "%s".

            Open de uitnodiging via:
            %s
            """.formatted(
                workflowName,
                invitationUrl
            ));

        mailSender.send(message);
    }
}
```
invitationService
```java
mailService.sendWorkflowInvitation(
    invitedUserEmail,
    workflow.getName(),
    invitationUrl
);
``

















