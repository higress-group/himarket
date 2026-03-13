#!/bin/bash

# Nacos Setup Installation Script
# This script downloads and installs nacos-setup from remote repository

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ============================================================================
# Configuration
# ============================================================================

DOWNLOAD_BASE_URL="https://download.nacos.io"
# nacos-setup version configuration
NACOS_SETUP_VERSION="${NACOS_SETUP_VERSION:-0.0.1}"
# nacos-cli configuration
NACOS_CLI_VERSION="${NACOS_CLI_VERSION:-0.0.8}"
INSTALL_BASE_DIR="/usr/local"
CURRENT_LINK="nacos-setup"
BIN_DIR="/usr/local/bin"
SCRIPT_NAME="nacos-setup"
TEMP_DIR="/tmp/nacos-setup-install-$$"
CACHE_DIR="${HOME}/.nacos/cache"  # 缓存目录

# ============================================================================
# Check Requirements
# ============================================================================

check_requirements() {
    print_info "Checking system requirements..."
    
    # Check if running on macOS or Linux
    if [[ "$OSTYPE" != "darwin"* ]] && [[ "$OSTYPE" != "linux-gnu"* ]]; then
        print_error "Unsupported OS: $OSTYPE"
        print_error "This script only supports macOS and Linux"
        exit 1
    fi
    
    # Check for required commands
    local missing_commands=()
    
    if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
        missing_commands+=("curl or wget")
    fi
    
    if ! command -v unzip >/dev/null 2>&1; then
        missing_commands+=("unzip")
    fi
    
    if [ ${#missing_commands[@]} -gt 0 ]; then
        print_error "Missing required commands: ${missing_commands[*]}"
        echo ""
        print_info "Please install missing commands:"
        if [[ "$OSTYPE" == "darwin"* ]]; then
            echo "  brew install curl unzip"
        else
            echo "  sudo apt-get install curl unzip  # Debian/Ubuntu"
            echo "  sudo yum install curl unzip      # CentOS/RHEL"
        fi
        return 1
    fi
    
    # Check if we have write permission to install directory
    local mode="${1:-full}"
    if [[ "$mode" == "onlycli" ]]; then
        if [ ! -w "$BIN_DIR" ]; then
            print_warn "No write permission to $BIN_DIR"
            print_warn "You may need to run with sudo"
            return 1
        fi
    else
        if [ ! -w "$INSTALL_BASE_DIR" ]; then
            print_warn "No write permission to $INSTALL_BASE_DIR"
            print_warn "You may need to run with sudo"
            return 1
        fi
    fi
    
    return 0
}

# ============================================================================
# Download
# ============================================================================

download_file() {
    local url=$1
    local output=$2
    
    print_info "Downloading from $url..." >&2
    
    # Try curl first, then wget
    if command -v curl >/dev/null 2>&1; then
        if curl -fSL --progress-bar "$url" -o "$output"; then
            return 0
        else
            print_error "Download failed with curl" >&2
            return 1
        fi
    elif command -v wget >/dev/null 2>&1; then
        if wget -q --show-progress "$url" -O "$output"; then
            return 0
        else
            print_error "Download failed with wget" >&2
            return 1
        fi
    else
        print_error "Neither curl nor wget is available" >&2
        return 1
    fi
}

# Download nacos-setup package with caching support
# Parameters: version
# Returns: path to zip file (in cache or temp) or empty on error
download_nacos_setup() {
    local version=$1
    local zip_filename="nacos-setup-${version}.zip"
    local download_url="${DOWNLOAD_BASE_URL}/nacos-setup-${version}.zip"
    local cached_file="$CACHE_DIR/$zip_filename"
    
    # Create cache directory
    mkdir -p "$CACHE_DIR" 2>/dev/null
    
    # Check if cached file exists and is valid
    if [ -f "$cached_file" ] && [ -s "$cached_file" ]; then
        # Verify the cached zip file is valid
        if unzip -t "$cached_file" >/dev/null 2>&1; then
            print_info "Found cached package: $cached_file" >&2
            print_info "Skipping download, using cached file" >&2
            echo "" >&2
            echo "$cached_file"
            return 0
        else
            print_warn "Cached file is corrupted, re-downloading..." >&2
            rm -f "$cached_file"
        fi
    fi
    
    # Download the file to cache
    print_info "Downloading nacos-setup version: $version" >&2
    echo "" >&2
    
    if ! download_file "$download_url" "$cached_file"; then
        print_error "Failed to download nacos-setup" >&2
        rm -f "$cached_file"
        return 1
    fi
    
    echo "" >&2
    
    # Verify downloaded file is a valid zip
    if ! unzip -t "$cached_file" >/dev/null 2>&1; then
        print_error "Downloaded file is corrupted or invalid" >&2
        rm -f "$cached_file"
        return 1
    fi
    
    print_info "Download completed: $zip_filename" >&2
    echo "$cached_file"
    return 0
}

# Download nacos-cli package with caching support
# Parameters: version, os, arch
# Returns: path to zip file (in cache) or empty on error
download_nacos_cli() {
    local version=$1
    local os=$2
    local arch=$3
    local zip_filename="nacos-cli-${version}-${os}-${arch}.zip"
    local download_url="${DOWNLOAD_BASE_URL}/${zip_filename}"
    local cached_file="$CACHE_DIR/$zip_filename"
    
    # Create cache directory
    mkdir -p "$CACHE_DIR" 2>/dev/null
    
    # Check if cached file exists and is valid
    if [ -f "$cached_file" ] && [ -s "$cached_file" ]; then
        # Verify the cached zip file is valid
        if unzip -t "$cached_file" >/dev/null 2>&1; then
            print_info "Found cached package: $cached_file" >&2
            print_info "Skipping download, using cached file" >&2
            echo "" >&2
            echo "$cached_file"
            return 0
        else
            print_warn "Cached file is corrupted, re-downloading..." >&2
            rm -f "$cached_file"
        fi
    fi
    
    # Download the file to cache
    print_info "Downloading nacos-cli version: $version" >&2
    echo "" >&2
    
    if ! download_file "$download_url" "$cached_file"; then
        print_error "Failed to download nacos-cli" >&2
        rm -f "$cached_file"
        return 1
    fi
    
    echo "" >&2
    
    # Verify downloaded file is a valid zip
    if ! unzip -t "$cached_file" >/dev/null 2>&1; then
        print_error "Downloaded file is corrupted or invalid" >&2
        rm -f "$cached_file"
        return 1
    fi
    
    print_info "Download completed: $zip_filename" >&2
    echo "$cached_file"
    return 0
}

# ============================================================================
# Installation
# ============================================================================

install_nacos_setup() {
    print_info "Installing nacos-setup..."
    echo ""
    
    # Get version from environment variable or use default
    local setup_version="${NACOS_SETUP_VERSION}"
    
    print_info "Target version: $setup_version"
    
    # Download nacos-setup (with caching)
    # If cached version exists, it will be used directly
    # If not, download from remote and save to cache
    local zip_file=$(download_nacos_setup "$setup_version")
    
    if [ -z "$zip_file" ]; then
        print_error "Failed to download nacos-setup"
        exit 1
    fi
    
    print_success "Package ready: $zip_file"
    echo ""
    
    # Create temporary directory for extraction
    mkdir -p "$TEMP_DIR"
    
    # Extract zip file
    print_info "Extracting nacos-setup..."
    if ! unzip -q "$zip_file" -d "$TEMP_DIR"; then
        print_error "Failed to extract zip file"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    # Find extracted directory (should be nacos-setup-VERSION or similar)
    local extracted_dir=$(find "$TEMP_DIR" -maxdepth 1 -type d ! -path "$TEMP_DIR" | head -1)
    
    if [ -z "$extracted_dir" ] || [ ! -d "$extracted_dir" ]; then
        print_error "Failed to find extracted directory"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    # Verify required files
    if [ ! -f "$extracted_dir/nacos-setup.sh" ]; then
        print_error "nacos-setup.sh not found in package"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    if [ ! -d "$extracted_dir/lib" ]; then
        print_error "lib directory not found in package"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    # Prepare versioned installation directory
    local INSTALL_DIR="$INSTALL_BASE_DIR/${CURRENT_LINK}-$setup_version"

    # Remove old installation for this version if exists
    if [ -d "$INSTALL_DIR" ]; then
        print_info "Removing old installation..."
        rm -rf "$INSTALL_DIR"
    fi
    
    # Create installation directory
    print_info "Creating installation directory: $INSTALL_DIR"
    mkdir -p "$INSTALL_DIR"
    
    # Copy nacos-setup.sh to bin directory
    print_info "Installing nacos-setup command..."
    mkdir -p "$INSTALL_DIR/bin"
    cp "$extracted_dir/nacos-setup.sh" "$INSTALL_DIR/bin/$SCRIPT_NAME"
    chmod +x "$INSTALL_DIR/bin/$SCRIPT_NAME"
    
    # Copy lib directory
    print_info "Installing libraries..."
    cp -r "$extracted_dir/lib" "$INSTALL_DIR/"
    
    # Make all lib scripts executable
    chmod +x "$INSTALL_DIR/lib"/*.sh
    
    # Create or update current symlink and global command
    print_info "Updating active version symlink: $INSTALL_BASE_DIR/$CURRENT_LINK -> nacos-setup-$setup_version"
    if [ -L "$INSTALL_BASE_DIR/$CURRENT_LINK" ] || [ -e "$INSTALL_BASE_DIR/$CURRENT_LINK" ]; then
        rm -f "$INSTALL_BASE_DIR/$CURRENT_LINK"
    fi
    ln -s "nacos-setup-$setup_version" "$INSTALL_BASE_DIR/$CURRENT_LINK"

    print_info "Creating global command..."
    # Ensure bin directory exists
    mkdir -p "$BIN_DIR"
    
    # Remove old symlink if exists
    if [ -L "$BIN_DIR/$SCRIPT_NAME" ] || [ -f "$BIN_DIR/$SCRIPT_NAME" ]; then
        rm -f "$BIN_DIR/$SCRIPT_NAME"
    fi
    
    # Create symlink with absolute path
    local target_script="$INSTALL_BASE_DIR/$CURRENT_LINK/bin/$SCRIPT_NAME"
    
    # Verify target exists before creating symlink
    if [ ! -f "$target_script" ]; then
        print_error "Target script not found: $target_script"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    ln -s "$target_script" "$BIN_DIR/$SCRIPT_NAME"
    
    # Verify symlink was created successfully
    if [ ! -L "$BIN_DIR/$SCRIPT_NAME" ]; then
        print_error "Failed to create symlink at $BIN_DIR/$SCRIPT_NAME"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    print_info "Global command created: $BIN_DIR/$SCRIPT_NAME -> $target_script"
    
    # Cleanup temporary directory
    rm -rf "$TEMP_DIR"
    
    # Store version info
    echo "$setup_version" > "$INSTALL_DIR/.version"
    
    # Fix permissions for Nacos installation directory
    # Allow current user to manage Nacos instances without sudo
    local nacos_base_dir="${HOME}/ai-infra/nacos"
    if [ -d "$nacos_base_dir" ]; then
        print_info "Setting ownership of Nacos directory to current user..."
        if ! sudo chown -R "$USER:$(id -gn)" "$nacos_base_dir" 2>/dev/null; then
            print_warn "Failed to change ownership of $nacos_base_dir"
            print_info "You can fix this manually with: sudo chown -R \$USER:\$(id -gn) $nacos_base_dir"
        fi
    fi
    
    print_success "Installation completed!"
    echo ""
    
    # Export version for later use
    INSTALLED_VERSION="$setup_version"
}

# ============================================================================
# nacos-cli Installation
# ============================================================================

install_nacos_cli() {
    local version="${NACOS_CLI_VERSION}"

    print_info "Preparing to install nacos-cli version $version..."

    # Detect OS
    local os=""
    if [[ "$OSTYPE" == "darwin"* ]]; then
        os="darwin"
    elif [[ "$OSTYPE" == "linux-gnu"* ]] || [[ "$OSTYPE" == "linux"* ]]; then
        os="linux"
    elif [[ "$OSTYPE" == "msys"* ]] || [[ "$OSTYPE" == "cygwin"* ]] || [[ "$OSTYPE" == "win32"* ]]; then
        os="windows"
    else
        # Try using uname as fallback
        local uname_os
        uname_os=$(uname -s 2>/dev/null || echo "")
        if [[ "$uname_os" == "Darwin" ]]; then
            os="darwin"
        elif [[ "$uname_os" == "Linux" ]]; then
            os="linux"
        elif [[ "$uname_os" == MINGW* ]] || [[ "$uname_os" == MSYS* ]] || [[ "$uname_os" == CYGWIN* ]]; then
            os="windows"
        else
            print_warn "Unsupported OS for nacos-cli: $OSTYPE (uname: $uname_os)"
            return 1
        fi
    fi

    # Detect architecture
    local arch=""
    local uname_arch
    uname_arch=$(uname -m)
    case "$uname_arch" in
        x86_64|amd64)
            arch="amd64"
            ;;
        arm64|aarch64)
            arch="arm64"
            ;;
        *)
            print_warn "Unsupported architecture for nacos-cli: $uname_arch"
            return 1
            ;;
    esac
    local url="${DOWNLOAD_BASE_URL}/nacos-cli-${version}-${os}-${arch}.zip"
    local zip_filename="nacos-cli-${version}-${os}-${arch}.zip"
    
    # Download nacos-cli (with caching)
    local zip_file=$(download_nacos_cli "$version" "$os" "$arch")
    
    if [ -z "$zip_file" ]; then
        print_error "Failed to download nacos-cli"
        return 1
    fi
    
    print_success "Package ready: $zip_file"
    echo ""
    
    # Create temporary directory for extraction
    local tmp_dir
    tmp_dir=$(mktemp -d "/tmp/nacos-cli-extract-$$.XXXXXX") || {
        print_error "Failed to create temp directory for nacos-cli extraction"
        return 1
    }

    # Extract zip file
    print_info "Extracting nacos-cli..."
    if ! unzip -q "$zip_file" -d "$tmp_dir"; then
        print_error "Failed to extract zip file"
        rm -rf "$tmp_dir"
        return 1
    fi

    # Expected binary filename: nacos-cli-{version}-{os}-{arch} or nacos-cli-{version}-{os}-{arch}.exe
    local expected_binary_name="nacos-cli-${version}-${os}-${arch}"
    local expected_binary_name_exe="${expected_binary_name}.exe"
    local binary_path
    
    # For Windows, prioritize .exe files; for others, prioritize files without extension
    if [[ "$os" == "windows" ]]; then
        # Try .exe first for Windows
        binary_path=$(find "$tmp_dir" -name "$expected_binary_name_exe" -type f | head -1)
        # Fallback to non-.exe (shouldn't happen, but just in case)
        if [ -z "$binary_path" ]; then
            binary_path=$(find "$tmp_dir" -name "$expected_binary_name" -type f | head -1)
        fi
    else
        # For non-Windows, try without .exe first
        binary_path=$(find "$tmp_dir" -name "$expected_binary_name" -type f | head -1)
        # Fallback to .exe (shouldn't happen, but just in case)
        if [ -z "$binary_path" ]; then
            binary_path=$(find "$tmp_dir" -name "$expected_binary_name_exe" -type f | head -1)
        fi
    fi

    if [ -z "$binary_path" ] || [ ! -f "$binary_path" ]; then
        local expected_names="$expected_binary_name"
        if [[ "$os" == "windows" ]]; then
            expected_names="$expected_binary_name_exe (or $expected_binary_name)"
        else
            expected_names="$expected_binary_name (or $expected_binary_name_exe)"
        fi
        print_error "Binary file not found in package. Expected: $expected_names"
        print_info "Available files in package:"
        find "$tmp_dir" -type f | sed 's|^|  |'
        rm -rf "$tmp_dir"
        return 1
    fi

    # Ensure bin dir exists
    mkdir -p "$BIN_DIR"

    # Determine target binary name (add .exe for Windows)
    local target_binary_name="nacos-cli"
    if [[ "$os" == "windows" ]]; then
        target_binary_name="nacos-cli.exe"
    fi

    # Install binary
    if ! cp "$binary_path" "$BIN_DIR/$target_binary_name"; then
        print_error "Failed to copy nacos-cli to $BIN_DIR (permission denied?)"
        rm -rf "$tmp_dir"
        return 1
    fi

    # Set executable permission (not needed on Windows, but harmless)
    if ! chmod +x "$BIN_DIR/$target_binary_name" 2>/dev/null; then
        # On Windows, chmod might fail, which is fine
        if [[ "$os" != "windows" ]]; then
            print_warn "Failed to mark nacos-cli as executable: $BIN_DIR/$target_binary_name"
        fi
    fi

    # On macOS, add ad-hoc signature to avoid Gatekeeper killing the binary
    if [[ "$os" == "darwin" ]]; then
        if command -v codesign >/dev/null 2>&1; then
            if ! codesign --force --deep --sign - "$BIN_DIR/$target_binary_name" >/dev/null 2>&1; then
                print_warn "Failed to codesign nacos-cli (may be blocked by Gatekeeper): $BIN_DIR/$target_binary_name"
            fi
        else
            print_warn "codesign not found; nacos-cli may be blocked by Gatekeeper"
        fi
    fi

    # Cleanup
    rm -rf "$tmp_dir"

    print_success "nacos-cli $version installed to $BIN_DIR/$target_binary_name"
}

# ============================================================================
# Verification
# ============================================================================

verify_installation() {
    print_info "Verifying installation..."
    
    # Check if the symlink or file exists (use -e for both files and symlinks)
    if [ ! -e "$BIN_DIR/$SCRIPT_NAME" ]; then
        print_error "Installation failed: $BIN_DIR/$SCRIPT_NAME not found"
        return 1
    fi
    
    # Check if the symlink target exists (resolve and check the actual target)
    if [ -L "$BIN_DIR/$SCRIPT_NAME" ]; then
        local link_path="$BIN_DIR/$SCRIPT_NAME"
        # Follow the symlink to check if target is accessible
        if [ ! -e "$link_path" ]; then
            local target=$(readlink "$link_path")
            print_error "Installation failed: Broken symlink at $link_path"
            print_error "Target does not exist: $target"
            return 1
        fi
    fi
    
    if ! command -v $SCRIPT_NAME >/dev/null 2>&1; then
        print_info "Configuring PATH automatically..."
        
        # Detect shell configuration file
        local shell_config=""
        if [ -n "$SHELL" ]; then
            case "$SHELL" in
                */zsh)
                    shell_config="$HOME/.zshrc"
                    ;;
                */bash)
                    shell_config="$HOME/.bashrc"
                    ;;
            esac
        fi
        
        # Fallback: detect by checking which file exists
        if [ -z "$shell_config" ]; then
            if [ -f "$HOME/.zshrc" ]; then
                shell_config="$HOME/.zshrc"
            elif [ -f "$HOME/.bashrc" ]; then
                shell_config="$HOME/.bashrc"
            else
                # Create .bashrc if nothing exists
                shell_config="$HOME/.bashrc"
            fi
        fi
        
        # Check if PATH is already configured
        local path_export="export PATH=\"$BIN_DIR:\$PATH\""
        if ! grep -qF "$BIN_DIR" "$shell_config" 2>/dev/null; then
            echo "" >> "$shell_config"
            echo "# Added by nacos-setup installer" >> "$shell_config"
            echo "$path_export" >> "$shell_config"
            print_success "PATH configured in $shell_config"
        else
            print_info "PATH already configured in $shell_config"
        fi
        
        # Note: We cannot automatically source in the current shell due to shell limitations
        # The script runs in a subshell, sourcing only affects the subshell, not the parent shell
        # But we can use the command directly via absolute path
        print_info "PATH will be available in new terminal sessions"
        echo ""
        return 0
    fi
    
    print_success "Installation verified successfully!"
    echo ""
    
    return 0
}


