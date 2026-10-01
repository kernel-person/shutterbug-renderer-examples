#!/usr/bin/env python3
"""Original 32-unit painter's easel; geometry owned here, editable PNG in art/."""
from pathlib import Path
import argparse, base64, copy, hashlib, json, math, uuid
from PIL import Image
import paintbrush

ROOT = Path(__file__).resolve().parents[1]
ID = 'painters_easel'
NS = 'village_trades'
SIZE = 128
SIDES = ('north','south','east','west','up','down')

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n')

def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'heirloom/easel/'+name))

def design():
    """Twelve structural cuboids; slants use native rotations, not voxel steps."""
    parts=[]
    def add(name,lo,hi,grain='y',rotation=None):
        p={'name':name,'from':lo,'to':hi,'grain':grain}
        if rotation:p['rotation']=rotation
        parts.append(p)
    for side,x in [('left',1.5),('right',14.5)]:
        add('foot_'+side,[x-2,0,6],[x+2,1,10],'x')
    add('foot_rear',[6,0,-.5],[10,1,3.5],'x')
    base=round(.5+math.sin(math.pi/8),10)
    for side,x,angle in [('left',1.5,-22.5),('right',14.5,22.5)]:
        add('leg_'+side,[x-1,base,7],[x+1,base+15,9],rotation={
            'origin':[x,base,8],'axis':'z','angle':angle,'rescale':False})
    add('leg_rear',[7,base,.5],[9,base+15,2.5],rotation={
        'origin':[8,base,1.5],'axis':'x','angle':22.5,'rescale':False})
    add('lower_crossbar',[3.5,6,7],[12.5,8,8],'x')
    add('paint_stained_ledge',[-2,14,7],[18,16,12],'x')
    add('frame_left',[-2,16,7],[0,32,9])
    add('frame_right',[16,16,7],[18,32,9])
    add('frame_top',[0,30,7],[16,32,9],'x')
    add('rear_crossbar',[0,21,7],[16,23,8],'x')
    return parts

def face_dimensions(p, side):
    x,y,z=[round(b-a,8) for a,b in zip(p['from'],p['to'])]
    return (x,y) if side in ('north','south') else (z,y) if side in ('east','west') else (x,z)

def atlas_layout(parts):
    """One original texture pixel per raw model unit, with padded face islands."""
    x=y=1; row_h=0; layout={}
    for p in parts:
        for side in SIDES:
            w,h=face_dimensions(p,side)
            wi,hi=math.ceil(w),math.ceil(h)
            if x+wi+1>SIZE:x=1;y+=row_h+1;row_h=0
            if y+hi+1>SIZE:raise ValueError('Atlas overflow')
            layout[p['name'],side]=(x,y,w,h)
            x+=wi+1;row_h=max(row_h,hi)
    return layout

OAK=((104,69,39),(130,86,46),(151,105,58),(174,125,70),(193,147,89))
STAINS={'red':(139,63,52),'blue':(64,99,119),'cream':(204,189,134),'green':(98,119,69)}

def paint(parts,layout):
    image=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0))
    for index,p in enumerate(parts):
        for side in SIDES:
            ox,oy,w,h=layout[p['name'],side];w,h=math.ceil(w),math.ceil(h)
            along_u=p['grain']=='x' and side in ('north','south','up','down')
            end=side in ('east','west') if p['grain']=='x' else side in ('up','down')
            for v in range(h):
                for u in range(w):
                    along,cross=(u,v) if along_u else (v,u)
                    # Long quiet grain streaks, short deliberate dark joints.
                    tone=2+((cross+index)%3==0)
                    if (cross*3+index)%7==2 and 2 <= along%11 <= 7:tone=1
                    if (along+index*3)%17==8 and cross%3==1:tone=0
                    if end:tone=1+(min(u,v,w-1-u,h-1-v)+index)%3
                    c=OAK[tone]
                    if p['name'].startswith('foot_'):c=tuple(max(0,a-10) for a in c)
                    if p['name']=='paint_stained_ledge':
                        if side=='up':
                            if (u,v) in {(3,3),(4,3),(4,2)}:c=STAINS['blue']
                            if (u,v) in {(13,2),(14,2),(14,3)}:c=STAINS['red']
                            if (u,v) in {(8,3),(8,4)}:c=STAINS['cream']
                            if (u,v)==(17,1):c=STAINS['green']
                        elif side=='south':
                            if (u,v) in {(4,0),(4,1)}:c=STAINS['blue']
                            if (u,v)==(14,0):c=STAINS['red']
                            if (u,v)==(8,0):c=STAINS['cream']
                    # Small wooden peg/join marks stay in the texture.
                    if p['name'] in ('frame_left','frame_right') and side=='south' and v in (2,13) and u==0:c=OAK[0]
                    image.putpixel((ox+u,oy+v),(*c,255))
    return image

def transforms():
    return {
        'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
        'gui':{'rotation':[15,-30,0],'translation':[0,-3,0],'scale':[.43]*3},
        'ground':{'rotation':[0,0,0],'translation':[0,4,0],'scale':[.35]*3},
        'firstperson_righthand':{'rotation':[0,-35,0],'translation':[0,1,0],'scale':[.32]*3},
        'firstperson_lefthand':{'rotation':[0,35,0],'translation':[0,1,0],'scale':[.32]*3},
        'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.32]*3},
        'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.32]*3},
    }

