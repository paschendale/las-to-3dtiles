For these steps, I'm using PDAL installed through Conda and py3dtiles inside a Docker container.

To run PDAL:

```bash
conda activate pdal
```

To run py3dtiles:

```bash
docker run --platform linux/arm64 -it -v ./output:/app/data/output -v ./input:/app/data/input --name py3dtiles  --entrypoint /bin/bash \
    paschendale/py3dtiles
```

After everything is ok, we test it locally serving the 3D Tiles through:

```bash
docker run -it -v ./output:/var/www -p 8080:8080 \
    connormanning/http-server
```

## PINEY DAM MBES 1IN.laz

First, we'll run a PDAL pipeline to:

1. Reproject from ESRI:103140 to EPSG:3857
2. Colorize the elevation data using a color ramp (pestel_shades)
3. Cast the RGB values into 16 bit integers
4. Translate the elevation data from feet to meters
5. Write the output to a LAS file: PINEY_DAM_MBES_1IN_3857_colorized_z_meters.las

The following PDAL pipeline is used:

```json
// pdal_pipeline_PINEY_DAM_MBES_1IN.json

{
  "pipeline": [
    {
      "type": "readers.las",
      "filename": "/Users/victo/Documents/GitHub/3dtiles-conversion/input/PINEY DAM MBES 1IN.laz",
      "compression": "laszip"
    },
    {
      "type": "filters.reprojection",
      "in_srs": "ESRI:103140",
      "out_srs": "EPSG:3857"
    },
    {
      "type": "filters.colorinterp",
      "ramp": "pestel_shades",
      "dimension": "Z"
    },
    {
      "type": "filters.python",
      "script": "/Users/victo/Documents/GitHub/3dtiles-conversion/pdal/cast_type.py",
      "function": "cast_type",
      "module": "cast_type"
    },
    {
      "type": "filters.python",
      "script": "/Users/victo/Documents/GitHub/3dtiles-conversion/pdal/from_feet_to_meters.py",
      "function": "from_feet_to_meters",
      "module": "from_feet_to_meters"
    },
    {
      "type": "writers.las",
      "filename": "/Users/victo/Documents/GitHub/3dtiles-conversion/input/PINEY_DAM_MBES_1IN_3857_colorized_z_meters.las",
      "compression": "laszip"
    }
  ]
}
```

Now let's run the pipeline:

```bash
pdal pipeline pdal_pipeline_PINEY_DAM_MBES_1IN.json
```

The output is a LAS file with the elevation data translated from feet to meters and colorized using the pestel_shades color ramp.

Now, we use `py3dtiles` to convert the LAS file to a 3D Tiles file:

```bash
py3dtiles convert --out="/app/data/output/PINEY_DAM_MBES_1IN" --srs_out 4978 --verbose --overwrite  /app/data/input/PINEY_DAM_MBES_1IN_3857_colorized_z_meters.las
```

## Laser Scan_2024.laz

This file has colors in 16 bit integers, so no type casting is needed.

First, we'll run a PDAL pipeline to:

1. Reproject from ESRI:103140 to EPSG:3857
2. Translate the elevation data from feet to meters
3. Write the output to a LAS file: PINEY_DAM_MBES_1IN_3857_colorized_z_meters.las

The following PDAL pipeline is used:

```json
// pdal_pipeline_Laser_Scan_2024.json
{
  "pipeline": [
    {
      "type": "readers.las",
      "filename": "/Users/victo/Documents/GitHub/3dtiles-conversion/input/Laser Scan_2024.laz",
      "compression": "laszip"
    },
    {
      "type": "filters.reprojection",
      "in_srs": "ESRI:103140",
      "out_srs": "EPSG:3857"
    },
    {
      "type": "filters.python",
      "script": "/Users/victo/Documents/GitHub/3dtiles-conversion/pdal/from_feet_to_meters.py",
      "function": "from_feet_to_meters",
      "module": "feet_to_meters"
    },
    {
      "type": "writers.las",
      "filename": "/Users/victo/Documents/GitHub/3dtiles-conversion/input/Laser_Scan_2024_3857_z_meters.las",
      "compression": "laszip"
    }
  ]
}
```

Now let's run the pipeline:

```bash
pdal pipeline pdal_pipeline_Laser_Scan_2024.json
```

The output is a LAS file with the elevation data translated from feet to meters and colorized using the pestel_shades color ramp.

Now, we use `py3dtiles` to convert the LAS file to a 3D Tiles file:

```bash
py3dtiles convert --out="/app/data/output/Laser_Scan_2024" --srs_out 4978 --verbose --overwrite  /app/data/input/Laser_Scan_2024_3857_z_meters.las
```

> NOTE: Beware of memory issues, py3dtiles will just quit if you don't have enough memory showing a `killed` message, which is actually from the OS.
