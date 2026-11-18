#!/usr/bin/env python3
"""Portable, Create-style turbine meshes and sprites. Existing footprints and axes stay intact."""
import json,math,struct,zlib
from pathlib import Path
from minecraft_style import grain
A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors'

def png(path,px):
 def chunk(k,d):return struct.pack('>I',len(d))+k+d+struct.pack('>I',zlib.crc32(k+d)&0xffffffff)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',len(px[0]),len(px),8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+bytes(c for p in row for c in p) for row in px)))+chunk(b'IEND',b''))

def rect(u0,v0,u1,v1,width=16,height=16):
 """The part of a sprite a face shows, in its pixels, so that faces of any size keep 16 pixels to the block."""
 return [u0/width,v1/height,u0/width,v0/height,u1/width,v0/height,u1/width,v1/height]

def quad(mesh,vertices,texture=0,animated=False,uv=(0,1,0,0,1,0,1,1)):
 a,b,c=vertices[:3];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
 n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];length=math.sqrt(sum(x*x for x in n))
 mesh.append(dict(texture=texture,vertices=[round(c/16,6) for p in vertices for c in p],uv=[round(c,6) for c in uv],normal=[round(c/length,6) for c in n],animated=animated))

def cube(mesh,a,b,texture=0,animated=False,angle=0,uvs=None):
 """uvs gives the sprite part of each face in the order of the faces below; without it a face shows the whole sprite."""
 x,y,z=a;X,Y,Z=b
 faces=[[(x,y,z),(x,Y,z),(X,Y,z),(X,y,z)],[(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],[(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],[(X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z)],[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]]
 for i,face in enumerate(faces):
  if angle:face=[(xx,8+(yy-8)*math.cos(angle)-(zz-8)*math.sin(angle),8+(yy-8)*math.sin(angle)+(zz-8)*math.cos(angle)) for xx,yy,zz in face]
  quad(mesh,face,texture,animated,uvs[i] if uvs else (0,1,0,0,1,0,1,1))

def save(name,mesh,**metadata):
 coords=[q['vertices'] for q in mesh]
 bounds={'min':[min(v[i] for v in coords for i in range(axis,len(v),3)) for axis in range(3)],'max':[max(v[i] for v in coords for i in range(axis,len(v),3)) for axis in range(3)]}
 path=A/'models/block'/name
 old=json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
 old.update(source='tools/generate_rotating_models.py',texture_size=[16,16],quad_count=len(mesh),local_bounds=bounds,quads=mesh,**metadata)
 old.pop('bounds',None);old.pop('source_bounds',None)
 path.write_text(json.dumps(old,separators=(',',':'))+'\n')

# A real steam-turbine row is a dense ring of narrow, twisted blades rather than a radiator fan. Two radial segments give
# each blade a readable sweep and twist without returning to the original exporter's thousands of tiny triangles. A
# low-pressure rotor is drawn up to 1.95 times as large, so its faces show twice the pixels of a high-pressure one.
DISC=rect(4,4,10,10)
def blade_section_uv(sprite,start,end,reverse=False):
 u0,bottom,_,top,u1,_,_,_=sprite
 v0=bottom+(top-bottom)*start;v1=bottom+(top-bottom)*end
 if reverse:return [u1,v0,u1,v1,u0,v1,u0,v0]
 return [u0,v0,u0,v1,u1,v1,u1,v0]

def radial_point(radius,angle,tangent):
 return (8+radius*math.cos(angle)-tangent*math.sin(angle),
         8+radius*math.sin(angle)+tangent*math.cos(angle))

def turbine_blade(mesh,angle,face,edge,tip,sweep,tip_width):
 # The centre line bends mostly near the tip while the axial centre shifts the other way. This makes the broad face,
 # leading edge and changing pitch legible from the oblique view used for assembled turbine shafts.
 sections=[
  (3.65,0,.32,1.35,-.25),
  (9.55,sweep*.38,.70,1.02,0),
  (15.85,sweep,tip_width,.68,.32),
 ]
 rings=[]
 for radius,turn,half_width,half_depth,axial_shift in sections:
  left=radial_point(radius,angle+turn,-half_width)
  right=radial_point(radius,angle+turn,half_width)
  rings.append({
   'lo_left':(8+axial_shift-half_depth,*left),
   'lo_right':(8+axial_shift-half_depth,*right),
   'hi_left':(8+axial_shift+half_depth,*left),
   'hi_right':(8+axial_shift+half_depth,*right),
  })
 for section in range(len(rings)-1):
  start=section/(len(rings)-1);end=(section+1)/(len(rings)-1)
  a=rings[section];b=rings[section+1]
  quad(mesh,[a['hi_left'],b['hi_left'],b['hi_right'],a['hi_right']],0,uv=blade_section_uv(face,start,end))
  quad(mesh,[a['lo_right'],b['lo_right'],b['lo_left'],a['lo_left']],0,uv=blade_section_uv(face,start,end,True))
  quad(mesh,[a['lo_left'],b['lo_left'],b['hi_left'],a['hi_left']],0,uv=blade_section_uv(edge,start,end))
  quad(mesh,[a['hi_right'],b['hi_right'],b['lo_right'],a['lo_right']],0,uv=blade_section_uv(edge,start,end,True))
 root=rings[0];outer=rings[-1]
 quad(mesh,[root['lo_right'],root['lo_left'],root['hi_left'],root['hi_right']],0,uv=edge)
 quad(mesh,[outer['lo_left'],outer['lo_right'],outer['hi_right'],outer['hi_left']],0,uv=tip)

def rotor_hub(mesh,sides=16):
 radius=4.25;x0=5.5;x1=10.5
 centre0=(x0,8,8);centre1=(x1,8,8)
 for i in range(sides):
  a=2*math.pi*i/sides;b=2*math.pi*(i+1)/sides
  p0=(x0,8+radius*math.cos(a),8+radius*math.sin(a))
  p1=(x0,8+radius*math.cos(b),8+radius*math.sin(b))
  q0=(x1,p0[1],p0[2]);q1=(x1,p1[1],p1[2])
  quad(mesh,[p0,q0,q1,p1],1,uv=DISC)
  quad(mesh,[centre0,p1,p0,centre0],1,uv=DISC)
  quad(mesh,[centre1,q0,q1,centre1],1,uv=DISC)

def rotor(name,count,face,edge,tip,sweep,tip_width):
 mesh=[]
 for i in range(count):turbine_blade(mesh,i*2*math.pi/count,face,edge,tip,sweep,tip_width)
 rotor_hub(mesh)
 save('turbine/'+name,mesh,circumferential_blade_count=count,radial_blade_segments=2)
rotor('minecraft_realistic_impeller_turbine_render.json',40,rect(4,5,12,32,16,32),rect(7,5,9,32,16,32),rect(4,5,12,7,16,32),math.radians(4.2),1.02)
rotor('high_pressure_turbine_blade_exact_render.json',36,rect(6,1,10,15),rect(7,1,8,15),rect(6,1,10,3),math.radians(3.4),.90)

def ring(radius,thickness,texture,span):
 """Eight panels: the outer face runs along the axis over span pixels of the sprite's height; the plain part covers the rest."""
 mesh=[];outer=[];inner=[]
 plain=rect(3,3,6,5,16,32)
 for i in range(8):
  angle=math.pi/8+i*math.pi/4
  outer.append((round(8+radius*math.cos(angle)),round(8+radius*math.sin(angle))))
  inner.append((round(8+(radius-thickness)*math.cos(angle)),round(8+(radius-thickness)*math.sin(angle))))
 for i in range(8):
  k=(i+1)%8;o=outer[i];O=outer[k];p=inner[i];P=inner[k]
  quad(mesh,[(0,*o),(16,*o),(16,*O),(0,*O)],texture,uv=(0,0,1,0,1,span/32,0,span/32))
  for face in [[(0,*P),(16,*P),(16,*p),(0,*p)],[(0,*p),(16,*p),(16,*o),(0,*o)],[(0,*O),(16,*O),(16,*P),(0,*P)],[(0,*o),(0,*O),(0,*P),(0,*p)],[(16,*p),(16,*P),(16,*O),(16,*o)]]:quad(mesh,face,texture,uv=plain)
 return mesh
# Low-pressure casings are the same mesh drawn 5/3 as large, so their panels show 33 pixels.
for name,radius,span,strut in [('high_pressure_turbine_casing_exact_render.json',26,20,(-10,26)),('low_pressure_turbine_casing_render.json',26,32,(-10,26)),('tarbin_end_render.json',17,9,(-4,20))]:
 mesh=ring(radius,3,1,span)
 plain=rect(3,3,6,5,16,32)
 cube(mesh,[4,5,5],[12,11,11],0,uvs=[DISC]*6)
 cube(mesh,[6,strut[0],6],[10,5,10],1,uvs=[plain]*6)
 cube(mesh,[6,11,6],[10,strut[1],10],1,uvs=[plain]*6)
 save('turbine/'+name,mesh)

def shade(color,amount):
 return tuple(max(0,min(255,c+amount)) for c in color)+(255,)

def sheen(share):
 """Hard-edged reflection bands round a polished panel: a narrow bright line, its falloff, and a dark band under it."""
 for end,amount in ((.12,-10),(.26,12),(.34,42),(.46,16),(.72,-14),(.86,10),(1,-8)):
  if share<end:return amount
 return -8

def casing(name,base,flange,bolts,seams,span):
 """A bolted section of casing: u along the shaft, v round the ring. Flanged joints at both ends of the block, with ribs along it."""
 height=32;px=[]
 for y in range(height):
  row=[]
  for x in range(16):
   c=flange if x<3 or x>12 else base;s=sheen(min(y,span-1)/span)+grain(name,x,y%16,.5)
   if x in (2,13):s=-16
   if x in (0,15):s=-36
   if x in (1,14):s=sheen(min(y,span-1)/span)//2+26
   if y in bolts and x in (1,14):s=-46
   if y-1 in bolts and x in (1,14):s=62
   if 2<x<13:
    if y in seams:s=34
    if y-1 in seams:s=-30
   row.append(shade(c,s))
  px.append(row)
 png(A/'textures/block/turbine'/(name+'.png'),px)

# Cast high-pressure shell, dark and heavily bolted, showing 20 of its rows; plated low-pressure hood with stiffening ribs.
casing('high_pressure_casing',(88,96,102),(102,110,114),{2,7,12,17},{9},20)
casing('low_pressure_casing',(130,140,142),(116,126,130),{3,11,19,27},{0,10,21,30},32)

def blade(name,width,height,left,right,top,bottom,steel,tint,wires):
 """A twisted forged blade: the reflection ridge sweeps from the broad tip towards its darker root."""
 px=[]
 for y in range(height):
  row=[]
  for x in range(width):
   span=max(1,bottom-top-1);share=max(0,min(1,(y-top)/span))
   ridge=right-2-round((right-left-3)*share);distance=x-ridge
   s=64 if distance==0 else 30 if distance==-1 else 12 if distance in (1,2) else -30
   c=tint if y>=bottom-4 else steel
   if x==left:s+=24
   if x==right-1:s-=22
   if y<top+2:s+=18-9*(y-top)
   if y>=bottom-2:s=-38+(12 if (x+y)%2 else 0)
   if y in wires:s-=18
   row.append(shade(c,s+grain(name,x,y%16,.4)))
  px.append(row)
 png(A/'textures/block/turbine'/(name+'.png'),px)

blade('high_pressure_blade',16,16,6,10,1,15,(160,170,178),(132,130,124),{8})

def low_pressure_blade_texture():
 """A long final-stage blade: dark forged steel, crossed twist reflections, a tie band and fir-tree root grooves."""
 name='low_pressure_blade';steel=(116,124,128);root=(86,85,81);px=[]
 for y in range(32):
  row=[];share=max(0,min(1,(y-5)/26))
  # Two stair-stepped reflections converge towards the root, making the 32px face read as a strongly twisted blade.
  leading=11-round(4*share);crown=5+round(3*share)
  for x in range(16):
   c=root if y>=27 else steel
   s=-34 if x<crown else -8 if x<leading else -27
   if x==crown:s=22
   elif x==crown+1:s=max(s,8)
   if x==leading-1:s=max(s,36)
   if x==leading:s=82
   elif x==leading+1:s=max(s,15)
   if x==4:s-=24
   if x<4 or x>12:s-=12
   if y==5:s+=20
   elif y==6:s+=9
   # The photographed rotor shows a narrow circumferential restraint between the polished outer span and darker root span.
   if y==16:s-=30
   elif y==17:s+=12
   if y>=27:
    s-=14
    if y==27:s+=42
    elif y in (29,31):s-=28
    elif y in (28,30):s+=9
   row.append(shade(c,s+grain(name,x,y,.55)))
  px.append(row)
 png(A/'textures/block/turbine/low_pressure_blade.png',px)

low_pressure_blade_texture()

def blade_icon(name,low_pressure):
 """A single swept replacement blade, readable in the inventory at native 16px size."""
 px=[[(0,0,0,0) for _ in range(16)] for _ in range(16)]
 rows=(
  [(2,11,14),(3,10,14),(4,9,14),(5,8,13),(6,7,12),(7,6,12),(8,5,11),(9,4,10),(10,4,9),(11,3,8)]
  if low_pressure else
  [(2,10,12),(3,9,12),(4,9,11),(5,8,11),(6,7,10),(7,7,10),(8,6,9),(9,5,9),(10,5,8),(11,4,7)])
 steel=(112,121,125) if low_pressure else (160,170,178)
 for y,x0,x1 in rows:
  for x in range(x0,x1):
   if low_pressure:
    amount=82 if x==x1-1 else -38 if x==x0 else 28 if x>=x1-3 else -8
   else:
    amount=58 if x==x1-1 else -34 if x==x0 else 24 if x>=x1-3 else 2
   px[y][x]=shade(steel,amount+grain(name,x,y,.3))
 root=(138,135,129) if low_pressure else (132,130,124)
 for y,x0,x1,color in [(11,2,8,(65,72,76)),(12,2,8,(65,72,76)),(13,2,8,(65,72,76)),
                       (11,3,8,shade(root,42)[:3]),(12,3,8,root),(13,4,7,(82,82,80)),(14,4,7,(82,82,80))]:
  for x in range(x0,x1):px[y][x]=color+(255,)
 png(A/'textures/item'/(name+'.png'),px)

blade_icon('high_pressure_turbine_blade',False)
blade_icon('low_pressure_turbine_blade',True)

def plain(name,base,rows,roughness):
 px=[[shade(base,rows.get(y,0)+grain(name,x,y,roughness)) for x in range(16)] for y in range(16)]
 for row in px:row[0]=shade(base,-26);row[15]=shade(base,-26)
 png(A/'textures/block/turbine'/(name+'.png'),px)

# The shaft shows rows 6 to 9 along its octagon's faces; a groove marks the coupling at each block.
plain('rotor_disc',(108,116,120),{4:-12,5:34,6:52,7:14,8:-6,9:-22},.6)
print('Generated dense 36/40-blade swept rotors, octagonal casings and their turbine sprites')