def canvas_contract():
    return {'schema':1,'item_id':f'{NS}:{ID}','units_per_block':16,
            'height_model_units':32,'ground_anchor_model_units':[8,0,8],
            'canvas':{'min':[0,16,10],'max':[16,32,10],'center':[8,24,10],
                      'size_model_units':[16,16],'front_normal':[0,0,1],
                      'image_right':[1,0,0],'image_up':[0,1,0],
                      'backing_max_z':9,'backing_gap_model_units':1,
                      'exported':False,'provided_by':'plugin live map'},
            'placement':{'item_display_context':'fixed','fixed_is_identity':True,
                         'entity_offset_from_ground_blocks':[0,.5,0],
                         'south_facing_entity_yaw_degrees':180,'entity_pitch_degrees':0,
                         'entity_scale':[1,1,1],'billboard':'fixed',
                         'canvas_center_from_ground_south_blocks':[0,1.5,.125],
                         'south_front_normal':[0,0,1],
                         'note':'ItemDisplay renderer adds Y180; entity yaw desired front yaw+180 cancels it. Align the live map visible plane separately.'}}

def generate(output, art_dir, seed=False):
    parts=design();layout=atlas_layout(parts)
    input_path=art_dir/f'{ID}.png'
    if input_path.exists():
        with Image.open(input_path) as src:image=src.convert('RGBA')
    else:
        image=paint(parts,layout)
        if seed:
            input_path.parent.mkdir(parents=True,exist_ok=True)
            image.save(input_path,compress_level=9)
    if image.size!=(SIZE,SIZE):raise ValueError('Expected 128x128 editable PNG')
    elements=[]
    for p in parts:
        e={k:copy.deepcopy(v) for k,v in p.items() if k!='grain'};e['faces']={}
        for side in SIDES:
            x,y,w,h=layout[p['name'],side]
            e['faces'][side]={'uv':[round(v*16/SIZE,10) for v in (x,y,x+w,y+h)],'texture':'#art'}
        elements.append(e)
    model={'credit':'Original Heirloom easel; tools/generate.py and editable art/painters_easel.png',
           'texture_size':[SIZE,SIZE],'ambientocclusion':False,'gui_light':'side',
           'textures':{'art':f'{NS}:item/{ID}','particle':f'{NS}:item/{ID}'},
           'elements':elements,'display':transforms()}
    pack=output/'public/resourcepack';assets=pack/'assets'/NS
    write_json(pack/'pack.mcmeta',{'pack':{'min_format':[88,0],'max_format':[88,0],
                                        'description':'Village Trades — original wooden painter’s easel'}})
    write_json(assets/'items'/f'{ID}.json',{'model':{'type':'minecraft:model','model':f'{NS}:item/{ID}'}})
    write_json(assets/'models/item'/f'{ID}.json',model)
    texture=assets/'textures/item'/f'{ID}.png';texture.parent.mkdir(parents=True,exist_ok=True)
    image.save(texture,compress_level=9)
    tid=uid('texture');bb=[]
    for e in elements:
        rotation=e.get('rotation',{});angles=[0,0,0]
        if rotation:angles['xyz'.index(rotation['axis'])]=rotation['angle']
        bb.append({'name':e['name'],'type':'cube','uuid':uid(e['name']),
                   'from':e['from'],'to':e['to'],'origin':rotation.get('origin',[8,8,8]),
                   'rotation':angles,'rescale':False,'autouv':0,'box_uv':False,'color':0,
                   'faces':{side:{'uv':[v*SIZE/16 for v in face['uv']],'texture':tid} for side,face in e['faces'].items()}})
    groups=[]
    for label,names in [('Stand',[e['name'] for e in elements if e['name'].startswith(('foot_','leg_'))]+['lower_crossbar']),
                        ('Frame',['frame_left','frame_right','frame_top','rear_crossbar']),
                        ('Ledge',['paint_stained_ledge'])]:
        groups.append({'name':label,'uuid':uid('group/'+label),'origin':[8,0,8],
                       'rotation':[0,0,0],'children':[uid(name) for name in names]})
    write_json(output/'blockbench'/f'{ID}.bbmodel',{
        'meta':{'format_version':'5.0','model_format':'java_block','box_uv':False},
        'name':'Painter’s Easel','model_identifier':f'{NS}:item/{ID}',
        'resolution':{'width':SIZE,'height':SIZE},'front_gui_light':False,'ambientocclusion':False,
        'elements':bb,
        'groups':[{k:v for k,v in group.items() if k!='children'} for group in groups],
        'outliner':[{'uuid':group['uuid'],'isOpen':True,'children':group['children']} for group in groups],
        'textures':[{'name':texture.name,'namespace':NS,'folder':'item','id':'art','uuid':tid,
                     'mode':'bitmap','internal':True,'width':SIZE,'height':SIZE,'uv_width':SIZE,'uv_height':SIZE,
                     'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}],
        'display':transforms()})
    contract=canvas_contract()
    for folder in ('public','handoff'):write_json(output/folder/'canvas-contract.json',contract)
    write_json(output/'handoff/atlas-layout.json',{
        'texture_size':[SIZE,SIZE],'pixels_per_model_unit':1,
        'parts':{p['name']:{s:list(layout[p['name'],s]) for s in SIDES} for p in parts}})
    paintbrush.generate(output,art_dir,seed)
    from props import extend
    extend(output,art_dir,seed)
    files=[p for folder in ('public/resourcepack','blockbench') for p in (output/folder).rglob('*') if p.is_file()]
    write_json(output/'handoff/asset-checksums.json',{str(p.relative_to(output)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)})
    print(f'Generated {len(elements)} cuboids, original {SIZE}×{SIZE} texture, native item and editable Blockbench source into {output}')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT)
    parser.add_argument('--art-dir',type=Path,default=ROOT/'art')
    parser.add_argument('--seed-art',action='store_true')
    args=parser.parse_args();generate(args.output.resolve(),args.art_dir.resolve(),args.seed_art)
