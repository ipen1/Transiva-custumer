#!/usr/bin/env bash
set -euo pipefail

if (( $# == 0 )); then
    echo "Usage: $0 PACKAGE..." >&2
    exit 2
fi

work_dir=$(mktemp -d)
trap 'sudo rm -rf -- "$work_dir"' EXIT
chmod 0755 "$work_dir"

# Both callers run on Ubuntu 24.04. Use the Ubuntu archive keyring and keep
# the runner's global source configuration untouched.
cat > "$work_dir/ubuntu.sources" <<'SOURCES'
Types: deb
URIs: https://archive.ubuntu.com/ubuntu/
Suites: noble noble-updates noble-backports
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg

Types: deb
URIs: https://security.ubuntu.com/ubuntu/
Suites: noble-security
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
SOURCES
chmod 0644 "$work_dir/ubuntu.sources"

packages=("$@")
network_options=(
    -o Acquire::Retries=1
    -o Acquire::http::Timeout=15
    -o Acquire::https::Timeout=15
)

install_from() {
    local lists_dir=$1
    shift
    local -a options=("${network_options[@]}" -o "Dir::State::lists=$lists_dir" "$@")
    sudo apt-get "${options[@]}" -o APT::Update::Error-Mode=any update &&
        sudo env DEBIAN_FRONTEND=noninteractive apt-get "${options[@]}" install -y -- "${packages[@]}"
}

mkdir "$work_dir/archive-lists" "$work_dir/runner-lists"
if install_from "$work_dir/archive-lists"     -o "Dir::Etc::sourcelist=$work_dir/ubuntu.sources" -o Dir::Etc::sourceparts=-; then
    exit 0
fi

echo "::warning::Ubuntu archive installation failed. Trying the runner's configured APT sources."
if install_from "$work_dir/runner-lists"; then
    exit 0
fi

echo "::error::Could not install Ubuntu packages from either source: ${packages[*]}" >&2
exit 100
