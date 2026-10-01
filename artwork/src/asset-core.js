const DIRECTIONS = ['north', 'south', 'east', 'west', 'up', 'down'];
const DISPLAY_CONTEXTS = ['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand','gui','head','ground','fixed'];

function supportedKeys(object, keys, label) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) throw new Error(`${label} must be an object.`);
  for (const key of Object.keys(object)) {
    if (!keys.includes(key)) throw new Error(`Unsupported ${label} field: ${key}.`);
  }
}

function vector(value, length, label) {
  if (!Array.isArray(value) || value.length !== length || value.some(n => typeof n !== 'number' || !Number.isFinite(n))) {
    throw new Error(`${label} must contain ${length} finite numbers.`);
  }
}

export function resourceUrl(location, kind, assetBase = '') {
  if (assetBase!=='' && !/^\/(?:[a-z0-9_-]+\/)*[a-z0-9_-]+$/.test(assetBase)) throw new Error(`Invalid asset source: ${assetBase}.`);
  if (typeof location !== 'string' || !/^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$/.test(location) || location.includes('..')) {
    throw new Error(`Invalid resource location: ${location}.`);
  }
  const [namespace, path] = location.includes(':') ? location.split(':') : ['minecraft', location];
  if (path.startsWith('/') || path.endsWith('/')) throw new Error(`Invalid resource location: ${location}.`);
  const extension = { models:'json', items:'json', textures:'png' }[kind];
  if (!extension) throw new Error(`Unsupported resource kind: ${kind}.`);
  return `${assetBase}/resourcepack/assets/${namespace}/${kind}/${path}.${extension}`;
}

export function resolveTexture(reference, textures, visited = new Set()) {
  if (typeof reference !== 'string') throw new Error('Face texture must be a resource location or alias.');
  if (!reference.startsWith('#')) return reference;
  const key = reference.slice(1);
  if (visited.has(key)) throw new Error(`Cyclic texture alias: ${reference}.`);
  if (!Object.hasOwn(textures, key)) throw new Error(`Missing texture alias: ${reference}.`);
  visited.add(key);
  return resolveTexture(textures[key], textures, visited);
}

/** Minecraft FaceInfo vertex order; quads face outwards and UV origin is image top-left. */
export function faceGeometry(direction, from, to, uv, rotation = 0) {
  const [x0,y0,z0] = from, [x1,y1,z1] = to;
  const vertices = {
    north:[[x1,y1,z0],[x1,y0,z0],[x0,y0,z0],[x0,y1,z0]],
    south:[[x0,y1,z1],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1]],
    east: [[x1,y1,z1],[x1,y0,z1],[x1,y0,z0],[x1,y1,z0]],
    west: [[x0,y1,z0],[x0,y0,z0],[x0,y0,z1],[x0,y1,z1]],
    up:   [[x0,y1,z0],[x0,y1,z1],[x1,y1,z1],[x1,y1,z0]],
    down: [[x0,y0,z1],[x0,y0,z0],[x1,y0,z0],[x1,y0,z1]],
  }[direction];
  if (!vertices) throw new Error(`Unsupported face direction: ${direction}.`);
  if (![0,90,180,270].includes(rotation)) throw new Error(`Unsupported UV rotation: ${rotation}.`);
  const [u0,v0,u1,v1] = uv;
  const coordinates = [[u0/16,1-v0/16],[u0/16,1-v1/16],[u1/16,1-v1/16],[u1/16,1-v0/16]];
  return {positions: vertices, uv: coordinates.map((_,i)=>coordinates[(i+rotation/90)%4])};
}

export function rotatePoint(point, rotation) {
  if (!rotation) return [...point];
  const { origin, axis, angle, rescale = false } = rotation;
  const r = angle * Math.PI / 180, c = Math.cos(r), s = Math.sin(r);
  const [x,y,z] = point.map((n,i)=>n-origin[i]);
  const rotated = axis === 'x' ? [x,y*c-z*s,y*s+z*c] : axis === 'y' ? [x*c+z*s,y,-x*s+z*c] : [x*c-y*s,x*s+y*c,z];
  const axisIndex = ['x','y','z'].indexOf(axis);
  return rotated.map((n,i)=>origin[i]+n*(rescale && i!==axisIndex ? 1/c : 1));
}

