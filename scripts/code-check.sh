#!/bin/bash
#
# code-check.sh - Comprehensive Code Quality Check and Format Script
# =================================================================
#
# DESCRIPTION:
#   This script performs code quality checks across HiMarket:
#   - Backend: Maven Spotless (apply + check)
#   - himarket-frontend: Prettier, ESLint --fix, type-check
#   - himarket-admin: Prettier, ESLint --fix, type-check
#
# USAGE:
#   ./scripts/code-check.sh [all|backend|frontend|admin]
#
#   all       (default) backend + both frontends
#   backend   Java / Maven only
#   frontend  himarket-frontend only
#   admin     himarket-admin only
#
#   In GitHub Actions (GITHUB_ACTIONS set), npm ci runs automatically for the
#   frontend target(s) before npm scripts.
#
# EXIT CODES:
#   0 - All checks passed successfully
#   1 - One or more checks failed (see output for details)
#
# REQUIREMENTS:
#   - Maven (backend)
#   - Node.js and npm (frontends; local runs expect node_modules unless CI)
#
# =================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly BLUE='\033[0;34m'
readonly BOLD='\033[1m'
readonly NC='\033[0m'

# Maven: batch mode in CI for cleaner logs
mvn_cmd() {
  if [ -n "${GITHUB_ACTIONS:-}" ]; then
    mvn -B -ntp "$@"
  else
    mvn "$@"
  fi
}

run_step() {
  local name="$1"
  shift
  local start_time elapsed_time
  start_time=$(date +%s)

  printf '%b\n' "${BLUE}${BOLD}▶${NC} ${BOLD}${name}${NC}"
  echo "   Working directory: $(pwd)"
  echo "   Starting at: $(date '+%Y-%m-%d %H:%M:%S')"
  echo

  if "$@"; then
    elapsed_time=$(($(date +%s) - start_time))
    echo
    printf '%b\n' "${GREEN}${BOLD}✓${NC} ${GREEN}SUCCESS${NC} - ${name}"
    echo "   Completed in ${elapsed_time}s"
    echo
    return 0
  else
    elapsed_time=$(($(date +%s) - start_time))
    echo
    printf '%b\n' "${RED}${BOLD}✗${NC} ${RED}FAILED${NC} - ${name}"
    echo "   Failed after ${elapsed_time}s"
    printf '%b\n' "${RED}${BOLD}Aborting script execution${NC}"
    exit 1
  fi
}

npm_ci_if_ci() {
  local dir="$1"
  local label="$2"
  if [ -n "${GITHUB_ACTIONS:-}" ]; then
    run_step "npm ci (${label})" bash -c "cd \"${dir}\" && npm ci"
  fi
}

run_backend() {
  run_step "Maven Spotless Apply (Java Formatting)" mvn_cmd spotless:apply
  run_step "Maven Spotless Check (Java Format Verification)" mvn_cmd spotless:check
}

run_frontend_npm_steps() {
  local rel="$1"
  local name="$2"
  local dir="${ROOT}/${rel}"

  run_step "${name}: Prettier Format" bash -c "cd \"${dir}\" && npm run format"
  run_step "${name}: ESLint Fix" bash -c "cd \"${dir}\" && npm run lint:fix"
  run_step "${name}: Type Check" bash -c "cd \"${dir}\" && npm run type-check"
}

# Install deps (CI only) then Prettier / ESLint / tsc for one frontend app
run_frontend_target() {
  local rel="$1"
  local name="$2"
  local dir="${ROOT}/${rel}"
  npm_ci_if_ci "${dir}" "${name}"
  run_frontend_npm_steps "${rel}" "${name}"
}

usage() {
  echo "Usage: $0 [all|backend|frontend|admin]" >&2
  echo "  all       - backend + himarket-frontend + himarket-admin (default)" >&2
  echo "  backend   - Maven Spotless only" >&2
  echo "  frontend  - himarket-frontend only" >&2
  echo "  admin     - himarket-admin only" >&2
}

main() {
  local cmd="${1:-all}"

  case "${cmd}" in
    -h | --help)
      usage
      exit 0
      ;;
    all)
      echo "================================================================================"
      printf '%b\n' "${BOLD}HiMarket Code Quality Check (all)${NC}"
      echo "================================================================================"
      echo ""
      npm_ci_if_ci "${ROOT}/himarket-web/himarket-frontend" "himarket-frontend"
      npm_ci_if_ci "${ROOT}/himarket-web/himarket-admin" "himarket-admin"
      run_backend
      run_frontend_npm_steps "himarket-web/himarket-frontend" "himarket-frontend"
      run_frontend_npm_steps "himarket-web/himarket-admin" "himarket-admin"
      ;;
    backend)
      echo "================================================================================"
      printf '%b\n' "${BOLD}HiMarket Code Quality Check (backend)${NC}"
      echo "================================================================================"
      echo ""
      run_backend
      ;;
    frontend)
      echo "================================================================================"
      printf '%b\n' "${BOLD}HiMarket Code Quality Check (frontend)${NC}"
      echo "================================================================================"
      echo ""
      run_frontend_target "himarket-web/himarket-frontend" "himarket-frontend"
      ;;
    admin)
      echo "================================================================================"
      printf '%b\n' "${BOLD}HiMarket Code Quality Check (admin)${NC}"
      echo "================================================================================"
      echo ""
      run_frontend_target "himarket-web/himarket-admin" "himarket-admin"
      ;;
    *)
      echo "Unknown command: ${cmd}" >&2
      usage
      exit 1
      ;;
  esac

  if [ "${cmd}" = "all" ] || [ "${cmd}" = "frontend" ] || [ "${cmd}" = "admin" ]; then
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
      run_step "Verify no uncommitted format/lint changes" git diff --exit-code
    fi
  fi

  echo "================================================================================"
  printf '%b\n' "${GREEN}${BOLD}✓ CHECKS COMPLETED${NC}"
  echo "================================================================================"
}

main "$@"
