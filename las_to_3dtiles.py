import argparse
import subprocess
import numpy as np
import pdal
import json 
import os

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=str, help="Input LAS file")
    parser.add_argument("--output", required=True, type=str, help="Output 3D Tiles directory")
    parser.add_argument("--input_srs", type=str, help="SRS code")
    parser.add_argument("--output_srs", type=str, help="SRS code")
    parser.add_argument("--colorize_dimension", type=str, help="Dimension to colorize")
    parser.add_argument("--colorize_ramp", type=str, help="Color ramp to use")
    parser.add_argument("--convert_type", type=bool, help="Convert type")
    parser.add_argument("--scale_x", type=float, help="Scale X")
    parser.add_argument("--scale_y", type=float, help="Scale Y")
    parser.add_argument("--scale_z", type=float, help="Scale Z")
    args = parser.parse_args()

    output_dir = args.output
    os.makedirs(os.path.dirname(output_dir), exist_ok=True)
    print(f"Output directory: {output_dir}")

    config =  [
        {
            "type": "readers.las",
            "filename": args.input,
            "compression": "laszip"
        },
    ]


    if args.input_srs is not None and args.output_srs is not None:
        print("Reprojecting from {} to {}".format(args.input_srs, args.output_srs))
        config.append({
            "type": "filters.reprojection",
            "in_srs": args.input_srs,
            "out_srs": args.output_srs
        })
        

    if args.colorize_dimension is not None:
        print("Colorizing with {} by {}".format(args.colorize_ramp, args.colorize_dimension))
        ramp = args.colorize_ramp if args.colorize_ramp is not None else "pestel_shades"
        config.append({
            "type": "filters.colorinterp",
            "ramp": ramp,
            "dimension": args.colorize_dimension
        })

    if args.convert_type:
        print("Casting types to 16 bit integers")
        config.append({
            "type": "filters.python",
            "module": "cast_type",
            "function": "cast_type",
            "source": f"""
import numpy as np
def cast_type(input, output):
    Red = input['Red']
    Green = input['Green']
    Blue = input['Blue']

    output['Red'] = (65535*Red.astype(int)/255).astype(np.uint16)
    output['Green'] = (65535*Green.astype(int)/255).astype(np.uint16)
    output['Blue'] = (65535*Blue.astype(int)/255).astype(np.uint16)

    return True
            """
        })

    if args.scale_x is not None or args.scale_y is not None or args.scale_z is not None:
        scale_x = args.scale_x if args.scale_x is not None else 1
        scale_y = args.scale_y if args.scale_y is not None else 1
        scale_z = args.scale_z if args.scale_z is not None else 1
        print("Scaling X by {}, Y by {} and Z by {}".format(scale_x, scale_y, scale_z))
        config.append({
            "type": "filters.python",
            "module": "scale",
            "function": "scale",
            "source": f"""
import numpy as np
def scale(input, output):
                    
    output['X'] = input['X'] * {scale_x}
    output['Y'] = input['Y'] * {scale_y}
    output['Z'] = input['Z'] * {scale_z}

    return True
            """
        })
    
    print("Writing LAS file")
    config.append({
            "type": "writers.las",
            "filename": '/tmp/las.las',
            "compression": "laszip"
        })
    
    print(config)

    with open('pdal_config.json', 'w') as f:
        json.dump(config, f)

    if (len(config) > 2):
        # We only need to run pdal if we have more than one stage
        cmd = f'pdal pipeline -i pdal_config.json --debug'
        subprocess.run(cmd, shell=True, check=True)
        result_las = '/tmp/las.las'
    else:
        result_las = args.input

    cmd = f'py3dtiles convert --out="/tmp/tiles" --srs_out 4978 --verbose --overwrite {result_las}'

    print("Converting LAS to 3D Tiles")
    print(cmd)
    subprocess.run(cmd, shell=True, check=True)


    config_3d_tiles_tools_pipeline = {
        "input": '/tmp/tiles',
        "output": output_dir,
        "tilesetStages": [
            {
                "name": "upgrade",
                "description": "Upgrade the input tileset to the latest version"
            },
            {
                "name": "combine",
                "description": "Combine all external tilesets into one"
            },
            {
                "name": "_b3dmToGlb",
                "description": "Convert B3DM to GLB",
                "contentStages": [
                    {
                        "name": "b3dmToGlb",
                        "description": "Convert each B3DM content into GLB"
                    }
                ]
            },
            {
                "name": "_optimizeGlb",
                "description": "Optimize GLB",
                "contentStages": [
                    {
                        "name": "optimizeGlb",
                        "description": "Apply gltf-pipeline to each GLB content, with the given options",
                        "options": {
                            "dracoOptions": {
                            "compressionLevel": 10
                            }
                        }
                    }
                ]
            },
            {
                "name": "_separateGltf",
                "description": "Separate glTF",
                "contentStages": [
                    {
                    "name": "separateGltf",
                    "description": "Convert each GLB content into a .gltf file with separate resources"
                    }
                ]
            },
            {
                "name": "gzip",
                "description": "Compresses each entry with GZIP",
                "includedContentTypes": ["CONTENT_TYPE_GLTF"]
            }
        ]
        }

    with open('3d-tiles-tools-pipeline.json', 'w') as f:
        json.dump(config_3d_tiles_tools_pipeline, f, indent=2)

    print("Applying 3d-tiles-tools pipeline")
    print(config_3d_tiles_tools_pipeline)
    cmd = f'npx 3d-tiles-tools pipeline -i 3d-tiles-tools-pipeline.json --logLevel debug'
    subprocess.run(cmd, shell=True, check=True)

