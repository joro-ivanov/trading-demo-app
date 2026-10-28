#!/usr/bin/env bash
# Installs Mozilla's official Firefox and geckodriver on Debian/Ubuntu (amd64 or arm64), for the
# WebdriverIO component tests. Used by Dockerfile.test and the devcontainer, so both test with the
# same browser. Run as root (or with sudo).
#   Firefox:     Mozilla's APT repository, packages.mozilla.org (official amd64 + arm64 builds)
#   geckodriver: Mozilla's GitHub releases (official linux64 + linux-aarch64 builds)
set -euo pipefail

GECKODRIVER_VERSION="${GECKODRIVER_VERSION:-0.36.0}"

case "$(dpkg --print-architecture)" in
  amd64) geckodriver_asset=linux64 ;;
  arm64) geckodriver_asset=linux-aarch64 ;;
  *) echo "Unsupported architecture: $(dpkg --print-architecture)" >&2; exit 1 ;;
esac

apt-get update
apt-get install -y --no-install-recommends ca-certificates curl gnupg

# Mozilla's documented APT setup; the pin makes its firefox win over any distro firefox/firefox-esr.
install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://packages.mozilla.org/apt/repo-signing-key.gpg -o /etc/apt/keyrings/packages.mozilla.org.asc
echo "deb [signed-by=/etc/apt/keyrings/packages.mozilla.org.asc] https://packages.mozilla.org/apt mozilla main" \
  > /etc/apt/sources.list.d/mozilla.list
printf 'Package: *\nPin: origin packages.mozilla.org\nPin-Priority: 1000\n' > /etc/apt/preferences.d/mozilla
apt-get update
apt-get install -y --no-install-recommends firefox

curl -fsSL "https://github.com/mozilla/geckodriver/releases/download/v${GECKODRIVER_VERSION}/geckodriver-v${GECKODRIVER_VERSION}-${geckodriver_asset}.tar.gz" \
  | tar -xz -C /usr/local/bin geckodriver

rm -rf /var/lib/apt/lists/*
firefox --version
geckodriver --version | head -1
