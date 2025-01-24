# 3dtiles-conversion

A collection of tools to convert 3D models into 3D Tiles.

Run all of these commands at the root of the repository.

## Mago 3D Tiler

Install OpenJDK:
```bash
brew install openjdk
```

Check if it's installed:
```bash
java -version
```

Run the tool:
```bash
java -jar mago-3d-tiler/tiler/dist/mago-3d-tiler-1.10.0-natives-macos.jar --help
```

Run a 3D model conversion from a .LAS file:
```bash
java -jar mago-3d-tiler/tiler/dist/mago-3d-tiler-1.10.0-natives-macos.jar -c 3451 -i '/Users/victo/Library/CloudStorage/OneDrive-Personal/58. Freelance/20241025 Underwater Acoustics International (231846342157634191361)/20241106 LA Hydro Data/LA Hydro Project/LA Hydro 2023/LIDAR_MODEL.las' -o ./output/LIDAR_MODEL_mago_3d_tiler.3dtiles
```

> Note: Unfortunately, the tool sometimes throws an error `Cannot read field "R" because "rgbRecord" is null `. This was mapped here (Cannot read field "R" because "rgbRecord" is null #3)[https://github.com/Gaia3D/mago-3d-tiler/issues/3] and supposedly fixed, but it doesn't seem to be the case.

## Entwine

Entwine can be ran as a Docker container.

> Although documentation states that we can convert into 3D tiles like this, Entwine version 2.1.0 doesn't seem to support it. So we must downgrade it to 2.0

If the LAS files doesn't have a CRS, we must define it through a JSON file:
```json
{
    "reprojection": {
        "in": "EPSG:3451",
        "out": "EPSG:3857"
    }
}
```

Save this into `entwine/configuration.json`.

First, we must convert the LAS file into a EPT format, which is the format that Entwine can read.
```bash
docker run -it -v ./output:/output -v ./entwine/configuration.json:/configuration.json -v ./input:/input --name entwine\
    connormanning/entwine:2.0 build \
    -c /configuration.json \
    -i /input/Laser_Scan_2024_4326.laz \
    -o /output/LIDAR_MODEL_entwine 
```

Now, convert from EPT to 3D tiles:
```bash
docker run -it -v ./output:/output  --name entwine connormanning/entwine:2.0 convert \
    -i ./output/LIDAR_MODEL_entwine \
    -o ./output/LIDAR_MODEL_entwine_cesium
```

From the Entwine documentation, we can also serve a Cesium 3D Tiles file:
```bash
docker run -it -v ./output/LIDAR_MODEL_entwine_cesium:/var/www -p 8080:8080 \
    connormanning/http-server
```

> Note: I didn't manage to get Entwine to work either with the 3D Tiles file, but I'm not sure if it's because of the EPT file or because of the Docker container.

## py3dtiles

This time I'm using a Python library to convert the LAS file into a 3D Tiles file. The library is available [here](https://py3dtiles.org/v9.0.0/cli.html).

First, we must install the library:
```bash
pip install py3dtiles
```

And also the LAS dependency:
```bash
pip install "py3dtiles[las]"
```

Then, we can convert the LAS file into a 3D Tiles file:
```bash
py3dtiles convert input/PINEY_DAM_MBES_1IN_4326.las --out output/PINEY_DAM_MBES_1IN_4326
```
