VERSION=$1
echo "Building docker image"
docker buildx build --progress plain --load --platform linux/amd64 . -t paschendale/las-to-3dtiles:latest

echo "Pushing docker image"
docker push paschendale/las-to-3dtiles:$VERSION
docker push paschendale/las-to-3dtiles:latest
exit 0

exit 1
