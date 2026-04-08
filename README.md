# mineGrafana

Real-time Minecraft server monitoring plugin with Prometheus metrics and Grafana dashboard integration.

Supports **Paper 1.21+** and **Velocity 3.4+** in multi-server environments.

## Features

### Performance Monitoring
| Metric | Description |
|--------|-------------|
| TPS | Current, 1m/5m/15m averages |
| MSPT | Average, min, max, 95th percentile |
| Tick Distribution | Histogram of tick times (<5ms, <10ms, <25ms, <50ms, >50ms) |
| CPU | Process and system usage |
| Memory | Heap used/max, free percentage |
| GC | Per-collector count and time |

### World Metrics (Paper only)
| Metric | Description |
|--------|-------------|
| Entities | Total + per-world + per-type breakdown |
| Chunks | Loaded chunks per world |
| Tile Entities | Per-world tile entity count |
| Redstone | Hoppers, comparators, repeaters |
| Disk | Used/free/total + world folder size |

### Thread Profiling (Paper only)
| Metric | Description |
|--------|-------------|
| Plugin CPU % | Per-plugin CPU usage via stack sampling |
| Hot Classes | Most active classes per plugin |
| Thread Activity | Sample count per thread category |

### Proxy Metrics (Velocity only)
| Metric | Description |
|--------|-------------|
| Total Players | Proxy-wide player count |
| Per-Server Players | Player count per backend server |
| Server Status | Online/offline per backend |
| Player Ping | Per-player and average ping |

### Flame Graph Profiler
- **Linux**: async-profiler (native, CPU/alloc/wall profiling)
- **Windows/macOS**: JDK Flight Recorder (JFR fallback)
- Interactive HTML flame graphs via d3-flame-graph
- In-game commands: `/mg profile start`, `/mg profile stop`

## Quick Start

### 1. Install

Drop the JAR into your plugins folder:
- `mineGrafana-paper-1.0.0.jar` → Paper server `plugins/`
- `mineGrafana-velocity-1.0.0.jar` → Velocity proxy `plugins/`

### 2. Configure

Edit `plugins/mineGrafana/config.yml`:

```yaml
server-id: "survival-1"    # Unique ID for this server
server-type: "paper"        # "paper" or "velocity"

web:
  port: 9100                # Metrics endpoint port (use 9200 for Velocity)
```

### 3. Verify

Open `http://localhost:9100/metrics` in your browser. You should see Prometheus-format metrics.

### 4. Connect Prometheus

Add to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'minecraft'
    scrape_interval: 5s
    static_configs:
      - targets:
          - 'localhost:9100'   # Paper server
          - 'localhost:9200'   # Velocity proxy
```

### 5. Import Grafana Dashboard

**Option A — Auto-provision** (recommended):

Set in `config.yml`:
```yaml
grafana:
  auto-provision: true
  url: "http://localhost:3000"
  api-key: "your-grafana-api-key"
  prometheus-url: "http://localhost:9090"
```

Restart the server. Dashboards are created automatically.

**Option B — Manual import**:

1. Open Grafana → Dashboards → Import
2. Download JSON from `http://localhost:9100/api/v1/grafana/dashboards/server-overview`
3. Select your Prometheus datasource → Import

## Configuration

<details>
<summary>Full config.yml reference</summary>

```yaml
# Unique server ID (used as Prometheus label)
server-id: "survival-1"
server-type: "paper"

web:
  port: 9100
  bind-address: "0.0.0.0"

features:
  monitoring:
    enabled: true
    collection-interval-seconds: 1     # Paper: 1s, Velocity: 5s recommended
    entity-collection-interval-seconds: 5
    profiler-enabled: true             # false for Velocity

metrics:
  prometheus:
    enabled: true
    endpoint: "/metrics"
  influx:
    enabled: false
    url: "http://localhost:8086"
    org: "minecraft"
    bucket: "minegrafana"
    token: ""
    step-seconds: 10

grafana:
  auto-provision: false
  url: "http://localhost:3000"
  api-key: ""
  datasource-name: "mineGrafana-prometheus"
  prometheus-url: "http://localhost:9090"
```

</details>

## Prometheus Metrics Reference

### Paper Metrics

```
minecraft_tps_current
minecraft_tps_avg1m / avg5m / avg15m
minecraft_mspt_avg / min / max / p95
minecraft_cpu_process / system
minecraft_memory_used_mb / max_mb / free_percent
minecraft_entities
minecraft_chunks_loaded
minecraft_tile_entities
minecraft_players_online
minecraft_players_ping_avg
minecraft_health_grade                              # 0=GOOD, 1=WARNING, 2=CRITICAL
minecraft_world_entities{world="world"}
minecraft_world_chunks{world="world"}
minecraft_world_tile_entities{world="world"}
minecraft_world_players{world="world"}
minecraft_entity_type_count{world="world",type="ZOMBIE"}
minecraft_player_ping{player="Steve"}
minecraft_plugin_cpu_percent{plugin="MythicMobs"}
minecraft_plugin_hotclass_samples{plugin="MythicMobs",class="MobExecutor"}
minecraft_thread_samples{thread="Server thread"}
minecraft_tick_under5ms / under10ms / under25ms / under50ms / over50ms
minecraft_redstone_active / hoppers / pistons
minecraft_disk_used_mb / free_mb / total_mb
minecraft_world_size_mb
```

### Velocity Metrics

```
velocity_players_total
velocity_servers_registered
velocity_server_players{server="survival"}
velocity_server_online{server="survival"}           # 1=online, 0=offline
velocity_player_ping{player="Steve"}
velocity_players_ping_avg
velocity_cpu_process / system
velocity_memory_used_mb / max_mb / free_percent
```

## In-Game Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mg monitor` | `minegrafana.command.monitor` | Show health report |
| `/mg profile start [sec] [cpu\|alloc\|wall]` | `minegrafana.command.profile` | Start profiler |
| `/mg profile stop` | `minegrafana.command.profile` | Stop and generate flame graph |
| `/mg profile view` | `minegrafana.command.profile` | Open flame graph URL |
| `/mg status` | `minegrafana.command.status` | Plugin status + dashboard URL |
| `/mg reload` | `minegrafana.command.reload` | Reload configuration |

## Multi-Server Setup

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Paper :9100│  │ Paper :9101│  │Velocity:9200│
└─────┬──────┘  └─────┬──────┘  └──────┬───────┘
      │               │               │
      └───────┬───────┘───────────────┘
              │
       ┌──────▼──────┐
       │  Prometheus  │
       └──────┬──────┘
              │
       ┌──────▼──────┐
       │   Grafana    │
       └─────────────┘
```

Each server runs its own metrics endpoint on a unique port. Prometheus scrapes all endpoints. Grafana visualizes everything with the `server` label to distinguish instances.

## Building from Source

```bash
git clone https://github.com/your-org/mineGrafana.git
cd mineGrafana
./gradlew :paper:shadowJar :velocity:shadowJar
```

Output:
- `paper/build/libs/mineGrafana-paper-1.0.0.jar`
- `velocity/build/libs/mineGrafana-velocity-1.0.0.jar`

**Requirements**: JDK 21+, Gradle 9.0+

## Tech Stack

- **Kotlin 2.3** + **Spring Boot 4.0** (embedded Reactor Netty)
- **Micrometer** + **Prometheus** metrics registry
- **Paper API 1.21.4** / **Velocity API 3.4**
- **async-profiler** / **JFR** for flame graphs
- **Shadow** plugin for fat JAR packaging

## License

[MIT](LICENSE)