# ============================================================================
# Post-installation Info
# ============================================================================

print_usage_info() {
    local version="${INSTALLED_VERSION:-unknown}"
    local install_location="unknown"
    if [ -L "$INSTALL_BASE_DIR/$CURRENT_LINK" ]; then
        install_location="$INSTALL_BASE_DIR/$(readlink "$INSTALL_BASE_DIR/$CURRENT_LINK")"
    fi

    echo "========================================"
    echo "  Nacos Setup Installation Complete"
    echo "========================================"
    echo ""
    echo "Version: $version"
    echo "Installation location: $install_location"
    echo "Global command: $SCRIPT_NAME"
    echo ""
    echo "Quick Start:"
    echo ""
    echo "  # Show help"
    echo "  $SCRIPT_NAME --help"
    echo ""
    echo "  # Install Nacos standalone"
    echo "  $SCRIPT_NAME -v 3.1.1"
    echo ""
    echo "  # Install Nacos cluster"
    echo "  $SCRIPT_NAME -c prod -n 3"
    echo ""
    echo "  # Configure datasource"
    echo "  $SCRIPT_NAME --datasource-conf"
    echo ""
    echo "Documentation: https://nacos.io"
    echo ""
    echo "========================================"
}

# ============================================================================
# Version Check
# ============================================================================

