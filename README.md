This is a toolkit for converting LAS files to 3D Tiles in 1.1 spec.

Motivated by the lack of a complete tool that can to this, I started scavenging the internet for one or more solution that could be used and ended up with this.

Currently, we're using:

- PDAL for LAS/LAZ reading and handling
- py3dtiles for 3D Tiles writing
- 3d-tiles-tools for upgrading the 3D Tiles spec and applying a pipeline for performance improvements

The idea of using a Docker image is to allow for running into workflows managers such as n8n or Argo Workflows.

# Usage

Run the Docker image:

```bash
./run.sh
```

This will mount the `input` and `output` directories and enter the container

Now, run the conversion scripts:

```bash
# We can do a full conversion, applying scales, color palletes, and reprojection
python las_to_3dtiles.py --input '/app/input/PINEY DAM MBES 1IN.laz' --output /app/output/piney_dam_mbes_1in --input_srs ESRI:103140 --output_srs EPSG:3857 --colorize_dimension Z --colorize_ramp pestel_shades --convert_type True --scale_z 0.348

# We can also run a simpler conversion, just converting the data to 16 bit integers and reprojecting
python las_to_3dtiles.py --input '/app/input/Laser Scan_2024.laz' --output /app/output/laser_scan_2024 --input_srs ESRI:103140 --output_srs EPSG:3857 --convert_type True  --scale_z 0.348

# Or we can just write the data
python las_to_3dtiles.py --input '/app/input/Laser_Scan_2024_3857_z_meters.las' --output /app/output/laser_scan_2024
```

The output will be in the `output` directory. You can serve it statically (from your host terminal) by:

```bash
docker run -it -v ./output:/var/www -p 8080:8080 \
    connormanning/http-server
```