export function parseAnimation(mcmeta, width, height) {
  if (mcmeta===null || mcmeta===undefined) return null;
  if (typeof mcmeta!=='object' || Array.isArray(mcmeta)) throw new Error('Texture metadata must be an object.');
  if (!Object.hasOwn(mcmeta,'animation')) return null;
  const metadata = mcmeta.animation;
  supportedKeys(metadata, ['frametime','frames','width','height','interpolate'], 'animation');
  for (const field of ['frametime','width','height']) {
    if (Object.hasOwn(metadata,field) && (!Number.isInteger(metadata[field]) || metadata[field]<=0)) {
      throw new Error(`Animation ${field} must be a positive integer.`);
    }
  }
  if (Object.hasOwn(metadata,'frames') && !Array.isArray(metadata.frames)) throw new Error('Animation frames must be an array.');
  if (Object.hasOwn(metadata,'interpolate') && typeof metadata.interpolate!=='boolean') throw new Error('Animation interpolate must be a boolean.');
  if (metadata.interpolate) throw new Error('Interpolated texture animation is unsupported; use discrete Minecraft frames.');
  const frameWidth = metadata.width ?? (metadata.height ? width : Math.min(width,height));
  const frameHeight = metadata.height ?? (metadata.width ? height : Math.min(width,height));
  const frametime = metadata.frametime ?? 1;
  if (![frameWidth,frameHeight,frametime].every(n=>Number.isInteger(n) && n>0) || width%frameWidth || height%frameHeight) {
    throw new Error('Animation frame dimensions or frametime are invalid.');
  }
  const frameCount = width/frameWidth * height/frameHeight;
  const rawFrames = metadata.frames ?? Array.from({length:frameCount},(_,i)=>i);
  if (!Array.isArray(rawFrames) || rawFrames.length===0) throw new Error('Animation requires at least one frame.');
  const frames = rawFrames.map(frame => {
    if (typeof frame==='object' && frame!==null) supportedKeys(frame,['index','time'],'animation frame');
    const index = typeof frame==='number' ? frame : frame?.index;
    const time = typeof frame==='number' ? frametime : frame && Object.hasOwn(frame,'time') ? frame.time : frametime;
    if (!Number.isInteger(index) || index<0 || index>=frameCount || !Number.isInteger(time) || time<=0) throw new Error('Animation frame index or duration is invalid.');
    return {index,time};
  });
  return {width,height,frameWidth,frameHeight,frameCount,frames,totalTicks:frames.reduce((sum,f)=>sum+f.time,0)};
}

export function animationFrameAt(animation, elapsedMs) {
  let tick = Math.floor(Math.max(0,elapsedMs)/50)%animation.totalTicks;
  for (const frame of animation.frames) {
    if (tick<frame.time) return frame.index;
    tick-=frame.time;
  }
  return animation.frames[0].index;
}

export function frameTextureTransform(animation, frame) {
  const columns = animation.width/animation.frameWidth;
  const x = frame%columns, y = Math.floor(frame/columns);
  const repeat = [animation.frameWidth/animation.width,animation.frameHeight/animation.height];
  return {repeat,offset:[x*repeat[0],1-(y+1)*repeat[1]]};
}

