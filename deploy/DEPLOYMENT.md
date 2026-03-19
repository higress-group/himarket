# HiMarket Deployment Guide

## Overview

A complete HiMarket deployment includes the following components:
- **MySQL** - Database service
- **Nacos** - Configuration center
- **Higress** - AI Gateway
- **Redis** - Cache service
- **HiMarket** - Server + Admin + Frontend + Sandbox

Two deployment methods are available:
- **Docker Compose** - Suitable for local development and single-machine deployment
- **Helm (Kubernetes)** - Suitable for production environments and cluster deployment

Both methods use the interactive guided script `install.sh`, which automatically completes all configuration and initialization.

## Docker Compose Deployment

### Prerequisites

- `docker`
- `docker compose`
- `curl`
- `jq`

### Interactive Deployment (Recommended)

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/docker
./install.sh
```

The script will guide you step by step through all configurations, including image selection, password setup, AI model configuration, etc.

### Non-Interactive Mode (CI/CD)

```bash
cp .env.example ~/himarket-install.env
# Edit ~/himarket-install.env to modify configurations as needed
./install.sh -n
```

### Uninstall

```bash
./install.sh --uninstall
```

### Upgrade

Re-run `./install.sh`, and the script will automatically detect existing deployments and provide upgrade options.

### Service Ports

| Service | Host Port | Description |
|---------|-----------|-------------|
| HiMarket Admin | 5174 | Admin console UI |
| HiMarket Frontend | 5173 | Developer portal UI |
| HiMarket Server | 8081 | Backend API service |
| Nacos | 8848 | Nacos console |
| Higress Console | 8001 | Higress console |
| Higress Gateway | 8082 | Gateway HTTP endpoint |
| MySQL | 3306 | Database service |
| Redis | 6379 | Redis service |

## Helm Deployment (Kubernetes)

### Prerequisites

- `kubectl` (connected to a K8s cluster)
- `helm`
- `curl`
- `jq`
- `python3`

### Interactive Deployment (Recommended)

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/helm
./install.sh
```

### Non-Interactive Mode

```bash
cp .env.example ~/himarket-install.env
# Edit configurations
./install.sh -n
```

### Uninstall

```bash
./install.sh --uninstall
```

### Upgrade

Re-run `./install.sh`, and the script will automatically detect existing deployments and provide upgrade options.

### Notes

- Deploys to the `himarket` namespace by default
- Admin and Frontend services use LoadBalancer type by default

## Configuration

- In interactive mode, the script guides you through all configuration items (images, database passwords, service credentials, default users, AI models, etc.)
- Configurations are automatically saved to `~/himarket-install.env` and reused for subsequent upgrades
- `.env.example` is the configuration template containing all configurable items with default values, suitable for non-interactive mode
- Log file is located at `~/himarket-install.log`

## FAQ

### View Service Logs

Docker method:
```bash
docker compose logs -f
```

Helm method:
```bash
kubectl logs -n himarket <pod-name>
```

### How to Troubleshoot Deployment Failures

Check the log file:
```bash
cat ~/himarket-install.log
```

### How to Retry Initialization Hooks

Hook scripts are located in the `hooks/post_ready.d/` directory and can be executed independently.

### How to Skip Partial Initialization

- Interactive mode: The script provides skip options
- Non-interactive mode: Control via environment variables, e.g., `SKIP_MCP_INIT=true`
