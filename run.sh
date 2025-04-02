COMMAND=$1
docker run --platform linux/amd64 -it -v ./output:/app/output -v ./input:/app/input -v ./las_to_3dtiles.py:/app/las_to_3dtiles.py --rm  paschendale/las-to-3dtiles $COMMAND