export function validateModel(model) {
  supportedKeys(model,['parent','textures','elements','display','gui_light','ambientocclusion','credit','texture_size'],'model');
  if (model.gui_light && !['front','side'].includes(model.gui_light)) throw new Error(`Unsupported gui_light: ${model.gui_light}.`);
  if (!Array.isArray(model.elements) || !model.elements.length) throw new Error('Model has no explicit cuboid elements. Generated and entity models are unsupported.');
  if (!model.textures || typeof model.textures!=='object') throw new Error('Model textures must be an object.');
  for (const [index, element] of model.elements.entries()) {
    const label = `element ${element.name ?? index}`;
    supportedKeys(element,['name','from','to','rotation','shade','light_emission','faces'],label);
    if (Object.hasOwn(element,'light_emission')) {
      const emission=element.light_emission;
      if (!Number.isInteger(emission) || emission<0 || emission>15) throw new Error(`${label} light_emission must be an integer from 0 to 15.`);
      if (emission!==0 && emission!==15) throw new Error(`${label}: partial lightmap levels are unsupported; this viewer approximates light_emission 0 and 15 only.`);
    }
    vector(element.from,3,`${label} from`);
    vector(element.to,3,`${label} to`);
    if (element.from.some((n,i)=>n>element.to[i])) throw new Error(`${label} has reversed bounds.`);
    if (element.rotation) {
      supportedKeys(element.rotation,['origin','axis','angle','rescale'],`${label} rotation`);
      vector(element.rotation.origin,3,`${label} rotation origin`);
      if (!['x','y','z'].includes(element.rotation.axis)) throw new Error(`${label} rotation axis is unsupported.`);
      if (![-45,-22.5,0,22.5,45].includes(element.rotation.angle)) throw new Error(`${label} rotation angle is unsupported.`);
    }
    supportedKeys(element.faces,DIRECTIONS,`${label} faces`);
    for (const [direction,face] of Object.entries(element.faces)) {
      supportedKeys(face,['uv','texture','rotation','cullface','tintindex'],`${label} ${direction}`);
      vector(face.uv,4,`${label} ${direction} UV`);
      if (face.rotation!==undefined && ![0,90,180,270].includes(face.rotation)) throw new Error(`${label} face rotation is unsupported.`);
      if (face.tintindex!==undefined && face.tintindex!==-1) throw new Error(`${label} tinted faces are unsupported.`);
      if (face.cullface!==undefined && !DIRECTIONS.includes(face.cullface)) throw new Error(`${label} cullface is invalid.`);
      resourceUrl(resolveTexture(face.texture,model.textures),'textures');
    }
  }
  if (model.display) {
    supportedKeys(model.display,DISPLAY_CONTEXTS,'display context');
    for (const [key,value] of Object.entries(model.display)) {
      supportedKeys(value,['rotation','translation','scale'],`display ${key}`);
      for (const property of ['rotation','translation','scale']) if (value[property]) vector(value[property],3,`display ${key} ${property}`);
    }
  }
  return model;
}

export function displayTransform(model, context) {
  const fallback = context.endsWith('_lefthand') ? context.replace('_lefthand','_righthand') : context;
  const transform = model.display?.[context] ?? model.display?.[fallback] ?? {};
  const rotation = [...(transform.rotation ?? [0,0,0])];
  const translation = [...(transform.translation ?? [0,0,0])];
  const scale = [...(transform.scale ?? [1,1,1])];
  if (context.endsWith('_lefthand')) {
    translation[0]*=-1;
    rotation[1]*=-1;
    rotation[2]*=-1;
  }
  return {rotation,translation,scale};
}

const IDENTITY_TRANSFORMATION = [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];

function itemTransformation(value) {
  if (value===undefined) return [...IDENTITY_TRANSFORMATION];
  vector(value,16,'Item transformation (row-major matrix)');
  if (value[12]!==0 || value[13]!==0 || value[14]!==0 || value[15]!==1) {
    throw new Error('Unsupported non-affine item transformation.');
  }
  const determinant=value[0]*(value[5]*value[10]-value[6]*value[9])-value[1]*(value[4]*value[10]-value[6]*value[8])+value[2]*(value[4]*value[9]-value[5]*value[8]);
  if (determinant===0) throw new Error('Unsupported singular item transformation: native normal transformation requires an inverse.');
  return [...value];
}

function composeTransformation(parent, child) {
  return Array.from({length:16},(_,i)=>{
    const row=Math.floor(i/4),column=i%4;
    return [0,1,2,3].reduce((sum,k)=>sum+parent[row*4+k]*child[k*4+column],0);
  });
}

