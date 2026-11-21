#!/usr/bin/env python3
"""Build the offline tooltip geometry with RDKit and NumPy."""
import json
import math
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
    return [[element(atom), *(round(float(value / span), 3) for value in point),
             *([atom.GetFormalCharge()] if atom.GetFormalCharge() else [])]
            for atom, point in zip(mol.GetAtoms(), positions, strict=True)]


def arrange_fragments(positions, fragments):
    if len(fragments) <= 1:
        return positions
    cell_width, cell_height = np.max([np.ptp(positions[list(fragment), :2], axis=0)
                                     for fragment in fragments], axis=0) + 1.75
    columns = math.ceil(math.sqrt(len(fragments))) if len(fragments) > 2 else 2
    for i, fragment in enumerate(fragments):
        atoms = list(fragment)
        points = positions[atoms]
        positions[atoms] -= (points.min(axis=0) + points.max(axis=0)) / 2
        positions[atoms, :2] += [i % columns * cell_width, i // columns * cell_height]
    return positions


def model(entry):
    molecule = Chem.MolFromSmiles(entry["smiles"])
    if molecule is None:
        raise ValueError(entry["smiles"])
    spatial = Chem.AddHs(molecule)
    # Small molecules keep their explicit hydrogens in the structure diagram.
    flat = Chem.Mol(spatial if molecule.GetNumHeavyAtoms() <= 1 or entry.get("show_hydrogens", False) else molecule)
    Chem.Kekulize(flat, clearAromaticFlags=True)
    rdDepictor.Compute2DCoords(flat)
    xy = arrange_fragments(flat.GetConformer().GetPositions()[:, :2], Chem.GetMolFrags(flat))

    indices = []
    fragments = Chem.GetMolFrags(spatial, asMols=True, fragsMolAtomMapping=indices)
    xyz = np.zeros((spatial.GetNumAtoms(), 3))
    for fragment, atoms in zip(fragments, indices, strict=True):
        xyz[list(atoms)] = coordinates(fragment, entry["smiles"])
    xyz = arrange_fragments(xyz, indices)
    return [point_list(flat, xy), bond_list(flat), point_list(spatial, xyz), bond_list(spatial)]


def coordinates(fragment, name):
    count = fragment.GetNumAtoms()
    if count == 1:
        return np.zeros((1, 3))
    uranium = next((atom.GetIdx() for atom in fragment.GetAtoms() if atom.GetSymbol() == "U"), None)
    if uranium is not None:
        xyz = np.zeros((count, 3))
        vectors = [*np.eye(3), *-np.eye(3)] if count == 7 else [(1, 0, 0), (-1, 0, 0)]
        for index, vector in zip((i for i in range(count) if i != uranium), vectors, strict=True):
            xyz[index] = vector
    else:
        options = AllChem.ETKDGv3()
        options.randomSeed = 1967
        if AllChem.EmbedMolecule(fragment, options) == -1:
            raise ValueError("Cannot embed " + name)
        if AllChem.UFFHasAllMoleculeParams(fragment):
            AllChem.UFFOptimizeMolecule(fragment)
        xyz = fragment.GetConformer().GetPositions()

    # Align the principal dimensions and save the model's complete three-dimensional coordinates.
    xyz -= xyz.mean(axis=0)
    _, _, basis = np.linalg.svd(xyz, full_matrices=True)
    xyz = xyz @ basis.T
    if count > 2:
        for a, b, angle in ((1, 2, .53), (0, 2, .25)):
            first, second = xyz[:, a].copy(), xyz[:, b].copy()
            xyz[:, a] = np.cos(angle) * first - np.sin(angle) * second
            xyz[:, b] = np.sin(angle) * first + np.cos(angle) * second
    return xyz


def fluid_formula(info):
    if not info.get("aqueous"):
        return info["formula"]
    solutes = [part for part in info["formula"].split(" + ") if part != "H₂O"]
    if len(solutes) != 1 or "…" in solutes[0] or "/" in solutes[0]:
        raise ValueError(f"Model requires one defined solute: {info['formula']}")
    return solutes[0]


def main():
    info = json.loads((DATA / "fluids.json").read_text())
    structures = []
    for entry in json.loads(INPUT.read_text()):
        fluids = entry.get("fluids", [])
        formula = entry.get("formula") or fluid_formula(info[fluids[0]])
        if any(fluid_formula(info[fluid]) != formula for fluid in fluids):
            raise ValueError(f"Formula mismatch for {fluids}")
        keys = [fluid if ":" in fluid else f"{MOD}:{fluid}" for fluid in fluids]
        items = [item if ":" in item else f"{MOD}:{item}" for item in entry.get("items", [])]
        structures.append([formula, keys, items, *model(entry)])
    result = ",\n".join("  " + json.dumps(row, ensure_ascii=False, separators=(",", ":")) for row in structures)
    (DATA / "molecule_structures.json").write_text("[\n" + result + "\n]\n")
    print(f"Generated {len(structures)} molecular structures for {sum(len(row[1]) for row in structures)} fluids")


if __name__ == "__main__":
    main()
