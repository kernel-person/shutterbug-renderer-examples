import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {loadAsset,applyDisplay,disposeAsset} from './asset-loader.js';

const stage=document.querySelector('#stage'),scene=new THREE.Scene();
scene.background=new THREE.Color('#1c2427');
const renderer=new THREE.WebGLRenderer({antialias:true});renderer.setPixelRatio(devicePixelRatio);stage.append(renderer.domElement);
const camera=new THREE.PerspectiveCamera(38,1,.1,1000),controls=new OrbitControls(camera,renderer.domElement);
const light=new THREE.DirectionalLight(0xffffff,2.4);light.position.set(18,35,30);scene.add(light,new THREE.AmbientLight(0xffffff,1.8));
const grid=new THREE.GridHelper(128,8,0x7e8a77,0x404c47);scene.add(grid);
const thumb=new THREE.WebGLRenderer({canvas:document.querySelector('#inventory'),alpha:true,antialias:false});thumb.setSize(64,64,false);
const iconScene=new THREE.Scene(),iconCamera=new THREE.OrthographicCamera(-16,16,16,-16,.1,200);iconCamera.position.set(0,0,100);
const iconLight=new THREE.DirectionalLight(0xffffff,2.4);iconLight.position.set(-10,20,40);iconScene.add(iconLight,new THREE.AmbientLight(0xffffff,1.8));
let asset,icon,dim=false,radius=50,center=new THREE.Vector3(),revision=0;
function view(kind='oblique') {
  const dirs={front:[0,0,1],back:[0,0,-1],side:[1,0,0],oblique:[1,.65,1.5]};
  camera.position.copy(center).add(new THREE.Vector3(...dirs[kind]).normalize().multiplyScalar(radius));controls.target.copy(center);controls.update();
}
async function select(){
  const token=++revision;document.querySelector('#error').textContent='';
  try {
    const loaded=await loadAsset(document.querySelector('#asset').value,1);
    if(token!==revision){disposeAsset(loaded);return;}
    if(asset){scene.remove(asset.group);disposeAsset(asset);}if(icon)iconScene.remove(icon);
    asset=loaded;applyDisplay(asset.group,asset.model,'fixed');scene.add(asset.group);
    const bounds=new THREE.Box3().setFromObject(asset.group);bounds.getCenter(center);radius=Math.max(...bounds.getSize(new THREE.Vector3()).toArray())*2.3;
    grid.position.y=bounds.min.y-.05;
    icon=asset.group.clone();applyDisplay(icon,asset.model,'gui');iconScene.add(icon);
    document.querySelector('#status').textContent=asset.group.children.length+' native elements · 64px inventory';view();
  }catch(error){document.querySelector('#error').textContent=String(error);}
}
document.querySelector('#asset').addEventListener('change',select);
document.querySelectorAll('[data-view]').forEach(button=>button.onclick=()=>view(button.dataset.view));
document.querySelector('#light').onclick=()=>{dim=!dim;scene.traverse(o=>{if(o.isLight)o.intensity*=dim?.22:1/.22;});};
function resize(){renderer.setSize(stage.clientWidth,stage.clientHeight);camera.aspect=stage.clientWidth/stage.clientHeight;camera.updateProjectionMatrix();}
window.addEventListener('resize',resize);resize();select();
renderer.setAnimationLoop(()=>{controls.update();renderer.render(scene,camera);thumb.render(iconScene,iconCamera);});
