"""Reserve the complete editing volume for the crafted turbine Ponder."""
from generate_petrochemical_ponder import ROOT, Structure


structure = Structure(25, 11)
# Ponder clips edits to the occupied bounds, not the structure's size tag.
# The scene removes this marker before revealing any sections.
structure.block(24, 10, 24, 'minecraft:polished_andesite')
structure.save('turbine_unit_bounds')
source = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/ponder/chemistry/turbine_unit_bounds.nbt'
target = source.parent.parent / 'turbine/multiblock.nbt'
target.write_bytes(source.read_bytes())
source.unlink()
