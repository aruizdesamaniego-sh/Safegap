# SafeGap - Agent Guidelines

## Code Style

- Kotlin with trailing commas
- No unnecessary abstractions or over-engineering
- Prefer constructor injection with Hilt
- Keep modules focused: each module has a single responsibility

## When Modifying Build Files

- AGP 9.0 includes built-in Kotlin support — do NOT add `kotlin-android` plugin to modules
- Use `kotlin { jvmToolchain(17) }` instead of `kotlinOptions { jvmTarget }`
- Version catalog is at `gradle/libs.versions.toml` — always add new dependencies there
- All modules use `compileSdk = 36`

## Testing

- Unit tests for pure logic (estimation, alert, tracking) — no Android dependencies needed
- Use synthetic data for testing algorithms (known bboxes, distances, speeds)
- Integration tests for CameraX require a real device

## Key Architectural Decisions

- `DrivingService` is a Foreground Service with `foregroundServiceType="camera"` — required so Android doesn't kill the process while driving
- Camera frames use `SharedFlow` with `DROP_OLDEST` to handle backpressure — never block the camera thread
- `ImageProxy.toBitmap()` from CameraX 1.6 is used for frame conversion — no manual YUV conversion unless profiling shows it's needed
- Kalman filter smooths distance estimates per tracked object
- TTC (Time-to-Collision) is the primary alert trigger, not raw distance

## Custom Sub-agents

Three specialist sub-agents are defined in `.claude/agents/`:

| Agent | File | Model | Role |
|:------|:------|:------|:-----|
| `kotlin-expert` | `.claude/agents/kotlin-expert.md` | Sonnet | Kotlin idioms, coroutines, performance, Hilt/KSP |
| `mobile-expert` | `.claude/agents/mobile-expert.md` | Sonnet | Android lifecycle, CameraX, TFLite, Compose |
| `project-manager` | `.claude/agents/project-manager.md` | Opus | Decomposes tasks, delegates to specialists, evaluates output |

### Usage

```bash
# Run with the project manager as main agent (can delegate to specialists)
claude --agent project-manager

# Or reference agents in conversation
# "Use the kotlin-expert to review the coroutine usage in :estimation"
# "Use the mobile-expert to check the CameraX lifecycle in :camera"
```

### Architecture

```
User Request
    │
    ▼
[project-manager]  ── analyzes, decomposes
    │
    ├──► [kotlin-expert]   (isolated context)
    │         └─► output
    │
    ├──► [mobile-expert]   (isolated context)
    │         └─► output
    │
    ▼
[project-manager]  ── evaluates, merges, resolves
    │
    ▼
Final Response
```

The project-manager can only spawn `kotlin-expert` and `mobile-expert` (restricted via `tools: Agent(kotlin-expert, mobile-expert)`). Each specialist runs in its own context window and returns results to the project-manager for evaluation.
