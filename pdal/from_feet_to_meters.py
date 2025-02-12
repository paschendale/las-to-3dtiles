import numpy as np

def from_feet_to_meters(ins, outs):
    Z = ins['Z']

    outs['Z'] = Z.astype(int) * 0.3048

    return True