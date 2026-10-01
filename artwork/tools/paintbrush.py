"""Original chunky paintbrush geometry and persistent editable pixel texture."""
from pathlib import Path
import base64, copy, json, math, uuid
from PIL import Image

NAME='paintbrush'
SIZE=64
SIDES=('north','south','east','west','up','down')
GRIP=[8,5.5,8]
PARTS=(
    ('wood_handle_butt',[6.5,0,6.5],[9.5,2,9.5],'wood'),
    ('wood_handle',[7,2,7],[9,10,9],'wood'),
    ('wood_handle_neck',[6.5,10,7],[9.5,12,9],'wood'),
    ('iron_ferrule',[6,12,6.5],[10,15,9.5],'iron'),
    ('bristles',[5.5,15,6.5],[10.5,19,9.5],'bristle'),
    ('bristle_tip',[6.5,19,6.5],[9.5,20,9.5],'paint'),
)

def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')

def uid(label):return str(uuid.uuid5(uuid.NAMESPACE_URL,'heirloom/paintbrush/'+label))

def layout():
    x=y=1;row_h=0;result={}
    for name,lo,hi,_material in PARTS:
        w,h,d=[int(b-a) for a,b in zip(lo,hi)]
        for side in SIDES:
            width,height=(w,h) if side in ('north','south') else (d,h) if side in ('east','west') else (w,d)
            if x+width+1>SIZE:x=1;y+=row_h+1;row_h=0
            if y+height+1>SIZE:raise ValueError('Paintbrush atlas overflow')
            result[name,side]=[x,y,width,height];x+=width+1;row_h=max(row_h,height)
    return result

def transforms():
    return {
        'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
        'gui':{'rotation':[0,-22.5,-35],'translation':[0,-1,0],'scale':[.72]*3},
        'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.5]*3},
        'firstperson_righthand':{'rotation':[0,-90,12],'translation':[1.13,3.2,1.13],'scale':[.65]*3},
        'firstperson_lefthand':{'rotation':[0,90,-12],'translation':[1.13,3.2,1.13],'scale':[.65]*3},
        'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,3,1],'scale':[.65]*3},
        'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,3,1],'scale':[.65]*3},
    }

WOOD=((77,48,30),(100,63,36),(124,80,44),(146,99,55))
IRON=((65,73,75),(95,105,105),(130,139,135),(157,162,150))
HAIR=((139,120,78),(174,153,101),(203,184,129),(224,207,153))
BLUE=((47,80,96),(61,102,123),(79,125,144),(109,149,158))

def source_image():
    image=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0));islands=layout()
    for index,(name,lo,hi,material) in enumerate(PARTS):
        for side in SIDES:
            ox,oy,w,h=islands[name,side]
            end=side in ('up','down')
            for v in range(h):
                for u in range(w):
                    if material=='wood':
                        tone=2 if u==0 else 1
                        if v%7 in (2,3) and u==w-1:tone=0
                        if end:tone=1+(u+v)%2
                        colour=WOOD[tone]
                        # A single old paint fleck near the neck, away from grip.
                        if name=='wood_handle_neck' and side=='south' and (u,v)==(2,0):colour=BLUE[1]
                    elif material=='iron':
                        tone=2 if v==0 or side=='up' else 1
                        if u==w-1:tone=max(0,tone-1)
                        colour=IRON[tone]
                        if side in ('north','south') and (u,v)==(1,1):colour=IRON[3]
                    else:
                        # Long bristle strands are pixels, not tiny geometry.
                        strand=(u+(1 if side in ('east','west') else 0))%3
                        painted=material=='paint' or (not end and (v==0 or v==1 and u in (1,2)))
                        if side=='up':painted=True
                        if side=='down' and material=='bristle':painted=False
                        colour=(BLUE if painted else HAIR)[strand+1 if strand<2 else 1]
                    image.putpixel((ox+u,oy+v),(*colour,255))
    return image

