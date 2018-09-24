#!/bin/bash

set -ex

TAG=$1
DOCKERFILE_PATH=$2

NAMESPACE="FINRAOSS"
REPOSITORY="herd-mdl"

## Publish herd-mdl's 'build-and-deploy' image to docker-hub.


login_hub() {
  echo "Logging onto docker hub."
  docker login --username=${DOCKERHUB_USER} --password=${DOCKERHUB_PASS} --email=herd_mdl@finra.org
}


build() {
  echo "Building image"
  docker build -t ${NAMESPACE}/${REPOSITORY}:${TAG} ${DOCKERFILE_PATH}
}

push() {
  echo "Publishing image to docker hub"
  docker push ${NAMESPACE}/${REPOSITORY}:${TAG}
}


login_hub
build
push
