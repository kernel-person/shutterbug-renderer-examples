import * as THREE from 'three';
import { resolveTexture, resourceUrl, parseAnimation, faceGeometry, rotatePoint, displayTransform, animationFrameAt, frameTextureTransform, loadExportedModel } from './asset-core.js';

// Browser approximation of native level-15 world light. Lambert divides white
// irradiance by pi; calibrate the front-facing surface to unit light without
// removing normal-based shading. These match makeLighting() in the viewer.
const FULL_BRIGHT_STUDIO_MULTIPLIER=Math.PI/(0.65+0.9*30/Math.sqrt(1444));
const FULL_BRIGHT_FRONT_GUI_MULTIPLIER=Math.PI/(0.65+0.9);

export async function readJson(url, revision, optional = false) {
  const response = await fetch(`${url}?revision=${revision}`, {cache:'no-store'});
  if (!response.ok || response.headers.get('content-type')?.includes('html')) {
    // Vite's SPA fallback returns HTML with status 200 for absent public files.
    if (optional && (response.status===404 || response.headers.get('content-type')?.includes('html'))) return null;
    throw new Error(`Missing exported file: ${url}`);
  }
  try { return await response.json(); }
  catch { throw new Error(`Invalid JSON in ${url}`); }
}

export function applyDisplay(group, model, context) {
  const transform = displayTransform(model,context);
  group.position.fromArray(transform.translation);
  group.rotation.set(...transform.rotation.map(THREE.MathUtils.degToRad),'XYZ');
  group.scale.fromArray(transform.scale);
  group.updateMatrixWorld(true);
}

/** Use native 0..1 block coordinates for item-node matrices, then centre for display. */
export function exportedFaceGeometry(element, direction, face, transformation) {
  const data=faceGeometry(direction,element.from,element.to,face.uv,face.rotation);
  const geometry=new THREE.BufferGeometry();
  const positions=data.positions.flatMap(point=>rotatePoint(point,element.rotation).map(n=>n/16));
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(data.uv.flat(),2));
  geometry.setIndex([0,1,2,0,2,3]);
  geometry.computeVertexNormals();
  // Matrix4.set consumes row-major values; applyMatrix4 also inverse-transposes
  // normals, as the native PoseStack does (including sheared guide quads).
  if (transformation) geometry.applyMatrix4(new THREE.Matrix4().set(...transformation));
  geometry.scale(16,16,16);
  geometry.translate(-8,-8,-8);
  return geometry;
}

export async function loadAsset(id, revision = Date.now(), selection={}, assetBase='') {
  const asset = await loadExportedModel(id,url=>readJson(url,revision),selection,assetBase);
  const textureLocations = [...new Set(asset.parts.flatMap(part=>part.model.elements.flatMap(element=>Object.values(element.faces).map(face=>resolveTexture(face.texture,part.model.textures)))))];
  const textures = new Map();
  const materials = new Map();
  const group = new THREE.Group();
  group.name = id;
  const animations = [];
  const textureDetails = [];
  try {
    await Promise.all(textureLocations.map(async location => {
      const url = resourceUrl(location,'textures',assetBase);
      const texture = await new THREE.TextureLoader().loadAsync(`${url}?revision=${revision}`).catch(()=>{ throw new Error(`Missing or invalid exported texture: ${url}`); });
      textures.set(location,texture);
      texture.colorSpace = THREE.SRGBColorSpace;
      texture.minFilter = THREE.NearestFilter;
      texture.magFilter = THREE.NearestFilter;
      texture.generateMipmaps = false;
      texture.wrapS = texture.wrapT = THREE.ClampToEdgeWrapping;
      const {width,height} = texture.image;
      const metadata = await readJson(`${url}.mcmeta`,revision,true);
      const animation = parseAnimation(metadata,width,height);
      if (animation) animations.push({texture,animation,frame:-1});
      textureDetails.push({location,url,width,height,animation});
    }));

    for (const part of asset.parts) for (const [index,element] of part.model.elements.entries()) {
      const elementGroup = new THREE.Group();
      elementGroup.name = element.name ?? `Element ${index+1}`;
      for (const [direction,face] of Object.entries(element.faces)) {
        const location = resolveTexture(face.texture,part.model.textures);
        const emission=element.light_emission ?? 0;
        const materialKey = `${location}:${element.shade!==false}:${emission}`;
        if (!materials.has(materialKey)) {
          const Material = element.shade===false ? THREE.MeshBasicMaterial : THREE.MeshLambertMaterial;
          const material=new Material({map:textures.get(location),alphaTest:0.1,side:THREE.FrontSide,transparent:false});
          material.userData.emission=emission;
          materials.set(materialKey,material);
        }
        const geometry = exportedFaceGeometry(element,direction,face,part.transformation);
        const mesh = new THREE.Mesh(geometry,materials.get(materialKey));
        mesh.name = `${elementGroup.name} / ${direction}`;
        elementGroup.add(mesh);
      }
      group.add(elementGroup);
    }
    const result = {...asset,group,textures,materials,animations,textureDetails: textureDetails.sort((a,b)=>a.location.localeCompare(b.location))};
    updateAnimation(result,0);
    return result;
  } catch (error) {
    group.traverse(object=>object.geometry?.dispose());
    materials.forEach(material=>material.dispose());
    textures.forEach(texture=>texture.dispose());
    throw error;
  }
}

export function updateAnimation(asset, elapsedMs) {
  let changed = false;
  for (const entry of asset.animations) {
    const frame = animationFrameAt(entry.animation,elapsedMs);
    if (frame===entry.frame) continue;
    const {repeat,offset} = frameTextureTransform(entry.animation,frame);
    entry.texture.repeat.fromArray(repeat);
    entry.texture.offset.fromArray(offset);
    entry.frame = frame;
    changed = true;
  }
  return changed;
}

export function updateMaterialLighting(asset, dimSurroundings, fullBrightMultiplier=FULL_BRIGHT_STUDIO_MULTIPLIER) {
  if (!asset) return;
  for (const material of asset.materials.values()) {
    // Approximate the world-light floor separately from Lambert's directional
    // shading. Native level 15 receives calibrated full light without adding
    // emissive color, bloom, or light sources to the scene.
    const multiplier=material.userData.emission===15 ? fullBrightMultiplier : dimSurroundings ? 0.18 : 1;
    material.color.setScalar(multiplier);
  }
}

export function withInventoryLighting(asset, dimSurroundings, render) {
  // Minecraft's GUI item path supplies full world light independently of the
  // held/world environment. Shared materials must be restored after this pass.
  const calibration=asset.model?.gui_light==='front' ? FULL_BRIGHT_FRONT_GUI_MULTIPLIER : FULL_BRIGHT_STUDIO_MULTIPLIER;
  updateMaterialLighting(asset,false,calibration);
  try { return render(); }
  finally { updateMaterialLighting(asset,dimSurroundings); }
}

export function disposeAsset(asset) {
  if (!asset) return;
  asset.group.traverse(object=>object.geometry?.dispose());
  asset.materials.forEach(material=>material.dispose());
  asset.textures.forEach(texture=>texture.dispose());
}
