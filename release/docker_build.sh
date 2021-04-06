#!/bin/sh
# Build and optionally push docker image for Systems service
# This is the job run in Jenkins as part of job Java-MultiService-Build-Publish-Deploy-<ENV>
#   and job Tapis jobs->DEV->release-systems-start
# Environment name must be passed in as first argument
# Existing docker login is used for push
# Docker image is created with a unique tag: tapis/<SVC_NAME>-<ENV>-<VER>-<COMMIT>-<YYYYmmddHHMM>
#   - other tags are created and updated as appropriate
#
# REPO env var may be set to push to $REPO/${SVC_NAME}. Default is tapis/${SVC_NAME}
# Env var TAPIS_DEPLOY_MANUAL may be set to "true" to indicate it is a manual deployment and the
#   image should also be tagged with $ENV

PrgName=$(basename "$0")

USAGE="Usage: $PrgName { dev staging prod } [ -push ]"

SVC_NAME="systems"

if [ -z "$REPO" ]; then
  REPO=tapis
fi

BUILD_DIR=../tapis-systemsapi/target
ENV=$1

# Check number of arguments
if [ $# -lt 1 -o $# -gt 2 ]; then
  echo $USAGE
  exit 1
fi

# Check that env name is valid
if [ "$ENV" != "dev" -a "$ENV" != "staging" -a "$ENV" != "prod" ]; then
  echo $USAGE
  exit 1
fi

# Check second arg
if [ $# -eq 2 -a "x$2" != "x-push" ]; then
  echo $USAGE
  exit 1
fi

# Determine absolute path to location from which we are running
#  and change to that directory.
export RUN_DIR=$(pwd)
export PRG_RELPATH=$(dirname "$0")
cd "$PRG_RELPATH"/. || exit
export PRG_PATH=$(pwd)

# Make sure service has been built
if [ ! -d "$BUILD_DIR" ]; then
  echo "Build directory missing. Please build. Directory: $BUILD_DIR"
  exit 1
fi

# Copy Dockerfile to build dir
cp Dockerfile $BUILD_DIR

# Move to the build directory
cd $BUILD_DIR || exit

# Set variables used for build
VER=$(cat classes/tapis.version)
GIT_BRANCH_LBL=$(awk '{print $1}' classes/git.info)
GIT_COMMIT_LBL=$(awk '{print $2}' classes/git.info)
TAG_UNIQ="${REPO}/${SVC_NAME}:${ENV}-${VER}-$(date +%Y%m%d%H%M)-${GIT_COMMIT}"
TAG_RELEASE_CANDIDATE="${REPO}/${SVC_NAME}:${VER}-rc"
# TAG_ENV_VER="${REPO}/${SVC_NAME}:${ENV}-${VER}"
TAG_ENV="${REPO}/${SVC_NAME}:${ENV}"
TAG_LATEST="${REPO}/${SVC_NAME}:latest"
TAG_LOCAL="${REPO}/${SVC_NAME}:dev_local"

# If branch name is UNKNOWN or empty as might be the case in a jenkins job then
#   set it to GIT_BRANCH. Jenkins jobs should have this set in the env.
if [ -z "$GIT_BRANCH_LBL" -o "x$GIT_BRANCH_LBL" = "xUNKNOWN" ]; then
  GIT_BRANCH_LBL=$(echo "$GIT_BRANCH" | awk -F"/" '{print $2}')
fi

# Build image from Dockerfile
echo "Building local image using primary tag: $TAG_UNIQ"
echo "  ENV=        ${ENV}"
echo "  VER=        ${VER}"
echo "  GIT_BRANCH_LBL= ${GIT_BRANCH_LBL}"
echo "  GIT_COMMIT_LBL= ${GIT_COMMIT_LBL}"
docker build -f Dockerfile \
   --label VER="${VER}" --label GIT_COMMIT="${GIT_COMMIT_LBL}" --label GIT_BRANCH="${GIT_BRANCH_LBL}" \
    -t "${TAG_UNIQ}" .

# Create other tags for remote repo
# echo "Creating ENV_VER image tag: $TAG_ENV_VER"
# docker tag "$TAG_UNIQ" "$TAG_ENV_VER"
echo "Creating RELEASE_CANDIDATE image tag: $TAG_RELEASE_CANDIDATE"
docker tag "$TAG_UNIQ" "$TAG_RELEASE_CANDIDATE"

echo "Creating image for local testing user tag: $TAG_LOCAL"
docker tag "$TAG_UNIQ" "$TAG_LOCAL"

# Push to remote repo
if [ "x$2" = "x-push" ]; then
  if [ "$ENV" = "prod" ]; then
    echo "Creating third image tag for prod env: $TAG_LATEST"
    docker tag "$TAG_UNIQ" "$TAG_LATEST"
  fi
  echo "Pushing images to docker hub."
  # NOTE: Use current login. Jenkins job does login
  docker push "$TAG_UNIQ"
  docker push "$TAG_RELEASE_CANDIDATE"
#  docker push "$TAG_ENV_VER"
  if [ "x$TAPIS_DEPLOY_MANUAL" = "xtrue" ]; then
    echo "Creating ENV image tag: $TAG_ENV"
    docker tag "$TAG_UNIQ" "$TAG_ENV"
    docker push "$TAG_ENV"
  fi
  if [ "$ENV" = "prod" ]; then
    docker push "$TAG_LATEST"
  fi
fi
cd "$RUN_DIR"
