#!/bin/sh

# Script to delete a tag from Docker Hub
# Docker Hub doesn't support tag deletion via docker CLI, must use the API

REPO="plenitudeai/vispana"

if [ -z "$1" ]; then
    echo "Usage: $0 <tag-name>"
    echo ""
    echo "Examples:"
    echo "  $0 arm       # Delete the 'arm' tag"
    echo "  $0 x86       # Delete the 'x86' tag"
    echo "  $0 1.6.0     # Delete the '1.6.0' tag"
    echo ""
    echo "⚠️  Note: Cannot delete 'latest' tag if it's the only tag remaining"
    exit 1
fi

TAG=$1

if [ "$TAG" = "latest" ]; then
    echo "❌ Cannot delete 'latest' tag"
    exit 1
fi

echo "╔════════════════════════════════════════════════════════════╗"
echo "║  ⚠️  WARNING: DELETE TAG FROM DOCKER HUB  ⚠️               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "  Repository: ${REPO}"
echo "  Tag:        ${TAG}"
echo ""
echo "This will PERMANENTLY DELETE the tag '${TAG}' from Docker Hub!"
echo ""
read -p "Are you sure you want to delete this tag? (yes/no): " -r
echo ""

if [ "$REPLY" != "yes" ]; then
    echo "❌ Deletion cancelled."
    exit 1
fi

echo "🗑️  Deleting tag ${TAG}..."
echo ""
echo "Please provide your Docker Hub credentials:"
read -p "Username: " USERNAME
read -sp "Password or Token: " PASSWORD
echo ""
echo ""

# Get JWT token from Docker Hub
echo "🔐 Authenticating with Docker Hub..."
TOKEN=$(curl -s -H "Content-Type: application/json" -X POST \
    -d "{\"username\": \"${USERNAME}\", \"password\": \"${PASSWORD}\"}" \
    https://hub.docker.com/v2/users/login/ | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "❌ Authentication failed. Please check your credentials."
    echo "   If using 2FA, create an access token at:"
    echo "   https://hub.docker.com/settings/security"
    exit 1
fi

# Delete the tag
echo "🗑️  Deleting tag..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    -H "Authorization: JWT ${TOKEN}" \
    "https://hub.docker.com/v2/repositories/${REPO}/tags/${TAG}/")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Successfully deleted tag '${TAG}' from ${REPO}"
    echo "   Verify at: https://hub.docker.com/r/${REPO}/tags"
else
    echo "❌ Failed to delete tag (HTTP ${HTTP_CODE})"
    echo "$RESPONSE" | head -n-1
    exit 1
fi

