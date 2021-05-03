#!/bin/bash
set -e # exit on the first error
./gradlew jsBrowserDistribution
rm -rf build/gh-pages 2> /dev/null
git worktree add -f build/gh-pages gh-pages
cp -r build/distributions/* build/gh-pages
if [ -z $1 ] ; then
  echo "Use ./deploy.sh <version> to deploy a specific version"
else
  version=$1
  git tag -f $version
  pushd build/gh-pages
  git add *
  git commit -m "Version $version"
  git push origin gh-pages
  popd
fi