check_installed_version() {
    # Read active version from current symlink
    if [ -L "$INSTALL_BASE_DIR/$CURRENT_LINK" ]; then
        local target=$(readlink "$INSTALL_BASE_DIR/$CURRENT_LINK")
        local active_dir="$INSTALL_BASE_DIR/$target"
        if [ -f "$active_dir/.version" ]; then
            local version=$(cat "$active_dir/.version")
            print_info "Installed nacos-setup version: $version"
            print_info "Installation location: $active_dir"
            return 0
        fi
    fi

    print_warn "nacos-setup is not installed or version information not found"
    return 1
}

# ============================================================================
# Uninstallation
# ============================================================================

uninstall_nacos_setup() {
    print_info "Uninstalling nacos-setup (active version)..."

    # If current symlink exists, remove the target directory
    if [ -L "$INSTALL_BASE_DIR/$CURRENT_LINK" ]; then
        local target=$(readlink "$INSTALL_BASE_DIR/$CURRENT_LINK")
        local target_dir="$INSTALL_BASE_DIR/$target"
        if [ -d "$target_dir" ]; then
            rm -rf "$target_dir"
            print_success "Removed $target_dir"
        fi

        # Remove current symlink
        rm -f "$INSTALL_BASE_DIR/$CURRENT_LINK"
        print_success "Removed $INSTALL_BASE_DIR/$CURRENT_LINK"
    else
        print_warn "No active installation found at $INSTALL_BASE_DIR/$CURRENT_LINK"
    fi

    # Remove global command
    if [ -L "$BIN_DIR/$SCRIPT_NAME" ] || [ -f "$BIN_DIR/$SCRIPT_NAME" ]; then
        rm -f "$BIN_DIR/$SCRIPT_NAME"
        print_success "Removed $BIN_DIR/$SCRIPT_NAME"
    fi

    print_success "Uninstallation completed!"
    echo ""
}

