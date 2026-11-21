"""Shared structure helpers for the plant Ponder generators: stainless steel pipe, formed radiator cell and fan shroud orientation."""
NS='create_refinery_n_reactors:'

def pipe(s,x,y,z,*sides):
 s.block(x,y,z,NS+'stainless_steel_pipe',**{side:str(side in sides).lower() for side in ('north','south','east','west','up','down')},waterlogged='false')

def radiator_cell(put,x0,y0,z0,along):
 """One formed 3x3x4 cell, as RadiatorBlockEntity sets it: radiators from the low corner (x0, y0, z0), fan and shrouds on top.
 The pipe runs along 'along'; its two flanges are the middle-row centres of the ends, the first facing back against it."""
 steps={'south':(0,1),'west':(-1,0),'north':(0,-1),'east':(1,0)};back={'south':'north','north':'south','east':'west','west':'east'}
 ax,az=steps[along]
 for dx in (-1,0,1):
  for dz in (-1,0,1):
   for y in range(3):
    first=(dx,dz)==(-ax,-az) and y==1;last=(dx,dz)==(ax,az) and y==1
    put(x0+1+dx,y0+y,z0+1+dz,NS+'cooler_radiator',facing=back[along] if first else along,section=('bottom','middle','top')[y],
     drive=str(dx==0 and dz==0).lower(),outlet=str(y==0).lower(),port=str(first or last).lower())
   if dx or dz:put(x0+1+dx,y0+3,z0+1+dz,NS+'cooling_fan_shroud',**shroud(-dx,-dz))
 put(x0+1,y0+3,z0+1,NS+'cooling_fan')
def shroud(dx,dz):
 """Same orientation rule as CoolingFanShroudBlock.around: the hub lies at (dx, dz)."""
 steps={'south':(0,1),'west':(-1,0),'north':(0,-1),'east':(1,0)};ccw={'south':'east','east':'north','north':'west','west':'south'}
 if dx==0 or dz==0:return {'facing':next(f for f,v in steps.items() if v==(dx,dz)),'part':'side'}
 return {'facing':next(f for f in steps if (steps[f][0]+steps[ccw[f]][0],steps[f][1]+steps[ccw[f]][1])==(dx,dz)),'part':'corner'}
