#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${SIGN_SERVER_BUILDER_IMAGE:-signserver341-builder:local}"
BUILDER_DIR="${ROOT}/.build/docker/signserver341-builder"
SIGN_SERVER_HOME="${ROOT}/signserver"
SIGN_SERVER_BUILD_ROOT="${SIGN_SERVER_BUILD_ROOT:-/Users/pruiz/Develop/mine/SignServerBuild.git}"
RPM_BUILD_DIR="${ROOT}/.artifacts/rpm-build"
RPM_SPEC_SOURCE="${SIGN_SERVER_BUILD_ROOT}/packaging/SignServer.spec"

usage() {
    printf '%s\n' "Usage: $0 {build-image|compile|pdfsigner-test|build-rpm|shell}"
}

docker_run() {
    docker run --rm --platform linux/amd64 \
        -e SIGNSERVER_HOME=/workspace/signserver \
        -e JBOSS_HOME=/var/lib/jbossas \
        -e APPSRV_HOME=/var/lib/jbossas \
        -v "${SIGN_SERVER_HOME}:/workspace/signserver" \
        "$@"
}

build_rpm() {
    if [ ! -f "${RPM_SPEC_SOURCE}" ]; then
        printf 'RPM spec not found: %s\n' "${RPM_SPEC_SOURCE}" >&2
        exit 1
    fi

    rm -rf "${RPM_BUILD_DIR}"
    mkdir -p "${RPM_BUILD_DIR}/BUILD" "${RPM_BUILD_DIR}/BUILDROOT" "${RPM_BUILD_DIR}/RPMS" \
        "${RPM_BUILD_DIR}/SOURCES" "${RPM_BUILD_DIR}/SPECS" "${RPM_BUILD_DIR}/SRPMS" \
        "${RPM_BUILD_DIR}/signserver"

    git -C "${ROOT}" ls-files | tar -C "${ROOT}" -cf - -T - | tar -C "${RPM_BUILD_DIR}/signserver" -xf -
    cp "${RPM_SPEC_SOURCE}" "${RPM_BUILD_DIR}/SPECS/SignServer.spec"

    local version release source_rev packaging_rev git_commit git_branch full_version_string
    version="$(awk -F= '/^app.version.number=/ { print $2 }' "${ROOT}/signserver/res/compile.properties")"
    source_rev="$(git -C "${ROOT}" rev-list --count HEAD)"
    packaging_rev="$(git -C "${SIGN_SERVER_BUILD_ROOT}" rev-list --count HEAD)"
    release="${RPM_RELEASE:-${source_rev}.${RPM_RELEASE_SUFFIX:-${packaging_rev}}}"
    git_commit="$(git -C "${ROOT}" rev-parse HEAD)"
    git_branch="$(git -C "${ROOT}" rev-parse --abbrev-ref HEAD)"
    full_version_string="${version}.${source_rev}-${git_commit:0:9} (${git_branch})"

    printf 'Building SignServer RPM version %s release %s\n' "${version}" "${release}"

    docker run --rm --platform linux/amd64 \
        -e SIGNSERVER_HOME=/opt/signserver \
        -e JBOSS_HOME=/var/lib/jbossas \
        -e APPSRV_HOME=/var/lib/jbossas \
        -v "${RPM_BUILD_DIR}:/workspace/rpm-build" \
        "${IMAGE}" \
        rpmbuild -bb \
            --define "version ${version}" \
            --define "release ${release}" \
            --define "_topdir /workspace/rpm-build" \
            --define "_builddir /workspace/rpm-build/signserver/signserver" \
            --define "full_version_string ${full_version_string}" \
            --define 'setup echo "do nothing."' \
            /workspace/rpm-build/SPECS/SignServer.spec
}

case "${1:-}" in
    build-image)
        docker build --platform linux/amd64 -t "${IMAGE}" "${BUILDER_DIR}"
        ;;
    compile)
        docker_run -w /workspace/signserver "${IMAGE}" bash ./bin/ant build
        ;;
    pdfsigner-test)
        docker_run -w /workspace/signserver/modules/SignServer-Module-PDFSigner "${IMAGE}" \
            ant test:runone -Dtest.runone=PDFSignerUnitTest
        ;;
    build-rpm)
        build_rpm
        ;;
    shell)
        docker_run -it -w /workspace/signserver "${IMAGE}" /bin/bash
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
