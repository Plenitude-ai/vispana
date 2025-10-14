#!/bin/sh

# Multiplatform build and push to Docker Hub
# The --push flag is REQUIRED to push the multi-platform manifest to Docker Hub
# Without --push, the images stay in the build cache only

# Get version tag from argument, default to "latest"
VERSION=${1:-latest}
IMAGE_NAME="plenitudeai/vispana:${VERSION}"

echo "╔════════════════════════════════════════════════════════════╗"
echo "║  ⚠️  WARNING: PRODUCTION DOCKER PUSH  ⚠️                   ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "  Image:     ${IMAGE_NAME}"
echo "  Platforms: linux/amd64, linux/arm64"
echo "  Registry:  Docker Hub (public)"
echo ""
echo "This will BUILD and PUSH the image to Docker Hub!"
echo "This will OVERWRITE the existing '${VERSION}' tag if it exists."
echo ""
read -p "Are you sure you want to continue? (yes/no): " -r
echo ""

if [ "$REPLY" != "yes" ]; then
    echo "❌ Push cancelled."
    exit 1
fi

echo "🚀 Building and pushing ${IMAGE_NAME}..."
echo ""

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  -t "${IMAGE_NAME}" \
  .

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Successfully pushed ${IMAGE_NAME}"
    echo "   View at: https://hub.docker.com/r/plenitudeai/vispana/tags"
else
    echo ""
    echo "❌ Failed to push image"
    exit 1
fi