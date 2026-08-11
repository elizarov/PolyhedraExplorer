#!/bin/bash
set -euo pipefail

if [[ $# -ne 1 || ( $1 != "patch" && ! $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ) ]]; then
  echo "Usage: ./deploy.sh <patch|major.minor.patch>" >&2
  exit 2
fi

if [[ $1 == "patch" ]]; then
  remote_tags="$(git ls-remote --tags --refs origin)"
  latest="$({ printf '%s\n' "$remote_tags" | sed 's|.*refs/tags/||' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true; } | sort -V | tail -n 1)"
  if [[ -z "$latest" ]]; then
    version="0.0.1"
  else
    IFS=. read -r major minor patch <<< "$latest"
    version="$major.$minor.$((patch + 1))"
  fi
else
  version="$1"
fi

head="$(git rev-parse HEAD)"
echo "Releasing $version from $head"

if git show-ref --verify --quiet "refs/tags/$version"; then
  tagged="$(git rev-list -n 1 "$version")"
  if [[ "$tagged" != "$head" ]]; then
    echo "Tag $version already points to $tagged, not HEAD $head" >&2
    exit 1
  fi
else
  git tag "$version"
fi

git push origin "refs/tags/$version"
