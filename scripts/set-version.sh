#!/usr/bin/env bash
#
# Sets or verifies the DepClean version in every file that carries it.
#
#   scripts/set-version.sh <version>          set <version> everywhere (X.Y.Z or X.Y.Z-SNAPSHOT)
#   scripts/set-version.sh --check            fail if any location disagrees with the root pom.xml
#   scripts/set-version.sh --readme-only <v>  update only README.md (used by the Deploy workflow)
#
# The root pom.xml is the single source of truth. The Maven modules are handled by
# versions:set; everything else (Gradle plugin, test fixtures, READMEs) is a literal
# string that this script keeps in sync. Every substitution is anchored on a DepClean
# coordinate so unrelated versions (e.g. jcabi-manifests 2.2.0) are never touched.
#
# Exception: the root README.md documents the plugin as consumed from Maven Central, so
# it carries the latest RELEASE and is left alone when the pom moves to a -SNAPSHOT;
# --check then only requires it to be a single, consistent release version.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

VERSION_RE='^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$'
RELEASE_RE='^[0-9]+\.[0-9]+\.[0-9]+$'

die() { echo "error: $*" >&2; exit 1; }

pom_version() {
  ./mvnw -ntp -q help:evaluate -Dexpression=project.version -DforceStdout
}

README=README.md

# Files where <version> is on the line right after <artifactId>depclean-maven-plugin</artifactId>
MAVEN_XML_FILES=(
  depclean-maven-plugin/src/test/resources/DepCleanMojoResources/pom-debloated.xml
  depclean-core/src/test/resources/basic_spring_maven_project/pom.xml
)

GRADLE_BUILD=depclean-gradle-plugin/build.gradle
GRADLE_README=depclean-gradle-plugin/README.md
GRADLE_FIXTURES=(depclean-gradle-plugin/src/test/resources-fts/*/build.gradle)

# apply <file> <anchor-regex> <sed-expression>
# Applies the substitution and fails if the anchor does not occur in the file,
# so a moved or renamed snippet is detected instead of silently skipped.
apply() {
  local file=$1 anchor=$2 expr=$3
  [[ -f "$file" ]] || die "missing file: $file"
  grep -Eq -- "$anchor" "$file" || die "anchor '$anchor' not found in $file"
  sed -i -E -- "$expr" "$file"
}

# The README carries the version both in <plugin> snippets and in
# `mvn se.kth.castor:depclean-maven-plugin:<version>:depclean` command lines.
set_readme() {
  local v=$1
  apply "$README" '<artifactId>depclean-maven-plugin</artifactId>' \
    "/<artifactId>depclean-maven-plugin<\/artifactId>/{n;s|<version>[^<]*</version>|<version>$v</version>|}"
  apply "$README" 'se\.kth\.castor:depclean-maven-plugin:[^:]+:depclean' \
    "s|(se\.kth\.castor:depclean-maven-plugin:)[^:]+(:depclean)|\1$v\2|g"
}

# Version the README currently documents (first <plugin> snippet).
readme_version() {
  sed -n -E "/<artifactId>depclean-maven-plugin<\/artifactId>/{n;s|.*<version>([^<]*)</version>.*|\1|p;q}" "$README"
}

set_literals() {
  local v=$1 f
  for f in "${MAVEN_XML_FILES[@]}"; do
    apply "$f" '<artifactId>depclean-maven-plugin</artifactId>' \
      "/<artifactId>depclean-maven-plugin<\/artifactId>/{n;s|<version>[^<]*</version>|<version>$v</version>|}"
  done
  # README documents the latest release only; a SNAPSHOT bump must not touch it.
  if [[ "$v" =~ $RELEASE_RE ]]; then
    set_readme "$v"
  fi
  apply "$GRADLE_BUILD" "^version = '" "s|^version = '[^']*'|version = '$v'|"
  apply "$GRADLE_BUILD" "depcleanVersion = '" "s|(depcleanVersion = ')[^']*'|\1$v'|"
  apply "$GRADLE_README" "depclean-gradle-plugin' version '" "s|(depclean-gradle-plugin' version ')[^']*'|\1$v'|"
  for f in "${GRADLE_FIXTURES[@]}"; do
    apply "$f" 'depclean-gradle-plugin:' "s|(depclean-gradle-plugin:)[^']*'|\1$v'|"
  done
}

set_version() {
  local v=$1
  ./mvnw -ntp -q versions:set -DnewVersion="$v" -DgenerateBackupPoms=false -DprocessAllModules=true
  set_literals "$v"
  echo "version set to $v"
}

# Re-applies the literal substitutions with the pom version on a scratch copy of the
# tree and diffs; any difference means a location has drifted from the pom. The README
# is normalised to the release it documents (the pom version when that is a release),
# so the diff catches both drift from a release and inconsistent occurrences.
check_version() {
  local v rv tmp rc=0
  v=$(pom_version)
  [[ "$v" =~ $VERSION_RE ]] || die "unexpected pom version '$v'"
  rv=$(readme_version)
  [[ "$rv" =~ $RELEASE_RE ]] || { echo "MISMATCH $README: documents '$rv', expected a release version (X.Y.Z)"; rc=1; }
  tmp=$(mktemp -d)
  # shellcheck disable=SC2064  # expand now: $tmp is local and gone when the trap fires
  trap "rm -rf '$tmp'" EXIT
  local files=("${MAVEN_XML_FILES[@]}" "$README" "$GRADLE_BUILD" "$GRADLE_README" "${GRADLE_FIXTURES[@]}")
  local f
  for f in "${files[@]}"; do
    mkdir -p "$tmp/$(dirname "$f")"
    cp -- "$f" "$tmp/$f"
  done
  (
    cd "$tmp"
    set_literals "$v"
    [[ "$v" =~ $RELEASE_RE ]] || set_readme "$rv"
  )
  for f in "${files[@]}"; do
    if ! diff -q -- "$f" "$tmp/$f" >/dev/null; then
      echo "MISMATCH $f"
      diff -u -- "$f" "$tmp/$f" | grep -E '^[-+][^-+]' || true
      rc=1
    fi
  done
  if [[ $rc -eq 0 ]]; then
    echo "all version references match pom.xml ($v)"
  else
    echo "run: scripts/set-version.sh $v" >&2
    [[ "$v" =~ $RELEASE_RE ]] || echo "and, for $README: scripts/set-version.sh --readme-only <latest release>" >&2
  fi
  return $rc
}

case "${1:-}" in
  --check)
    [[ $# -eq 1 ]] || die "--check takes no arguments"
    check_version
    ;;
  --readme-only)
    [[ $# -eq 2 && "$2" =~ $RELEASE_RE ]] || die "usage: $0 --readme-only X.Y.Z"
    set_readme "$2"
    ;;
  -h|--help|"")
    sed -n '3,/^$/p' "$0" | sed 's/^# \{0,1\}//'
    [[ -n "${1:-}" ]] || exit 1
    ;;
  *)
    [[ $# -eq 1 && "$1" =~ $VERSION_RE ]] || die "invalid version '$1' (expected X.Y.Z or X.Y.Z-SNAPSHOT)"
    set_version "$1"
    ;;
esac
