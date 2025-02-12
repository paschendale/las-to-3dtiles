import numpy as np

def cast_type(ins, outs):
    #outs = ins
    Red = ins['Red']
    Green = ins['Green']
    Blue = ins['Blue']

    outs['Red'] = (65535*Red.astype(int)/255).astype(np.uint16)
    outs['Green'] = (65535*Green.astype(int)/255).astype(np.uint16)
    outs['Blue'] = (65535*Blue.astype(int)/255).astype(np.uint16)

    return True