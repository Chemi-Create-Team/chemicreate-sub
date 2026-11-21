#!/usr/bin/env python3
"""Build the offline tooltip geometry with RDKit and NumPy."""
import json
from pathlib import Path

import numpy as np
from rdkit import Chem, RDLogger
from rdkit.Chem import AllChem, rdDepictor

ROOT = Path(__file__).resolve().parents[1]
MOD = "create_refinery_n_reactors"
DATA = ROOT / "src/main/resources/data" / MOD / "chemistry"
INPUT = ROOT / "tools/data/molecule_structures.json"
RDLogger.DisableLog("rdApp.warning")


def element(atom):
    if atom.GetSymbol() == "H":
        return {2: "D", 3: "T"}.get(atom.GetIsotope(), "H")
    return atom.GetSymbol()


def bond_list(mol):
    return [[bond.GetBeginAtomIdx(), bond.GetEndAtomIdx(), round(bond.GetBondTypeAsDouble())]
            for bond in mol.GetBonds()]


def point_list(mol, positions):
    positions = positions - (positions.min(axis=0) + positions.max(axis=0)) / 2
    span = max(np.ptp(positions[:, 0]), np.ptp(positions[:, 1]), 1.45)
    return [[element(atom), *(round(float(value / span), 3) for value in point)]
            for atom, point in zip(mol.GetAtoms(), positions, strict=True)]


def model(entry):
    molecule = Chem.MolFromSmiles(entry["smiles"])
    if molecule is None:
        raise ValueError(entry["smiles"])
    spatial = Chem.AddHs(molecule)
    # Small molecules keep their explicit hydrogens in the structure diagram.
    flat = Chem.Mol(spatial if molecule.GetNumHeavyAtoms() <= 1 else molecule)
    Chem.Kekulize(flat, clearAromaticFlags=True)
    rdDepictor.Compute2DCoords(flat)
    xy = flat.GetConformer().GetPositions()[:, :2]

    if spatial.GetNumAtoms() == 1:
        xyz = np.zeros((1, 3))
    elif any(atom.GetSymbol() == "U" for atom in spatial.GetAtoms()):
        uranium = next(atom.GetIdx() for atom in spatial.GetAtoms() if atom.GetSymbol() == "U")
        xyz = np.zeros((7, 3))
        for index, vector in zip((i for i in range(7) if i != uranium),
                                 [*np.eye(3), *-np.eye(3)], strict=True):
            xyz[index] = vector
    else:
        options = AllChem.ETKDGv3()
        options.randomSeed = 1967
        if AllChem.EmbedMolecule(spatial, options) == -1:
            raise ValueError("Cannot embed " + entry["smiles"])
        if AllChem.UFFHasAllMoleculeParams(spatial):
            AllChem.UFFOptimizeMolecule(spatial)
        xyz = spatial.GetConformer().GetPositions()

    # Keep the largest molecular dimensions on the screen and tilt the projection to show depth.
    xyz -= xyz.mean(axis=0)
    _, _, basis = np.linalg.svd(xyz, full_matrices=True)
    xyz = xyz @ basis.T
    if spatial.GetNumAtoms() > 2:
        for a, b, angle in ((1, 2, .53), (0, 2, .25)):
            first, second = xyz[:, a].copy(), xyz[:, b].copy()
            xyz[:, a] = np.cos(angle) * first - np.sin(angle) * second
            xyz[:, b] = np.sin(angle) * first + np.cos(angle) * second
    return [point_list(flat, xy), bond_list(flat), point_list(spatial, xyz), bond_list(spatial)]


def main():
    info = json.loads((DATA / "fluids.json").read_text())
    structures = []
    for entry in json.loads(INPUT.read_text()):
        fluids = entry.get("fluids", [])
        formula = entry.get("formula") or info[fluids[0]]["formula"]
        if any(info[fluid]["formula"] != formula for fluid in fluids):
            raise ValueError(f"Formula mismatch for {fluids}")
        keys = [fluid if ":" in fluid else f"{MOD}:{fluid}" for fluid in fluids]
        structures.append([formula, keys, not entry.get("ambiguous_formula", False), *model(entry)])
    result = ",\n".join("  " + json.dumps(row, ensure_ascii=False, separators=(",", ":")) for row in structures)
    (DATA / "molecule_structures.json").write_text("[\n" + result + "\n]\n")
    print(f"Generated {len(structures)} molecular structures for {sum(len(row[1]) for row in structures)} fluids")


if __name__ == "__main__":
    main()