def generate(output,art_dir,seed=False):
    output,art_dir=Path(output),Path(art_dir)
    source=art_dir/f'{NAME}.png'
    if source.exists():
        with Image.open(source) as original:image=original.convert('RGBA')
    else:
        image=source_image()
        if seed:
            source.parent.mkdir(parents=True,exist_ok=True);image.save(source,compress_level=9)
    if image.size!=(SIZE,SIZE):raise ValueError('Paintbrush source must remain64×64')
    islands=layout();elements=[]
    for name,lo,hi,_material in PARTS:
        faces={}
        for side in SIDES:
            x,y,w,h=islands[name,side]
            faces[side]={'uv':[v/4 for v in (x,y,x+w,y+h)],'texture':'#art'}
        elements.append({'name':name,'from':lo,'to':hi,'faces':faces})
    model={'credit':'Original Heirloom paintbrush; tools/paintbrush.py and editable art/paintbrush.png',
           'texture_size':[SIZE,SIZE],'ambientocclusion':False,'gui_light':'side',
           'textures':{'art':'village_trades:item/paintbrush','particle':'village_trades:item/paintbrush'},
           'elements':elements,'display':transforms()}
    assets=output/'public/resourcepack/assets/village_trades'
    write_json(assets/'models/item/paintbrush.json',model)
    write_json(assets/'items/paintbrush.json',{'model':{'type':'minecraft:model','model':'village_trades:item/paintbrush'}})
    texture=assets/'textures/item/paintbrush.png';texture.parent.mkdir(parents=True,exist_ok=True);image.save(texture,compress_level=9)
    tid=uid('texture')
    bb=[{'name':e['name'],'type':'cube','uuid':uid(e['name']),'from':e['from'],'to':e['to'],
         'origin':[8,8,8],'rotation':[0,0,0],'rescale':False,'autouv':0,'box_uv':False,'color':0,
         'faces':{side:{'uv':[v*SIZE/16 for v in face['uv']],'texture':tid} for side,face in e['faces'].items()}} for e in elements]
    groups=[{'name':label,'uuid':uid('group/'+label),'origin':GRIP,'rotation':[0,0,0]} for label in ('Handle','Ferrule','Bristles')]
    members=(('wood_handle_butt','wood_handle','wood_handle_neck'),('iron_ferrule',),('bristles','bristle_tip'))
    write_json(output/'blockbench/paintbrush.bbmodel',{
        'meta':{'format_version':'5.0','model_format':'java_block','box_uv':False},
        'name':'Painter’s Brush','model_identifier':'village_trades:item/paintbrush',
        'resolution':{'width':SIZE,'height':SIZE},'front_gui_light':False,'ambientocclusion':False,
        'elements':bb,'groups':groups,
        'outliner':[{'uuid':group['uuid'],'isOpen':True,'children':[uid(name) for name in names]} for group,names in zip(groups,members)],
        'textures':[{'name':texture.name,'namespace':'village_trades','folder':'item','id':'art','uuid':tid,
                     'mode':'bitmap','internal':True,'width':SIZE,'height':SIZE,'uv_width':SIZE,'uv_height':SIZE,
                     'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}],
        'display':transforms()})
    write_json(output/'handoff/paintbrush-atlas-layout.json',{
        'texture_size':[SIZE,SIZE],'pixels_per_model_unit':1,
        'parts':{name:{side:islands[name,side] for side in SIDES} for name,*_rest in PARTS}})
    write_json(output/'handoff/paintbrush-mapping.json',{
        'item_id':'village_trades:paintbrush','model_id':'village_trades:item/paintbrush',
        'role':'finished painting tool artwork','raw_grip':GRIP,'raw_height_units':20,
        'source_png':'art/paintbrush.png','blockbench':'blockbench/paintbrush.bbmodel',
        'provider_assignment':'pending_integration','gameplay_implemented':False,
        'colour_note':'Static blue paint is decorative; plugin painting colour remains independent.'})
    print('Generated original six-cuboid paintbrush and64×64 editable-source export')
