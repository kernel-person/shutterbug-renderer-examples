"""Original demo props. Code owns geometry; existing PNG inputs are never repainted."""
import base64
import copy
import json
import math
import uuid
from PIL import Image

NS='village_trades'
SIDES=('north','south','east','west','up','down')
SIZE=128

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')

def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'kernel-person/renderer-examples/'+name))

def box(name,lo,hi,material='wood'):
    return {'name':name,'from':lo,'to':hi,'material':material}

def dimensions(part,side):
    x,y,z=[b-a for a,b in zip(part['from'],part['to'])]
    return (x,y) if side in ('north','south') else (z,y) if side in ('east','west') else (x,z)

def display(scale=.7):
    return {'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
            'gui':{'rotation':[20,-30,0],'translation':[0,0,0],'scale':[scale]*3},
            'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.4]*3},
            'firstperson_righthand':{'rotation':[0,-30,0],'translation':[0,0,0],'scale':[.4]*3},
            'firstperson_lefthand':{'rotation':[0,-30,0],'translation':[0,0,0],'scale':[.4]*3},
            'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.35]*3},
            'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.35]*3}}

def paint_color(material,u,v,side):
    palettes={'wood':[(112,77,47),(145,103,61),(176,133,81)],
              'iron':[(40,44,44),(63,68,67),(89,95,89)],
              'glass':[(17,33,39),(31,60,65),(81,117,114)],
              'redstone':[(91,28,26),(151,42,33),(198,68,45)],
              'screen':[(25,32,31),(32,39,37),(39,46,42)],
              'linen':[(219,215,201),(236,231,216),(247,243,229)]}
    colors=palettes[material]
    if material=='wood':tone=0 if u%7==1 and v%11<7 else 2 if u%6==0 else 1
    elif material=='linen':tone=0 if u%6==0 and v%5<3 else 2 if v%5==0 else 1
    elif material=='glass':tone=2 if u==v or u==v+1 else 1 if u%7==0 else 0
    else:tone=2 if u==0 or v==0 else 0 if (u+2*v)%17==3 else 1
    return (*colors[tone],255)

def export(root,art,name,parts,seed=False,scale=.7):
    image=Image.new('RGBA',(SIZE,SIZE));x=y=1;row=0;elements=[];pixel_faces=[]
    for part in parts:
        element={k:copy.deepcopy(v) for k,v in part.items() if k!='material'};element['faces']={};pixels={}
        for side in SIDES:
            w,h=dimensions(part,side);wi,hi=math.ceil(w),math.ceil(h)
            if x+wi+1>SIZE:x=1;y+=row+1;row=0
            if y+hi+1>SIZE:raise ValueError('Atlas overflow for '+name)
            pixels[side]=[x,y,x+w,y+h]
            element['faces'][side]={'uv':[v*16/SIZE for v in pixels[side]],'texture':'#art'}
            for v in range(hi):
                for u in range(wi):image.putpixel((x+u,y+v),paint_color(part['material'],u,v,side))
            x+=wi+1;row=max(row,hi)
        elements.append(element);pixel_faces.append(pixels)
    source=art/(name+'.png')
    if source.exists():
        with Image.open(source) as saved:image=saved.convert('RGBA')
    elif seed:
        art.mkdir(parents=True,exist_ok=True);image.save(source,compress_level=9)
    if image.size!=(SIZE,SIZE):raise ValueError('Unexpected editable atlas dimensions: '+name)
    assets=root/'public/resourcepack/assets'/NS
    texture=assets/'textures/item'/(name+'.png');texture.parent.mkdir(parents=True,exist_ok=True)
    image.save(texture,compress_level=9)
    model={'credit':'Original Kernel Person renderer example artwork','texture_size':[SIZE,SIZE],
           'ambientocclusion':False,'gui_light':'side',
           'textures':{'art':f'{NS}:item/{name}','particle':f'{NS}:item/{name}'},
           'elements':elements,'display':display(scale)}
    write(assets/'models/item'/(name+'.json'),model)
    write(assets/'items'/(name+'.json'),{'model':{'type':'minecraft:model','model':f'{NS}:item/{name}'}})
    tid=uid(name+'/texture')
    bb_elements=[]
    for element,pixels in zip(elements,pixel_faces):
        bb_elements.append({'name':element['name'],'type':'cube','uuid':uid(name+'/'+element['name']),
            'from':element['from'],'to':element['to'],'origin':[8,8,8],'rotation':[0,0,0],
            'autouv':0,'box_uv':False,'faces':{side:{'uv':values,'texture':tid} for side,values in pixels.items()}})
    write(root/'blockbench'/(name+'.bbmodel'),{
        'meta':{'format_version':'5.0','model_format':'java_block','box_uv':False},'name':name,
        'resolution':{'width':SIZE,'height':SIZE},'elements':bb_elements,
        'outliner':[e['uuid'] for e in bb_elements],'display':model['display'],
        'textures':[{'name':name+'.png','id':'art','uuid':tid,'mode':'bitmap','internal':True,
                     'width':SIZE,'height':SIZE,'uv_width':SIZE,'uv_height':SIZE,
                     'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}]})
    write(root/'handoff'/(name+'-atlas.json'),{'size':[SIZE,SIZE],'pixels_per_model_unit':1,
          'faces':{e['name']:faces for e,faces in zip(elements,pixel_faces)}})

def extend(root,art,seed=False):
    # Dedicated linen input: no repacking or repainting of approved wood/brush islands.
    linen_path=art/'canvas_linen.png'
    if linen_path.exists():
        with Image.open(linen_path) as saved:linen=saved.convert('RGBA')
    else:
        linen=Image.new('RGBA',(16,16))
        for y in range(16):
            for x in range(16):linen.putpixel((x,y),paint_color('linen',x,y,'south'))
        if seed:linen.save(linen_path,compress_level=9)
    if linen.size!=(16,16):raise ValueError('Canvas linen must remain 16x16')
    assets=root/'public/resourcepack/assets'/NS
    texture=assets/'textures/item/canvas_linen.png';linen.save(texture,compress_level=9)
    slab=box('canvas_backing',[0,16,7.9375],[16,32,9.9375],'linen');del slab['material']
    slab['faces']={side:{'texture':'#linen','uv':[0,0,*dimensions(slab,side)]} for side in SIDES}
    path=assets/'models/item/painters_easel.json';model=json.loads(path.read_text())
    model['textures']['linen']=f'{NS}:item/canvas_linen';model['elements'].append(slab);write(path,model)
    path=root/'blockbench/painters_easel.bbmodel';bb=json.loads(path.read_text());tid=uid('canvas-linen')
    bb['textures'].append({'name':'canvas_linen.png','id':'linen','uuid':tid,'mode':'bitmap','internal':True,
        'width':16,'height':16,'uv_width':16,'uv_height':16,
        'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()})
    cube={**copy.deepcopy(slab),'uuid':uid('canvas-backing'),'type':'cube','origin':[8,24,9],
          'rotation':[0,0,0],'autouv':0,'box_uv':False}
    cube['faces']={side:{'uv':face['uv'],'texture':tid} for side,face in slab['faces'].items()}
    bb['elements'].append(cube);bb['outliner'].append(cube['uuid']);write(path,bb)
    for folder in ('public','handoff'):
        path=root/folder/'canvas-contract.json';contract=json.loads(path.read_text())
        contract['canvas'].update(backing_max_z=9.9375,backing_gap_model_units=.0625,
                                 backing_min_z=7.9375,backing_thickness_model_units=2)
        write(path,contract)

    body=[box('oak_case',[0,0,0],[16,16,16])]
    for x in (0,14):
        shift=-.0625 if x==0 else .0625
        body.append(box('iron_band_'+str(x),[x+shift,-.0625,-.03125],[x+2+shift,15.9375,16.03125],'iron'))
    body += [box('status_lamp',[13,12,16.03125],[16,14,16.09375],'redstone'),
             box('shutter',[5,15,5],[10,16.5,10],'iron')]
    lens=[box('socket',[2,2,7],[14,14,8],'iron'),
          box('lens_barrel',[3,3,8],[13,13,11],'iron'),
          box('glass',[4,4,11],[12,12,12],'glass')]
    export(root,art,'redstone_camera_body',body,seed)
    export(root,art,'redstone_camera_lens',lens,seed,.9)
    combined=copy.deepcopy(body+ lens)
    for part in combined[len(body):]:
        part['from'][2]+=8.24;part['to'][2]+=8.24
    export(root,art,'redstone_camera',combined,seed,.65)
    monitor=[box('left_bezel',[-2,0,6],[0,16,11]),box('right_bezel',[16,0,6],[18,16,11]),
             box('top_bezel',[-2,16,6],[18,18,11]),box('bottom_bezel',[-2,-2,6],[18,0,11]),
             box('screen_back',[0,0,6],[16,16,9.9375],'screen'),
             box('stand',[6,-14,6],[10,-2,9],'iron'),box('foot',[1,-16,2],[15,-14,14]),
             box('power_light',[13,-1.5,11],[15,-.5,11.25],'redstone')]
    export(root,art,'pov_monitor',monitor,seed,.5)
    write(root/'handoff/camera-contract.json',{'schema':1,'units_per_block':16,
          'body_half_extent_blocks':.53125*1.002,'body_scale':1.002,'lens_socket_gap_blocks':.015,
          'lens_front_blocks':.25,'capture_clearance_blocks':.05,'raw_front':[0,0,1],
          'body_item':'village_trades:redstone_camera_body','lens_item':'village_trades:redstone_camera_lens',
          'placement':'Body at block center, fixed world axes. Lens center = center + direction*(body_half_extent/max(abs(direction)) + socket_gap). Capture = lens center + direction*(lens_front + clearance). Native ItemDisplay Y180 is cancelled before applying the aim quaternion.'})
    write(root/'handoff/monitor-contract.json',{'schema':1,'item_id':'village_trades:pov_monitor',
          'canvas':{'min':[0,0,10],'max':[16,16,10],'center':[8,8,10],'front_normal':[0,0,1]},
          'ground_anchor':[8,-16,8],'backing_max_z':9.9375,'fixed_is_identity':True,
          'placement':'Same visible-frame offset as easel, but no vertical artwork translation: monitor screen center is raw [8,8,10]. Two owned support blocks; content supplied by contextual map renderer.'})
    write(root/'public/resourcepack/pack.mcmeta',{'pack':{'min_format':[88,0],'max_format':[88,0],
          'description':'Kernel Person — Renderer Examples 1.1.0: easel, brush, camera and POV monitor'}})