# ============================================================================
# Main
# ============================================================================

main() {
    echo ""
    echo "========================================"
    echo "  Nacos Setup Installer"
    echo "========================================"
    echo ""

    # Parse arguments
    local install_cli=false
    local only_cli=false
    local cli_version=""

    while [[ $# -gt 0 ]]; do
        case "$1" in
            version|--version)
                check_installed_version
                exit $?
                ;;
            uninstall|--uninstall|-u)
                uninstall_nacos_setup
                exit 0
                ;;
            --cli)
                install_cli=true
                only_cli=true
                shift
                ;;
            -v|--version-cli)
                if [ -z "$2" ] || [[ "$2" == -* ]]; then
                    print_error "Option $1 requires a version number"
                    echo ""
                    print_info "Usage: ./nacos-installer.sh --cli -v <version>"
                    exit 1
                fi
                if [[ "$install_cli" != true ]]; then
                    print_error "Option $1 can only be used with --cli"
                    echo ""
                    print_info "Usage: ./nacos-installer.sh --cli -v <version>"
                    exit 1
                fi
                cli_version="$2"
                shift 2
                ;;
            --help|-h)
                echo "Usage: curl -fsSL https://nacos.io/installer.sh | sudo bash"
                echo ""
                echo "Install nacos-setup and nacos-cli tools for managing Nacos instances."
                echo ""
                echo "Options:"
                echo "  (none)              Install nacos-setup"
                echo "  --cli               Install nacos-cli only"
                echo "  -v, --version-cli   Specify nacos-cli version (with --cli)"
                echo "  version             Show installed version"
                echo "  uninstall, -u       Uninstall nacos-setup"
                echo "  --help, -h          Show this help message"
                echo ""
                echo "Examples:"
                echo "  ./nacos-installer.sh --cli              Install latest nacos-cli"
                echo "  ./nacos-installer.sh --cli -v 0.0.3     Install nacos-cli v0.0.3"
                echo ""
                echo "After installation, use 'nacos-setup' command to manage Nacos:"
                echo "  nacos-setup --help              Show nacos-setup help"
                echo "  nacos-setup -v 3.1.1            Install Nacos standalone"
                echo "  nacos-setup -c prod -n 3        Install Nacos cluster"
                echo ""
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                echo ""
                print_info "Use --help for usage information"
                exit 1
                ;;
        esac
    done

    # Apply CLI version if specified
    if [ -n "$cli_version" ]; then
        NACOS_CLI_VERSION="$cli_version"
    fi
    
    # Check requirements
    if ! check_requirements "${only_cli:+onlycli}"; then
        print_error "Requirements check failed"
        print_info "Try running with sudo: curl -fsSL https://nacos.io/installer.sh | sudo bash"
        exit 1
    fi

    if [[ "$only_cli" == true ]]; then
        echo ""
        install_nacos_cli
        exit $?
    fi

    # Install
    install_nacos_setup

    # Verify
    if verify_installation; then
        print_usage_info

        # Install nacos-cli if --cli flag is provided
        if [[ "$install_cli" == true ]]; then
            echo ""
            install_nacos_cli
        fi

        # After installation, offer to install Nacos (default version)
        echo ""
        # Try to detect default Nacos version from installed script
        detected_default_version="3.1.1"
        installed_script="$INSTALL_BASE_DIR/$CURRENT_LINK/bin/$SCRIPT_NAME"
        if [ -f "$installed_script" ]; then
            v=$(sed -n 's/^DEFAULT_VERSION="\(.*\)"/\1/p' "$installed_script" || true)
            if [ -n "$v" ]; then
                detected_default_version="$v"
            fi
        fi

        read -p "Do you want to install Nacos $detected_default_version now? (Y/n): " -r REPLY
        echo ""
        if [[ "$REPLY" =~ ^[Yy]?$ ]] || [[ -z "$REPLY" ]]; then
            print_info "Installing Nacos $detected_default_version..."
            # Always use absolute path to ensure it works even if PATH is not yet loaded
            "$BIN_DIR/$SCRIPT_NAME" -v "$detected_default_version"
        else
            print_info "Skipping Nacos installation."
            print_info "To install later, run: $SCRIPT_NAME -v $detected_default_version"
            print_info "Or use absolute path: $BIN_DIR/$SCRIPT_NAME -v $detected_default_version"
        fi

        exit 0
    else
        print_error "Installation verification failed"
        exit 1
    fi
}

# Run main
main "$@"