/** Native item transforms accumulate parent × child before the leaf display transform. */
export function selectItemModels(node, {usingItem=false,useTicks=0}={}, trace=[], parent=IDENTITY_TRANSFORMATION) {
  if (!node || typeof node!=='object') throw new Error('Missing item model branch.');
  switch (node.type) {
    case 'minecraft:model':
      supportedKeys(node,['type','model','transformation'],'item model');
      resourceUrl(node.model,'models');
      return [{modelLocation:node.model,trace,transformation:composeTransformation(parent,itemTransformation(node.transformation))}];
    case 'minecraft:composite': {
      supportedKeys(node,['type','models','transformation'],'item composite');
      if (!Array.isArray(node.models) || !node.models.length) throw new Error('Item composite requires at least one model.');
      const transformation=composeTransformation(parent,itemTransformation(node.transformation));
      return node.models.flatMap((child,index)=>selectItemModels(child,{usingItem,useTicks},[...trace,`composite part ${index+1}/${node.models.length}`],transformation));
    }
    case 'minecraft:condition': {
      supportedKeys(node,['type','property','on_true','on_false'],'item condition');
      if (node.property!=='minecraft:using_item') throw new Error(`Unsupported item condition property: ${node.property}.`);
      return selectItemModels(usingItem?node.on_true:node.on_false,{usingItem,useTicks},[...trace,`using_item = ${Boolean(usingItem)}`],parent);
    }
    case 'minecraft:range_dispatch': {
      supportedKeys(node,['type','property','scale','entries','fallback'],'item range');
      if (node.property!=='minecraft:use_duration') throw new Error(`Unsupported item range property: ${node.property}.`);
      const scale=node.scale??1;
      if (!Number.isFinite(scale) || !Number.isFinite(useTicks) || useTicks<0) throw new Error('Invalid native use duration.');
      if (!Array.isArray(node.entries)) throw new Error('Native range requires entries.');
      let branch=node.fallback, threshold=-Infinity;
      const value=useTicks*scale;
      for (const entry of node.entries) {
        supportedKeys(entry,['threshold','model'],'range entry');
        if (!Number.isFinite(entry.threshold)) throw new Error('Invalid native range threshold.');
        if (value>=entry.threshold && entry.threshold>=threshold) {threshold=entry.threshold;branch=entry.model;}
      }
      return selectItemModels(branch,{usingItem,useTicks},[...trace,`use_duration ${useTicks} × ${scale} = ${Number(value.toFixed(4))}`,threshold===-Infinity?'fallback':`threshold ≥ ${threshold}`],parent);
    }
    default: throw new Error(`Unsupported item model type: ${node.type??'missing'}.`);
  }
}

// Preserve the original first-leaf selection API for labels and existing callers.
export function selectItemModel(node, selection={}, trace=[]) {
  const {modelLocation,trace:selectedTrace}=selectItemModels(node,selection,trace)[0];
  return {modelLocation,trace:selectedTrace};
}

export async function loadExportedModel(id, readJson, selection={}, assetBase='') {
  const itemUrl = resourceUrl(`village_trades:${id}`,'items',assetBase);
  const definition = await readJson(itemUrl);
  supportedKeys(definition,['model'],'item definition');
  const selected = selectItemModels(definition.model,selection);
  const models = new Map();
  async function readModel(location, ancestors=new Set()) {
    if (ancestors.has(location)) throw new Error(`Cyclic model parent: ${location}.`);
    if (!models.has(location)) models.set(location,(async()=>{
      const url = resourceUrl(location,'models',assetBase);
      const source = await readJson(url);
      if (!source || typeof source!=='object') throw new Error(`Missing exported model: ${url}.`);
      if (!source.parent) return source;
      if (source.parent.includes('builtin/')) throw new Error(`Unsupported built-in model parent: ${source.parent}.`);
      const parent = await readModel(source.parent,new Set([...ancestors,location]));
      const result = {...parent,...source,textures:{...parent.textures,...source.textures},display:{...parent.display,...source.display}};
      delete result.parent;
      return result;
    })());
    return models.get(location);
  }
  const parts=[];
  for (const part of selected) parts.push({...part,model:validateModel(await readModel(part.modelLocation))});
  const {model,modelLocation,trace}=parts[0];
  // The viewer applies one display group. Reject different leaf displays rather
  // than silently showing a composite that Minecraft would place differently.
  for (const part of parts.slice(1)) {
    for (const context of DISPLAY_CONTEXTS) {
      if (JSON.stringify(displayTransform(part.model,context))!==JSON.stringify(displayTransform(model,context))) {
        throw new Error(`Unsupported composite with differing display transforms: ${part.modelLocation} (${context}).`);
      }
    }
    if ((part.model.gui_light??'side')!==(model.gui_light??'side')) throw new Error('Unsupported composite with differing gui_light modes.');
  }
  return {id,itemUrl,modelLocation,modelUrl:resourceUrl(modelLocation,'models',assetBase),assetBase,model,parts,definition,trace};
}
