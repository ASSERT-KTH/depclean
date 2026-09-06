#!/usr/bin/env bash
#
# Drives a DepClean release end to end, one step at a time.
#
#   scripts/release.sh prepare <X.Y.Z>   bump every version reference, sanity-build, open the release PR
#   scripts/release.sh publish <X.Y.Z>   dispatch the Deploy workflow on master and wait for it
#   scripts/release.sh finish  <X.Y.Z>   publish the draft GitHub release, open the next-SNAPSHOT PR
#
# Options:  --dry-run   print the commands that would change something instead of running them
# Env:      DEPCLEAN_REPO   GitHub repository to release from (default: ASSERT-KTH/depclean)
#
# Flow: prepare -> merge the PR -> publish -> approve the run in the `release`
# environment -> finish. Each step checks its own preconditions and can be re-run.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

REPO=${DEPCLEAN_REPO:-ASSERT-KTH/depclean}
REPO_URL="https://github.com/$REPO.git"
CENTRAL_BASE="https://repo1.maven.org/maven2/se/kth/castor/depclean-maven-plugin"
RELEASE_RE='^[0-9]+\.[0-9]+\.[0-9]+$'
DRY_RUN=0

die()  { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

# run <cmd...>: executes, or only prints under --dry-run
run() {
  if [[ $DRY_RUN -eq 1 ]]; then
    printf '[dry-run]'; printf ' %q' "$@"; echo
  else
    "$@"
  fi
}

need() { command -v "$1" >/dev/null || die "'$1' is required"; }

pom_version() {
  ./mvnw -ntp -q help:evaluate -Dexpression=project.version -DforceStdout
}

# Owner of the `origin` remote; PR heads are pushed there (fork or upstream alike).
origin_owner() {
  git remote get-url origin | sed -E 's#^.*github\.com[:/]([^/]+)/.*$#\1#'
}

require_clean_master() {
  [[ -z "$(git status --porcelain)" ]] || die "working tree is not clean"
  [[ "$(git branch --show-current)" == master ]] || die "must be on master"
  git fetch --quiet "$REPO_URL" master
  [[ "$(git rev-parse HEAD)" == "$(git rev-parse FETCH_HEAD)" ]] \
    || die "local master differs from $REPO master; sync first (git merge --ff-only FETCH_HEAD)"
}

# open_pr <branch> <title> <body>
open_pr() {
  local branch=$1 title=$2 body=$3
  run git push --set-upstream origin "$branch"
  run gh pr create --repo "$REPO" --base master --head "$(origin_owner):$branch" \
    --title "$title" --body "$body"
}

next_snapshot() {
  local IFS=. ; read -r major minor patch <<<"$1"
  echo "$major.$minor.$((patch + 1))-SNAPSHOT"
}

# ---------------------------------------------------------------------------

cmd_prepare() {
  local v=$1
  local branch="release/$v"
  require_clean_master
  ! git rev-parse -q --verify "refs/heads/$branch" >/dev/null || die "branch $branch already exists"
  ! gh release view "$v" --repo "$REPO" >/dev/null 2>&1 || die "release $v already exists on $REPO"

  info "bumping all version references to $v"
  run git switch -c "$branch"
  run scripts/set-version.sh "$v"

  info "sanity build (no tests)"
  run ./mvnw -ntp -q clean install -DskipTests
  run ./depclean-gradle-plugin/gradlew -p depclean-gradle-plugin -q build -x test

  run git add -A
  run git commit -m "release: bump version to $v"
  open_pr "$branch" "release: bump version to $v" \
"Prepares the $v release: every version reference is set to \`$v\` via \`scripts/set-version.sh\`.

After merging, run \`scripts/release.sh publish $v\`."
}

cmd_publish() {
  local v=$1 prev run_id url
  require_clean_master
  [[ "$(pom_version)" == "$v" ]] || die "master pom.xml is at $(pom_version), not $v; merge the release PR first"
  ! git ls-remote --exit-code --tags "$REPO_URL" "refs/tags/$v" >/dev/null || die "tag $v already exists on $REPO"
  prev=$(gh release list --repo "$REPO" --exclude-drafts --exclude-pre-releases --limit 1 \
         --json tagName --jq '.[0].tagName')
  [[ -n "$prev" ]] || die "could not determine previous release"
  info "dispatching Deploy on $REPO master: previousVersion=$prev newVersion=$v"
  run gh workflow run deploy.yml --repo "$REPO" --ref master -f previousVersion="$prev" -f newVersion="$v"
  [[ $DRY_RUN -eq 0 ]] || return 0

  sleep 10
  run_id=$(gh run list --repo "$REPO" --workflow deploy.yml --limit 1 --json databaseId --jq '.[0].databaseId')
  url=$(gh run view "$run_id" --repo "$REPO" --json url --jq .url)
  info "run started: $url"
  info "approve it in the 'release' environment when prompted; waiting for completion..."
  if gh run watch "$run_id" --repo "$REPO" --exit-status >/dev/null 2>&1; then
    info "Deploy succeeded. Next: scripts/release.sh finish $v"
  else
    die "Deploy run failed or was cancelled: $url"
  fi
}

cmd_finish() {
  local v=$1 next branch
  need curl
  info "waiting for $v on Maven Central"
  local i
  for ((i = 0; i < 60; i++)); do
    curl -fsIL "$CENTRAL_BASE/$v/depclean-maven-plugin-$v.pom" -o /dev/null && break
    [[ $DRY_RUN -eq 0 ]] || break
    sleep 60
  done
  ((i < 60)) || die "$v not visible on Maven Central after 60 minutes"

  info "publishing draft GitHub release $v"
  run gh release edit "$v" --repo "$REPO" --draft=false --latest

  # Deploy pushed a README commit to master, so re-sync before branching.
  [[ -z "$(git status --porcelain)" ]] || die "working tree is not clean"
  [[ "$(git branch --show-current)" == master ]] || die "must be on master"
  git fetch --quiet "$REPO_URL" master
  run git merge --ff-only FETCH_HEAD

  next=$(next_snapshot "$v")
  branch="chore/next-snapshot-$next"
  info "bumping to $next"
  run git switch -c "$branch"
  run scripts/set-version.sh "$next"
  run git add -A
  run git commit -m "chore: bump version to $next"
  open_pr "$branch" "chore: bump version to $next" \
"Post-release housekeeping after $v: all version references move to \`$next\` via \`scripts/set-version.sh\`."
}

# ---------------------------------------------------------------------------

args=()
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=1 ;;
    *) args+=("$a") ;;
  esac
done
set -- "${args[@]+"${args[@]}"}"

cmd=${1:-}
version=${2:-}
case "$cmd" in
  prepare|publish|finish)
    [[ $# -eq 2 ]] || die "usage: $0 $cmd <X.Y.Z> [--dry-run]"
    [[ "$version" =~ $RELEASE_RE ]] || die "invalid release version '$version' (expected X.Y.Z)"
    need gh; need git
    gh auth status >/dev/null 2>&1 || die "gh is not authenticated (gh auth login)"
    "cmd_$cmd" "$version"
    ;;
  -h|--help|"")
    sed -n '3,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'
    [[ -n "$cmd" ]] || exit 1
    ;;
  *)
    die "unknown command '$cmd' (prepare|publish|finish)"
    ;;
esac
